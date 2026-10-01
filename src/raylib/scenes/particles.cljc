(ns raylib.scenes.particles
  "Particles that pour out of your fingertip: water falls, smoke rises and
  fades, fire shrinks from yellow to red. Ported from raylib-jlt's `particles`.

  The original emits at the mouse all the time and cycles the type with
  LEFT/RIGHT. A finger has no hover, so emission happens only while one is on
  the glass (`:press` or `:down`), at the finger. Nothing is emitted on
  `:release`, whose position is the last hardware value and on a device is not
  the touch that just ended. The type changes when a `:press` lands inside the
  info box. Three types cycle, so the box only goes forward.

  The original's Fade and ColorLerp are packed-int arithmetic. Here a colour is
  an `[r g b a]` vector and the lerp and fade work on the channels, so the
  namespace stays pure and the draw method packs the result with `rl/rgba`.

  Sizes and speeds are the original's, written for an 800x450 window and scaled
  by `(min w h) / 450`. Randomness is the project LCG, taking the high bits.")

(def default-seed 2026)

;; A plain def, so it can be changed over the nREPL and take effect on the next
;; frame. estimate: the original emits 3 a frame and its longest lived type,
;; smoke, lasts 1.8 s or about 108 frames, which is 324 live; 600 leaves room
;; for the tall phone, where water takes longer to leave the screen. Tune it on
;; the device.
(def max-particles 600)

(def per-frame
  "Particles emitted each frame a finger is down, as in the original."
  3)

(def ^:private frame-dt 0.0166667)
(def ^:private smoke-life 1.8)

(def water-colour [0 121 241 255])
(def gray [130 130 130 255])
(def yellow [253 249 0 255])
(def red [230 41 55 255])

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- rand-between
  "An integer from `lo` to `hi` inclusive, and the next seed. High bits, because
  the low bit of this LCG alternates on every step."
  [seed lo hi]
  (let [seed' (next-random seed)]
    [(+ lo (mod (quot seed' 65536) (inc (- hi lo)))) seed']))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        size (max 20 (int (* 0.026 side)))
        box-w (* 0.60 w)
        box-x (- w box-w (* 0.02 w))
        box-y (* 0.02 h)
        box-h (* 0.07 h)
        pad (* 0.03 box-w)]
    {:w w
     :h h
     :scale (/ side 450.0)
     :text-size size
     ;; Top right, so Back at the top left stays clear of it.
     :info-rect [box-x box-y box-w box-h]
     :line1-x (int (+ box-x pad))
     :line1-y (int (+ box-y (* 0.18 box-h)))
     :line2-x (int (+ box-x pad))
     :line2-y (int (+ box-y (* 0.58 box-h)))}))

(def type-names {:water "WATER"
                 :smoke "SMOKE"
                 :fire "FIRE"})

(def info-line "Touch to emit. Tap here: type")

(defn next-type [t]
  (case t :water :smoke :smoke :fire :water))

(defn type-line [{:keys [type particles]}]
  (str "Type: " (type-names type) "   Particles: " (count particles)))

(defn fade
  "`[r g b a]` with its alpha scaled by `alpha` clamped to 0-1."
  [[r g b a] alpha]
  [r g b (int (* a (max 0.0 (min 1.0 alpha))))])

(defn color-lerp
  "Every channel of `from` blended toward `to` by `t` clamped to 0-1."
  [from to t]
  (let [t (max 0.0 (min 1.0 t))]
    (mapv (fn [a b] (int (+ a (* t (- b a))))) from to)))

(defn colour
  "A particle's `[r g b a]`: water is blue, smoke fades gray out as it ages,
  fire runs yellow to red as it shrinks."
  [{:keys [type radius r0 age]}]
  (case type
    :water water-colour
    :smoke (fade gray (- 1.0 (/ age smoke-life)))
    (color-lerp yellow red (/ (- r0 radius) r0))))

(defn- in-rect? [[bx by bw bh] [px py]]
  (and (>= px bx) (<= px (+ bx bw))
       (>= py by) (<= py (+ by bh))))

(defn- alive? [{:keys [w h]} {:keys [type x y radius age r0]}]
  (and (> x (- radius)) (< x (+ w radius))
       (> y (- radius)) (< y (+ h radius))
       (case type
         :fire (> radius (* 0.05 r0))
         :smoke (< age smoke-life)
         true)))

(defn emit-particle
  "A new particle at `[ex ey]` and the next seed. Speed is 0 to 1.8 for water
  and smoke and a tenth of that for fire, in a random direction."
  [{:keys [scale]} type ex ey seed]
  (let [[b seed1] (rand-between seed 0 9)
        [deg seed2] (rand-between seed1 0 359)
        base (/ b 5.0)
        speed (* scale (if (= type :fire) (/ base 10.0) base))
        rad (* deg (/ Math/PI 180.0))
        r0 (* scale (case type :water 5.0 :smoke 7.0 10.0))]
    [{:type type
      :x (double ex)
      :y (double ey)
      :vx (* speed (Math/cos rad))
      :vy (* speed (Math/sin rad))
      :radius r0
      :r0 r0
      :age 0.0}
     seed2]))

(defn update-particle
  "One frame of the original's rule for the particle's type. Water gets
  gravity, smoke rises and swells, fire rises, wobbles sideways and shrinks."
  [{:keys [scale]} {:keys [type x y vx vy radius age]
                    :as p}]
  (let [age (+ age frame-dt)]
    (case type
      :water (let [vy' (+ vy (* scale 0.2))]
               (assoc p :x (+ x vx) :y (+ y vy') :vy vy' :age age))
      :smoke (let [vy' (- vy (* scale 0.05))]
               (assoc p :x (+ x vx) :y (+ y vy') :vy vy'
                      :radius (+ radius (* scale 0.5)) :age age))
      (let [vy' (- vy (* scale 0.05))]
        (assoc p :x (+ x vx (* scale (Math/cos (* age 215.0)))) :y (+ y vy')
               :vy vy' :radius (- radius (* scale 0.15)) :age age)))))

(defn- step-particles
  "Update every particle and drop the dead ones, in one pass."
  [dims particles]
  (persistent!
   (reduce (fn [acc p]
             (let [p' (update-particle dims p)]
               (if (alive? dims p') (conj! acc p') acc)))
           (transient [])
           particles)))

(defn- emit-batch
  "Up to `per-frame` new particles at `point`, never taking the live count past
  `max-particles`."
  [dims state point]
  (let [room (- max-particles (count (:particles state)))]
    (loop [n (min per-frame room)
           ps (:particles state)
           seed (:seed state)]
      (if (pos? n)
        (let [[p seed'] (emit-particle dims (:type state) (nth point 0) (nth point 1) seed)]
          (recur (dec n) (conj ps p) seed'))
        (assoc state :particles ps :seed seed)))))

(defn advance [state input]
  (let [dims (dimensions (:metrics input))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        down? (boolean (and point (#{:press :down} phase)))
        in-box? (and down? (in-rect? (:info-rect dims) point))
        state (assoc state :particles (step-particles dims (:particles state)))
        state (if (and in-box? (= :press phase))
                (assoc state :type (next-type (:type state)))
                state)]
    (if (and down? (not in-box?))
      (emit-batch dims state point)
      state)))

(defn- init [_]
  [{:particles []
    :type :water
    :seed default-seed}
   [[:scene/init :particles]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :particles]]])

(defn scene []
  {:id :particles
   :title "Particles"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
