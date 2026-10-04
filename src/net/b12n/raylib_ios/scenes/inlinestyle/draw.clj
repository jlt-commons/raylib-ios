(ns net.b12n.raylib-ios.scenes.inlinestyle.draw
  "The draw-scene! method for the `:inlinestyle` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.inlinestyle :as istyle]))

(def ^:private inlinestyle-layout
  "The last layout as `[key layout]`. Its key holds every input to it, the screen
  and the tint, because the markup, the sizes and the measure do not change."
  (atom nil))

(defn- inlinestyle-lines [m tint]
  (let [k [(:screen m) tint]
        [ck cl] @inlinestyle-layout]
    (if (= k ck)
      cl
      (let [l (istyle/layout (istyle/dimensions m) (fn [s sz] (rl/measure-text s (int sz))) tint)]
        (reset! inlinestyle-layout [k l])
        l))))

(defmethod draw-scene! :inlinestyle [_ {:keys [tint]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack istyle/background-colour))
        {:keys [lines box thick]} (inlinestyle-lines m tint)
        [bx by bw bh] box
        x2 (+ bx bw)
        y2 (+ by bh)
        edge (pack istyle/box-colour)]
    (doseq [{:keys [size y runs]} lines
            {:keys [text fg bg x w]} runs]
      (when bg
        (rl/draw-rectangle (int x) (int y) (int w) (int size) (pack bg)))
      (rl/draw-text text (int x) (int y) size (pack fg)))
    ;; DrawRectangleLines is a hairline here, so the box is four lines. Each is
    ;; butt-ended, so its ends are pushed out by half the thickness to close the
    ;; corners.
    (let [h (/ thick 2.0)
          lx (- bx h)
          rx (+ x2 h)
          ty (- by h)
          by2 (+ y2 h)]
      (rl/draw-line-ex lx by rx by thick edge)
      (rl/draw-line-ex x2 ty x2 by2 thick edge)
      (rl/draw-line-ex rx y2 lx y2 thick edge)
      (rl/draw-line-ex bx by2 bx ty thick edge))))
