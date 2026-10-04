(ns net.b12n.raylib-ios.scenes.mousepaint.draw
  "The draw-scene! method for the `:mousepaint` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure
                                                           outline!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.mousepaint :as mousepaint]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private mousepaint-cache
  "The last `[screen dims]` for `:mousepaint`. Its button text needs a measure,
  which depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- mousepaint-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @mousepaint-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (mousepaint/dimensions m host-measure)]
        (reset! mousepaint-cache [screen dims])
        dims))))

(defn- replay-mark!
  "One of the frame's marks, drawn into the canvas in its own pixels."
  [mark]
  (case (first mark)
    :clear (clear-to! (second mark))
    :stroke (let [[_ x0 y0 x1 y1 radius colour] mark
                  c (color colour)]
              (doseq [[x y] (mousepaint/stroke-points x0 y0 x1 y1 radius)]
                (rl/draw-circle (int x) (int y) (double radius) c)))))

(defmethod draw-scene! :mousepaint [_ state {:keys [m safe]}]
  (let [dims (mousepaint-dims m)
        {fx :x
         fy :y
         fw :w
         fh :h} (:field dims)
        rt (texture/target! :mousepaint :canvas {:w fw
                                                 :h fh
                                                 :depth? false})
        marks (:marks state)]
    ;; The canvas keeps its paint, so it is drawn into only by this frame's marks.
    (when (seq marks)
      (texture/with-target! rt safe
        (fn [] (doseq [mark marks] (replay-mark! mark)))))
    (clear-to! mousepaint/background-colour)
    (texture/quad! (:texture rt) {:x fx
                                  :y fy
                                  :width fw
                                  :height fh
                                  :v0 1.0
                                  :v1 0.0})
    (when-let [[cx cy] (:cursor state)]
      (if (:erase? state)
        (rl/draw-circle-lines (int cx) (int cy) (double (:brush state)) (color mousepaint/preview-colour))
        (rl/draw-circle (int cx) (int cy) (double (:brush state))
                        (color (mousepaint/paint-colour state)))))
    (rl/draw-rectangle 0 (dec fy) (int fw) 1 (color mousepaint/rule-colour))
    (doseq [{:keys [i x y w h]} (:palette dims)]
      (rl/draw-rectangle x y w h (color (nth mousepaint/palette i))))
    (let [{:keys [x y w h]} (nth (:palette dims) (:sel state))]
      (outline! (- x 2) (- y 2) (+ w 4) (+ h 4) mousepaint/outline-colour))
    (doseq [{:keys [id x y w h label]} (:buttons dims)
            :let [on? (and (= id :eraser) (:erase? state))]]
      (rl/draw-rectangle x y w h (color (if on? mousepaint/button-on-colour mousepaint/button-colour)))
      (draw-caption! label (if on? mousepaint/button-on-label-colour mousepaint/button-label-colour)))))
