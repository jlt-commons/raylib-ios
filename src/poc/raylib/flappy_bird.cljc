(ns poc.raylib.flappy-bird
  "Pure, deterministic, touch-first Flappy Bird simulation.

  Adapted from the pinned raylib-jlt flappy-bird example's gameplay, but not
  its window ownership or frame-based simulation. The Raylib host supplies one
  normalized input snapshot and delta time per frame; this namespace carries no
  polling, native values, or drawing calls.")

(def max-delta-seconds 0.05)
(def default-seed 1337)

(defn dimensions
  "Derive all gameplay geometry and rates from live screen metrics."
  [metrics]
  (let [[width height] (:screen metrics)
        scale (/ (double (min width height)) 450.0)
        size (fn [n] (max 1.0 (* n scale)))]
    {:width (double width)
     :height (double height)
     :bird-x (* 150.0 scale)
     :bird-radius (size 14)
     :gravity (* 1440.0 scale)
     :flap-velocity (* -420.0 scale)
     :pipe-width (size 70)
     :gap-height (size 140)
     :pipe-spacing (size 300)
     :scroll-speed (* 180.0 scale)
     :edge-margin (size 80)}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- gap-from-seed [seed {:keys [height gap-height edge-margin]}]
  (let [minimum edge-margin
        maximum (max minimum (- height edge-margin gap-height))
        span (inc (long (- maximum minimum)))]
    [(/ (double (+ minimum (mod seed span))) 1.0) (next-random seed)]))

(defn- pipe [x seed dims]
  (let [[gap next-seed] (gap-from-seed seed dims)]
    [{:x (double x) :gap gap :scored? false} next-seed]))

(defn new-game
  "Create a reproducible game for metrics and an optional integer seed."
  ([metrics] (new-game metrics default-seed))
  ([metrics seed]
   (let [{:keys [width height pipe-spacing]} (dimensions metrics)
         ;; Keep the first obstacle visible on a phone-sized initial frame;
         ;; later pipes preserve the stable metric-scaled spacing.
         first-x (* 0.75 width)]
     (loop [index 0
            seed (long seed)
            pipes []]
       (if (= index 3)
         {:seed seed
          :elapsed 0.0
          :y (/ height 2.0)
          :vy 0.0
          :score 0
          :over? false
          :pipes pipes}
         (let [[next-pipe next-seed]
               (pipe (+ first-x (* index pipe-spacing)) seed (dimensions metrics))]
           (recur (inc index) next-seed (conj pipes next-pipe))))))))

(defn activate? [input]
  (or (= :press (get-in input [:pointer :phase]))
      (true? (get-in input [:keyboard :activate?]))))

(defn clamp-delta [delta]
  (max 0.0 (min max-delta-seconds (double (or delta 0.0)))))

(defn- collides? [y {:keys [x gap]} {:keys [bird-x bird-radius pipe-width gap-height]}]
  (and (< (- bird-x bird-radius) (+ x pipe-width))
       (> (+ bird-x bird-radius) x)
       (or (< (- y bird-radius) gap)
           (> (+ y bird-radius) (+ gap gap-height)))))

(defn- move-pipes [pipes seed dims dt]
  ;; Replacements use the previous frame's rightmost position. At most one pipe
  ;; can leave during a clamped frame with the configured spacing.
  (let [rightmost (apply max (map :x pipes))]
    (loop [remaining pipes
           seed seed
           moved []]
      (if-let [current (first remaining)]
        (let [next-x (- (:x current) (* (:scroll-speed dims) dt))]
          (if (< next-x (- (:pipe-width dims)))
            (let [[replacement next-seed] (pipe (+ rightmost (:pipe-spacing dims)) seed dims)]
              (recur (rest remaining) next-seed (conj moved replacement)))
            (recur (rest remaining) seed (conj moved (assoc current :x next-x)))))
        [moved seed]))))

(defn- score-pipes [pipes bird-x pipe-width]
  (reduce (fn [[score updated] pipe]
            (if (and (not (:scored? pipe)) (< (+ (:x pipe) pipe-width) bird-x))
              [(inc score) (conj updated (assoc pipe :scored? true))]
              [score (conj updated pipe)]))
          [0 []] pipes))

(defn step
  "Advance one frame. A press edge flaps once; when over, that edge restarts.

  Position uses constant-acceleration integration, so uncollided fixture runs
  have equivalent positions across 30/60/variable frame partitions. Lifecycle
  spikes are explicitly capped at `max-delta-seconds`."
  [state input]
  (let [metrics (:metrics input)
        dims (dimensions metrics)
        flap? (activate? input)
        dt (clamp-delta (:delta-seconds input))]
    (cond
      (:over? state) (if flap? (new-game metrics (:seed state)) state)
      (zero? dt) state
      :else
      (let [initial-vy (if flap? (:flap-velocity dims) (:vy state))
            next-y (+ (:y state) (* initial-vy dt) (* 0.5 (:gravity dims) dt dt))
            next-vy (+ initial-vy (* (:gravity dims) dt))
            [moved-pipes next-seed] (move-pipes (:pipes state) (:seed state) dims dt)
            [passed scored-pipes] (score-pipes moved-pipes (:bird-x dims) (:pipe-width dims))
            collision? (or (< (- next-y (:bird-radius dims)) 0.0)
                           (> (+ next-y (:bird-radius dims)) (:height dims))
                           (some #(collides? next-y % dims) scored-pipes))]
        (assoc state
               :seed next-seed
               :elapsed (+ (:elapsed state) dt)
               :y next-y
               :vy next-vy
               :pipes scored-pipes
               :score (+ (:score state) passed)
               :over? (boolean collision?))))))

(defn ^:export scene
  "Gallery scene descriptor. Rendering remains in the owner-affine host."
  []
  {:id :flappy-bird
   :title "Flappy Bird"
   :init (fn [input] [(new-game (:metrics input)) [[:scene/init :flappy-bird]]])
   :update (fn [state input] [(step state input) []])
   :draw (fn [state _] [state []])
   :dispose (fn [state] [state [[:scene/dispose :flappy-bird]]])})
