(ns net.b12n.raylib-ios.scenes.spritebutton.draw
  "The draw-scene! method for the `:spritebutton` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.spritebutton :as spritebutton]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private spritebutton-cache
  "The last `[screen dims]` for `:spritebutton`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- spritebutton-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @spritebutton-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (spritebutton/dimensions m host-measure)]
        (reset! spritebutton-cache [screen dims])
        dims))))

(def ^:private spritebutton-spec (spritebutton/sheet-spec))

(defmethod draw-scene! :spritebutton [_ state {:keys [m]}]
  (clear-to! spritebutton/background-colour)
  (let [dims (spritebutton-dims m)
        id (texture/id! :spritebutton :sheet spritebutton-spec)
        {clicks-at :clicks
         state-at :state
         hint :hint} (:lines dims)]
    (texture/quad! id (spritebutton/button-quad state dims))
    (draw-caption! (assoc clicks-at :s (spritebutton/clicks-line (:clicks state))) spritebutton/clicks-colour)
    (draw-caption! (assoc state-at :s (nth spritebutton/frame-names (:frame state))) spritebutton/state-colour)
    (draw-caption! (assoc hint :s (spritebutton/hint state)) spritebutton/hint-colour)
    (texture/quad! id (spritebutton/preview-quad dims))
    (let [c (color spritebutton/outline-colour)]
      (doseq [[x y w h] (spritebutton/outline-rects state dims)]
        (rl/draw-rectangle (int x) (int y) (max 1 (int w)) (max 1 (int h)) c)))))
