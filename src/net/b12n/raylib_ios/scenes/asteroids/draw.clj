(ns net.b12n.raylib-ios.scenes.asteroids.draw
  "The draw-scene! method for the `:asteroids` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.asteroids :as astr]))

(defmethod draw-scene! :asteroids [_ {:keys [ship bullets asteroids score lives over? invuln held]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack astr/background-colour))
        dims (astr/dimensions m)
        {:keys [thick bullet-r text-size score-x score-y msg-size msg-y msg2-size msg2-y]} dims
        line (fn [[x1 y1] [x2 y2] colour]
               (rl/draw-line-ex x1 y1 x2 y2 thick (pack colour)))
        outline (fn [pts colour]
                  (doseq [[a b] (map vector pts (concat (rest pts) [(first pts)]))]
                    (line a b colour)))]
    (doseq [a asteroids]
      (outline (astr/asteroid-points a) astr/asteroid-colour))
    (doseq [b bullets]
      (rl/draw-circle (int (:x b)) (int (:y b)) (float bullet-r) (pack astr/bullet-colour)))
    (when (astr/ship-visible? invuln)
      (let [[nose left right] (astr/ship-points dims ship)]
        (line nose left astr/ship-colour)
        (line left right astr/ship-base-colour)
        (line right nose astr/ship-colour)))
    (doseq [{:keys [id rect label label-x label-y label-size]} (:buttons dims)
            :let [[x y w h] rect]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h)
                         (pack (if (contains? held id) astr/button-held-colour astr/button-colour)))
      (rl/draw-text label label-x label-y label-size (pack astr/text-colour)))
    (rl/draw-text (astr/score-line score) score-x score-y text-size (pack astr/text-colour))
    (let [lives-line (astr/lives-line lives)]
      (rl/draw-text lives-line (astr/lives-x dims lives-line) score-y text-size (pack astr/text-colour)))
    (when over?
      (rl/draw-text astr/over-line (astr/centred-x dims msg-size astr/over-line) msg-y msg-size
                    (pack astr/over-colour))
      (rl/draw-text astr/restart-line (astr/centred-x dims msg2-size astr/restart-line) msg2-y msg2-size
                    (pack astr/text-colour)))))
