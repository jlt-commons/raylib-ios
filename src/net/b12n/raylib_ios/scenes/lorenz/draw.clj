(ns net.b12n.raylib-ios.scenes.lorenz.draw
  "The draw-scene! method for the `:lorenz` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.lorenz :as lor]))

(defmethod draw-scene! :lorenz [_ {:keys [points t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 12 14 22 255))
  ;; Everything the projection needs is pulled out to primitive locals and the
  ;; previous screen point is carried in the loop, so a frame allocates nothing
  ;; per segment. The first draft called lor/project and lor/trail-colour per
  ;; point, each returning a fresh vector, and ran at 18 fps. Same lesson as
  ;; flowfield and the draw loop before it: the allocation is the cost.
  (let [cam (lor/camera m t)
        cs (double (:cos cam)) sn (double (:sin cam))
        cx (double (:cx cam)) cy (double (:cy cam))
        f (double (:f cam)) dist (double (:distance cam))
        sc (double (:scale cam)) lift (double (:lift cam))
        n (count points)
        denom (double (max 1 n))]
    (loop [i 0 px 0.0 py 0.0 have-prev? false]
      (when (< i n)
        (let [p (nth points i)
              ax (* sc (double (nth p 0)))
              ay (* sc (- (double (nth p 2)) lift))
              az (* sc (double (nth p 1)))
              rx (- (* ax cs) (* az sn))
              rz (+ (* ax sn) (* az cs))
              d (- dist rz)]
          (if (> d 0.5)
            (let [k (/ f d)
                  sx (+ cx (* rx k))
                  sy (- cy (* ay k))]
              (when have-prev?
                (let [age (/ (double i) denom)]
                  (rl/draw-line (int px) (int py) (int sx) (int sy)
                                (rl/rgba (int (+ 90.0 (* 165.0 age)))
                                         (int (- 200.0 (* 130.0 age)))
                                         (int (- 255.0 (* 105.0 age)))
                                         255))))
              (recur (inc i) sx sy true))
            ;; behind the camera: drop the segment rather than drawing across
            (recur (inc i) 0.0 0.0 false)))))))
