(ns raylib.scenes.randomvalues
  "A new random number from 0 to 99 every two seconds, with a short history of
  recent rolls. Ported from raylib-jlt's `random_values`.

  The original asks raylib's GetRandomValue. This uses the project LCG instead,
  copied privately as the sibling scenes do, so the scene stays pure and the
  same seed always replays the same sequence.")

(def default-seed 2026)

;; Plain defs rather than constants, so either can be changed over the nREPL
;; and take effect on the next frame.
(def roll-every 120)
(def history-length 8)

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn roll
  "A value in 0-99 and the next seed.

  The value comes from the high bits, `(quot seed' 65536)`. The low bit of an
  LCG with an odd multiplier and odd increment flips on every step, and
  `mod 100` keeps that bit, so `(mod seed' 100)` alternated odd and even."
  [seed]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) 100) seed']))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        big (int (* 0.25 side))]
    {:label-size (max 20 (int (* 0.034 side)))
     :big-size big
     :caption-x (int (* 0.04 w))
     :caption-y (int (* 0.12 h))
     :value-y (int (- (* 0.5 h) (* 0.5 big)))
     :recent-x (int (* 0.04 w))
     :recent-y (int (- h (* 0.055 h)))}))

(defn advance
  "Count a frame, and roll when the count reaches a multiple of `roll-every`.
  The history keeps only the last `history-length` rolls."
  [state _input]
  (let [frame' (inc (:frame state))]
    (if (zero? (mod frame' roll-every))
      (let [[value seed'] (roll (:seed state))]
        (assoc state
               :frame frame'
               :seed seed'
               :value value
               :history (vec (take-last history-length (conj (:history state) value)))))
      (assoc state :frame frame'))))

(defn- init [_]
  (let [[value seed'] (roll default-seed)]
    [{:frame 0
      :seed seed'
      :value value
      :history [value]} [[:scene/init :randomvalues]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :randomvalues]]])

(defn scene []
  {:id :randomvalues
   :title "Random Values"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
