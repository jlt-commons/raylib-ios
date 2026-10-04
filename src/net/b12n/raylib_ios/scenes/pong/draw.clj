(ns net.b12n.raylib-ios.scenes.pong.draw
  "The draw-scene! method for the `:pong` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.pong :as pong]))

(defmethod draw-scene! :pong [_ {:keys [ly ry ls rs over? winner]
                                 :as state} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack pong/background-colour))
        dims (pong/dimensions m)
        {:keys [score-size hint-size msg-size msg-y]} dims
        rect (fn [[x y w h] colour]
               (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack colour)))]
    (doseq [r (pong/centre-dashes dims)]
      (rect r pong/dash-colour))
    (rect (pong/paddle-rect dims :player ly) pong/paddle-colour)
    (rect (pong/paddle-rect dims :cpu ry) pong/paddle-colour)
    (rect (pong/ball-rect dims state) pong/ball-colour)
    (rl/draw-text (str ls) (:you-score-x dims) (:you-score-y dims) score-size
                  (pack pong/text-colour))
    (rl/draw-text (str rs) (:cpu-score-x dims) (:cpu-score-y dims) score-size
                  (pack pong/text-colour))
    (rl/draw-text pong/you-line (:you-hint-x dims) (:you-hint-y dims) hint-size
                  (pack pong/hint-colour))
    (rl/draw-text pong/cpu-line (:cpu-hint-x dims) (:cpu-hint-y dims) hint-size
                  (pack pong/hint-colour))
    (when over?
      (let [line (if (= winner :player) pong/player-wins-line pong/cpu-wins-line)]
        (rl/draw-text line (pong/msg-x dims line) msg-y msg-size
                      (pack pong/win-colour))))))
