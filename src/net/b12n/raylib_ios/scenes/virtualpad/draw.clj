(ns net.b12n.raylib-ios.scenes.virtualpad.draw
  "The draw-scene! method for the `:virtualpad` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.virtualpad :as vpad]))

(defmethod draw-scene! :virtualpad [_ {:keys [dirs a?]
                                       :as state} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack vpad/background-colour))
        dims (vpad/dimensions m)
        {:keys [pad-x pad-y pad-r btn-x btn-y btn-r thick segs a-glyph lines]} dims
        [sx sy sw sh] (vpad/square-rect dims state)]
    (doseq [{:keys [s x y size]} lines
            :let [colour (if (= s vpad/title-line) vpad/title-colour vpad/hint-colour)]]
      (rl/draw-text s x y size (pack colour)))
    (rl/draw-rectangle (int sx) (int sy) (int sw) (int sh) (pack vpad/square-colour))
    ;; The ring is a filled disc with a smaller background-coloured disc over
    ;; it, in place of the original's `circle-lines!`, because that is a
    ;; one-pixel line and a phone wants a thicker ring.
    (rl/draw-circle (int pad-x) (int pad-y) (float pad-r) (pack vpad/ring-colour))
    (rl/draw-circle (int pad-x) (int pad-y) (float (- pad-r thick)) (pack vpad/background-colour))
    (doseq [{:keys [dir cx cy r glyph gx gy gsize]} segs
            :let [active? (contains? dirs dir)]]
      (rl/draw-circle (int cx) (int cy) (float r)
                      (pack (if active? vpad/segment-active-colour vpad/segment-colour)))
      (rl/draw-text glyph gx gy gsize
                    (pack (if active? vpad/arrow-active-colour vpad/arrow-colour))))
    (rl/draw-circle (int btn-x) (int btn-y) (float btn-r)
                    (pack (if a? vpad/a-active-colour vpad/a-colour)))
    (rl/draw-text "A" (:x a-glyph) (:y a-glyph) (:size a-glyph)
                  (pack (if a? vpad/a-text-active-colour vpad/a-text-colour)))))
