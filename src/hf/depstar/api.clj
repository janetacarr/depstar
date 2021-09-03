;; copyright (c) 2021 sean corfield, all rights reserved

(ns hf.depstar.api
  "An API that exposes functions that are compatible with,
  and intended to be a drop-in replacement for, functions
  in `tools.build`."
  (:require [clojure.tools.deps.alpha :as t]
            [hf.depstar.uberjar :as impl]))

(defn- resolve-path
  "Delegate to tools.build to resolve a path. This is a
  dynamic dependency, since this API is only intended to
  be used from a tools.build-based process.

  Note that tools.build returns a File but depstar wants
  a file path (String)."
  [path]
  (let [^java.io.File file
        ((requiring-resolve 'clojure.tools.build.api/resolve-path) path)]
    (.getCanonicalPath file)))

(defn jar
  "Given `:class-dir`, `:jar-file`, and optionally `:main`,
  build a (thin) JAR file from the contents of the specified
  directory, with the specified main class in the manifest."
  [{:keys [class-dir jar-file main ; tools.build.api/jar
           aliases mvn/local-repo repro] ; our additional options
    :as options}]
  (impl/task* (merge (dissoc options :class-dir :jar-file :main)
                     {:basis
                      (t/create-basis (cond-> {}
                                        local-repo
                                        (assoc-in [:extra :mvn/local-repo] local-repo)
                                        (seq aliases)
                                        (assoc :aliases aliases)
                                        repro
                                        (assoc :user nil)))
                      :classpath-roots [(resolve-path class-dir)]
                      :jar             (resolve-path jar-file)
                      :main-class      main
                      ;; control options:
                      :jar-type        :thin
                      ;; assume pom.xml is in the class-dir!
                      :no-pom          true}))
  ;; return nil like tools.build.api/jar:
  nil)

(defn uber
  "Given `:class-dir`, `:uber-file`, and optionally `:basis`
  and `:main`, build an uber JAR file from the deps (in the
  specified basis or the default project basis) and the
  contents of the specified directory, with the specified
  main class in the manifest."
  [{:keys [basis class-dir main uber-file ; tools.build.api/uber
           aliases mvn/local-repo repro] ; our additional options
    :as options}]
  (let [basis (or basis
                  (t/create-basis (cond-> {}
                                    local-repo
                                    (assoc-in [:extra :mvn/local-repo] local-repo)
                                    (seq aliases)
                                    (assoc :aliases aliases)
                                    repro
                                    (assoc :user nil))))]
    (impl/task* (merge (dissoc options :basis :class-dir :uber-file :main)
                       {:basis           basis
                        :classpath-roots (conj (:classpath-roots basis)
                                               (resolve-path class-dir))
                        :jar             (resolve-path uber-file)
                        :main-class      main
                        ;; control options:
                        :jar-type        :uber
                        ;; assume pom.xml is in the class-dir!
                        :no-pom          true})))
  ;; return nil like tools.build.api/uber:
  nil)
