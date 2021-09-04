# `depstar` and CI Environments

If you are using `depstar` in a CI environment, you are probably trying to ensure that you can pre-cache dependencies so they are not downloaded on every run. You can use the `-P` option on the `clojure` CLI script to "prepare" dependencies: it will fetch all the dependencies and compute the classpath but will not run main/exec functions.

Since `depstar` fetches dependencies _at runtime_, you will likely need _two_ such "prepare" commands: one for your project's dependencies and one for `depstar` itself, since the recommended way to use `depstar` is via an alias that uses `:replace-deps`.

If you provide any `:aliases` to `depstar` when building JARs, remember to use those in the "prepare" command for your project.

If you build your JAR like this:

```bash
clojure -X:uberjar
```

and your `:uberjar` alias has `:aliases` in the `:exec-args` of `[:extra :stuff]` then you will need the following two "prepare" commands for your CI pre-cache step:

```bash
# cache depstar's own dependencies:
clojure -P -X:uberjar
# cache your project's build dependencies:
clojure -P -A:extra:stuff
```

If your CI solution doesn't cache `~/.m2/repository` and expects you to use a custom directory, you can tell the Clojure CI to use that via `-Sdeps`:

```bash
# cache depstar's own dependencies:
clojure -Sdeps '{:mvn/local-repo "my-m2"}' -P -X:uberjar
# cache your project's build dependencies:
clojure -Sdeps '{:mvn/local-repo "my-m2"}' -P -A:extra:stuff
```

In such cases, you will also need to tell `depstar` to use that local repo when it builds a basis internally, via `depstar`'s `:mvn/local-repo` option:

```bash
clojure -Sdeps '{:mvn/local-repo "my-m2"}' -X:uberjar :mvn/local-repo '"my-m2"' :aliases '[:extra :stuff]'
```

The `-Sdeps` here tells the Clojure CLI to use `my-m2` as the local repo and the `:mvn/local-repo` option tells `depstar` to use `my-m2` as the local repo whenever it creates a basis internally.
