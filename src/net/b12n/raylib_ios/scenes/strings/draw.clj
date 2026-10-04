(ns net.b12n.raylib-ios.scenes.strings.draw
  "The draw-scene! method for the `:strings` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.strings :as strings]))

(defmethod draw-scene! :strings [_ {:keys [particles dims shatter? case]} _]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack strings/background-colour))
        measure host-measure
        text (fn [{:keys [s x y size]} colour] (rl/draw-text s (int x) (int y) size (pack colour)))
        {:keys [pad size]} (:size-info dims)
        border (pack strings/border-colour)
        ink (pack strings/text-colour)]
    (text (:hint dims) strings/hint-colour)
    (doseq [{:keys [text x y w h color]} particles]
      (rl/draw-rectangle (int (- x pad)) (int (- y pad)) (int (+ w (* 2 pad))) (int (+ h (* 2 pad))) border)
      (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack color))
      (rl/draw-text text (int (+ x pad)) (int (+ y pad)) (int size) ink))
    (text (assoc (:count-line dims) :s (str strings/count-prefix (count particles))) strings/count-colour)
    (doseq [[rect label armed?] [[(:shatter dims) (:shatter-label dims) shatter?]
                                 [(:shake dims) (:shake-label dims) false]
                                 [(:case dims) (assoc (:case-label dims) :s (strings/case-label case)) false]]
            :let [[x y w h] rect
                  ls (:s label)
                  lsz (:size label)]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h)
                         (pack (if armed? strings/armed-colour strings/button-colour)))
      (rl/draw-text ls (strings/centred-x rect ls lsz measure) (int (:y label)) lsz
                    (pack (if armed? strings/armed-label-colour strings/button-label-colour))))))
