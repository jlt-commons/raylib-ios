(ns net.b12n.raylib-ios.scenes.flowfield.draw
  "The draw-scene! method for the `:flowfield` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.flowfield :as flow]))

(defmethod draw-scene! :flowfield [_ {:keys [parts]} _]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (count parts)]
    (loop [i 0]
      (when (< i n)
        (let [p (nth parts i)
              trail (:trail p)
              tn (count trail)
              [cr cg cb ca] (flow/trail-colour (:angle p))
              packed (rl/rgba cr cg cb ca)]
          (loop [j 1]
            (when (< j tn)
              (let [a (nth trail (dec j)) b (nth trail j)]
                (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1)) packed))
              (recur (inc j)))))
        (recur (inc i))))))
