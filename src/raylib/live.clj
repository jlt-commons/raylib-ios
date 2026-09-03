(ns raylib.live
  "The gallery with an nREPL listening, so a running app on the phone can be
  inspected and driven from an editor.

  Dev only, and deliberately so. App Store rule 2.5.2 says an app may not
  download, install or execute code, and an nREPL is exactly that: a shipped
  build carries no listener. Everything else in this project is bundled data
  interpreted by a signed kernel, which is the shape the rule permits.

  jolt.nrepl binds loopback only, so reaching it means forwarding a port over
  USB:

      NS=raylib.live TARGET=device jolt build-app
      UDID=... CONSOLE=0 jolt deploy
      jolt proxy                                  # iproxy, in another terminal
      tools/ios/nrepl-eval 7889 '(raylib.host/state)'

  An eval runs on jolt.nrepl's accept thread while the main thread is parked
  inside SDL_UIKitRunApp, and raylib and SDL are main-thread-affine. So read
  freely, and put anything that touches raylib through
  raylib.host/on-next-frame!, which runs it at the top of the next frame:

      ;; safe: reads a value the loop refreshes every frame
      (:gstate (raylib.host/state))

      ;; safe: runs on the main thread, inside the frame
      (raylib.host/on-next-frame! #(raylib.host/set-target-fps 30))

      ;; NOT safe: calls raylib from the nREPL thread
      (raylib.host/set-target-fps 30)"
  (:require [jolt.nrepl]
            [raylib.gallery :as gallery]
            [raylib.host :as rl]))

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

(defn -main [& _]
  ;; Printed rather than assumed. jolt picks its socket constants and sockaddr
  ;; layout from os.name, and it reported "Linux" on iOS until 0.8.0 fixed it
  ;; for portable-bytecode builds, which is what this is. When it was wrong,
  ;; Darwin's socket() answered EINVAL and jolt.nrepl/start died with
  ;; "socket() failed"; the demo this port follows had to own its listener. If
  ;; that line ever comes back, that is why.
  (println "live: os.name" (pr-str (System/getProperty "os.name"))
           "os.arch" (pr-str (System/getProperty "os.arch")))
  (let [p (port)]
    (try
      (jolt.nrepl/start p)
      (println "live: nREPL on the phone's 127.0.0.1:" p "- forward it with: jolt proxy")
      (catch :default e
        (println "live: jolt.nrepl/start failed:" (ex-message e))
        (println "live: continuing without a REPL; the gallery still runs"))))
  (gallery/-main))
