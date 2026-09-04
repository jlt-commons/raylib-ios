(ns raylib.scenes.sequence
  "A shuffled sequence of bars, ported from raylib-jlt's `random_sequence`.

  The example upstream is about `LoadRandomSequence`, which hands back each
  value in a range exactly once in a random order. That is a different thing
  from `GetRandomValue`, which samples WITH replacement: calling it n times
  gives duplicates and gaps, so the bars would not be a permutation and a
  reshuffle would change the set rather than only its order.

  So this shuffles rather than redraws. Every height appears exactly once, and
  reshuffling moves the bars without changing which heights exist. Watch a
  colour travel rather than change, which is the property the whole example is
  demonstrating and is invisible in a still picture.

  The shuffle is a seeded Fisher-Yates over the same LCG the other scenes use,
  not `clojure.core/shuffle`. Two reasons: a scene should replay identically,
  and a test cannot say much about a shuffle it cannot reproduce."
  (:require [clojure.string]))

(def default-seed 7777)
(def bar-count 24)
(def frames-per-shuffle 150)

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn shuffle-with
  "Fisher-Yates, seeded. Returns [shuffled seed'].

  Walks from the end and swaps each element with one at or before it, which is
  the version that gives every permutation equal probability. The off-by-one
  that picks from the whole vector instead does not, and produces a subtly
  biased shuffle that looks fine."
  [v seed]
  (loop [i (dec (count v)) s seed out (vec v)]
    (if (<= i 0)
      [out s]
      (let [s' (next-random s)
            j (mod s' (inc i))]
        (recur (dec i) s' (assoc out i (out j) j (out i)))))))

(defn bars
  "`n` bars, each with a distinct height rank and a hue from that rank.

  Height comes from the rank rather than a fresh draw, which is what makes the
  set a permutation: every bar is a different height and reshuffling cannot
  change which heights are present."
  [n]
  (mapv (fn [i]
          (let [t (/ (double (inc i)) n)]
            {:rank i
             :fraction t
             :colour [(int (* 255 t))
                      (int (* 255 (- 1.0 (abs (- (* 2.0 t) 1.0)))))
                      (int (* 255 (- 1.0 t)))]}))
        (range n)))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :bar-w (/ (double w) bar-count)
     :max-height (* 0.78 h)
     :baseline (* 0.92 h)}))

(defn advance [state]
  (let [t (inc (:t state))]
    (if-not (zero? (mod t frames-per-shuffle))
      (assoc state :t t)
      (let [[shuffled seed] (shuffle-with (:bars state) (:seed state))]
        (assoc state :t t :bars shuffled :seed seed :shuffles (inc (:shuffles state)))))))

(defn- init [_]
  (let [[b seed] (shuffle-with (bars bar-count) default-seed)]
    [{:bars b :seed seed :t 0 :shuffles 0} [[:scene/init :sequence]]]))

(defn- update-scene [state _] [(advance state) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :sequence]]])

(defn scene []
  {:id :sequence :title "Random Sequence"
   :init init :update update-scene :draw draw :dispose dispose})
