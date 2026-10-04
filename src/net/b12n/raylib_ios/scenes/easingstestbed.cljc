(ns net.b12n.raylib-ios.scenes.easingstestbed
  "One easing curve at a time, plotted with a ball running along it. Ported from
  raylib-jlt's `easings_testbed`, which is raylib's `shapes_easings_testbed`
  example. A curve alone is hard to judge, because elastic and back leave the
  [0,1] band, which a plot shows and a number does not, and bounce and elastic
  feel alike in words and nothing alike in motion.

  The original uses RIGHT and LEFT to change curve, SPACE to replay and D to
  toggle the plot. Here a horizontal swipe from `net.b12n.raylib-ios.gesture` changes the
  curve (right is next, left is previous, wrapping at both ends), a `:tap`
  replays and a `:long-press` toggles the plot. A touch that starts in the Back
  region belongs to the host and is ignored. A vertical swipe does nothing.

  Timing is frame-locked, as in the original: it counts frames and never reads a
  clock, so `advance` reads no `:delta-seconds`. A run is `duration` frames,
  then the ball waits at the end until 1.6 times that, then it loops.

  The curves are the original's fifteen, in its order, which is the names
  sorted, taken from `net.b12n.raylib-ios.easings`. The plot samples the curve at
  `plot-samples` points and joins them with line segments, since the original's
  RL_LINES batch is not bound. The frame round the [0,1] band is four lines for
  the same reason.

  The original's plot is 600 by 220 in an 800 by 450 window, and the ball runs
  past the window's edge on the overshooting curves. Here everything is in the
  safe region below Back and must stay inside the screen. The two overshooting
  curves leave the band by at most `overshoot` of its size (elastic-out above by
  0.3729, elastic-in below by the same), both ways, so the band is narrowed to
  leave `overshoot` of its width at each side for the ball and of its height
  above and below for the plot. A test checks every curve against that.
  Everything is derived from the screen every frame, so a rotation needs no
  reset. Text is laid out in `dimensions` with widths estimated at 0.6 of the
  size per character."
  (:require [net.b12n.raylib-ios.easings :as ez]
            [net.b12n.raylib-ios.gesture :as gesture]))

(def duration "The original's frames per run." 120.0)
(def loop-after "The original's loop point, with a pause." (* duration 1.6))
(def plot-samples "Segments in the plotted curve." 64)
(def overshoot
  "The most any curve leaves [0,1] by. estimate: measured 0.3729 for elastic-in
  below and elastic-out above over 121 frames, 0.38 rounds it up. A test checks
  every curve."
  0.38)

(def curve-names
  "The original's order: the curve names, sorted."
  (vec (sort (map first ez/curves))))

(def ^:private curve-fns (into {} ez/curves))

(defn curve-fn [idx] (get curve-fns (nth curve-names idx)))

(def background-colour [245 245 245 255])
(def title-colour [190 33 55 255])
(def hint-colour [130 130 130 255])
(def frame-colour [200 200 200 255])
(def curve-colour [0 121 241 255])
(def rail-colour [220 220 220 255])

(defn title-line [idx]
  (str (nth curve-names idx) "   (" (inc idx) "/" (count curve-names) ")"))

(def hint-lines ["[SWIPE] curve  [TAP] replay" "[HOLD] plot"])

(defn dimensions
  "The layout for `metrics`' `:screen`. `:lines` lists the title and the two
  hints, the widest each can get, as `{:s :x :y :size}`. The plot band is
  `:plot-x :plot-y :plot-w :plot-h`, and the ball runs along the row at
  `:ball-y` with radius `:ball-r`. The title sits below `gesture/back-region`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w (- h top))
        u (/ side 450.0)
        ts (max 20 (int (* 0.03 side)))
        tt (int (* 1.5 ts))
        x (int (* 0.04 w))
        y-title (int (+ top (* 0.5 ts)))
        y-hint (int (+ y-title (* 1.3 tt)))
        y-hint2 (int (+ y-hint (* 1.3 ts)))
        text-bottom (+ y-hint2 ts)
        gap (* 0.8 ts)
        r (* 18.0 u)
        pw (/ (- w (* 2.0 r)) (+ 1.0 (* 2.0 overshoot)))
        ball-y (- h r (* 0.5 gap))
        span (- (- ball-y r gap) (+ text-bottom gap))
        ph (min (* 0.37 pw) (/ span (+ 1.0 (* 2.0 overshoot))))]
    {:w w
     :h h
     :u u
     :plot-x (+ r (* overshoot pw))
     :plot-w pw
     :plot-y (+ text-bottom gap (* overshoot ph))
     :plot-h ph
     :ball-y ball-y
     :ball-r r
     :lines [{:s (apply max-key count (map title-line (range (count curve-names))))
              :x x
              :y y-title
              :size tt}
             {:s (first hint-lines)
              :x x
              :y y-hint
              :size ts}
             {:s (second hint-lines)
              :x x
              :y y-hint2
              :size ts}]}))

(defn plot-points
  "The curve `f` as `[x y]` points across the band, left to right. y grows
  downward, so a value of 1 sits at the top of the band and 0 at the bottom."
  [{:keys [plot-x plot-w plot-y plot-h]} f]
  (mapv (fn [i]
          (let [frac (/ (double i) plot-samples)
                v (f (* frac duration) 0.0 1.0 duration)]
            [(+ plot-x (* frac plot-w))
             (- (+ plot-y plot-h) (* v plot-h))]))
        (range (inc plot-samples))))

(defn ball
  "Where the ball is `counter` frames into a run of curve `f`: it runs the same
  curve horizontally, held at the end value once the run is over."
  [{:keys [plot-x plot-w ball-y]} f counter]
  [(f (min counter duration) plot-x plot-w duration) ball-y])

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  horizontal swipe changes the curve and restarts the run, a tap restarts it,
  and a long press toggles the plot, none of them when they start in Back."
  [state input]
  (let [[g event] (gesture/track (:gesture state) input)
        in-back? (gesture/in-back-region? (or (:at event) (:from event) [-1 -1]))
        event (when-not in-back? event)
        n (count curve-names)
        step (case [(:type event) (:dir event)]
               [:swipe :right] 1
               [:swipe :left] -1
               0)
        idx (mod (+ (:idx state) step) n)
        replay? (or (not= 0 step) (= :tap (:type event)))
        counter (if replay? 0.0 (inc (:counter state)))]
    (assoc state
           :gesture g
           :idx idx
           :counter (if (> counter loop-after) 0.0 counter)
           :plot? (if (= :long-press (:type event))
                    (not (:plot? state))
                    (:plot? state)))))

(defn- init [_]
  [{:idx 0
    :counter 0.0
    :plot? true
    :gesture gesture/idle}
   [[:scene/init :easingstestbed]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :easingstestbed]]])

(defn scene []
  {:id :easingstestbed
   :title "Easings Testbed"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
