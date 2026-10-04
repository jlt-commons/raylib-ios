(ns net.b12n.raylib-ios.scenes.clipbox.draw
  "The draw-scene! method for the `:clipbox` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene! stroke!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.clipbox :as clipbox]))

(defmethod draw-scene! :clipbox [_ {:keys [t]} {:keys [m safe]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [label-size w h]
         :as d} (clipbox/dimensions m)
        b (clipbox/box d t)
        [bx by bw bh] b]
    ;; The scene's own scissor, intersected with the host's rather than
    ;; replacing it. rlgl keeps one scissor rectangle, so BeginScissorMode here
    ;; takes over from the safe-region one entirely: without the intersection
    ;; this scene could paint its grid over the status bar.
    (when-let [[cx cy cw ch] (clipbox/clip-rect safe b)]
      (rl/begin-scissor-mode (int cx) (int cy) (int cw) (int ch))
      ;; The grid is walked inline rather than through clipbox/cells, which
      ;; returns a vector per cell plus a vector per colour: about 870
      ;; allocations a frame for 435 rectangles, and 49 fps. cells stays for the
      ;; tests. Every cell is still drawn, because the scissor doing the
      ;; clipping is the whole demonstration.
      (let [step (+ clipbox/cell clipbox/gap)]
        (loop [gy 0]
          (when (< gy (long (:h d)))
            (loop [gx 0]
              (when (< gx (long (:w d)))
                (rl/draw-rectangle gx gy clipbox/cell clipbox/cell
                                   (rl/rgba (mod (* gx 3) 256) (mod (* gy 5) 256) 180 255))
                (recur (+ gx step))))
            (recur (+ gy step)))))
      (rl/end-scissor-mode)
      ;; Put the host's own scissor back. Leaving the scene's in place would
      ;; clip everything drawn after this, including the Back button.
      (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe)))
    (stroke! (int bx) (int by) (int (+ bx bw)) (int by) (rl/rgba 230 41 55 255))
    (stroke! (int (+ bx bw)) (int by) (int (+ bx bw)) (int (+ by bh)) (rl/rgba 230 41 55 255))
    (stroke! (int (+ bx bw)) (int (+ by bh)) (int bx) (int (+ by bh)) (rl/rgba 230 41 55 255))
    (stroke! (int bx) (int (+ by bh)) (int bx) (int by) (rl/rgba 230 41 55 255))
    (rl/draw-text "only the box shows the grid"
                  (int (* 0.08 w)) (int (- h (* 0.10 h)))
                  label-size (rl/rgba 60 60 60 255))))
