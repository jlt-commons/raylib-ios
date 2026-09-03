(ns raylib.live-cider
  "raylib.live plus the cider-nrepl op set, for an editor rather than a script.

  Build this ONLY under the :cider alias, which is where the dependency lives:

      NS=raylib.live-cider TARGET=device ALIAS=:cider sh tools/ios/build.sh
      CIDER=1 jolt live                                 # the same, via the task

  Without the alias this namespace cannot compile, and that is deliberate. The
  default build has no dependencies at all, which is worth keeping: it is why
  this project needs no private repository and why its whole native story is
  two static archives. jolt-lang/nrepl brings orchard, compliment and
  spec.alpha with it, and about six megabytes.

  What it buys, over the built-in clone/describe/eval/load-file/close:
  completions, `info` and `eldoc`, the namespace browser, macroexpansion,
  apropos, the test ops and stacktrace analysis. Worth it from an editor,
  pointless from `nrepl-eval`.

  The two requires below are NOT unused. deps.edn's :nrepl/middleware key is
  read by the `jolt nrepl-server` subcommand only; what puts middleware into a
  cross-compiled binary is requiring the namespaces so jolt build emits them,
  and then naming the symbols at start. Deleting either require produces a
  binary whose middleware silently is not there.

  Verified on an iPhone 17 Pro over the app's own nREPL: cider.nrepl loads and
  answers, alongside the gallery running at 58.8 fps."
  (:require [cider.nrepl]        ; see the docstring: not unused
            [nrepl.middleware]   ; see the docstring: not unused
            [raylib.gallery :as gallery]
            [raylib.live :as live]))

(def middleware
  "The symbols jolt.nrepl composes over its built-in handler, in order."
  ['nrepl.middleware/default-middleware
   'cider.nrepl/cider-middleware])

(defn -main [& _]
  (println "live: os.name" (pr-str (System/getProperty "os.name"))
           "os.arch" (pr-str (System/getProperty "os.arch")))
  (live/start-nrepl! middleware)
  (gallery/-main))
