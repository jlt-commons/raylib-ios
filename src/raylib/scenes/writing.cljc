(ns raylib.scenes.writing
  "A message that types itself, ported from raylib-jlt's `writing_anim`.

  A growing prefix of a string, driven by the frame counter, with a pause at
  the end before it starts over. That is the whole scene, and it is here
  because it is the only one that animates text rather than geometry.

  The pause matters more than it looks. Without it the message completes and
  restarts on consecutive frames, so the finished sentence is never on screen
  long enough to read, which defeats the point of a scene about writing."
  (:require [clojure.string]))

(def message
  "This message types itself out, one character at a time, on an iPhone, from Clojure.")

(def frames-per-character 3)
(def pause-frames 60)

(defn visible
  "The prefix showing at frame `t`. Clamped rather than wrapped, so the tail of
  the cycle holds the complete message instead of starting over immediately."
  [t]
  (let [n (count message)
        period (+ n pause-frames)
        k (mod (quot t frames-per-character) period)]
    (subs message 0 (min n k))))

(defn complete?
  "Is the message fully typed, i.e. are we in the pause?"
  [t]
  (= (count message) (count (visible t))))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        margin (* 0.07 w)
        size (int (* 0.055 w))
        ;; raylib's default font is not monospaced, but its glyphs average
        ;; close enough to this fraction of the point size that wrapping on a
        ;; character count lands within a glyph of measuring each candidate
        ;; line. It also keeps this namespace free of MeasureText, which lives
        ;; on the other side of the pure boundary.
        advance (* 0.55 size)]
    {:w w :h h
     :margin margin
     :top (* 0.30 h)
     :size size
     :line-height (* 0.075 h)
     :columns (max 8 (int (/ (- w (* 2 margin)) advance)))}))

(defn wrap
  "Break `text` into lines of at most `columns`, on spaces where it can."
  [text columns]
  (loop [words (clojure.string/split text #" ") line "" out []]
    (cond
      (empty? words)
      (if (seq line) (conj out line) out)

      (empty? line)
      (recur (rest words) (first words) out)

      (<= (+ (count line) 1 (count (first words))) columns)
      (recur (rest words) (str line " " (first words)) out)

      :else
      (recur words "" (conj out line)))))

(defn- init [_] [{:t 0} [[:scene/init :writing]]])
(defn- update-scene [state _] [(update state :t inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :writing]]])

(defn scene []
  {:id :writing :title "Writing"
   :init init :update update-scene :draw draw :dispose dispose})
