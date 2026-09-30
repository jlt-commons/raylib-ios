(ns raylib.scenes.formattext
  "A zero-padded score and an MM:SS timer counting up, built with
  clojure.core/format. Ported from raylib-jlt's `format_text`.

  The score gains 7 a frame and the timer reads frames at 60 to the second.
  The timer has no hours field, as in the original, so it keeps counting
  minutes past 59.

  The original uses clojure.core/format. This port pads with `str` instead,
  because the linter also reads this .cljc as cljs, where `format` does not
  exist.")

(defn- zero-pad
  "Non-negative integer `n` as a string of at least `width` digits, padded with
  leading zeros. A longer number is never truncated, like `%0Nd`."
  [n width]
  (let [digits (str n)]
    (str (apply str (repeat (- width (count digits)) "0")) digits)))

(defn readouts
  "The score and time lines for `frame`, exactly as the original formats them."
  [frame]
  (let [secs (quot frame 60)]
    [(str "SCORE: " (zero-pad (* frame 7) 8))
     (str "TIME: " (zero-pad (quot secs 60) 2) ":" (zero-pad (mod secs 60) 2))]))

(defn dimensions
  "Where the two lines go. They sit in the middle third of the screen, well
  clear of the Back button, and scale off the short side like the siblings."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        size (max 20 (int (* 0.06 side)))]
    {:size size
     :x (int (* 0.08 w))
     :score-y (int (* 0.40 h))
     :time-y (int (* 0.50 h))}))

(defn advance [state _input]
  (update state :frame inc))

(defn- init [_] [{:frame 0} [[:scene/init :formattext]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :formattext]]])

(defn scene []
  {:id :formattext
   :title "Formatted Text"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
