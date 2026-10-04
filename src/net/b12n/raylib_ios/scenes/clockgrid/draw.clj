(ns net.b12n.raylib-ios.scenes.clockgrid.draw
  "The draw-scene! method for the `:clockgrid` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.clockgrid :as cgrid]))

(defmethod draw-scene! :clockgrid [_ {:keys [current]} {:keys [m]}]
  (rl/clear-background (rl/rgba 8 12 28 255))
  ;; Three drafts, and the middle one is the interesting failure.
  ;;
  ;; The bezels were draw-ring at 20 segments: 120 vertices each, 17000 FFI
  ;; calls a frame for 144 of them, 6 fps. draw-circle-lines is ONE call and
  ;; draws the same circle. rlgl is the right tool when raylib has no call for
  ;; the shape, and the wrong one when it does. That alone took it to 50.
  ;;
  ;; Then the 288 hands were collected into a vector and handed to a single
  ;; batched rlgl call, to save the 864 calls that 288 separate begin/colour/end
  ;; triples cost. It measured 47. The batch saved the calls and paid for them
  ;; in 288 vector allocations, which is this project's oldest lesson arriving
  ;; again: the allocation costs more than the call.
  ;;
  ;; So the batch stays and the vector goes. One begin, one colour, one end, and
  ;; the vertices emitted straight from the loop with nothing allocated between.
  (let [{:keys [x0 y0 step radius hand row-step pair-w]} (cgrid/dimensions m)
        ;; Bright enough to actually see. The first value was 42 48 74 on an
        ;; 8 12 28 ground, which at one pixel wide vanished entirely and left
        ;; the digits reading as loose dashes rather than as clock faces, which
        ;; is the whole idea of the scene.
        bezel (rl/rgba 74 88 140 255)
        half (* radius 0.08)]
    (dotimes [d 6]
      (let [pair (quot d 2) side (mod d 2)
            ox (+ x0 (* side pair-w)) oy (+ y0 (* pair row-step))]
        (dotimes [i cgrid/cells]
          (rl/draw-circle-lines (int (+ ox (* (mod i cgrid/cols) step) radius))
                                (int (+ oy (* (quot i cgrid/cols) step) radius))
                                (float radius) bezel))))
    (rl/rl-begin rl/RL-TRIANGLES)
    (rl/rl-color-4ub 255 249 196 255)
    (dotimes [d 6]
      (let [pair (quot d 2) side (mod d 2)
            ox (+ x0 (* side pair-w)) oy (+ y0 (* pair row-step))
            grid (nth current d)]
        (dotimes [i cgrid/cells]
          (let [cx (double (+ ox (* (mod i cgrid/cols) step) radius))
                cy (double (+ oy (* (quot i cgrid/cols) step) radius))
                cell (nth grid i)]
            (dotimes [k 2]
              (let [t (Math/toRadians (double (nth cell k)))
                    dx (Math/cos t) dy (Math/sin t)
                    x2 (+ cx (* hand dx)) y2 (+ cy (* hand dy))
                    px (* half dy) py (* half (- dx))]
                (rl/rl-vertex-2f (float (+ cx px)) (float (+ cy py)))
                (rl/rl-vertex-2f (float (- cx px)) (float (- cy py)))
                (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                (rl/rl-vertex-2f (float (+ cx px)) (float (+ cy py)))
                (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                (rl/rl-vertex-2f (float (+ x2 px)) (float (+ y2 py)))))))))
    (rl/rl-end)))
