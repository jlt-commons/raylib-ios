(ns raylib.scenes.easingsbox
  "A square that drops, flattens into a bar, spins, grows to fill the screen and
  fades out, each stage on its own easing curve. Ported from raylib-jlt's
  `easings_box`, which is raylib's `shapes_easings_box_anim` example. A `:tap`
  from `raylib.gesture` restarts it, in place of SPACE, unless it lands in the
  Back region, which belongs to the host.

  The five curves are chosen to contrast, as in the original: elastic-out for
  the drop, so it overshoots and springs back, bounce-out to flatten, quad-out
  for a plain decelerating spin, circ-out for a grow that starts fast and
  brakes hard, and sine-out for a gentle fade. They come from `raylib.easings`,
  which keeps raylib's `(t b c d)` shape.

  Timing is frame-locked, as in the original: it counts frames, never reads a
  clock, and each stage lasts the original's number of frames (120, 120, 240,
  120 and 160). So `advance` reads no `:delta-seconds`. Each property keeps the
  value its own stage left, which is what lets later stages animate on top of
  earlier ones. After the fade the scene holds at `:done`, fully transparent,
  until a tap. Finishing is not a restart trigger, so no gesture value needs
  parking when it happens.

  The original draws with DrawRectanglePro, a rotated rectangle about its
  centre, which is not bound. `quad-corners` computes the four rotated corners
  in this pure namespace and the draw method fills them with two triangles.

  The original works in an 800 by 450 window, with the square dropping in from
  above the top edge and growing past the window's. Here everything lives in the
  safe region below Back, scaled by `u`, the shorter side of that region over
  450. The drop starts with the square's top edge touching Back's lower edge, so
  nothing is ever drawn under the Back button or off the screen. The bar's
  length is 0.9 of the shorter side, so it stays inside at every angle of the
  spin. The box then grows until it fills the region exactly. It has turned 270
  degrees by then, so its local width ends as the region's height and its local
  height as the region's width, which is the original's `W` by `W` growth
  adapted to a window that is not square. In the original the width stays fixed
  during the grow. Here it grows too, since a portrait region is taller than
  the bar is long. Text is laid out in `dimensions`, with widths estimated at
  0.6 of the size per character."
  (:require [raylib.easings :as ez]
            [raylib.gesture :as gesture]))

(def drop-frames "The original's frames per stage." 120.0)
(def flatten-frames 120.0)
(def spin-frames 240.0)
(def grow-frames 120.0)
(def fade-frames 160.0)

(defn stage-length
  "Frames in `stage`; `:done` holds, so it counts as one."
  [stage]
  (case stage
    :drop drop-frames
    :flatten flatten-frames
    :spin spin-frames
    :grow grow-frames
    :fade fade-frames
    1.0))

(def ^:private next-stage
  {:drop :flatten
   :flatten :spin
   :spin :grow
   :grow :fade
   :fade :done})

(def background-colour [245 245 245 255])
(def box-colour "The original's colour, with the alpha left to the fade." [0 82 172])
(def text-colour [80 80 80 255])

(defn stage-line
  "The original's readout, with a tap for SPACE."
  [stage]
  (str "stage: " (name stage) "   [TAP] restart"))

(defn dimensions
  "The layout for `metrics`' `:screen`. `:top` is where the region below Back
  starts, `:cx :cy` its centre and `:u` the scale of the original's pixels.
  `:bar` is the flattened bar's length and `:fill-w :fill-h` the box's local
  width and height once grown, which after the 270 degree spin are the region's
  height and width. `:lines` holds the one text line, the widest it can get, as
  `{:s :x :y :size}` so a test can check it fits. It sits at the bottom, clear
  of Back."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        ah (- h top)
        side (min w ah)
        ts (max 20 (int (* 0.03 side)))
        widest (apply max-key count (map stage-line [:drop :flatten :spin :grow :fade :done]))]
    {:w w
     :h h
     :top top
     :cx (* 0.5 w)
     :cy (+ top (* 0.5 ah))
     :u (/ side 450.0)
     :bar (* 0.9 side)
     :fill-w ah
     :fill-h w
     :text-size ts
     :lines [{:s widest
              :x (int (* 0.04 w))
              :y (int (- h (* 2.0 ts)))
              :size ts}]}))

(defn shape
  "The box at `counter` frames into `stage`: `{:cx :cy :w :h :rot :alpha}`, `:rot`
  in degrees and `:alpha` in [0,1]. Each property is in its stage's curve while
  that stage runs and holds the finished value after it."
  [{:keys [top cx cy u bar fill-w fill-h]} stage counter]
  (let [side (* 100.0 u)
        thin (* 10.0 u)
        y0 (+ top (* 0.5 side))]
    {:cx cx
     :cy (if (= stage :drop)
           (ez/elastic-out counter y0 (- cy y0) drop-frames)
           cy)
     :w (case stage
          :drop side
          :flatten (ez/bounce-out counter side (- bar side) flatten-frames)
          :grow (ez/circ-out counter bar (- fill-w bar) grow-frames)
          (:fade :done) fill-w
          bar)
     :h (case stage
          :drop side
          :flatten (ez/bounce-out counter side (- thin side) flatten-frames)
          :grow (ez/circ-out counter thin (- fill-h thin) grow-frames)
          (:fade :done) fill-h
          thin)
     :rot (case stage
            (:drop :flatten) 0.0
            :spin (ez/quad-out counter 0.0 270.0 spin-frames)
            270.0)
     :alpha (case stage
              :fade (ez/sine-out counter 1.0 -1.0 fade-frames)
              :done 0.0
              1.0)}))

(defn quad-corners
  "The four corners `[[x y] ...]` of a `w` by `h` rectangle centred on `[cx cy]`
  and turned `deg` degrees about that centre, in the order top-left, top-right,
  bottom-right, bottom-left before the turn. Screen y grows downward, so a
  positive angle turns clockwise on screen, as DrawRectanglePro's does."
  [cx cy w h deg]
  (let [rad (* (double deg) (/ Math/PI 180.0))
        c (Math/cos rad)
        s (Math/sin rad)
        hw (* 0.5 (double w))
        hh (* 0.5 (double h))]
    (mapv (fn [[dx dy]]
            [(+ cx (- (* dx c) (* dy s)))
             (+ cy (+ (* dx s) (* dy c)))])
          [[(- hw) (- hh)] [hw (- hh)] [hw hh] [(- hw) hh]])))

(defn quad-corners-of
  "`quad-corners` of a `shape` map."
  [{:keys [cx cy w h rot]}]
  (quad-corners cx cy w h rot))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  tap outside Back restarts from the first stage, and a frame that ends a stage
  starts the next one. At `:done` the counter stops, so nothing changes until a
  tap."
  [state input]
  (let [[g event] (gesture/track (:gesture state) input)
        restart? (and (= :tap (:type event))
                      (not (gesture/in-back-region? (:at event))))
        stage (if restart? :drop (:stage state))
        counter (cond restart? 0.0
                      (= stage :done) (:counter state)
                      :else (inc (:counter state)))
        over? (and (not= stage :done) (>= counter (stage-length stage)))]
    (assoc state
           :gesture g
           :stage (if over? (next-stage stage) stage)
           :counter (if over? 0.0 counter))))

(defn- init [_]
  [{:stage :drop
    :counter 0.0
    :gesture gesture/idle}
   [[:scene/init :easingsbox]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :easingsbox]]])

(defn scene []
  {:id :easingsbox
   :title "Easings Box"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
