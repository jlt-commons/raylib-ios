(ns net.b12n.raylib-ios.scenes.logo.draw
  "The draw-scene! method for the `:logo` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.logo :as still-logo]))

(defmethod draw-scene! :logo [_ _ {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack still-logo/background-colour))
        measure (fn [s sz] (rl/measure-text s (int sz)))
        {:keys [outer inner label]} (still-logo/layout (still-logo/dimensions m) measure)
        [ox oy ow oh] outer
        [ix iy iw ih] inner]
    ;; A thick border is a logo-coloured square with a background one on top.
    (rl/draw-rectangle (int ox) (int oy) (int ow) (int oh) (pack still-logo/logo-colour))
    (rl/draw-rectangle (int ix) (int iy) (int iw) (int ih) (pack still-logo/background-colour))
    (rl/draw-text (:s label) (int (:x label)) (int (:y label)) (:size label)
                  (pack still-logo/logo-colour))))
