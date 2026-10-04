(ns net.b12n.raylib-ios.scenes.fbrender.draw
  "The draw-scene! method for the `:fbrender` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure
                                                           outline!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fbrender :as fbrender]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private fbrender-cache
  "The last `[screen dims]` for `:fbrender`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- fbrender-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @fbrender-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (fbrender/dimensions m host-measure)]
        (reset! fbrender-cache [screen dims])
        dims))))

(defmethod draw-scene! :fbrender [_ state {:keys [m safe]}]
  (let [dims (fbrender-dims m)
        [[ox oy hw hh] [sx sy]] (:halves dims)
        spec {:w hw
              :h hh
              :depth? false}
        observer (texture/target! :fbrender :observer spec)
        subject (texture/target! :fbrender :subject spec)
        [note label] (:observer-lines dims)
        [sub-label] (:subject-lines dims)
        [cx cy cw ch] (:crop-rect dims)
        [ix iy iw ih] (:inset-rect dims)
        [u0 v0 u1 v1] (:inset-uv dims)
        [dx1 dy1 dx2 dy2] (:divider dims)]
    (texture/with-target! observer safe
      (fn []
        (clear-to! fbrender/background-colour)
        (rl/draw-3d! (fbrender/observer-list state dims))
        (draw-caption! note fbrender/note-colour)
        (draw-caption! label fbrender/label-colour)))
    (texture/with-target! subject safe
      (fn []
        (clear-to! fbrender/background-colour)
        (rl/draw-3d! (fbrender/subject-list state dims))
        (outline! cx cy cw ch fbrender/crop-colour)
        (draw-caption! sub-label fbrender/label-colour)))
    (clear-to! fbrender/screen-colour)
    (texture/quad! (:texture observer) {:x ox
                                        :y oy
                                        :width hw
                                        :height hh
                                        :v0 1.0
                                        :v1 0.0})
    (texture/quad! (:texture subject) {:x sx
                                       :y sy
                                       :width hw
                                       :height hh
                                       :v0 1.0
                                       :v1 0.0})
    ;; The inset is the SAME texture sampled through a narrower rectangle.
    (texture/quad! (:texture subject) {:x (+ sx ix)
                                       :y (+ sy iy)
                                       :width iw
                                       :height ih
                                       :u0 u0
                                       :v0 v0
                                       :u1 u1
                                       :v1 v1})
    (outline! (+ sx ix) (+ sy iy) iw ih fbrender/crop-colour)
    (rl/draw-line (int dx1) (int dy1) (int dx2) (int dy2) (color fbrender/divider-colour))))
