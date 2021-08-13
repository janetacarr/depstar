(ns build
  "depstar's build script.

  Also serves as an example of using hf.depstar.api as
  a drop-in replacement for jar and uber from tools.build.

  clojure -T:build run-tests

  clojure -T:build ci

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:require [clojure.tools.build.api :as b]
            [clojure.tools.deps.alpha :as t]
            [hf.depstar.api :as d]))

(def lib 'com.github.seancorfield/depstar)
(def version (format "2.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean "Remove the target folder." [_]
  (println "\nCleaning target...")
  (b/delete {:path "target"}))

(defn jar "Build the library JAR file." [_]
  (println "\nWriting pom.xml...")
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :scm {:tag (str "v" version)}
                :basis basis
                :src-dirs ["src"]})
  (println "Copying src...")
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (println (str "Building jar " jar-file "..."))
  ;; Use depstar's replacement for b/jar:
  (d/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn run-tests "Run regular tests." [_]
  (let [basis    (b/create-basis {:aliases [:test]})
        combined (t/combine-aliases basis [:test])
        cmds     (b/java-command {:basis     basis
                                  :java-opts (:jvm-opts combined)
                                  :main      'clojure.main
                                  :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit)
      (throw (ex-info "Tests failed" {})))))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts (run-tests) (clean) (jar)))

(defn prep "From tools.build Guide." [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :scm {:tag (str "v" version)}
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir}))

(defn uber "From tools.build Guide (but using depstar)." [_]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  ;; Use depstar's replacement for b/uber:
  (d/uber {:class-dir class-dir
           :uber-file uber-file :main 'hf.depstar.uberjar
           :basis basis}))
