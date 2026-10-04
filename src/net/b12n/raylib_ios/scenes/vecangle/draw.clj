(ns net.b12n.raylib-ios.scenes.vecangle.draw
  "The draw-scene! method for the `:vecangle` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.vecangle :as vang]))

(defmethod draw-scene! :vecangle [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 26 28 36 255))
  (let [{:keys [cx cy arc thick label-size w h]
         :as d} (vang/dimensions m)
        {:keys [a b]} (vang/vectors d t)
        ba (vang/bearing a) bb (vang/bearing b)
        turn (vang/signed-between ba bb)
        ;; draw-ring wants start below end, so a negative turn sweeps the other
        ;; way round rather than drawing nothing
        [from to] (if (neg? turn) [(+ ba turn) ba] [ba (+ ba turn)])
        arm (fn [[vx vy] colour]
              (rl/draw-line-ex cx cy (+ cx vx) (+ cy vy) thick colour))]
    ;; Alpha 150, not 70. At 70 over this background the wedge was almost
    ;; invisible for the small angles the readout spends most of its time on,
    ;; which are exactly the ones worth being able to see.
    (rl/draw-ring cx cy 0.0 arc from to 48 (rl/rgba 253 249 0 150))
    (arm a (rl/rgba 102 191 255 255))
    (arm b (rl/rgba 255 109 194 255))
    (rl/draw-circle (int cx) (int cy) (float (* 0.010 w)) rl/RAYWHITE)
    (rl/draw-text (str (int turn) " degrees")
                  (int (* 0.08 w)) (int (- h (* 0.22 h)))
                  label-size rl/RAYWHITE)
    (rl/draw-text (if (neg? turn) "anticlockwise" "clockwise")
                  (int (* 0.08 w)) (int (- h (* 0.22 h) (- (+ label-size 14))))
                  label-size (rl/rgba 150 150 160 255))))
