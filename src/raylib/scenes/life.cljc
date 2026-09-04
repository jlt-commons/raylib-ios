(ns raylib.scenes.life
  "Conway's Game of Life, ported from raylib-jlt's `game_of_life`.

  Four rules, no exceptions: a live cell with two or three live neighbours
  survives, a dead cell with exactly three becomes live, everything else dies.
  The grid wraps at both edges, so it is a torus and nothing falls off.

  The original is 80x45 on a landscape window with SPACE to reseed. Here the
  grid is derived from the screen, and there is no reseed key because there are
  no keys: the population is watched instead, and a board that has stopped
  changing or died out reseeds itself.

  Cost is the live population, one rectangle each, so it is highest in the first
  second and settles as the soup does. The cell size is chosen to keep the
  opening frame inside the budget rather than the steady state, which is about
  a third of it."
  (:require [clojure.string]))

(def default-seed 1848)

;; Chosen so the opening population lands near a thousand rectangles, which
;; docs/guide/performance-on-a-phone.md puts inside the comfortable range. A
;; finer grid looks better for about a second and then costs frames for the
;; rest of the run.
(def target-cells 4300)
(def fill-percent 28)
(def ticks-per-generation 6)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        cell (max 8 (int (Math/sqrt (/ (* (double w) h) target-cells))))]
    {:cell cell
     :cols (quot w cell)
     :rows (quot h cell)}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn spawn
  "A random soup as a set of [col row], from the seeded generator rather than
  raylib's GetRandomValue, so a board replays identically."
  [{:keys [cols rows]} seed]
  (loop [i 0 s seed live (transient #{})]
    (if (= i (* cols rows))
      [(persistent! live) s]
      (let [s' (next-random s)
            on? (< (mod s' 100) fill-percent)]
        (recur (inc i) s'
               (if on? (conj! live [(mod i cols) (quot i cols)]) live))))))

(defn- neighbours [cols rows [c r]]
  (for [dc [-1 0 1] dr [-1 0 1]
        :when (not (and (zero? dc) (zero? dr)))]
    [(mod (+ c dc) cols) (mod (+ r dr) rows)]))

(defn step
  "One generation. Only cells adjacent to something living can change, so the
  candidate set is the live cells plus their neighbours rather than the whole
  grid. On a mostly-empty board that is the difference between thousands of
  tests and tens of thousands."
  [{:keys [cols rows]} live]
  (let [candidates (into live (mapcat #(neighbours cols rows %)) live)]
    (into #{}
          (filter (fn [cell]
                    (let [n (count (filter live (neighbours cols rows cell)))]
                      (if (live cell)
                        (or (= n 2) (= n 3))
                        (= n 3)))))
          candidates)))

(defn stalled?
  "Has the board stopped being interesting? Either it is empty, or the last few
  generations have all been the same size, which catches both a still life and
  the period-two oscillators almost every soup decays into."
  [history]
  (or (zero? (first history))
      (and (>= (count history) 6)
           (apply = (take 6 history)))))

(defn advance [state metrics]
  (let [dims (dimensions metrics)
        t (inc (:t state))]
    (if-not (zero? (mod t ticks-per-generation))
      (assoc state :t t)
      (let [live (step dims (:live state))
            history (take 8 (cons (count live) (:history state)))]
        (if (stalled? history)
          (let [[live' seed'] (spawn dims (:seed state))]
            (assoc state :t t :live live' :seed seed' :history () :generation 0))
          (assoc state :t t :live live :history history
                 :generation (inc (:generation state))))))))

(defn- init [{:keys [metrics]}]
  (let [[live seed] (spawn (dimensions metrics) default-seed)]
    [{:live live :seed seed :t 0 :history () :generation 0}
     [[:scene/init :life]]]))

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :life]]])

(defn scene []
  {:id :life :title "Life"
   :init init :update update-scene :draw draw :dispose dispose})
