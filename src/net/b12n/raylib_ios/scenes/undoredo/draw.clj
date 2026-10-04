(ns net.b12n.raylib-ios.scenes.undoredo.draw
  "The draw-scene! method for the `:undoredo` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.undoredo :as undoredo]))

(defmethod draw-scene! :undoredo [_ {:keys [player history cursor]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack undoredo/background-colour))
        dims (undoredo/dimensions m host-measure)
        text (fn [{:keys [s x y size]} colour] (rl/draw-text s (int x) (int y) size (pack colour)))
        {:keys [cell grid-x grid-y]} dims
        hair (max 1.0 (* 0.04 cell))
        line (fn [x0 y0 x1 y1 colour] (rl/draw-line-ex x0 y0 x1 y1 2.0 (pack colour)))
        cell-xy (fn [c r] [(+ grid-x (* c cell)) (+ grid-y (* r cell))])
        n (count history)
        undo? (undoredo/can-undo? {:cursor cursor})
        redo? (undoredo/can-redo? {:cursor cursor
                                   :history history})]
    (text (:caption dims) undoredo/caption-colour)
    ;; The trail: every state up to the cursor, so an undo shortens it.
    (dotimes [i (inc cursor)]
      (let [[x y w h] (undoredo/cell-rect dims (:x (nth history i)) (:y (nth history i)))]
        (rl/draw-rectangle (int x) (int y) (int (Math/ceil w)) (int (Math/ceil h)) (pack undoredo/trail-colour))))
    (dotimes [i (inc undoredo/cells-x)]
      (let [[x y] (cell-xy i 0)]
        (rl/draw-line-ex x y x (+ y (* undoredo/cells-y cell)) hair (pack undoredo/grid-colour))))
    (dotimes [i (inc undoredo/cells-y)]
      (let [[x y] (cell-xy 0 i)]
        (rl/draw-line-ex x y (+ x (* undoredo/cells-x cell)) y hair (pack undoredo/grid-colour))))
    (let [[x y w h] (undoredo/cell-rect dims (:x player) (:y player))]
      (rl/draw-rectangle (int x) (int y) (int (Math/ceil w)) (int (Math/ceil h)) (pack (nth undoredo/palette (:color player)))))
    (text (assoc (:history dims) :s (undoredo/history-line cursor n)) undoredo/label-colour)
    (dotimes [i n]
      (let [[x y w h] (undoredo/slot-rect dims i)]
        (if (= i cursor)
          (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack undoredo/strip-fill-colour))
          ;; A 2 px outline from four lines, each end run on by half the
          ;; thickness so the corners do not notch.
          (let [x1 (+ x w)
                y1 (+ y h)]
            (line (- x 1.0) y (+ x1 1.0) y undoredo/strip-line-colour)
            (line (- x 1.0) y1 (+ x1 1.0) y1 undoredo/strip-line-colour)
            (line x (- y 1.0) x (+ y1 1.0) undoredo/strip-line-colour)
            (line x1 (- y 1.0) x1 (+ y1 1.0) undoredo/strip-line-colour)))))
    (doseq [[rect label on?] [[(:undo dims) (:undo-label dims) undo?]
                              [(:redo dims) (:redo-label dims) redo?]]
            :let [[x y w h] rect]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h)
                         (pack (if on? undoredo/button-colour undoredo/button-off-colour)))
      (text label (if on? undoredo/button-label-colour undoredo/button-off-label-colour)))))
