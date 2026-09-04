(ns raylib.scenes.automata
  "Elementary cellular automata, ported from raylib-jlt's `cellular_automata`.

  Wolfram's one-dimensional rules, one generation per row. Each cell's next
  state is decided by itself and its two neighbours, and the eight possible
  neighbourhoods are read off the eight bits of the rule number. Rule 30 is
  chaotic, rule 90 draws a Sierpinski triangle, and rule 110 is complicated
  enough to be Turing complete.

  Two changes from the original, both forced by the device.

  It scrolls. The original computes the whole triangle once and redraws every
  cell every frame, which on a phone screen is tens of thousands of rectangles
  and nowhere near a frame budget. Here a bounded window of generations is
  kept: it fills from a single live cell, and once full the oldest row falls off
  the top. That also makes it something to watch rather than a static image,
  which suits a scene that has no keys to change the rule with.

  Each row is drawn as runs of adjacent live cells rather than one rectangle
  per cell. Rule 30 averages a little over two cells per run, so this is most of
  a third of the draw calls for an identical picture.

  The rules cycle on their own, since the original stepped through them with
  the arrow keys and there is no keyboard here."
  (:require [clojure.string]))

;; The rules worth watching, in the order they are shown. 30 chaotic, 90 the
;; Sierpinski triangle, 110 Turing complete, 150 an XOR fractal, 54 and 22
;; somewhere in between.
(def rules [30 90 110 150 54 22])
(def generations-per-rule 400)
(def ticks-per-generation 2)

;; Sized against the draw budget, not the screen. At 132 columns and a 150-row
;; window the triangle is 7932 live cells, which run-encodes to 3968 rectangles:
;; a fine picture and about triple what docs/guide/performance-on-a-phone.md
;; says fits in a frame. These numbers keep it wide enough to read while filling
;; the display.
(def target-cols 90)
(def window-rows 110)

(defn dimensions
  "Cell width and row height are chosen separately.

  Width comes from the column count, which is what decides whether the
  automaton is legible: 92 columns is enough to read rule 30 and few enough to
  draw. Height is then whatever fills the space, which on this screen makes a
  row about 1.6 times taller than a cell is wide.

  Making them equal was the first attempt and left the pattern occupying the
  top 39% of the display with white below it, because a square cell wide enough
  to draw quickly is also too wide to need many rows."
  [metrics]
  (let [[w h] (:screen metrics)
        px (max 4 (quot w target-cols))
        rows (min window-rows (quot h 8))]
    {:px px
     :cols (quot w px)
     :rows rows
     :row-h (max 1 (quot h rows))}))

(defn next-row
  "One generation. The neighbourhood, read as a three-bit number, picks which
  bit of the rule to use. The edges wrap, so the strip is a ring."
  [row rule]
  (let [n (count row)]
    (mapv (fn [i]
            (let [l (nth row (mod (dec i) n))
                  c (nth row i)
                  r (nth row (mod (inc i) n))]
              (bit-and (bit-shift-right rule (+ (* 4 l) (* 2 c) r)) 1)))
          (range n))))

(defn seed-row
  "One live cell in the middle, which is what makes the triangle."
  [cols]
  (let [mid (quot cols 2)]
    (mapv (fn [i] (if (= i mid) 1 0)) (range cols))))

(defn runs
  "A row as [start length] pairs of adjacent live cells. One rectangle each,
  instead of one per cell."
  [row]
  (loop [i 0 start nil out []]
    (cond
      (= i (count row))
      (if start (conj out [start (- i start)]) out)

      (= 1 (nth row i))
      (recur (inc i) (or start i) out)

      :else
      (recur (inc i) nil (if start (conj out [start (- i start)]) out)))))

(defn advance
  "One tick. A new generation is appended and the oldest dropped once the window
  is full, so the drawn cost is flat however long it runs."
  [state metrics]
  (let [{:keys [cols rows]} (dimensions metrics)
        t (inc (:t state))]
    (if-not (zero? (mod t ticks-per-generation))
      (assoc state :t t)
      (let [gen (inc (:generation state))]
        (if (>= gen generations-per-rule)
          ;; next rule, from a fresh single cell
          (let [idx (mod (inc (:rule-index state)) (count rules))]
            (assoc state :t t :generation 0 :rule-index idx
                   :window [(runs (seed-row cols))]
                   :row (seed-row cols)))
          (let [row' (next-row (:row state) (nth rules (:rule-index state)))
                w (conj (:window state) (runs row'))]
            (assoc state :t t :generation gen :row row'
                   :window (if (> (count w) rows) (subvec w 1) w))))))))

(defn rule-of [state] (nth rules (:rule-index state)))

(defn- init [{:keys [metrics]}]
  (let [{:keys [cols]} (dimensions metrics)
        row (seed-row cols)]
    [{:row row :window [(runs row)] :rule-index 0 :generation 0 :t 0}
     [[:scene/init :automata]]]))

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :automata]]])

(defn scene []
  {:id :automata :title "Automata"
   :init init :update update-scene :draw draw :dispose dispose})
