;; copyright (c) 2019-2021 sean corfield

(ns ^:no-doc hf.depstar.aot
  "AOT compilation logic."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.deps.alpha :as t]
            [clojure.tools.namespace.find :as tnsf]
            [hf.depstar.files :as files])
  (:import (java.io InputStreamReader BufferedReader)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(set! *warn-on-reflection* true)

(defn- compile-arguments
  "Given a namespace to compile (a symbol), a vector of JVM
  options to apply, the classpath, a symbol for the compile
  function, and the temporary directory to write the classes
  to, return the process arguments that would compile it
    (java -cp ...).
  If compile-fn is omitted (nil), clojure.core/compile is
  used. Otherwise, a require of the namespace is added and
  a call of resolve is added."
  [ns-syms jvm-opts cp compile-fn tmp-c-dir]
  (let [java     (or (System/getenv "JAVA_CMD") "java")
        windows? (-> (System/getProperty "os.name")
                     (str/lower-case)
                     (str/includes? "windows"))
        comp-req (when-let [comp-ns (and compile-fn (namespace compile-fn))]
                   (str "(require,'" comp-ns "),"))
        comp-fn  (if compile-fn
                   (str "(resolve,'" compile-fn ")")
                   "compile")
        compnses (for [ns-sym ns-syms]
                   (str "(println,'Compiling,'" (name ns-sym) ")"
                        "(" comp-fn ",'" (name ns-sym) ")"))
        args     (-> [java]
                     (into jvm-opts)
                     (into ["-cp"
                            (str/join (System/getProperty "path.separator") cp)
                            "clojure.main"
                            "-e"
                            (str "(binding,[*compile-path*,"
                                 (pr-str (str tmp-c-dir))
                                 "]," comp-req
                                 (str/join "," compnses)
                                 ")")]))]
    (if windows?
      (mapv #(str/replace % "\"" "\\\"") args)
      args)))

(defn- compile-them
  "Given a sequence of namespaces to compile (symbols), a
  vector of JVM options to apply, the classpath, a symbol
  for the compile function, and the directory to write the
  classes to, compile the namespaces and return a Boolean
  indicating any failures (the failures will be printed to
  standard error)."
  [ns-syms jvm-opts cp compile-fn tmp-c-dir]
  (let [p (.start
           (ProcessBuilder.
            ^"[Ljava.lang.String;"
            (into-array
             String
             (compile-arguments ns-syms jvm-opts cp compile-fn tmp-c-dir))))]
    (with-open [rdr (BufferedReader.
                     (InputStreamReader.
                      (.getInputStream p)))]
      ;; prior to batching the compiles, we just printed the
      ;; Compiling ns ... line and no other stdout so we try
      ;; to mimic that here:
      (doseq [line (line-seq rdr) :when (str/starts-with? line "Compiling ")]
        (println line "...")))
    (.waitFor p)
    ;; this may lead to a slightly strange experience where
    ;; all the standard output appears for all the compiles
    ;; followed by any standard error for the batch but it
    ;; should be sufficient for folks to debug problems:
    (let [stderr (slurp (.getErrorStream p))]
      (when (seq stderr) (println stderr))
      (if (zero? (.exitValue p))
        false ; no AOT failure
        (do
          (println "Compilation failed!")
          true)))))

(defn task*
  "Handling of AOT compilation as a -X task.

  Inputs:
  * aot
  * basis
  * classpath
  * compile-aliases
  * compile-batch (defaults to the size of compile-ns)
  * compile-fn
  * compile-ns
  * delete-on-exit
  * jar-type
  * main-class
  * paths-only
  * target-dir

  Outputs:
  * classpath-roots
  "
  [{:keys [aot basis classpath compile-aliases compile-batch compile-fn
           compile-ns delete-on-exit jar-type jvm-opts main-class
           paths-only repro target-dir]
    :as options}]
  (let [jvm-opts    (if (sequential? jvm-opts) (vec jvm-opts) [])

        c-basis     (if-let [c-aliases (not-empty compile-aliases)]
                      (t/create-basis (cond-> {:aliases c-aliases}
                                        repro (assoc :user nil)))
                      basis)

        cp          (or (some-> classpath (files/parse-classpath))
                        (if (and paths-only (= :thin jar-type))
                          (vec (into (set (:paths basis))
                                     (-> basis :classpath-args :extra-paths)))
                          (:classpath-roots basis)))
        c-cp        (or (some-> classpath (files/parse-classpath))
                        (if (and paths-only (= :thin jar-type))
                          (vec (into (set (:paths c-basis))
                                     (-> c-basis :classpath-args :extra-paths)))
                          (:classpath-roots c-basis)))

        ;; expand :all and regex string using tools.namespace:
        dir-file-ns (comp
                     (filter #(= :directory (files/classify %)))
                     (map io/file)
                     (mapcat tnsf/find-namespaces-in-dir))
        compile-ns  (cond (= :all compile-ns)
                          (into [] dir-file-ns c-cp)
                          (sequential? compile-ns)
                          (let [patterns (into []
                                               (comp (filter string?)
                                                     (map re-pattern))
                                               compile-ns)]
                            (cond-> (filterv symbol? compile-ns)
                              (seq patterns)
                              (into (comp
                                     dir-file-ns
                                     (filter #(files/included? (str %) patterns)))
                                    c-cp)))
                          :else
                          (when compile-ns
                            (println ":compile-ns should be a vector (or :all) -- ignoring")))

        ;; force AOT if compile-ns explicitly requested:
        do-aot      (or aot (seq compile-ns))
        ;; compile main-class at least (if also do-aot):
        compile-ns  (if (and do-aot main-class)
                      (into (or compile-ns []) [main-class])
                      compile-ns)
        _           (when (and aot (empty? compile-ns))
                      (println ":aot true but no namespaces to compile -- ignoring"))
        tmp-c-dir   (when do-aot
                      (if target-dir
                        (let [classes (files/path (str target-dir "/classes"))]
                          (Files/createDirectories classes (make-array FileAttribute 0))
                          classes)
                        (let [classes (Files/createTempDirectory "depstarc" (make-array FileAttribute 0))]
                          (when delete-on-exit
                            (files/delete-path-on-exit classes))
                          classes)))

        cp          (into (cond-> [] do-aot (conj (str tmp-c-dir))) cp)
        c-cp        (into (cond-> [] do-aot (conj (str tmp-c-dir))) c-cp)]

    (when do-aot
      (when (some #(compile-them % jvm-opts c-cp compile-fn tmp-c-dir)
                  (partition-all (or compile-batch (count compile-ns)) compile-ns))
        (throw (ex-info "AOT compilation failed" {}))))
    (assoc options :classpath-roots cp)))
