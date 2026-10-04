(ns net.b12n.raylib-ios.scenes.gestures.draw
  "The draw-scene! method for the `:gestures` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.gestures :as gestures]))

(def ^:private gestures-dims-cache
  "The last `[screen dims]` for `:gestures`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- gestures-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @gestures-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (gestures/dimensions m host-measure)]
        (reset! gestures-dims-cache [screen dims])
        dims))))

(defmethod draw-scene! :gestures [_ state {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack gestures/background-colour))
        dims (gestures-dims m)
        {:keys [rect size row-h rows-y text-x text-dy header-y]} (:log dims)
        [lx ly lw lh] rect
        [ax ay aw ah] (:area dims)
        entries (:log state)
        newest (dec (count entries))]
    (rl/draw-rectangle (int ax) (int ay) (int aw) (int ah) (pack gestures/area-colour))
    (let [{:keys [s x y size]} (:title dims)]
      (rl/draw-text s (int x) (int y) (int size) (pack gestures/area-text-colour)))
    (let [{:keys [s x y size]} (:hint-line dims)]
      (rl/draw-text s (int x) (int y) (int size) (pack gestures/hint-colour)))
    ;; The log, newest at the bottom of the entries, alternating rows.
    (doseq [[i entry] (map-indexed vector entries)
            :let [ry (+ rows-y (* i row-h))]]
      (when (odd? i)
        (rl/draw-rectangle (int lx) (int ry) (int lw) (int row-h) (pack gestures/row-colour)))
      (rl/draw-text entry (int text-x) (int (+ ry text-dy)) (int size)
                    (pack (if (= i newest) gestures/newest-colour gestures/log-text-colour))))
    (rl/draw-text gestures/header-text (int text-x) (int header-y) (int size)
                  (pack gestures/header-colour))
    ;; The outline is four lines: there is no rectangle-lines call bound.
    (let [c (pack gestures/header-colour)
          x2 (int (+ lx lw))
          y2 (int (+ ly lh))]
      (rl/draw-line (int lx) (int ly) x2 (int ly) c)
      (rl/draw-line x2 (int ly) x2 y2 c)
      (rl/draw-line x2 y2 (int lx) y2 c)
      (rl/draw-line (int lx) y2 (int lx) (int ly) c))
    ;; Where the finger last was, while raylib reports any gesture.
    (when (and (:at state) (not= 0 (:gesture state)))
      (let [[x y] (:at state)]
        (rl/draw-circle (int x) (int y) (double (:circle-radius dims))
                        (pack gestures/circle-colour))))))
