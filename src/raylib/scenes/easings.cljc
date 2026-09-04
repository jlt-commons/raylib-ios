(ns raylib.scenes.easings
  "All fifteen easing curves at once, each plotted and each driving a dot.

  raylib-jlt splits this across several examples: `easings` draws the family,
  `easings_ball` animates one, `easings_rectangles` another. A phone screen is
  tall enough to hold the lot, and seeing them together is the point, because
  a curve on its own tells you very little about how it feels.

  Each cell plots its curve across the cell and runs a dot along it on a shared
  clock, so every dot starts and finishes together and the differences between
  them are exactly the curves. The pairs are deliberate: `quad-in` beside
  `quad-out`, `elastic-in` beside `elastic-out`. An -in curve is its -out
  reflected, and reading them side by side is what makes that obvious.

  Watch `elastic-out` and `back-out` leave their cells. Both overshoot past the
  target and settle back, which is the whole reason they exist, and it is also
  why the plot is drawn with room above and below rather than filling its cell."
  (:require [raylib.easings :as ez]))

(def cycle-frames 150)
(def pause-frames 40)
(def samples 28)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        cols 3
        rows (int (Math/ceil (/ (count ez/curves) (double cols))))
        pad (* 0.02 w)
        ;; The host draws its Back button over the top-left of whatever a scene
        ;; put there, and the first cell's label went straight underneath it.
        ;; Nothing in the contract stops a scene using that corner, so a scene
        ;; that wants a label there has to leave the room itself.
        top-margin (* 0.045 h)
        cw (/ (- w (* pad (inc cols))) cols)
        ch (/ (- h top-margin (* pad (inc rows)) (* 0.02 h)) rows)]
    {:w w :h h :cols cols :rows rows :pad pad :top-margin top-margin
     :cell-w cw :cell-h ch
     ;; the plot sits inside its cell with headroom, since two curves overshoot
     :inset-y (* 0.26 ch)
     :label-h (* 0.16 ch)}))

(defn cell-origin
  "Top-left of cell `i`, reading across then down."
  [{:keys [cols pad cell-w cell-h top-margin]} i]
  [(+ pad (* (mod i cols) (+ cell-w pad)))
   (+ top-margin pad (* (quot i cols) (+ cell-h pad)))])

(defn progress
  "Where the shared clock is, in [0,1], with a pause at each end so the
  finished state is readable before it restarts."
  [t]
  (let [period (+ cycle-frames pause-frames)
        k (mod t period)]
    (min 1.0 (/ (double k) cycle-frames))))

(defn plot
  "A curve as [x y] points across its cell, oldest first.

  The curve maps its normalised output to the cell's inner height, so 0 is the
  bottom of the plot and 1 the top. A value outside [0,1] lands outside that
  band on purpose: that is what an overshoot looks like."
  [dims f ox oy]
  (let [{:keys [cell-w cell-h inset-y label-h]} dims
        top (+ oy label-h)
        height (- cell-h label-h inset-y)]
    (mapv (fn [i]
            (let [frac (/ (double i) (dec samples))
                  v (f (* frac cycle-frames) 0.0 1.0 cycle-frames)]
              [(+ ox (* frac cell-w))
               (- (+ top height) (* v height))]))
          (range samples))))

(defn dot
  "Where the running dot sits on `f` at progress `p`."
  [dims f ox oy p]
  (let [{:keys [cell-w cell-h inset-y label-h]} dims
        top (+ oy label-h)
        height (- cell-h label-h inset-y)
        v (f (* p cycle-frames) 0.0 1.0 cycle-frames)]
    [(+ ox (* p cell-w))
     (- (+ top height) (* v height))]))

(defn- init [_] [{:t 0} [[:scene/init :easings]]])
(defn- update-scene [state _] [(update state :t inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :easings]]])

(defn scene []
  {:id :easings :title "Easings"
   :init init :update update-scene :draw draw :dispose dispose})
