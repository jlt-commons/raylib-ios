(ns raylib.easings
  "raylib's easing curves, ported from raylib-jlt's `reasings`, which is itself
  the Clojure counterpart of `examples/shapes/reasings.h`.

  Not a scene. This is a shared namespace two scenes draw from, kept separate
  for the same reason the upstream examples share one header rather than each
  carrying a copy.

  Every function keeps raylib's four-argument shape:

      (ease t b c d)  ->  b + c * f(t/d)

  `t` is elapsed time, `b` the value at the start, `c` the TOTAL CHANGE rather
  than the end value, and `d` the duration. Normalising to [0,1] would read more
  naturally in Clojure and is deliberately not done: passing a negative `c` is
  how the upstream examples shrink rather than grow, and keeping the signature
  lets a ported call site sit beside the C line it came from.

  `t` is clamped to `d`, so a counter that overruns holds at the end value
  rather than continuing past it. The C relies on its callers to stop
  incrementing; clamping here makes each function total, which is also what
  makes them testable without a loop.

  Named for the header rather than for Clojure, so the correspondence stays
  obvious to anyone reading both."
  (:refer-clojure :exclude [name]))

(defn- norm
  "t/d, clamped to [0,1]. Zero duration reads as finished rather than dividing
  by zero, which is the sane answer for an animation with no length."
  ^double [t d]
  (let [d (double d)]
    (if (zero? d) 1.0 (min 1.0 (max 0.0 (/ (double t) d))))))

(defn linear [t b c d] (+ b (* c (norm t d))))

(defn cubic-in   [t b c d] (let [x (norm t d)] (+ b (* c x x x))))
(defn cubic-out  [t b c d] (let [x (- (norm t d) 1.0)] (+ b (* c (inc (* x x x))))))
(defn cubic-in-out
  [t b c d]
  (let [x (* 2.0 (norm t d))]
    (if (< x 1.0)
      (+ b (* (/ c 2.0) x x x))
      (let [x (- x 2.0)] (+ b (* (/ c 2.0) (+ (* x x x) 2.0)))))))

(defn quad-in  [t b c d] (let [x (norm t d)] (+ b (* c x x))))
(defn quad-out [t b c d] (let [x (norm t d)] (- b (* c x (- x 2.0)))))

(defn circ-in  [t b c d] (let [x (norm t d)] (+ b (* c (- 1.0 (Math/sqrt (- 1.0 (* x x))))))))
(defn circ-out [t b c d] (let [x (- (norm t d) 1.0)] (+ b (* c (Math/sqrt (- 1.0 (* x x)))))))

;; The two elastic curves are the interesting pair. Each is a decaying sine, so
;; the value crosses its target several times before settling, which is why a
;; ball eased with elastic-out arrives past centre and springs back.
(defn elastic-in
  [t b c d]
  (let [x (norm t d)]
    (cond
      (zero? x) b
      (>= x 1.0) (+ b c)
      :else (let [p 0.3
                  s (/ p 4.0)
                  x (- x 1.0)]
              (- b (* c (Math/pow 2.0 (* 10.0 x))
                      (Math/sin (/ (* (- x s) 2.0 Math/PI) p))))))))

(defn elastic-out
  [t b c d]
  (let [x (norm t d)]
    (cond
      (zero? x) b
      (>= x 1.0) (+ b c)
      :else (let [p 0.3
                  s (/ p 4.0)]
              (+ b c (* c (Math/pow 2.0 (* -10.0 x))
                        (Math/sin (/ (* (- x s) 2.0 Math/PI) p))))))))

;; Four parabolas of decreasing height, which is what a bouncing ball is.
(defn bounce-out
  [t b c d]
  (let [x (norm t d)]
    (+ b (* c (cond
                (< x (/ 1.0 2.75)) (* 7.5625 x x)
                (< x (/ 2.0 2.75)) (let [x (- x (/ 1.5 2.75))]   (+ (* 7.5625 x x) 0.75))
                (< x (/ 2.5 2.75)) (let [x (- x (/ 2.25 2.75))]  (+ (* 7.5625 x x) 0.9375))
                :else              (let [x (- x (/ 2.625 2.75))] (+ (* 7.5625 x x) 0.984375)))))))

(defn bounce-in
  "bounce-out reflected in both axes, which is what upstream does too.

  The reflection has one degenerate case the others do not. At `d` of zero,
  `norm` clamps to 1.0 so bounce-out returns its full change, and subtracting
  that from b+c gives b: the opposite of every other curve here, all of which
  read a zero duration as finished. Special-cased so the family is consistent,
  since a caller cannot reasonably be expected to know which curve it is
  holding."
  [t b c d]
  (if (zero? (double d))
    (+ b c)
    (+ b c (- (bounce-out (- d t) 0 c d)))))

(defn sine-in  [t b c d] (+ b (* c (- 1.0 (Math/cos (* (norm t d) (/ Math/PI 2.0)))))))
(defn sine-out [t b c d] (+ b (* c (Math/sin (* (norm t d) (/ Math/PI 2.0))))))

(defn back-out
  [t b c d]
  (let [s 1.70158
        x (- (norm t d) 1.0)]
    (+ b (* c (inc (+ (* x x (+ (* (inc s) x) s))))))))

(def curves
  "Every curve with its name, in the order a grid should show them: the gentle
  ones first, then the two that overshoot, then the bounces."
  [["linear" linear]
   ["quad-in" quad-in]         ["quad-out" quad-out]
   ["sine-in" sine-in]         ["sine-out" sine-out]
   ["cubic-in" cubic-in]       ["cubic-out" cubic-out]
   ["cubic-in-out" cubic-in-out]
   ["circ-in" circ-in]         ["circ-out" circ-out]
   ["back-out" back-out]
   ["elastic-in" elastic-in]   ["elastic-out" elastic-out]
   ["bounce-in" bounce-in]     ["bounce-out" bounce-out]])
