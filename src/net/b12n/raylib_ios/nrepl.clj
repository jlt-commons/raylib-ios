(ns net.b12n.raylib-ios.nrepl
  "The nREPL half of a live build, apart from the gallery.

  Split out of net.b12n.raylib-ios.live so a live build of a single scene
  (net.b12n.raylib-ios.runner.live) can start the same listener without
  requiring the gallery, which would pull every scene into its binary. See
  net.b12n.raylib-ios.live for what an eval may safely touch and why this is
  dev only."
  (:require [jolt.nrepl]))

(def default-port
  "The phone's own loopback port. 7888 by convention, matching the notebooks."
  7888)

(defn port
  "The nREPL port, from RAYLIB_NREPL_PORT if the launcher set one.

  An iOS app inherits no shell environment, so this arrives through devicectl,
  which forwards any DEVICECTL_CHILD_-prefixed variable in the caller's
  environment into the launched process. tools/ios/deploy.sh sets
  DEVICECTL_CHILD_RAYLIB_NREPL_PORT when DEVICE_PORT is given.

  A value that is not a number is reported and ignored rather than crashing the
  launch, since losing the whole app to a typo in a port would be a poor trade."
  []
  (if-let [s (System/getenv "RAYLIB_NREPL_PORT")]
    (try (let [n (Integer/parseInt (str s))]
           (if (< 0 n 65536)
             n
             (do (println "live: RAYLIB_NREPL_PORT" (pr-str s) "out of range, using" default-port)
                 default-port)))
         (catch :default _
           (println "live: RAYLIB_NREPL_PORT" (pr-str s) "is not a number, using" default-port)
           default-port))
    default-port))

(defn start-nrepl!
  "Start the nREPL, optionally composing `middleware` over jolt.nrepl's built-in
  handler. nil means the built-in ops alone, which is what this namespace uses
  and what keeps the default build free of dependencies.

  A failure is reported and swallowed on purpose: losing the whole app because
  a REPL could not bind would be a poor trade, and the app is still worth
  looking at without one."
  [middleware]
  (let [p (port)]
    (try
      (if (seq middleware)
        (jolt.nrepl/start p middleware)
        (jolt.nrepl/start p))
      (println "live: nREPL on the phone's 127.0.0.1:" p
               (if (seq middleware) "(with middleware)" "(built-in ops)")
               "- forward it with: jolt proxy")
      (catch :default e
        (println "live: jolt.nrepl/start failed:" (ex-message e))
        (println "live: continuing without a REPL; the app still runs")))))

(defn announce-platform!
  "Print the runtime's os.name and os.arch.

  Printed rather than assumed. jolt picks its socket constants and sockaddr
  layout from os.name, and it reported \"Linux\" on iOS until 0.8.0 fixed it
  for portable-bytecode builds, which is what this is. When it was wrong,
  Darwin's socket() answered EINVAL and jolt.nrepl/start died with
  \"socket() failed\"; the demo this port follows had to own its listener. If
  that line ever comes back, that is why."
  []
  (println "live: os.name" (pr-str (System/getProperty "os.name"))
           "os.arch" (pr-str (System/getProperty "os.arch"))))
