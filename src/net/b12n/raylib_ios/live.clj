(ns net.b12n.raylib-ios.live
  "The gallery with an nREPL listening, so a running app on the phone can be
  inspected and driven from an editor.

  Dev only, and deliberately so. App Store rule 2.5.2 says an app may not
  download, install or execute code, and an nREPL is exactly that: a shipped
  build carries no listener. Everything else in this project is bundled data
  interpreted by a signed kernel, which is the shape the rule permits.

  jolt.nrepl binds loopback only, so reaching it means forwarding a port over
  USB:

      NS=net.b12n.raylib-ios.live TARGET=device jolt build-app
      UDID=... CONSOLE=0 jolt deploy
      jolt proxy                                  # iproxy, in another terminal
      tools/ios/nrepl-eval 7889 '(net.b12n.raylib-ios.host/state)'

  An eval runs on jolt.nrepl's accept thread while the main thread is parked
  inside SDL_UIKitRunApp, and raylib and SDL are main-thread-affine. So read
  freely, and put anything that touches raylib through
  net.b12n.raylib-ios.host/on-next-frame!, which runs it at the top of the next frame:

      ;; safe: reads a value the loop refreshes every frame
      (:gstate (net.b12n.raylib-ios.host/state))

      ;; safe: runs on the main thread, inside the frame
      (net.b12n.raylib-ios.host/on-next-frame! #(net.b12n.raylib-ios.host/set-target-fps 30))

      ;; NOT safe: calls raylib from the nREPL thread
      (net.b12n.raylib-ios.host/set-target-fps 30)"
  (:require [net.b12n.raylib-ios.gallery :as gallery]
            [net.b12n.raylib-ios.nrepl :as nrepl]))

(defn -main [& _]
  (nrepl/announce-platform!)
  (nrepl/start-nrepl! nil)
  (gallery/-main))
