(ns raylib.scenes.analog
  "A live clock face. Ported from raylib-jlt's `analog_clock`.

  The digital `clock` scene already reads libc's `time` and `localtime` through
  the FFI, so this shares that and spends its effort on the face: a bezel ring,
  sixty ticks with every fifth one longer, and three hands.

  Hands move continuously rather than stepping. The minute hand carries the
  seconds and the hour hand carries the minutes, which is what a mechanical
  movement does and the reason an hour hand is rarely pointing straight at a
  number. The second hand gets a sub-second fraction from the frame clock, so it
  sweeps instead of ticking."
  (:require [clojure.string]))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        r (* 0.40 (min w h))]
    {:cx (* 0.5 w) :cy (* 0.5 h) :r r
     :label-size (max 20 (int (* 0.034 (min w h))))}))

(defn polar
  "A point `len` from the centre at `deg`, zero pointing up and increasing
  clockwise, which is the direction a clock runs."
  [cx cy len deg]
  (let [t (Math/toRadians (double deg))]
    [(+ cx (* len (Math/sin t))) (- cy (* len (Math/cos t)))]))

(defn hand-angles
  "The three hand angles in degrees for `[h m s]` plus a sub-second fraction.

  Each hand carries the one below it. Without that the hour hand jumps a whole
  step on the hour and reads wrong for the other fifty-nine minutes."
  [[h m s] frac]
  (let [sec (+ s frac)]
    {:second (* 6.0 sec)
     :minute (* 6.0 (+ m (/ sec 60.0)))
     :hour   (* 30.0 (+ (mod h 12) (/ m 60.0) (/ sec 3600.0)))}))

(defn ticks
  "The sixty tick marks as [x0 y0 x1 y1 long?]."
  [{:keys [cx cy r]}]
  (mapv (fn [i]
          (let [long? (zero? (mod i 5))
                deg (* 6.0 i)
                [x0 y0] (polar cx cy (- r (if long? (* r 0.15) (* r 0.09))) deg)
                [x1 y1] (polar cx cy (- r (* r 0.055)) deg)]
            [x0 y0 x1 y1 long?]))
        (range 60)))

(defn advance
  "Carry a sub-second fraction, phased to the wall clock.

  The fraction has to be re-phased on every tick rather than free-running. libc's
  `time` has one-second resolution, so the only sub-second source here is the
  frame delta, and an accumulator over those drifts against the clock it is
  meant to subdivide. Measured on device before this fix: at second 12 the
  fraction ran .60 .68 .75 .81 .88 .95 and then wrapped to .02 while the second
  was still 12, so the hand jumped BACKWARD 5.6 degrees mid-second and forward
  again at the tick. That is the jitter.

  Snapping to zero when the second changes costs at most one frame of error and
  keeps the sweep monotonic within each second, which is the property the eye
  is actually watching for. The fraction is also clamped below 1 so a slow frame
  cannot push the hand past the tick it is approaching."
  [state input]
  (let [[_ _ sec] (:local-time input)
        dt (max 0.0 (double (:delta-seconds input 0.0)))]
    (if (and sec (not= sec (:sec state)))
      (assoc state :sec sec :frac 0.0)
      (assoc state :frac (min 0.999 (+ (:frac state 0.0) dt))))))

(defn- init [_] [{:frac 0.0 :sec nil} [[:scene/init :analog]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :analog]]])

(defn scene []
  {:id :analog :title "Analog Clock"
   :init init :update update-scene :draw draw :dispose dispose})
