(ns net.b12n.raylib-ios.runner.live
  "net.b12n.raylib-ios.runner with an nREPL, for driving a single-scene app on
  the phone from an editor.

  Its own namespace rather than an option on `runner/run!`, so a release app
  never requires jolt.nrepl: a binary that does not require it does not carry
  it, and App Store rule 2.5.2 is about exactly that listener. Dev only, with
  the same cautions as net.b12n.raylib-ios.live: an eval runs on another thread
  while raylib is main-thread-affine, so anything that touches it goes through
  net.b12n.raylib-ios.host/on-next-frame!."
  (:require [net.b12n.raylib-ios.nrepl :as nrepl]
            [net.b12n.raylib-ios.runner :as runner]))

(defn live-run!
  "Start the nREPL, then `runner/run!` `scene-map`. Does not return."
  [scene-map]
  (nrepl/announce-platform!)
  (nrepl/start-nrepl! nil)
  (runner/run! scene-map))
