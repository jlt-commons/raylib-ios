(ns net.b12n.raylib-ios.scenes.bgscroll
  "Background scrolling, ported from raylib-jlt's `background_scrolling`
  (net/b12n/raylib_jlt/background_scrolling.clj, zlib licence): three skyline
  layers scroll left at different speeds for a parallax, each drawn twice side
  by side so the seam wraps.

  The original builds each layer as a 400 by 115 texture with
  `rl/texture-from-fn` and draws it at twice the size. There is no texture here.
  `skyline` rebuilds what that function draws as one rect per building, from the
  same arithmetic (`hash01` and `skyline-pixel`, lines 22-39): a column of
  `col-w` texels whose first `int(col-w * 0.15)` are a gap, standing from
  `top = int(horizon + span * 0.6 * hash01(seed + col))` down to the bottom of
  the texture, where `horizon = int(115 * (1 - horizon-frac))`. A column the
  texture cuts short is kept short, and one that is all gap is dropped. Every
  texel edge is doubled, as the 2x draw is, so a rect sits in the original's
  800 by 450 window.

  What is the original's:
  - The three layers (lines 66-74): seed, horizon fraction, column width and
    colour for the back (1, 0.2, 16, (20, 52, 78)), middle (7, 0.35, 26, (12, 34,
    50)) and front (23, 0.5, 44, (4, 12, 20)) skyline, on a clear colour of (5,
    44, 70).
  - The speeds (lines 78-80): back 0.1, middle 0.5, front 1.0 a frame, to the
    left, with no frame time, and the wrap: an offset that reaches minus 800 (the
    width of two textures at 2x) goes back to 0.
  - The draw (lines 87-100): each layer at `int(offset)` and again 800 to the
    right, bottom at the bottom of the window, back layer first.
  - The caption (lines 102-106), red.

  Units. The offsets are in the original's units, so the speeds are its own, and
  the picture is scaled by `u`, the safe region's width over 800, so the 800 units
  of one period are exactly one screen width and the second copy always fills
  what the first leaves. The skyline is anchored to the bottom of the screen,
  where the original's is, and a portrait screen shows more sky above it. There
  is no input, as in the original. A rect that lies wholly off the screen is not
  drawn; the first copy of a layer covers the screen from its offset, so about
  half the rects of each copy are skipped.

  The state holds numbers only: `:back`, `:mid` and `:fore`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def view-w "The original's window width, in its units." 800.0)
(def view-h "The original's window height, in its units." 450.0)
(def tex-w "The original's TEX-W." 400)
(def tex-h "The original's TEX-H." 115)
(def period "Two textures at 2x: where an offset wraps. The original's `bw`." 800.0)

(def background-colour "The original's clear colour." [5 44 70 255])
(def caption-colour "RED." [230 41 55 255])
(def caption "The original's caption." "BACKGROUND SCROLLING & PARALLAX")

(defn- hash01
  "The original's `hash01` (line 22)."
  [n]
  (let [h (bit-and (* (+ n 12345) 2654435761) 0xffffffff)]
    (/ (double (bit-and h 0xffff)) 65536.0)))

(defn skyline
  "The rects `[x y w h]` of one layer in the original's 800 by 450 units, left to
  right: the texture of `skyline-pixel` (lines 24-39) for `seed`, `horizon-frac`
  and `col-w`, doubled."
  [seed horizon-frac col-w]
  (let [horizon (int (* tex-h (- 1.0 horizon-frac)))
        span (- tex-h horizon)
        gap (int (* col-w 0.15))
        cols (quot (+ tex-w col-w -1) col-w)]
    (loop [col 0
           out []]
      (if (= col cols)
        out
        (let [left (+ (* col col-w) gap)
              right (min tex-w (* (inc col) col-w))
              top (int (+ horizon (* span 0.6 (hash01 (+ seed col)))))]
          (recur (inc col)
                 (if (< left right)
                   (conj out [(* 2 left)
                              (+ (- view-h (* 2 tex-h)) (* 2 top))
                              (* 2 (- right left))
                              (* 2 (- tex-h top))])
                   out)))))))

(def layers
  "Back to front: `{:key :speed :colour :rects}`."
  [{:key :back
    :speed 0.1
    :colour [20 52 78 255]
    :rects (skyline 1 0.2 16)}
   {:key :mid
    :speed 0.5
    :colour [12 34 50 255]
    :rects (skyline 7 0.35 26)}
   {:key :fore
    :speed 1.0
    :colour [4 12 20 255]
    :rects (skyline 23 0.5 44)}])

(defn geometry
  "The layout for `metrics`' `:screen`: `:w :h`, `:u` (pixels per original unit),
  the text `:size` and `:pad`, and `:caption-y`, just below Back."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))]
    {:w w
     :h h
     :u (/ (double w) view-w)
     :size size
     :pad pad
     :caption-y (+ back-y back-h pad)}))

(defn dimensions
  "`geometry` plus the text: `:lines` holds the caption as `{:s :x :y :size}`, cut
  back from `geometry`'s size when it would cover more than 0.92 of the width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w size pad caption-y]
         :as geo} (geometry metrics)
        size (max 8 (min size (int (/ (* 0.92 w 100.0) (measure caption 100)))))]
    (assoc geo :lines [{:s caption
                        :x pad
                        :y caption-y
                        :size size}])))

(defn- wrap
  "One frame of an offset: minus `speed`, back to 0 at the wrap (lines 78-80)."
  [offset speed]
  (let [o (- offset speed)]
    (if (<= o (- period)) 0.0 o)))

(defn advance
  "One frame: every layer moves left by its own speed and wraps."
  [state]
  (reduce (fn [s {:keys [key speed]}] (update s key wrap speed)) state layers))

(defn emit-layers!
  "Calls `(rect! x y w h colour)` for every building of `state`, in pixels, back
  layer first. `dims` is from `geometry`. Each layer is placed at
  `int(offset)` and again `period` to the right (lines 87-100); a rect whose
  pixel extent lies off the screen is skipped."
  [rect! state {:keys [w h u]}]
  (let [top-line (- h (* u view-h))]
    (doseq [{:keys [key colour rects]} layers
            :let [base (long (get state key))
                  n (count rects)]
            copy [0 1]
            :let [off (+ base (* copy period))]]
      (loop [i 0]
        (when (< i n)
          (let [[rx ry rw] (nth rects i)
                left (long (Math/round (* u (+ off rx))))
                right (long (Math/round (* u (+ off rx rw))))
                top (long (Math/round (+ top-line (* u ry))))]
            (when (and (> right 0) (< left w))
              (rect! left top (- right left) (- h top) colour)))
          (recur (inc i)))))))

(defn- init [_]
  [{:back 0.0
    :mid 0.0
    :fore 0.0}
   [[:scene/init :bgscroll]]])
(defn- update-scene [state _] [(advance state) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :bgscroll]]])

(defn scene []
  {:id :bgscroll
   :title "Background Scrolling"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
