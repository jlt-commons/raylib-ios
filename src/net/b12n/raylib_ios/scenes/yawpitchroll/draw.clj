(ns net.b12n.raylib-ios.scenes.yawpitchroll.draw
  "The draw-scene! method for the `:yawpitchroll` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.yawpitchroll :as ypr]))

(def ^:private ypr-cache
  "The last `[screen dims grid]` for `:yawpitchroll`. The camera never moves, so
  the layout, the text sizes and the grid depend on the screen alone."
  (atom nil))

(defn- ypr-layout [m]
  (let [screen (:screen m)
        [cached-screen dims grid] @ypr-cache]
    (if (= screen cached-screen)
      [dims grid]
      (let [dims (ypr/dimensions m host-measure)
            grid (ypr/grid-list (ypr/camera dims) dims)]
        (reset! ypr-cache [screen dims grid])
        [dims grid]))))

(defmethod draw-scene! :yawpitchroll [_ state {:keys [m safe]}]
  (clear-to! ypr/background-colour)
  (let [[dims grid] (ypr-layout m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        [px py pw ph] (:panel dims)
        track (pack ypr/track-colour)
        fill (pack ypr/fill-colour)
        tick (pack ypr/tick-colour)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! (ypr/scene-list grid state dims))))
    (draw-caption! (:title dims) ypr/title-colour)
    (rl/draw-rectangle (int px) (int py) (int pw) (int ph) (pack ypr/panel-colour))
    (draw-caption! (:hint dims) ypr/hint-colour)
    (doseq [[i {:keys [label value-x value-y value-size]
                [tx ty tw th] :track
                [kx ky kw kh] :tick}] (map-indexed vector (:gauges dims))
            :let [v (nth [(:yaw state) (:pitch state) (:roll state)] i)
                  [fx fy fw fh] (ypr/gauge-fill dims i v)]]
      (draw-caption! label ypr/label-colour)
      (rl/draw-rectangle (int tx) (int ty) (int tw) (int th) track)
      (rl/draw-rectangle (int fx) (int fy) (int fw) (int fh) fill)
      (rl/draw-rectangle (int kx) (int ky) (int kw) (int kh) tick)
      (draw-caption! {:s (ypr/value-text v)
                      :x value-x
                      :y value-y
                      :size value-size}
                     ypr/value-colour))
    (let [held (:held state)]
      (doseq [{:keys [id label rect]} (:buttons dims)
              :let [[bx by bw bh] rect]]
        (rl/draw-rectangle (int bx) (int by) (int bw) (int bh)
                           (pack (if (contains? held id) ypr/button-held-colour ypr/button-colour)))
        (draw-caption! label ypr/button-label-colour)))))
