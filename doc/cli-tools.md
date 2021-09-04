# CLI Tools Usage

If you are using at least version 1.10.3.933 of the Clojure CLI, you can install `depstar` as a "tool" instead of updating your `deps.edn` file and then invoke it using the following commands:

```bash
clojure -Ttools install com.github.seancorfield/depstar '{:git/tag "v2.1.297"}' :as depstar
# make an uberjar:
clojure -Tdepstar uberjar :jar MyProject.jar
# make a thin JAR:
clojure -Tdepstar jar :jar MyLib.jar
```

`depstar`'s `deps.edn` has a `:tools/usage` key that specifies the default namespace is `hf.depstar` so, in addition to `jar` and `uberjar` above, the following commands are also available:
* `aot` -- perform AOT compilation into a `:target-dir` directory,
* `pom` -- create/sync a `pom.xml`, optionally into a `:target-dir` directory,
* `build` -- build a JAR file -- either `:jar-type :thin` or `:jar-type :uber` -- from the project and any `:target-dir` directory.

See [Usage with `tools.build`](tools-build.md) for more details of all these tasks. See [All the Options](options.md) for the full list of options that these `depstar` commands accept.

You can ask the CLI to show the documentation for the installed `depstar` "tool":

```bash
# show all the documentation:
clojure -A:deps -Tdepstar help/doc
# show documentation just for the pom function:
clojure -A:deps -Tdepstar help/doc :fn pom
```
