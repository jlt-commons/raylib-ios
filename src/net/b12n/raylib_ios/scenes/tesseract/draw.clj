(ns net.b12n.raylib-ios.scenes.tesseract.draw
  "The draw-scene! method for the `:tesseract` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.tesseract :as tess]))

(defmethod draw-scene! :tesseract [_ {:keys [a]} {:keys [m]}]
  (rl/clear-background (rl/rgba 8 8 16 255))
  (let [pts (tess/points m a)
        es tess/edges
        n (count es)]
    (loop [k 0]
      (when (< k n)
        (let [e (nth es k)
              i (nth e 0) j (nth e 1)
              p (nth pts i) q (nth pts j)
              [r g b] (tess/edge-colour i j)]
          (rl/draw-line (int (nth p 0)) (int (nth p 1))
                        (int (nth q 0)) (int (nth q 1))
                        (rl/rgba r g b 255)))
        (recur (inc k))))))
