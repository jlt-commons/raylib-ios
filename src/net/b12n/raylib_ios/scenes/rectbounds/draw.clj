(ns net.b12n.raylib-ios.scenes.rectbounds.draw
  "The draw-scene! method for the `:rectbounds` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.rectbounds :as rbounds]))

;; The layout makes one MeasureText call per glyph in character mode and one per
;; word in word mode, which is 284 and 53 calls with the original's text at the
;; largest box on the phone. It only changes when the box, the wrap mode or the
;; size does, so the last one is kept here and an idle frame makes no calls. It
;; has to live in the draw method because the real `measure` only exists here.
(defonce rectbounds-layout (atom nil))

(defn- rectbounds-lines [box-w box-h wrap size]
  ;; Every input to the layout computation must be in the key: the defonce
  ;; atom survives an nREPL reload, so a missing input shows a stale layout.
  (let [k [rbounds/text box-w box-h wrap size]
        [ck cl] @rectbounds-layout]
    (if (= k ck)
      cl
      (let [l (rbounds/layout-text rbounds/text box-w box-h size
                                   (fn [s sz] (rl/measure-text s (int sz))) wrap)]
        (reset! rectbounds-layout [k l])
        l))))

(defmethod draw-scene! :rectbounds [_ {:keys [box-w box-h holding? wrap]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack rbounds/background-colour))
        d (rbounds/dimensions m)
        {:keys [box-x box-y size line-width]} d
        edge (pack (if holding? rbounds/faded-colour rbounds/border-colour))
        x2 (+ box-x box-w)
        y2 (+ box-y box-h)
        [hx hy hw hh] (rbounds/handle-rect d box-w box-h)
        [bx by bw bh] (:button d)
        label (rbounds/wrap-label wrap)
        hint (first (:lines d))
        measure (fn [s sz] (rl/measure-text s (int sz)))]
    ;; DrawRectangleLines is a hairline here, so the border is four lines.
    (rl/draw-line-ex box-x box-y x2 box-y line-width edge)
    (rl/draw-line-ex x2 box-y x2 y2 line-width edge)
    (rl/draw-line-ex x2 y2 box-x y2 line-width edge)
    (rl/draw-line-ex box-x y2 box-x box-y line-width edge)
    (doseq [[line y] (rectbounds-lines box-w box-h wrap size)]
      (rl/draw-text line (int (+ box-x (* 0.2 size))) (int (+ box-y y)) size
                    (pack rbounds/text-colour)))
    (rl/draw-rectangle (int hx) (int hy) (int hw) (int hh) edge)
    (rl/draw-text (:s hint) (:x hint) (:y hint) (:size hint) (pack rbounds/hint-colour))
    (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack rbounds/button-colour))
    (rl/draw-text label (int (rbounds/centred-x bx bw label size measure))
                  (int (+ by (* 0.5 (- bh size)))) size (pack rbounds/label-colour))))
