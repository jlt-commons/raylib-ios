(ns raylib.scenes.epicycles
  "Fourier epicycles drawing a square wave, ported from raylib-jlt's
  `fourier_epicycles`.

  A chain of circles, each turning at an odd harmonic with amplitude 4/(pi k),
  whose end traces the square wave those harmonics sum to. Watching the chain
  is watching a Fourier series converge.

  ROTATED FOR A PHONE, which is the interesting part of this port. The original
  is landscape: the epicycles sit on the left and the trace scrolls rightward
  across the remaining width. A phone is 1206 wide and 2622 tall, so there is
  no horizontal room for a scrolling wave and plenty of vertical. Here the
  chain hangs near the top and the wave scrolls DOWN, which is the same picture
  through ninety degrees. The maths is untouched: only which axis carries time
  has changed.")

(def terms 8)
(def theta-step 0.05)
(def trace-length 420)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)
        w (double width)
        h (double height)]
    {:cx (/ w 2.0)
     :cy (* h 0.20)
     :scale (* (min w h) 0.13)
     ;; the trace starts below the chain and runs down the screen
     :trace-top (* h 0.36)
     :trace-step (/ (* h 0.60) trace-length)}))

(defn chain
  "The epicycle centres and radii at angle `theta`, outermost last.

  Returns {:centers [[x y] ...] :radii [r ...]}, one more centre than radius,
  so centre i and centre i+1 are the ends of the rod of radius i."
  [dims theta]
  (let [{:keys [cx cy scale]} dims]
    (loop [k 1 x cx y cy centers [[cx cy]] radii []]
      (if (> k (dec (* 2 terms)))
        {:centers centers :radii radii}
        (let [radius (* scale (/ 4.0 Math/PI) (/ 1.0 k))
              nx (+ x (* radius (Math/cos (* k theta))))
              ny (+ y (* radius (Math/sin (* k theta))))]
          (recur (+ k 2) nx ny (conj centers [nx ny]) (conj radii radius)))))))

(defn advance [state metrics]
  (let [dims (dimensions metrics)
        theta (+ (:theta state) theta-step)
        {:keys [centers]} (chain dims theta)
        ;; the pen's x is what the wave records, because time runs down the
        ;; screen rather than across it
        [px _] (last centers)]
    (assoc state :theta theta
           :trace (vec (take trace-length (cons px (:trace state)))))))

(defn- init [_input]
  [{:theta 0.0 :trace []} [[:scene/init :epicycles]]])

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :epicycles]]])

(defn scene []
  {:id :epicycles :title "Fourier Epicycles"
   :init init :update update-scene :draw draw :dispose dispose})
