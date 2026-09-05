(ns raylib.gallery
  "The scene gallery: a two-level menu over seventeen scenes, on the iOS host.

  This namespace is the half that has to touch raylib, and it exists so that
  nothing else does. It polls the scalar input, hands the result to the pure
  gallery in poc.raylib.gallery, and draws whatever comes back. Every scene
  itself is pure and appears here only as a draw-scene! method.

  That split is why three of the scenes run byte-identical to files written for
  Android. Nothing in them knows what a platform is."
  (:require [poc.raylib.diagnostics :as diag]
            [poc.raylib.flappy-bird :as flappy]
            [poc.raylib.following-eyes :as eyes]
            [poc.raylib.gallery :as gallery]
            [poc.raylib.gallery-ui :as ui]
            [poc.raylib.touch-trail :as trail]
            [raylib.flappy :as flappy-draw]
            [raylib.host :as rl]
            [raylib.scenes.automata :as auto]
            [raylib.scenes.angles :as ang]
            [raylib.scenes.balls :as balls]
            [raylib.scenes.boids :as boids]
            [raylib.scenes.bullets :as bull]
            [raylib.scenes.collision :as coll]
            [raylib.scenes.dashed :as dash]
            [raylib.scenes.multitouch :as multi]
            [raylib.scenes.analog :as analog]
            [raylib.scenes.clockgrid :as cgrid]
            [raylib.scenes.sector :as sector]
            [raylib.scenes.palette :as pal]
            [raylib.scenes.gradient :as grad]
            [raylib.scenes.ring :as ring]
            [raylib.scenes.splines :as spl]
            [raylib.scenes.rounded :as rnd]
            [raylib.scenes.vecangle :as vang]
            [raylib.scenes.bars :as bars]
            [raylib.scenes.bezier :as bez]
            [raylib.scenes.fan :as fan]
            [raylib.scenes.clipbox :as clipbox]
            [raylib.scenes.resize :as rsz]
            [raylib.scenes.align :as align]
            [raylib.scroll :as scroll]
            [raylib.scenes.clock :as clock]
            [raylib.scenes.colorwheel :as wheel]
            [raylib.easings :as ez]
            [raylib.scenes.easings :as ease]
            [raylib.scenes.epicycles :as epi]
            [raylib.scenes.flowfield :as flow]
            [raylib.scenes.hilbert :as hil]
            [raylib.scenes.life :as life]
            [raylib.scenes.logoanim :as logo]
            [raylib.scenes.lorenz :as lor]
            [raylib.scenes.lsystem :as lsys]
            [raylib.scenes.fireworks :as fw]
            [raylib.scenes.kaleidoscope :as kal]
            [raylib.scenes.pendulum :as pend]
            [raylib.scenes.piechart :as pie]
            [raylib.scenes.penrose :as pen]
            [raylib.scenes.sequence :as seqn]
            [raylib.scenes.spirograph :as spiro]
            [raylib.scenes.stars :as stars]
            [raylib.scenes.tesseract :as tess]
            [raylib.scenes.unitcircle :as circle]
            [raylib.scenes.tree :as tree]
            [raylib.scenes.writing :as writ]))

(def scenes [(eyes/scene) (trail/scene) (flappy/scene)
             (spiro/scene) (kal/scene) (fw/scene) (pen/scene) (boids/scene)
             (pend/scene) (epi/scene) (hil/scene) (tree/scene) (stars/scene)
             (lsys/scene) (flow/scene) (lor/scene) (tess/scene)
             (life/scene) (auto/scene)
             (wheel/scene) (circle/scene)
             (clock/scene) (pie/scene) (logo/scene)
             (ease/scene)
             (ang/scene) (writ/scene) (balls/scene) (seqn/scene)
             (bull/scene) (coll/scene) (dash/scene) (multi/scene)
             (analog/scene) (cgrid/scene) (sector/scene) (pal/scene)
             (grad/scene) (ring/scene) (spl/scene)
             (rnd/scene) (vang/scene) (bars/scene)
             (bez/scene) (fan/scene) (clipbox/scene)
             (rsz/scene) (align/scene)])

(def registry (gallery/make-registry scenes))
(def scene-ids (mapv :id scenes))

;; --- a level above the scene contract ----------------------------------------
;; poc.raylib.gallery knows two modes, :gallery and :scene, and its layout fits
;; every card on one screen by dividing the height by the row count. That is
;; right for three scenes and unreadable for fifty: it never scrolls, it just
;; shrinks. So categories sit ABOVE that contract rather than inside it, and
;; the pure file stays byte-identical.
;;
;; The whole trick is that ui/gallery-layout takes the ids to lay out as an
;; argument. Hand it category ids and it lays out categories; hand it the ids
;; in a category and it lays out those. Same untouched function, twice.
(def categories
  [{:id :generative :title "Generative"
    :scenes [:spirograph :kaleidoscope :fireworks :penrose :epicycles :flowfield
             :lorenz :life :bullets]}
   {:id :fractals :title "Fractals"
    :scenes [:hilbert :tree :lsystem :automata]}
   {:id :toys :title "Toys"
    :scenes [:following-eyes :touch-trail :boids :pendulum :stars :tesseract
             :colorwheel :unitcircle :clock :piechart :logoanim :easings
             :angles :writing :balls :sequence :collision :dashed :multitouch
             :analog :clockgrid :sector :palette :gradient :ring :splines
             :rounded :vecangle :bars :bezier :fan :clipbox :resize :align]}
   {:id :games :title "Games"
    :scenes [:flappy-bird]}])

(def ^:private category-ids (mapv :id categories))

(defn- category-by-id [id] (some (fn [c] (when (= id (:id c)) c)) categories))

(defn- title-of
  "Cards are drawn from an id, and an id is a category at the top level and a
  scene inside one."
  [id]
  (or (:title (category-by-id id))
      (:title (gallery/scene-by-id registry id))
      (name id)))

(defn- within?
  "hit-test only checks the Back target in :scene mode, but a category's scene
  list needs cards AND a Back. contains-point? is private to the pure file, so
  this is the same four comparisons."
  [{:keys [x y width height]} [px py]]
  (and px (>= px x) (< px (+ x width)) (>= py y) (< py (+ y height))))

(def WHITE (rl/rgba 255 255 255 255))
(defn- color [[r g b a]] (rl/rgba r g b a))

;; --- driving it from an editor ------------------------------------------------
;; A synthetic tap, consumed by the next frame. The host can already read state
;; and run work on the main thread, but the scene state is threaded through the
;; loop rather than held in an atom, so an nREPL could look and not touch. This
;; is the missing half: it lets a session open a scene, press Back, or flap the
;; bird without a finger, which is what makes the examples testable from a
;; keyboard.
;;
;; One tap per frame, cleared as it is taken, so a queued tap cannot be seen
;; twice and read as a held touch.
(defonce pending-tap (atom nil))

;; A queued drag, as a vector of points still to deliver. tap! cannot express
;; one, because a drag is several frames of a finger being DOWN and moving,
;; which is exactly what the gallery's scrolling reads. Without this the scroll
;; can only be tested by a person swiping, and a person cannot swipe while
;; iPhone Mirroring holds the device screen locked.
(defonce pending-drag (atom nil))

(defn tap!
  "Queue a synthetic tap at [x y] in SCREEN PIXELS, not points. The next frame
  sees a press there and the frame after sees the release, which is the edge
  the gallery opens a card on."
  [x y]
  (reset! pending-tap [x y])
  nil)

(defn drag!
  "Queue a synthetic drag from [x1 y1] to [x2 y2] over `steps` frames.

  Delivered one point per frame: the first is a press, the rest are the finger
  moving while down, and running out queues nothing so the next device sample
  reports the release. That is the shape the scroll logic reads, and it is what
  tap! cannot express, since a tap is a press and a release with nothing in
  between."
  ([x1 y1 x2 y2] (drag! x1 y1 x2 y2 12))
  ([x1 y1 x2 y2 steps]
   (let [n (max 2 (long steps))]
     (reset! pending-drag
             (mapv (fn [i]
                     (let [t (/ (double i) (dec n))]
                       [(+ x1 (* (- x2 x1) t)) (+ y1 (* (- y2 y1) t))]))
                   (range n))))
   nil))

;; --- polling ------------------------------------------------------------------
;; raylib's input is scalar: nothing is delivered, you sample. The map built
;; here is the raw shape poc.raylib.diagnostics/normalize-input consumes, and
;; its keys are that function's contract rather than a choice made here.
;;
;; Edges are derived, not received. raylib will tell you how many fingers are
;; down this instant and nothing about the instant before, so a press is this
;; frame's count rising off zero and a release is it falling back to it. The
;; previous count arrives as an argument because the loop, not this function,
;; is what remembers.

(defn- touched-ids
  "The raylib id of every active touch. Ids are not always 0..n-1, which is why
  they are read rather than assumed."
  [n]
  (mapv rl/get-touch-point-id (range n)))

(defn- sample-device
  "One poll of the hardware."
  [previous-count]
  (let [n (rl/get-touch-point-count)]
    {:touch-count n
     :touch-ids   (touched-ids n)
     :pointer-x   (rl/get-touch-x)
     :pointer-y   (rl/get-touch-y)
     :down?       (pos? n)
     :pressed?    (and (pos? n) (zero? previous-count))
     :released?   (and (zero? n) (pos? previous-count))}))

(defn- sample-synthetic
  "One poll answered by a queued tap! instead of the screen. Reported as a
  single finger with id 0, since that is what one tap is."
  [[x y] previous-count]
  {:touch-count 1
   :touch-ids   [0]
   :pointer-x   x
   :pointer-y   y
   :down?       true
   :pressed?    (zero? previous-count)
   :released?   false})

(defn- raw-sample
  "A frame's input, from a queued tap if one is waiting and from the screen
  otherwise. A tap wins because it exists to drive the app with no finger
  present, and taking both would double-count the press.

  Returns `[raw tap]`. The tap comes back out because this is the only place it
  is consumed, and `touch-points` needs to know whether the frame was synthetic
  without reading the atom a second time and finding it already emptied."
  [previous-count]
  (let [tap (or (first (swap-vals! pending-tap (constantly nil)))
                ;; A queued drag delivers one point per frame and reports itself
                ;; the same way a tap does, so nothing downstream needs to know
                ;; which kind of synthetic gesture it is looking at.
                (let [[before] (swap-vals! pending-drag next)]
                  (first before)))
        w   (rl/get-screen-width)
        h   (rl/get-screen-height)]
    [(merge {:screen-width w :screen-height h
             ;; no HighDPI translation to do: the window is created at pixel
             ;; size, so the render surface and the screen are the same numbers
             :render-width w :render-height h
             :back? false}
            ;; Each sampler reports :down? from the count it already read. An
            ;; earlier draft derived it here with a second GetTouchPointCount
            ;; call, which polls the hardware twice in one frame and can
            ;; disagree with itself when a finger lifts between the two.
            (if tap
              (sample-synthetic tap previous-count)
              (sample-device previous-count)))
     tap]))

(defn- touch-points
  "Every active touch as [x y], in screen pixels.

  This sits beside `:touches` rather than inside it. The Android contract in
  `poc.raylib.diagnostics` reports `:all-coordinates-available? false` and gives
  coordinates for point zero only, which was honest on the platform it was
  written for: it has GetTouchX and GetTouchY and no binding for the by-value
  Vector2 that GetTouchPosition returns. That file is one of the six verified
  byte-identical against the notebooks, so the extra data goes in a key of our
  own and the contract keeps its word.

  A synthetic tap reports itself as the one point, so `tap!` drives this the
  same way it drives everything else."
  [tap]
  (if tap
    [[(double (first tap)) (double (second tap))]]
    (mapv rl/touch-position (range (rl/get-touch-point-count)))))

;; --- drawing: the owner-affine half of the contract
(defmulti draw-scene! (fn [id _state _env] id))

(defmethod draw-scene! :flappy-bird [_ game {:keys [k m]}]
  (flappy-draw/draw-game! k game m))

(defmethod draw-scene! :following-eyes [_ {:keys [target]} {:keys [m]}]
  (let [{:keys [eye-radius pupil-radius left right]} (eyes/layout m)]
    (rl/clear-background rl/RAYWHITE)
    (doseq [eye [left right]]
      (let [[ex ey] eye
            [px py] (eyes/pupil eye eye-radius pupil-radius target)]
        (rl/draw-circle (int ex) (int ey) (double eye-radius) WHITE)
        (rl/draw-circle-lines (int ex) (int ey) (double eye-radius) rl/DARKGRAY)
        (rl/draw-circle (int px) (int py) (double pupil-radius) rl/DARKGRAY)))))

(defmethod draw-scene! :kaleidoscope [_ {:keys [trail]} {:keys [m]}]
  ;; Everything on the per-line path is inlined here on purpose, and the
  ;; measurements are in docs/guide/performance-on-a-phone.md. Briefly: this
  ;; scene draws twelve lines per trail segment, and calling a helper that
  ;; returns [x y] allocated two vectors per line. Removing that took 1068
  ;; lines from 22 fps to 47. kaleidoscope/place is the readable statement of
  ;; the same transform, kept for the tests.
  (rl/clear-background (rl/rgba 12 12 20 255))
  (let [{:keys [cx cy]} (kal/dimensions m)
        rots (kal/rotations)
        rn (count rots)
        n (count trail)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth trail (dec i))
              b (nth trail i)
              ax (double (nth a 0)) ay (double (nth a 1))
              bx (double (nth b 0)) by (double (nth b 1))
              age (/ (double i) n)
              packed (rl/rgba (int (* 255 age)) 120 (int (* 255 (- 1.0 age))) 255)]
          (loop [r 0]
            (when (< r rn)
              (let [rot (nth rots r)
                    ca (double (nth rot 0))
                    sa (double (nth rot 1))
                    ax (if (nth rot 2) (- ax) ax)
                    bx (if (nth rot 2) (- bx) bx)]
                (rl/draw-line (int (+ cx (- (* ax ca) (* ay sa)))) (int (+ cy (+ (* ax sa) (* ay ca))))
                              (int (+ cx (- (* bx ca) (* by sa)))) (int (+ cy (+ (* bx sa) (* by ca))))
                              packed))
              (recur (inc r)))))
        (recur (inc i))))))

(defmethod draw-scene! :fireworks [_ {:keys [rockets parts]} {:keys [m]}]
  (let [{:keys [rocket-radius particle-radius]} (fw/dimensions m)]
    (rl/clear-background (rl/rgba 0 0 0 255))
    (doseq [{:keys [x y color]} rockets]
      (let [[r g b] color]
        (rl/draw-circle (int x) (int y) (double rocket-radius) (rl/rgba r g b 255))))
    (doseq [{:keys [x y life color]} parts]
      (let [[r g b] color]
        (rl/draw-circle (int x) (int y) (double particle-radius)
                        (rl/rgba r g b (int (* 255 (max 0.0 life)))))))))

(defmethod draw-scene! :boids [_ {:keys [flock]} {:keys [m]}]
  (let [dims (boids/dimensions m)
        body (:body dims)
        wing (rl/rgba 120 200 255 255)]
    (rl/clear-background (rl/rgba 20 20 30 255))
    (doseq [b flock]
      (let [[hx hy] (boids/heading b dims)]
        (rl/draw-line (int (:x b)) (int (:y b)) (int hx) (int hy) wing)
        (rl/draw-circle (int (:x b)) (int (:y b)) (double body) rl/SKYBLUE)))))

(defmethod draw-scene! :penrose [_ {:keys [tris]} _]
  (rl/clear-background (rl/rgba 18 18 24 255))
  ;; one rlgl batch for every fill, then the edges as ordinary lines
  (rl/rl-begin rl/RL-TRIANGLES)
  (doseq [[k [ax ay] [bx by] [cx cy]] tris]
    (let [[r g b a] (if (zero? k) pen/colour-thin pen/colour-thick)]
      (rl/rl-color-4ub r g b a)
      (rl/rl-vertex-2f (float ax) (float ay))
      (rl/rl-vertex-2f (float bx) (float by))
      (rl/rl-vertex-2f (float cx) (float cy))))
  (rl/rl-end)
  (let [edge (color pen/colour-edge)]
    (doseq [[_ [ax ay] [bx by] [cx cy]] tris]
      (rl/draw-line (int ax) (int ay) (int bx) (int by) edge)
      (rl/draw-line (int bx) (int by) (int cx) (int cy) edge)
      (rl/draw-line (int cx) (int cy) (int ax) (int ay) edge))))

(defmethod draw-scene! :lsystem [_ {:keys [segments frame]} _]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (lsys/shown frame (count segments))
        green (rl/rgba 0 228 48 255)]
    (loop [i 0]
      (when (< i n)
        (let [s (nth segments i)]
          (rl/draw-line (int (nth s 0)) (int (nth s 1)) (int (nth s 2)) (int (nth s 3)) green))
        (recur (inc i))))))

(defmethod draw-scene! :flowfield [_ {:keys [parts]} _]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (count parts)]
    (loop [i 0]
      (when (< i n)
        (let [p (nth parts i)
              trail (:trail p)
              tn (count trail)
              [cr cg cb ca] (flow/trail-colour (:angle p))
              packed (rl/rgba cr cg cb ca)]
          (loop [j 1]
            (when (< j tn)
              (let [a (nth trail (dec j)) b (nth trail j)]
                (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1)) packed))
              (recur (inc j)))))
        (recur (inc i))))))

(defmethod draw-scene! :hilbert [_ {:keys [points colours]} _]
  (rl/clear-background (rl/rgba 12 12 20 255))
  (let [n (count points)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth points (dec i))
              b (nth points i)
              [r g bl al] (nth colours i)]
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1))
                        (rl/rgba r g bl al)))
        (recur (inc i))))))

(defmethod draw-scene! :tree [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [segs (tree/branches m (tree/wind t))
        n (count segs)
        bark (color tree/bark)
        leaf (color tree/leaf)]
    (loop [i 0]
      (when (< i n)
        (let [s (nth segs i)]
          (rl/draw-line (int (nth s 0)) (int (nth s 1)) (int (nth s 2)) (int (nth s 3))
                        (if (nth s 4) leaf bark)))
        (recur (inc i))))))

(defmethod draw-scene! :stars [_ {:keys [stars]} {:keys [m]}]
  (rl/clear-background (rl/rgba 0 0 8 255))
  (let [dims (stars/dimensions m)
        n (count stars)
        white (rl/rgba 255 255 255 255)]
    (loop [i 0]
      (when (< i n)
        (when-let [p (stars/project dims (nth stars i))]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (double (nth p 2)) white))
        (recur (inc i))))))

(defmethod draw-scene! :pendulum [_ {:keys [trail] :as st} {:keys [m]}]
  ;; Indexed loops throughout, per docs/guide/performance-on-a-phone.md.
  (let [{:keys [ox oy bob trail-dot]} (pend/dimensions m)
        [[x1 y1] [x2 y2]] (pend/positions st m)
        n (count trail)]
    (rl/clear-background (rl/rgba 20 20 30 255))
    (loop [i 0]
      (when (< i n)
        (let [p (nth trail i)
              t (/ (double (inc i)) n)]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (* 2.0 trail-dot t)
                          (rl/rgba 80 200 255 (int (* 200 t)))))
        (recur (inc i))))
    (rl/draw-line (int ox) (int oy) (int x1) (int y1) rl/RAYWHITE)
    (rl/draw-line (int x1) (int y1) (int x2) (int y2) rl/RAYWHITE)
    (rl/draw-circle (int x1) (int y1) (double bob) (rl/rgba 255 203 0 255))
    (rl/draw-circle (int x2) (int y2) (double bob) rl/MAROON)))

(defmethod draw-scene! :epicycles [_ {:keys [theta trace]} {:keys [m]}]
  (let [dims (epi/dimensions m)
        {:keys [trace-top trace-step]} dims
        {:keys [centers radii]} (epi/chain dims theta)
        rn (count radii)
        faint (rl/rgba 70 70 80 255)
        rod (rl/rgba 130 130 130 255)
        wave (rl/rgba 255 203 0 255)]
    (rl/clear-background (rl/rgba 0 0 0 255))
    (loop [i 0]
      (when (< i rn)
        (let [a (nth centers i)
              b (nth centers (inc i))]
          (rl/draw-circle-lines (int (nth a 0)) (int (nth a 1)) (double (nth radii i)) faint)
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1)) rod))
        (recur (inc i))))
    ;; the pen, and the line joining it to where the wave starts
    (let [pen (nth centers rn)
          px (int (nth pen 0))
          py (int (nth pen 1))]
      (rl/draw-line px py px (int trace-top) (rl/rgba 90 90 90 255))
      (let [n (count trace)]
        (loop [i 1]
          (when (< i n)
            (rl/draw-line (int (nth trace (dec i))) (int (+ trace-top (* (dec i) trace-step)))
                          (int (nth trace i)) (int (+ trace-top (* i trace-step)))
                          wave)
            (recur (inc i))))))))

(defmethod draw-scene! :spirograph [_ {:keys [points]} _]
  ;; An indexed loop rather than (map-indexed vector (partition 2 1 points)).
  ;; Measured on device: the lazy sequence and its per-segment tuple took this
  ;; from about 50 fps to 14 at a thousand points, while the draw calls
  ;; themselves were never the problem.
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (count points)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth points (dec i))
              b (nth points i)
              [r g b' a'] (spiro/rainbow i)]
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1))
                        (rl/rgba r g b' a')))
        (recur (inc i))))))

(defmethod draw-scene! :touch-trail [_ {:keys [points]} {:keys [m]}]
  ;; The trail is drawn oldest first, so each circle overlaps the one before
  ;; and the stroke reads as a single tapering shape rather than a row of
  ;; discs. Radius scales with position in the trail, which is what makes the
  ;; head look like the finger and the tail look like where it has been.
  (let [{:keys [radius]} (trail/layout m)
        n (max 1 (count points))
        biggest (double radius)]
    (rl/clear-background rl/RAYWHITE)
    (loop [i 0 ps points]
      (when-let [p (first ps)]
        (let [scale (/ (double (inc i)) n)]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (* biggest scale) rl/MAROON))
        (recur (inc i) (rest ps))))))

(defn- centered-text!
  "Draw `text` centred in the rectangle, with MeasureText."
  [text {:keys [x y width height]} size colour]
  (rl/draw-text text (+ x (quot (- width (rl/measure-text text size)) 2)) (+ y (quot (- height size) 2)) size colour))

(defn- draw-back! [{:keys [back body-size]} accent]
  (let [{:keys [x y width height]} back]
    (rl/draw-rectangle x y width height accent)
    (centered-text! "< Back" back body-size WHITE)))

(defn- draw-gallery!
  "The heading and the card grid, with the grid clipped to the area below the
  heading so scrolled cards do not slide up over it.

  The heading is drawn after the cards rather than before. Drawing it first and
  letting a card scroll over it is the obvious ordering and the wrong one: the
  scissor already stops that, and the heading then has to be redrawn anyway
  because the card that overlapped it has repainted the background it sat on."
  [{:keys [margin title-size body-size line-gap cards content-height viewport-height]}
   p top scroll heading subheading]
  (rl/clear-background (color (:background p)))
  (let [grid-top (+ top margin title-size (* 2 line-gap))
        grid-h (- (+ top viewport-height) grid-top)]
    (rl/begin-scissor-mode 0 grid-top (rl/get-screen-width) grid-h)
    (doseq [{:keys [scene-id] :as card} cards]
      ;; Cards outside the window are skipped rather than drawn and clipped. A
      ;; long list is mostly off screen, and the scissor discards those pixels
      ;; only after paying for the draw call and the text measurement.
      (when (and (< (:y card) (+ grid-top grid-h))
                 (> (+ (:y card) (:height card)) grid-top))
        (rl/draw-rectangle (:x card) (:y card) (:width card) (:height card) (color (:card p)))
        (centered-text! (title-of scene-id) card body-size WHITE)))
    (rl/end-scissor-mode)
    (when-let [[ty th] (scroll/thumb scroll content-height viewport-height)]
      (let [w (rl/get-screen-width)
            bar-w (max 6 (quot w 160))
            x (- w bar-w (quot margin 3))]
        (rl/draw-rectangle x (+ grid-top (int (* ty (/ (double grid-h) viewport-height))))
                           bar-w (int (* th (/ (double grid-h) viewport-height)))
                           (rl/rgba 140 140 150 160)))))
  (rl/draw-text heading margin (+ top margin) title-size (color (:accent p)))
  (rl/draw-text subheading margin (+ top margin title-size (quot line-gap 2)) body-size rl/DARKGRAY))

(defn- below-the-safe-area
  "gallery-layout, shifted clear of the status bar.

  The pure layout function knows nothing about safe areas and should not: it is
  handed a screen and divides it. So it is given a screen shortened by the inset
  and every rectangle it returns is then moved down by the same amount. The
  result is identical to a layout that understood insets, and the pure half
  stays portable to a platform that has none.

  Both the cards and the Back target move. Missing the Back target is the
  interesting bug, because it still draws in the right place and only its
  hit-test is wrong, so it looks like an unresponsive button.

  Scrolling rides the same trick from the other direction. gallery-layout sizes
  cards to FIT, dividing the height it is given by the row count, so a fixed
  screen makes them shrink without limit: at twenty-seven scenes each card is
  about 140 pixels tall. Handing it a screen TALLER than the real one gets a
  comfortable grid for a screen that does not exist, and `scroll` then moves a
  window over it. The Back target does not scroll, because it is chrome rather
  than content."
  ([m sizes top ids] (below-the-safe-area m sizes top ids 0))
  ([m sizes top ids scroll]
   (let [[w h] (:screen m)
         viewport (- h top)
         content (scroll/content-height {:width w :height viewport} sizes (count ids)
                                        (if (>= (* w 3) (* viewport 2)) 3 2))
         shifted (ui/gallery-layout (assoc m :screen [w content]) ids sizes)
         lower (fn [rect] (update rect :y + top))
         ;; long, not whatever arithmetic produced. gallery-layout rounds its
         ;; own rectangles to ints, and this adds an offset afterwards: a
         ;; fractional scroll makes :y a double, draw-rectangle is bound
         ;; [:int :int :int :int :uint], and the process dies at the FFI
         ;; boundary rather than drawing in the wrong place.
         shift (long (- top scroll))
         lower-scrolled (fn [rect] (update rect :y + shift))]
     (assoc (-> shifted
                (update :back lower)
                (update :cards #(mapv lower-scrolled %)))
            :content-height content
            :viewport-height viewport))))

;; --- the scene, for raylib.host: the gallery state is the state
(defn- init [{:keys [scale inset-top]}]
  ;; :category nil is the top level, showing categories. Set, it is that
  ;; category's scene list. The pure gstate is unaware of either.
  {:k scale :insets {:top inset-top} :touches 0 :category nil
   :gstate gallery/initial-gallery-state})

(defn- resolve-insets
  "All four safe-area insets in pixels, asked once and then kept.

  UIKit cannot answer before the window has been laid out, so the first frame
  gets zeros and the answer arrives on some later one. Caching is not an
  optimisation: asking every frame would be four Objective-C message sends for
  numbers that never change while the app is upright.

  All four, not just the top. The status bar is the obvious one, but the home
  indicator sits over the bottom 102 pixels of this screen and a scene that
  fills the display draws underneath it."
  [k insets]
  (if (pos? (:top insets 0))
    insets
    (let [px (rl/safe-area-pixels k)]
      (when (pos? (:top px 0))
        (println (format "gallery: safe area %s px" (pr-str px))))
      px)))

(defn- safe-region
  "Where a scene may draw, in screen pixels."
  [[w h] {:keys [top bottom left right] :or {top 0 bottom 0 left 0 right 0}}]
  {:x left :y top
   :width  (- w left right)
   :height (- h top bottom)})

(defn- into-safe-region
  "The pointer, expressed in the coordinates the scene believes it is drawing in.

  A scene is handed the safe region as its whole screen and the host translates
  its drawing into place, so a scene's y of 0 is the screen's y of `top`. The
  pointer has to make the same journey or the two disagree: an untranslated
  touch at screen y 1500 reaches a scene that thinks its screen starts at 0, and
  whatever it draws there appears 186 pixels below the finger that asked for it.

  This is `below-the-safe-area` run backwards. That function moves rectangles
  the host computed down into the safe region; this moves a point the hardware
  reported up out of it.

  Verified on device before the fix: a tap at screen [600 1500] arrived at the
  scene as [600 1500] with a top inset of 186."
  [input {:keys [x y]}]
  (cond-> input
    (get-in input [:pointer :position])
    (update-in [:pointer :position]
               (fn [[px py]] [(- px x) (- py y)]))
    ;; :touch-points makes the same journey. Missing this is the bug the
    ;; pointer already had, one scene later.
    (seq (:touch-points input))
    (update :touch-points
            (fn [pts] (mapv (fn [[px py]] [(- px x) (- py y)]) pts)))))

(defn- visible-ids
  "The ids this level lays out: the categories at the top, or one category's
  scenes inside it. In :scene mode nothing but the Back target is read from the
  layout, so either list would do."
  [category]
  (if category
    (:scenes (category-by-id category))
    category-ids))

(defn- navigate
  "Where a press takes the level, as [category opening?].

  Level changes are resolved before the press reaches a scene, so one tap
  cannot both change level and land on whatever the change puts under the
  finger. `opening?` says a scene is being opened, which the caller turns into
  open-scene rather than run-frame.

  There are TWO Back presses here and they do different things. In :scene mode
  Back closes the scene, and the category must survive so the reader lands back
  on the list they opened it from. In :gallery mode Back leaves the category,
  and only then is it cleared. Collapsing the two sends every scene's Back
  straight to the top level, which looks like the list was never there."
  [category mode hit list-back?]
  (cond
    ;; Back out of a category's scene list, to the categories
    list-back?                              [nil false]
    ;; Back out of a running scene: run-frame closes it, the list stays put
    (not= :gallery mode)                    [category false]
    ;; a category card at the top level
    (nil? category)                         [(when (keyword? hit) hit) false]
    ;; a scene card inside a category
    (and (keyword? hit)
         (some #{hit} (:scenes (category-by-id category))))
    [category true]
    :else                                   [category false]))

(defn- drain-events!
  "Print and clear the scene's events. They are the pure half's only way of
  saying anything, so dropping them silently would hide a scene's own
  diagnostics."
  [gstate]
  (doseq [e (:scene-events gstate)] (println "gallery:" (pr-str e)))
  (assoc gstate :scene-events []))

(defn- ignore-close
  "A scene asking to close means nothing here. On a desktop the loop would end;
  an iOS app does not exit, and one that did would be rejected."
  [gstate]
  (if (:close-requested? gstate)
    (do (println "gallery: close requested, which an iOS app cannot do. Ignored.")
        (assoc gstate :close-requested? false))
    gstate))

(defn- render!
  "Three things can be on screen: a running scene, one category's scenes, or
  the categories. The first two carry a Back target and the last does not,
  because there is nowhere above it."
  [{:keys [mode active-scene-id scene-state]} category layout k m top safe scroll]
  (let [p (ui/live-presentation)
        accent (color (:accent p))]
    (cond
      (= :scene mode)
      (do
        ;; The inset bands, cleared before the scissor goes up.
        ;;
        ;; A scene's own ClearBackground is subject to the scissor, because it
        ;; becomes a glClear and glClear respects it. So the bands keep whatever
        ;; was drawn there last, which for a scene opened from the gallery is the
        ;; card grid. In portrait those bands are thin strips behind the status
        ;; bar and nobody noticed for weeks. Rotating the phone to landscape puts
        ;; 186 pixels down each side and the stale cards are unmissable.
        (rl/clear-background (color (:background p)))
        ;; The scene drew its geometry for the safe region starting at 0,0, so
        ;; it is translated into place here and clipped to it. Scissor as well
        ;; as translate, because a scene that overshoots its own bounds would
        ;; otherwise paint over the status bar it was moved clear of.
        (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))
        (rl/rl-push-matrix)
        (rl/rl-translatef (float (:x safe)) (float (:y safe)) 0.0)
        ;; :safe travels with the scene so a scene that wants to clip for
        ;; itself can intersect with the region rather than replace it. rlgl's
        ;; scissor does not nest: BeginScissorMode inside another one simply
        ;; takes over, so a scene clipping to its own box would be free to paint
        ;; over the status bar the host just moved it clear of.
        (draw-scene! active-scene-id scene-state {:k k :m m :safe safe})
        (rl/rl-pop-matrix)
        (rl/end-scissor-mode)
        (draw-back! layout accent))

      category
      (do (draw-gallery! layout p top scroll (title-of category) "Choose a scene")
          (draw-back! layout accent))

      :else
      (draw-gallery! layout p top scroll (:title p) "Choose a category"))))

(defn- frame
  "One frame: sample, decide where the press goes, advance the pure gallery,
  draw. The state carried between frames is the touch count, the cached inset,
  which category is open, and the pure state itself."
  [{:keys [k insets touches gstate category] :as s}]
  (let [insets (resolve-insets k insets)
        top    (:top insets 0)
        [raw tap] (raw-sample touches)
        input  (assoc (diag/normalize-input raw)
                      :touch-points (touch-points tap)
                      ;; The wall clock, for scenes that sweep between its
                      ;; ticks. It has to be the same clock the seconds come
                      ;; from: a sub-second fraction accumulated from frame
                      ;; deltas drifts out of phase with it and the hand jumps
                      ;; backward mid-second. Both go here rather than into
                      ;; diag/normalize-input, which is verified byte-identical.
                      :local-time (rl/local-time))
        m      (:metrics input)
        safe   (safe-region (:screen m) insets)
        ;; A scene is told it has the safe region and nothing else, so its own
        ;; geometry is computed for the space it will actually get. The host
        ;; then translates it into place at draw time, which is why no scene
        ;; has to know a safe area exists.
        scene-m (assoc m :screen [(:width safe) (:height safe)])
        layout (below-the-safe-area m (diag/layout m) top (visible-ids category)
                                    (:scroll s 0))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        ;; A list that scrolls cannot open a card on press: at press time there
        ;; is no way to know whether a drag is starting. So a press only begins
        ;; a gesture, movement scrolls, and the release opens a card if and only
        ;; if the finger stayed inside the slop. Back is chrome and does not
        ;; scroll, but it goes through the same rule so a drag that happens to
        ;; start on it does not navigate.
        ;; Travel accumulates on :down only, never on :release. A release
        ;; carries no finger, and raylib still answers GetTouchX and GetTouchY
        ;; with whatever it last had, which on device is not the touch that just
        ;; ended. Feeding that into the drag inflated travel to 1941 pixels on a
        ;; motionless tap and every tap was read as a scroll.
        drag (case phase
               :press (scroll/begin-drag (:scroll s 0) point)
               :down (if (and (:drag s) point)
                       (scroll/drag-to (:drag s) point)
                       (:drag s))
               :release (:drag s)
               nil)
        scroll' (if (and drag point (= :down phase))
                  (scroll/scroll-for drag point
                                     (:content-height layout) (:viewport-height layout))
                  (scroll/clamp (:scroll s 0)
                                (:content-height layout) (:viewport-height layout)))
        tapped? (and (= :release phase) (scroll/tap? drag (min (first (:screen m))
                                                               (second (:screen m)))))
        tap-at (when tapped? (scroll/tap-point drag))
        hit (when tapped? (ui/hit-test layout tap-at (:mode gstate)))
        ;; hit-test only looks for Back in :scene mode, so the scene list's own
        ;; Back is recognised here. Kept separate from the scene's Back on
        ;; purpose: see navigate.
        list-back? (and tapped? (= :gallery (:mode gstate)) category
                        (within? (:back layout) tap-at))
        [category' opening?] (navigate category (:mode gstate) hit list-back?)
        input  (assoc input :delta-seconds (rl/get-frame-time) :back? (= hit :back))
        scene-input (-> input (assoc :metrics scene-m) (into-safe-region safe))
        gstate (-> (if opening?
                     (gallery/open-scene registry gstate hit scene-input)
                     (gallery/run-frame registry gstate scene-input))
                   drain-events!
                   ignore-close)]
    (render! gstate category' layout k scene-m top safe scroll')
    (assoc s :insets insets
             :category category'
             ;; The offset belongs to the level being shown, so moving between
             ;; levels starts at the top rather than halfway down a list of a
             ;; different length. Without this, opening a scene from the bottom
             ;; of Toys and coming back lands on a blank stretch below the last
             ;; card of a shorter category.
             :scroll (if (= category category') scroll' 0)
             :drag (when-not (= :release phase) drag)
             :touches (get-in input [:touches :count])
             :gstate gstate)))

(defn -main [& _]
  (rl/run! {:title "Gallery" :init init :frame frame}))

(defmethod draw-scene! :lorenz [_ {:keys [points t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 12 14 22 255))
  ;; Everything the projection needs is pulled out to primitive locals and the
  ;; previous screen point is carried in the loop, so a frame allocates nothing
  ;; per segment. The first draft called lor/project and lor/trail-colour per
  ;; point, each returning a fresh vector, and ran at 18 fps. Same lesson as
  ;; flowfield and the draw loop before it: the allocation is the cost.
  (let [cam (lor/camera m t)
        cs (double (:cos cam)) sn (double (:sin cam))
        cx (double (:cx cam)) cy (double (:cy cam))
        f (double (:f cam)) dist (double (:distance cam))
        sc (double (:scale cam)) lift (double (:lift cam))
        n (count points)
        denom (double (max 1 n))]
    (loop [i 0 px 0.0 py 0.0 have-prev? false]
      (when (< i n)
        (let [p (nth points i)
              ax (* sc (double (nth p 0)))
              ay (* sc (- (double (nth p 2)) lift))
              az (* sc (double (nth p 1)))
              rx (- (* ax cs) (* az sn))
              rz (+ (* ax sn) (* az cs))
              d (- dist rz)]
          (if (> d 0.5)
            (let [k (/ f d)
                  sx (+ cx (* rx k))
                  sy (- cy (* ay k))]
              (when have-prev?
                (let [age (/ (double i) denom)]
                  (rl/draw-line (int px) (int py) (int sx) (int sy)
                                (rl/rgba (int (+ 90.0 (* 165.0 age)))
                                         (int (- 200.0 (* 130.0 age)))
                                         (int (- 255.0 (* 105.0 age)))
                                         255))))
              (recur (inc i) sx sy true))
            ;; behind the camera: drop the segment rather than drawing across
            (recur (inc i) 0.0 0.0 false)))))))

(defmethod draw-scene! :tesseract [_ {:keys [a]} {:keys [m]}]
  (rl/clear-background (rl/rgba 8 8 16 255))
  (let [pts (tess/points m a)
        es tess/edges
        n (count es)]
    (loop [k 0]
      (when (< k n)
        (let [e (nth es k)
              i (nth e 0) j (nth e 1)
              p (nth pts i) q (nth pts j)
              [r g b] (tess/edge-colour i j)]
          (rl/draw-line (int (nth p 0)) (int (nth p 1))
                        (int (nth q 0)) (int (nth q 1))
                        (rl/rgba r g b 255)))
        (recur (inc k))))))

(defmethod draw-scene! :life [_ {:keys [live]} {:keys [m]}]
  (rl/clear-background (rl/rgba 8 10 16 255))
  (let [{:keys [cell]} (life/dimensions m)
        size (max 1 (dec cell))
        lime (rl/rgba 0 228 48 255)]
    ;; One rectangle per live cell, straight off the set. There is no ordering
    ;; to respect, so this does not need an indexed loop the way a trail does.
    (doseq [c live]
      (rl/draw-rectangle (* (nth c 0) cell) (* (nth c 1) cell) size size lime))))

(defmethod draw-scene! :automata [_ {:keys [window] :as state} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [px row-h]} (auto/dimensions m)
        ink (rl/rgba 20 30 60 255)
        rows (count window)]
    ;; Runs, not cells: each [start length] is one rectangle covering however
    ;; many adjacent live cells it found. See the namespace docstring.
    (loop [r 0]
      (when (< r rows)
        (let [y (* r row-h)
              rr (nth window r)
              n (count rr)]
          (loop [i 0]
            (when (< i n)
              (let [run (nth rr i)]
                (rl/draw-rectangle (* (nth run 0) px) y (* (nth run 1) px) row-h ink))
              (recur (inc i)))))
        (recur (inc r))))
    ;; Bottom left, because the top left is where the host puts the Back button
    ;; and the first version of this label sat underneath it.
    (let [[sw sh] (:screen m)]
      (rl/draw-text (str "rule " (auto/rule-of state))
                    (int (* 0.04 sw)) (int (- sh (* 0.055 sh)))
                    ;; dark ink: the background here is RAYWHITE, and the
                    ;; first version of this label was near-white on it
                    30 (rl/rgba 80 80 80 255)))))

(defmethod draw-scene! :colorwheel [_ {:keys [offset]} {:keys [m]}]
  (rl/clear-background (rl/rgba 20 20 28 255))
  ;; The only place this project uses rlgl immediate mode. raylib's shapes API
  ;; cannot draw a triangle with a different colour at each corner, and the
  ;; gradient around the rim is exactly that.
  (let [dims (wheel/dimensions m)
        cx (double (:cx dims))
        cy (double (:cy dims))]
    (rl/rl-begin rl/RL-TRIANGLES)
    (loop [i 0]
      (when (< i wheel/slices)
        (let [sl (wheel/slice dims offset i)
              [r0 g0 b0] (wheel/hsv->rgb (nth sl 2))
              [r1 g1 b1] (wheel/hsv->rgb (nth sl 5))]
          (rl/rl-color-4ub r0 g0 b0 255)
          (rl/rl-vertex-2f (double (nth sl 0)) (double (nth sl 1)))
          (rl/rl-color-4ub 255 255 255 255)
          (rl/rl-vertex-2f cx cy)
          (rl/rl-color-4ub r1 g1 b1 255)
          (rl/rl-vertex-2f (double (nth sl 3)) (double (nth sl 4))))
        (recur (inc i))))
    (rl/rl-end)))

(defn- stroke!
  "A line a few pixels wide, drawn as offset copies of a one-pixel one.

  raylib has DrawLineEx for this and it takes two Vector2 by value, which is a
  different and more expensive FFI path than every other call here. At three
  copies per line and a handful of lines per frame, this is cheaper than the
  binding would be.

  It matters because a one-pixel line is a hairline on a 1206-pixel-wide screen.
  The examples these scenes come from were written for an 800-pixel window,
  where the same line is half again as thick relative to everything else."
  [x1 y1 x2 y2 colour]
  (rl/draw-line x1 y1 x2 y2 colour)
  (rl/draw-line (inc x1) y1 (inc x2) y2 colour)
  (rl/draw-line x1 (inc y1) x2 (inc y2) colour))

(defmethod draw-scene! :unitcircle [_ {:keys [angle trace]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (circle/dimensions m)
        cx (int (:cx d)) cy (int (:cy d)) r (:radius d)
        [px py] (circle/point-at d angle)
        px (int px) py (int py)
        grey (rl/rgba 200 200 200 255)
        blue (rl/rgba 0 121 241 255)
        green (rl/rgba 0 158 47 255)
        maroon (rl/rgba 190 33 55 255)]
    ;; the circle and its axes
    (rl/draw-circle-lines cx cy r grey)
    (rl/draw-line (int (- cx r)) cy (int (+ cx r)) cy grey)
    (rl/draw-line cx (int (- cy r)) cx (int (+ cy r)) grey)
    ;; the two projections of the radius, and the radius itself
    (stroke! px py px cy blue)
    (stroke! px cy cx cy green)
    (stroke! cx cy px py maroon)
    (rl/draw-circle px py (* 0.02 (min (:w d) (:h d))) maroon)
    ;; and the same two values traced over time, underneath
    (doseq [[pick colour centre] [[first blue 0.28] [second green 0.72]]]
      (let [pts (circle/wave-points d trace pick centre)
            n (count pts)]
        (loop [i 1]
          (when (< i n)
            (let [a (nth pts (dec i)) b (nth pts i)]
              (stroke! (int (nth a 0)) (int (nth a 1))
                       (int (nth b 0)) (int (nth b 1)) colour))
            (recur (inc i))))))))

(defmethod draw-scene! :clock [_ _ {:keys [m]}]
  (rl/clear-background (rl/rgba 16 20 18 255))
  ;; The time is read here rather than in the scene, so the pure half stays
  ;; testable without a clock and this stays the only namespace that talks to
  ;; anything outside the process.
  (let [[_ _ ss :as now] (rl/local-time)
        d (clock/dimensions m)
        on (rl/rgba 80 230 120 255)
        off (rl/rgba 28 44 34 255)
        pairs (clock/digit-pairs now)]
    (dotimes [row 3]
      (let [y (clock/row-origin d row)
            [tens units] (nth pairs row)]
        (doseq [[digit x] [[tens (:x0 d)] [units (:x1 d)]]]
          (let [lit (get clock/lit-segments digit)]
            (doseq [[seg r] (clock/segment-rects d x y)]
              (rl/draw-rectangle (int (nth r 0)) (int (nth r 1))
                                 (int (nth r 2)) (int (nth r 3))
                                 (if (contains? lit seg) on off)))))))
    ;; the separator between rows, blinking on even seconds
    (let [c (if (even? ss) on off)
          dot (* 0.9 (:thick d))]
      (dotimes [row 2]
        (let [y (+ (clock/row-origin d row) (:row-h d) (* 0.25 (:gap d)))]
          (rl/draw-rectangle (int (- (* 0.5 (:w d)) (* 0.5 dot))) (int y)
                             (int dot) (int dot) c))))))

(defmethod draw-scene! :piechart [_ {:keys [base]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (pie/dimensions m)
        cx (double (:cx d)) cy (double (:cy d))]
    ;; Same triangle fan as the colour wheel, one run per wedge. Flat colour
    ;; this time, so all three vertices carry it.
    (rl/rl-begin rl/RL-TRIANGLES)
    (doseq [wedge (pie/arcs base)]
      (let [[r g b] (:colour wedge)]
        (doseq [t (pie/triangles d wedge)]
          (rl/rl-color-4ub r g b 255)
          (rl/rl-vertex-2f (double (nth t 0)) (double (nth t 1)))
          (rl/rl-color-4ub r g b 255)
          (rl/rl-vertex-2f cx cy)
          (rl/rl-color-4ub r g b 255)
          (rl/rl-vertex-2f (double (nth t 2)) (double (nth t 3))))))
    (rl/rl-end)
    ;; legend, under the chart
    (let [size (int (* 0.30 (:swatch d)))]
      (doseq [[i wedge] (map-indexed vector (pie/arcs 0.0))]
        (let [[r g b] (:colour wedge)
              y (+ (:legend-y d) (* i (:legend-step d)))
              sw (int (:swatch d))]
          (rl/draw-rectangle (int (:legend-x d)) (int y) sw sw (rl/rgba r g b 255))
          (rl/draw-text (str (:label wedge) "  " (pie/percent (:value wedge)) "%")
                        (int (+ (:legend-x d) (* 1.5 sw))) (int (+ y (* 0.25 sw)))
                        (max 20 size) (rl/rgba 80 80 80 255)))))))

(defmethod draw-scene! :logoanim [_ {:keys [stage counter top left bottom right letters alpha]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [x y side scale border]} (logo/dimensions m)
        u (fn [units] (* units scale))
        ink (fn [a] (rl/rgba 0 0 0 (int (* 255 (max 0.0 (min 1.0 a))))))
        black (ink 1.0)]
    (case stage
      0 (when (logo/blink-on? counter)
          (rl/draw-rectangle (int x) (int y) (int border) (int border) black))

      (1 2) (do
              (rl/draw-rectangle (int x) (int y) (int (u top)) (int border) black)
              (rl/draw-rectangle (int x) (int y) (int border) (int (u left)) black)
              (when (= stage 2)
                (rl/draw-rectangle (int (+ x side (- border))) (int y)
                                   (int border) (int (u right)) black)
                (rl/draw-rectangle (int (- (+ x side) (u bottom))) (int (+ y side (- border)))
                                   (int (u bottom)) (int border) black)))

      3 (let [c (ink alpha)]
          (rl/draw-rectangle (int x) (int y) (int side) (int border) c)
          (rl/draw-rectangle (int x) (int y) (int border) (int side) c)
          (rl/draw-rectangle (int (+ x side (- border))) (int y) (int border) (int side) c)
          (rl/draw-rectangle (int x) (int (+ y side (- border))) (int side) (int border) c)
          (let [txt (logo/visible-word letters)
                size (int (* 0.20 side))]
            (when (seq txt)
              (rl/draw-text txt
                            (int (- (+ x side) (rl/measure-text txt size) (u 20)))
                            (int (- (+ y side) size (u 26)))
                            size c))))
      nil)))

(defmethod draw-scene! :easings [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (ease/dimensions m)
        p (ease/progress t)
        grey (rl/rgba 190 190 190 255)
        ink (rl/rgba 40 60 110 255)
        mark (rl/rgba 190 33 55 255)
        label (max 16 (int (* 0.13 (:cell-h d))))]
    (doseq [[i [nm f]] (map-indexed vector ez/curves)]
      (let [[ox oy] (ease/cell-origin d i)
            pts (ease/plot d f ox oy)
            n (count pts)]
        ;; the cell's floor, so an overshoot is visibly below or above a line
        (rl/draw-line (int ox) (int (+ oy (:cell-h d) (- (:inset-y d))))
                      (int (+ ox (:cell-w d))) (int (+ oy (:cell-h d) (- (:inset-y d))))
                      grey)
        (rl/draw-text nm (int ox) (int oy) label (rl/rgba 90 90 90 255))
        (loop [k 1]
          (when (< k n)
            (let [a (nth pts (dec k)) b (nth pts k)]
              (rl/draw-line (int (nth a 0)) (int (nth a 1))
                            (int (nth b 0)) (int (nth b 1)) ink))
            (recur (inc k))))
        (let [[dx dy] (ease/dot d f ox oy p)]
          (rl/draw-circle (int dx) (int dy) (* 0.022 (:cell-w d)) mark))))))

(defmethod draw-scene! :angles [_ {:keys [angle]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (ang/dimensions m)
        cx (int (:cx d)) cy (int (:cy d))
        grey (rl/rgba 200 200 200 255)
        maroon (rl/rgba 190 33 55 255)]
    (rl/draw-circle-lines cx cy (:radius d) grey)
    (doseq [a (ang/fixed-angles)]
      (let [[x y] (ang/spoke-end d a)]
        (stroke! cx cy (int x) (int y) grey)))
    (let [[x y] (ang/spoke-end d angle)]
      (stroke! cx cy (int x) (int y) maroon)
      (rl/draw-circle (int x) (int y) (* 0.03 (:radius d)) maroon))))

(defmethod draw-scene! :writing [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (writ/dimensions m)
        lines (writ/wrap (writ/visible t) (:columns d))
        ink (rl/rgba 0 82 172 255)]
    (doseq [[i line] (map-indexed vector lines)]
      (rl/draw-text line (int (:margin d)) (int (+ (:top d) (* i (:line-height d))))
                    (:size d) ink))
    ;; a cursor while typing, gone during the pause, which is how you can tell
    ;; the difference between finished and stalled
    (when-not (writ/complete? t)
      (let [row (max 0 (dec (count lines)))
            last-line (if (seq lines) (last lines) "")]
        (rl/draw-rectangle
         (int (+ (:margin d) (rl/measure-text last-line (:size d)) (* 0.2 (:size d))))
         (int (+ (:top d) (* row (:line-height d))))
         (int (* 0.09 (:size d))) (:size d) ink)))))

(defmethod draw-scene! :balls [_ {:keys [balls]} _]
  ;; No env needed: a ball already carries its position in the safe region's
  ;; own coordinates, which is the space the host has translated us into.
  (rl/clear-background (rl/rgba 245 245 245 255))
  (doseq [b balls]
    (let [[r g bl] (:colour b)]
      (rl/draw-circle (int (:x b)) (int (:y b)) (:r b) (rl/rgba r g bl 255))
      (rl/draw-circle-lines (int (:x b)) (int (:y b)) (:r b) (rl/rgba 80 80 80 90)))))

(defmethod draw-scene! :sequence [_ {:keys [bars]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (seqn/dimensions m)
        bw (:bar-w d)]
    (doseq [[i bar] (map-indexed vector bars)]
      (let [[r g b] (:colour bar)
            height (* (:fraction bar) (:max-height d))]
        (rl/draw-rectangle (int (* i bw))
                           (int (- (:baseline d) height))
                           (int (- bw 2))
                           (int height)
                           (rl/rgba r g b 255))))))

(defmethod draw-scene! :bullets [_ {:keys [bullets]} {:keys [m]}]
  (rl/clear-background (rl/rgba 15 15 30 255))
  (let [d (bull/dimensions m)
        gold (rl/rgba 255 203 0 255)
        r (:radius d)]
    (doseq [b bullets]
      (rl/draw-circle (int (:x b)) (int (:y b)) r gold))
    (rl/draw-circle (int (:cx d)) (int (:cy d)) (* 2.4 r) (rl/rgba 230 41 55 255))))

(defmethod draw-scene! :collision [_ {:keys [x target touching?]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (coll/dimensions m)
        a (coll/slider-box d x)
        b (coll/finger-box d target)
        box! (fn [[bx by bw bh] c]
               (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) c))]
    (box! a (rl/rgba 102 191 255 255))
    (box! b (rl/rgba 255 203 0 255))
    (when-let [ov (coll/intersection a b)]
      (box! ov (rl/rgba 230 41 55 255)))
    (rl/draw-text (if touching? "dragging" "drag a finger over the blue box")
                  (int (* 0.06 (:w d))) (int (* 0.88 (:h d)))
                  (max 20 (int (* 0.035 (:w d)))) (rl/rgba 80 80 80 255))))

(defmethod draw-scene! :dashed [_ {:keys [target]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (dash/dimensions m)
        maroon (rl/rgba 190 33 55 255)]
    (doseq [seg (dash/dashes d target)]
      (stroke! (int (nth seg 0)) (int (nth seg 1))
               (int (nth seg 2)) (int (nth seg 3)) maroon))
    (rl/draw-circle (int (:cx d)) (int (:cy d)) (:hub d) (rl/rgba 80 80 80 255))
    (rl/draw-circle (int (first target)) (int (second target)) (* 0.6 (:hub d)) maroon)))

(defmethod draw-scene! :multitouch [_ {:keys [trails live peak colours]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [label-size count-size touch-radius dot-radius centre-radius label-lift]}
        (multi/dimensions m)
        n (count live)]
    ;; Trails first so the live circles sit on top of their own history.
    (doseq [[id trail] trails]
      (let [[r g b] (multi/colour-for (get colours id))
            len (count trail)]
        (dotimes [i len]
          (let [[x y] (nth trail i)
                a (int (* 150 (/ (double (inc i)) len)))]
            (rl/draw-circle (int x) (int y) (float dot-radius) (rl/rgba r g b a))))))
    (doseq [[id [x y]] live]
      (let [[r g b] (multi/colour-for (get colours id))
            ix (int x) iy (int y)]
        (rl/draw-circle ix iy (float touch-radius) (rl/rgba r g b 70))
        (rl/draw-circle-lines ix iy (float touch-radius) (rl/rgba r g b 255))
        (rl/draw-circle ix iy (float centre-radius) (rl/rgba r g b 255))
        (rl/draw-text (str "id " id) (- ix (int (* 2.2 label-size))) (- iy (int label-lift))
                      label-size (rl/rgba r g b 255))))
    ;; Bottom left, for the third time in this file: the host owns the top left
    ;; for Back, and the first version of every one of these labels sat under it.
    (let [[sw sh] (:screen m)
          x (int (* 0.04 sw))
          y0 (int (- sh (* 0.055 sh) (* 2 (+ label-size 10)) count-size))]
      (rl/draw-text (str n " touch " (if (= 1 n) "point" "points"))
                    x y0 count-size rl/DARKGRAY)
      (rl/draw-text (if (zero? n)
                      "put fingers on the glass"
                      (str "most at once so far: " peak))
                    x (+ y0 count-size 10) label-size (rl/rgba 130 130 130 255))
      ;; A phone reports every point. The desktop example this came from could
      ;; only ever draw one, and said so.
      (rl/draw-text (str "all " n " positions read, not just point 0")
                    x (+ y0 count-size 10 label-size 10) label-size
                    (rl/rgba 130 130 130 255)))))

(defmethod draw-scene! :analog [_ {:keys [frac]} {:keys [m]}]
  (rl/clear-background (rl/rgba 24 26 33 255))
  ;; Read here, not in the scene, for the reason the digital clock gives above.
  (let [now (rl/local-time)
        [h mi s] now
        {:keys [cx cy r label-size] :as d} (analog/dimensions m)
        {:keys [hour minute second]} (analog/hand-angles now frac)
        pale (rl/rgba 235 235 245 255)
        red (rl/rgba 235 90 90 255)
        hand (fn [ang len thick colour]
               (let [[x y] (analog/polar cx cy len ang)]
                 (rl/draw-line-ex cx cy x y thick colour)))]
    (rl/draw-ring cx cy (- r (* r 0.05)) r 0 360 120 (rl/rgba 210 212 222 255))
    (doseq [[x0 y0 x1 y1 long?] (analog/ticks d)]
      (rl/draw-line-ex x0 y0 x1 y1 (if long? (* r 0.017) (* r 0.006))
                       (rl/rgba 150 155 165 255)))
    (hand hour   (* r 0.50) (* r 0.045) pale)
    (hand minute (* r 0.72) (* r 0.028) pale)
    (hand second (* r 0.84) (* r 0.012) red)
    (rl/draw-circle (int cx) (int cy) (float (* r 0.045)) red)
    ;; Bottom left, same reason the automaton's rule label is there: the host
    ;; owns the top left for Back, and the first version of this sat under it.
    (let [[sw sh] (:screen m)]
      (rl/draw-text (str (when (< h 10) "0") h ":"
                         (when (< mi 10) "0") mi ":"
                         (when (< s 10) "0") s)
                    (int (* 0.04 sw)) (int (- sh (* 0.055 sh)))
                    label-size rl/RAYWHITE))))

(defmethod draw-scene! :clockgrid [_ {:keys [current]} {:keys [m]}]
  (rl/clear-background (rl/rgba 8 12 28 255))
  ;; Three drafts, and the middle one is the interesting failure.
  ;;
  ;; The bezels were draw-ring at 20 segments: 120 vertices each, 17000 FFI
  ;; calls a frame for 144 of them, 6 fps. draw-circle-lines is ONE call and
  ;; draws the same circle. rlgl is the right tool when raylib has no call for
  ;; the shape, and the wrong one when it does. That alone took it to 50.
  ;;
  ;; Then the 288 hands were collected into a vector and handed to a single
  ;; batched rlgl call, to save the 864 calls that 288 separate begin/colour/end
  ;; triples cost. It measured 47. The batch saved the calls and paid for them
  ;; in 288 vector allocations, which is this project's oldest lesson arriving
  ;; again: the allocation costs more than the call.
  ;;
  ;; So the batch stays and the vector goes. One begin, one colour, one end, and
  ;; the vertices emitted straight from the loop with nothing allocated between.
  (let [{:keys [x0 y0 step radius hand row-step pair-w]} (cgrid/dimensions m)
        ;; Bright enough to actually see. The first value was 42 48 74 on an
        ;; 8 12 28 ground, which at one pixel wide vanished entirely and left
        ;; the digits reading as loose dashes rather than as clock faces, which
        ;; is the whole idea of the scene.
        bezel (rl/rgba 74 88 140 255)
        half (* radius 0.08)]
    (dotimes [d 6]
      (let [pair (quot d 2) side (mod d 2)
            ox (+ x0 (* side pair-w)) oy (+ y0 (* pair row-step))]
        (dotimes [i cgrid/cells]
          (rl/draw-circle-lines (int (+ ox (* (mod i cgrid/cols) step) radius))
                                (int (+ oy (* (quot i cgrid/cols) step) radius))
                                (float radius) bezel))))
    (rl/rl-begin rl/RL-TRIANGLES)
    (rl/rl-color-4ub 255 249 196 255)
    (dotimes [d 6]
      (let [pair (quot d 2) side (mod d 2)
            ox (+ x0 (* side pair-w)) oy (+ y0 (* pair row-step))
            grid (nth current d)]
        (dotimes [i cgrid/cells]
          (let [cx (double (+ ox (* (mod i cgrid/cols) step) radius))
                cy (double (+ oy (* (quot i cgrid/cols) step) radius))
                cell (nth grid i)]
            (dotimes [k 2]
              (let [t (Math/toRadians (double (nth cell k)))
                    dx (Math/cos t) dy (Math/sin t)
                    x2 (+ cx (* hand dx)) y2 (+ cy (* hand dy))
                    px (* half dy) py (* half (- dx))]
                (rl/rl-vertex-2f (float (+ cx px)) (float (+ cy py)))
                (rl/rl-vertex-2f (float (- cx px)) (float (- cy py)))
                (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                (rl/rl-vertex-2f (float (+ cx px)) (float (+ cy py)))
                (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                (rl/rl-vertex-2f (float (+ x2 px)) (float (+ y2 py)))))))))
    (rl/rl-end)))

(defmethod draw-scene! :sector [_ {:keys [start-angle end-angle requested]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [cx cy radius label-size w h]} (sector/dimensions m)
        {:keys [segments floor mode]} (sector/resolve-segments start-angle end-angle requested)
        auto? (= :auto mode)]
    ;; A sector is a ring with no hole, so draw-ring covers it. raylib's own
    ;; DrawCircleSector takes its centre as a by-value Vector2, which is the
    ;; same reason draw-ring exists at all.
    (rl/draw-ring cx cy 0.0 radius start-angle end-angle segments
                  (rl/rgba 190 33 55 90))
    (rl/draw-ring cx cy (* radius 0.97) radius start-angle end-angle segments
                  (rl/rgba 190 33 55 255))
    (let [x (int (* 0.06 w))
          y0 (int (- h (* 0.30 h)))
          line (fn [i s c] (rl/draw-text s x (+ y0 (* i (+ label-size 12))) label-size c))]
      (line 0 (str "arc " (int (- end-angle start-angle)) " degrees") rl/DARKGRAY)
      (line 1 (str "asked for " requested " segments") rl/DARKGRAY)
      (line 2 (str "floor is " floor " (one per 90 degrees)") (rl/rgba 130 130 130 255))
      (line 3 (if auto? (str "AUTO: drawing " segments)
                        (str "drawing " segments " as asked"))
            (if auto? (rl/rgba 190 33 55 255) (rl/rgba 0 130 60 255))))))

(defmethod draw-scene! :palette [_ _ {:keys [m]}]
  (rl/clear-background (rl/rgba 30 32 40 255))
  (let [{:keys [swatch-h label-size] :as d} (pal/dimensions m)]
    (doseq [[i entry] (map-indexed vector pal/colours)]
      (let [[x y w _] (pal/cell d i)
            [nm r g b] entry
            ink (if (pal/light? entry) (rl/rgba 20 20 20 255) rl/RAYWHITE)]
        (rl/draw-rectangle (int x) (int y) (int w) (int swatch-h) (rl/rgba r g b 255))
        ;; The name sits ON the swatch rather than under it, so the contrast
        ;; choice is visible: a label that vanishes is the bug this scene would
        ;; otherwise hide.
        (rl/draw-text nm (int (+ x (* 0.06 w))) (int (+ y (* 0.5 swatch-h) (- (quot label-size 2))))
                      label-size ink)
        (rl/draw-text (str r " " g " " b)
                      (int (+ x (* 0.06 w))) (int (+ y swatch-h (* 0.12 label-size)))
                      (max 14 (int (* 0.8 label-size))) (rl/rgba 150 150 160 255))))))

(defmethod draw-scene! :gradient [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 18 18 24 255))
  (let [{:keys [label-size] :as d} (grad/dimensions m)
        labels ["vertical, top pair equal"
                "horizontal, left pair equal"
                "four corners, all different"
                "and turning, so it is per pixel"]]
    (dotimes [i grad/bands]
      (let [[x y w h] (grad/band-rect d i)
            [tl tr br bl] (grad/corners i t)
            col (fn [hue] (let [[r g b] (grad/hsv->rgb hue)] (rl/rgba r g b 255)))]
        (rl/draw-gradient-quad x y w h (col tl) (col tr) (col br) (col bl))
        (rl/draw-text (nth labels i)
                      (int (+ x (* 0.03 w))) (int (+ y (* 0.04 h)))
                      label-size (rl/rgba 255 255 255 220))))))

(defmethod draw-scene! :ring [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 22 24 30 255))
  ;; The outline is one rlgl batch with nothing allocated in it. The first
  ;; version built two 97-point vectors with arc-points and partitioned them
  ;; into pairs, then called draw-line-ex per segment: about 200 vectors and
  ;; 1700 FFI calls a frame, for 40 fps. Same trade the clock grid made, and
  ;; the same answer. arc-points stays, because the tests use it to assert the
  ;; stroke actually follows the arc.
  (let [{:keys [cx cy outer thick label-size w h] :as d} (ring/dimensions m)
        {:keys [inner start end]} (ring/geometry d t)
        n ring/outline-segments
        span (- end start)
        half (* 0.5 (double thick))
        seg (fn [x1 y1 x2 y2]
              (let [dx (- x2 x1) dy (- y2 y1)
                    len (Math/sqrt (+ (* dx dx) (* dy dy)))]
                (when (pos? len)
                  ;; This vertex order is draw-line-ex's, copied rather than
                  ;; rederived. An earlier version here swapped two of them,
                  ;; which flips the cross product positive and culls every
                  ;; triangle: the outline simply did not appear, for the third
                  ;; time tonight. Winding is not something to reason out fresh
                  ;; each time it is written.
                  (let [px (* half (/ dy len)) py (* half (/ (- dx) len))]
                    (rl/rl-vertex-2f (float (+ x1 px)) (float (+ y1 py)))
                    (rl/rl-vertex-2f (float (- x1 px)) (float (- y1 py)))
                    (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                    (rl/rl-vertex-2f (float (+ x1 px)) (float (+ y1 py)))
                    (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                    (rl/rl-vertex-2f (float (+ x2 px)) (float (+ y2 py)))))))]
    (rl/draw-ring cx cy inner outer start end ring/fill-segments (rl/rgba 90 170 240 255))
    (rl/rl-begin rl/RL-TRIANGLES)
    ;; Light on a dark ground. The first value was 20 40 72 against 22 24 30,
    ;; which is the clock grid's invisible-bezel mistake made twice in one night.
    (rl/rl-color-4ub 226 240 255 255)
    (doseq [r [outer inner]]
      (dotimes [i n]
        (let [a0 (Math/toRadians (+ start (* span (/ (double i) n))))
              a1 (Math/toRadians (+ start (* span (/ (double (inc i)) n))))]
          (seg (+ cx (* r (Math/sin a0))) (- cy (* r (Math/cos a0)))
               (+ cx (* r (Math/sin a1))) (- cy (* r (Math/cos a1)))))))
    ;; the radial end caps, which are what make it read as a closed shape
    (doseq [deg [start end]]
      (let [a (Math/toRadians (double deg))
            s (Math/sin a) c (Math/cos a)]
        (seg (+ cx (* inner s)) (- cy (* inner c))
             (+ cx (* outer s)) (- cy (* outer c)))))
    (rl/rl-end)
    (rl/draw-text "draw-ring, an rlgl annulus"
                  (int (* 0.06 w)) (int (- h (* 0.14 h)))
                  label-size rl/RAYWHITE)))

(defmethod draw-scene! :splines [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  ;; Evaluated straight into the vertex stream. The first version called
  ;; spl/curve to build a vector of 164 points per basis and then partitioned it
  ;; into pairs, which is about a thousand allocations a frame across the three,
  ;; and ran at 20 fps. spl/curve stays because the tests inspect its output;
  ;; the draw path carries the previous point in locals instead.
  (let [{:keys [dot thick label-size w h] :as d} (spl/dimensions m)
        pts (spl/points d t)
        n (count pts)
        at (fn [i] (nth pts (max 0 (min (dec n) i))))
        half (* 0.5 thick)
        rgb [[230 41 55] [0 121 241] [0 158 47]]]
    (doseq [[ci [_ f]] (map-indexed vector spl/kinds)]
      (let [[cr cg cb] (nth rgb ci)]
        (rl/rl-begin rl/RL-TRIANGLES)
        (rl/rl-color-4ub cr cg cb 255)
        (dotimes [i (dec n)]
          (let [[ax ay] (at (dec i)) [bx by] (at i)
                [cx cy] (at (inc i)) [dx dy] (at (+ i 2))]
            (loop [s 1
                   px (double (f ax bx cx dx 0.0))
                   py (double (f ay by cy dy 0.0))]
              (when (<= s spl/samples)
                (let [tt (/ (double s) spl/samples)
                      qx (double (f ax bx cx dx tt))
                      qy (double (f ay by cy dy tt))
                      ex (- qx px) ey (- qy py)
                      len (Math/sqrt (+ (* ex ex) (* ey ey)))]
                  (when (pos? len)
                    ;; draw-line-ex's vertex order, copied not rederived
                    (let [ox (* half (/ ey len)) oy (* half (/ (- ex) len))]
                      (rl/rl-vertex-2f (float (+ px ox)) (float (+ py oy)))
                      (rl/rl-vertex-2f (float (- px ox)) (float (- py oy)))
                      (rl/rl-vertex-2f (float (- qx ox)) (float (- qy oy)))
                      (rl/rl-vertex-2f (float (+ px ox)) (float (+ py oy)))
                      (rl/rl-vertex-2f (float (- qx ox)) (float (- qy oy)))
                      (rl/rl-vertex-2f (float (+ qx ox)) (float (+ qy oy)))))
                  (recur (inc s) qx qy))))))
        (rl/rl-end)))
    ;; The control points last, on top, so it is obvious which curves touch them.
    (doseq [[x y] pts]
      (rl/draw-circle (int x) (int y) (float dot) (rl/rgba 40 40 40 255))
      (rl/draw-circle-lines (int x) (int y) (float (* 1.9 dot)) (rl/rgba 40 40 40 255)))
    (doseq [[i [nm _]] (map-indexed vector spl/kinds)]
      (let [[cr cg cb] (nth rgb i)]
        (rl/draw-text nm (int (* 0.08 w))
                      (int (- h (* 0.22 h) (* (- 2 i) (+ label-size 14))))
                      label-size (rl/rgba cr cg cb 255))))))

(defmethod draw-scene! :rounded [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [label-size w h] :as d} (rnd/dimensions m)
        r (rnd/radius-at d t)
        {:keys [rects corners]} (rnd/parts d r)
        fill (rl/rgba 0 121 241 255)]
    ;; Rounded outward, not truncated, and grown by a pixel. draw-rectangle
    ;; takes ints while draw-ring takes doubles, so truncating the rects left
    ;; sub-pixel gaps against the corner disks and along the arms' shared edge:
    ;; a faint cross of background showing through the middle of a solid shape.
    ;; Overlapping by a pixel costs one row of overdraw and removes it.
    (doseq [[x y rw rh] rects]
      (rl/draw-rectangle (int (Math/floor x)) (int (Math/floor y))
                         (int (Math/ceil (inc rw))) (int (Math/ceil (inc rh))) fill))
    (doseq [[cx cy start end] corners]
      ;; a quarter disk is draw-ring with no hole. A degree either side of the
      ;; quarter, for the same reason: the arc's flat ends have to reach under
      ;; the rects rather than stop exactly at them.
      (rl/draw-ring cx cy 0.0 (inc r) (- start 1.0) (+ end 1.0) 24 fill))
    (rl/draw-text (str "corner radius " (int r) " of " (int (:max-radius d)))
                  (int (* 0.08 w)) (int (- h (* 0.18 h)))
                  label-size (rl/rgba 60 60 60 255))))

(defmethod draw-scene! :vecangle [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 26 28 36 255))
  (let [{:keys [cx cy arc thick label-size w h] :as d} (vang/dimensions m)
        {:keys [a b]} (vang/vectors d t)
        ba (vang/bearing a) bb (vang/bearing b)
        turn (vang/signed-between ba bb)
        ;; draw-ring wants start below end, so a negative turn sweeps the other
        ;; way round rather than drawing nothing
        [from to] (if (neg? turn) [(+ ba turn) ba] [ba (+ ba turn)])
        arm (fn [[vx vy] colour]
              (rl/draw-line-ex cx cy (+ cx vx) (+ cy vy) thick colour))]
    ;; Alpha 150, not 70. At 70 over this background the wedge was almost
    ;; invisible for the small angles the readout spends most of its time on,
    ;; which are exactly the ones worth being able to see.
    (rl/draw-ring cx cy 0.0 arc from to 48 (rl/rgba 253 249 0 150))
    (arm a (rl/rgba 102 191 255 255))
    (arm b (rl/rgba 255 109 194 255))
    (rl/draw-circle (int cx) (int cy) (float (* 0.010 w)) rl/RAYWHITE)
    (rl/draw-text (str (int turn) " degrees")
                  (int (* 0.08 w)) (int (- h (* 0.22 h)))
                  label-size rl/RAYWHITE)
    (rl/draw-text (if (neg? turn) "anticlockwise" "clockwise")
                  (int (* 0.08 w)) (int (- h (* 0.22 h) (- (+ label-size 14))))
                  label-size (rl/rgba 150 150 160 255))))

(defmethod draw-scene! :bars [_ _ {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  ;; bars/outline still returns a vector, because the tests walk it, and it is
  ;; called once per bar rather than once per vertex. bars/shade is not called
  ;; here at all: it allocated a colour vector per vertex, 400 a frame across the
  ;; five bars, which with the outlines came to 54 fps. The channels are mixed
  ;; inline from primitives instead. Fourth scene to make this trade.
  (let [{:keys [label-size w h] :as d} (bars/dimensions m)
        [lr lg lb] bars/left-colour
        [rr rg rb] bars/right-colour]
    (dotimes [i bars/bar-count]
      (let [[x y bw bh] (bars/bar-rect d i)
            [rl* rrn] (bars/roundness i)
            pts (bars/outline x y bw bh rl* rrn)
            n (count pts)
            cx (+ x (* 0.5 bw))
            cy (+ y (* 0.5 bh))
            emit (fn [px py]
                   (let [t (max 0.0 (min 1.0 (/ (- (double px) x) bw)))]
                     (rl/rl-color-4ub (long (+ lr (* (- rr lr) t)))
                                      (long (+ lg (* (- rg lg) t)))
                                      (long (+ lb (* (- rb lb) t)))
                                      255)
                     (rl/rl-vertex-2f (float px) (float py))))]
        (rl/rl-begin rl/RL-TRIANGLES)
        (dotimes [k n]
          (let [[px py] (nth pts k)
                [qx qy] (nth pts (mod (inc k) n))]
            ;; centre, then NEXT, then current. The natural centre-current-next
            ;; order gives a positive cross for this clockwise outline and every
            ;; triangle is culled. Checked by hand on a square bar before
            ;; building: +80000 one way round, -80000 the other.
            (emit cx cy)
            (emit qx qy)
            (emit px py)))
        (rl/rl-end)
        (rl/draw-text (str "left " (format "%.2f" rl*) "   right " (format "%.2f" rrn))
                      (int (+ x (* 0.02 w))) (int (+ y (* 0.06 bh)))
                      label-size (rl/rgba 255 255 255 220))))
    (rl/draw-text "one loop draws a square and a lozenge"
                  (int (* 0.08 w)) (int (- h (* 0.14 h)))
                  label-size (rl/rgba 60 60 60 255))))

(defmethod draw-scene! :bezier [_ {:keys [end touching?]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [anchor dot thick label-size w h]} (bez/dimensions m)
        ctrl (bez/controls anchor end)
        [[ax ay] [bx by] [cx cy] [dx dy]] ctrl
        half (* 0.5 thick)]
    ;; The control polygon first, thin and grey, so the curve reads against it.
    (doseq [[[x1 y1] [x2 y2]] (partition 2 1 ctrl)]
      (rl/draw-line (int x1) (int y1) (int x2) (int y2) (rl/rgba 190 190 195 255)))
    ;; The curve, sampled inline into one batch. bez/curve exists for the tests.
    (rl/rl-begin rl/RL-TRIANGLES)
    (rl/rl-color-4ub 0 121 241 255)
    (loop [i 1 px (double ax) py (double ay)]
      (when (<= i bez/samples)
        (let [[qx qy] (bez/at ctrl (/ (double i) bez/samples))
              ex (- qx px) ey (- qy py)
              len (Math/sqrt (+ (* ex ex) (* ey ey)))]
          (when (pos? len)
            ;; draw-line-ex's vertex order, copied not rederived
            (let [ox (* half (/ ey len)) oy (* half (/ (- ex) len))]
              (rl/rl-vertex-2f (float (+ px ox)) (float (+ py oy)))
              (rl/rl-vertex-2f (float (- px ox)) (float (- py oy)))
              (rl/rl-vertex-2f (float (- qx ox)) (float (- qy oy)))
              (rl/rl-vertex-2f (float (+ px ox)) (float (+ py oy)))
              (rl/rl-vertex-2f (float (- qx ox)) (float (- qy oy)))
              (rl/rl-vertex-2f (float (+ qx ox)) (float (+ qy oy)))))
          (recur (inc i) (double qx) (double qy)))))
    (rl/rl-end)
    (doseq [[[x y] fill?] [[[ax ay] true] [[bx by] false] [[cx cy] false] [[dx dy] true]]]
      (if fill?
        (rl/draw-circle (int x) (int y) (float dot) (rl/rgba 230 41 55 255))
        (rl/draw-circle-lines (int x) (int y) (float dot) (rl/rgba 130 130 135 255))))
    (rl/draw-text (if touching? "following your finger" "drag anywhere")
                  (int (* 0.08 w)) (int (- h (* 0.14 h)))
                  label-size (rl/rgba 60 60 60 255))))

(defmethod draw-scene! :fan [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 18 20 28 255))
  (let [{:keys [cx cy label-size w h] :as d} (fan/dimensions m)]
    ;; One draw-line-ex per spoke rather than a batch: sixteen calls a frame is
    ;; nothing, and each spoke needs its own colour anyway, which a single
    ;; batched colour would not give.
    (dotimes [i fan/spokes]
      (let [{:keys [from to thick hue]} (fan/spoke d t i)
            [r g b] (fan/hsv->rgb hue)
            [x1 y1] from [x2 y2] to]
        (rl/draw-line-ex x1 y1 x2 y2 thick (rl/rgba r g b 255))))
    (rl/draw-circle (int cx) (int cy) (float (* 0.012 w)) rl/RAYWHITE)
    (rl/draw-text "sixteen widths, thinnest at the top"
                  (int (* 0.08 w)) (int (- h (* 0.14 h)))
                  label-size (rl/rgba 150 150 160 255))))

(defmethod draw-scene! :clipbox [_ {:keys [t]} {:keys [m safe]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [label-size w h] :as d} (clipbox/dimensions m)
        b (clipbox/box d t)
        [bx by bw bh] b]
    ;; The scene's own scissor, intersected with the host's rather than
    ;; replacing it. rlgl keeps one scissor rectangle, so BeginScissorMode here
    ;; takes over from the safe-region one entirely: without the intersection
    ;; this scene could paint its grid over the status bar.
    (when-let [[cx cy cw ch] (clipbox/clip-rect safe b)]
      (rl/begin-scissor-mode (int cx) (int cy) (int cw) (int ch))
      ;; The grid is walked inline rather than through clipbox/cells, which
      ;; returns a vector per cell plus a vector per colour: about 870
      ;; allocations a frame for 435 rectangles, and 49 fps. cells stays for the
      ;; tests. Every cell is still drawn, because the scissor doing the
      ;; clipping is the whole demonstration.
      (let [step (+ clipbox/cell clipbox/gap)]
        (loop [gy 0]
          (when (< gy (long (:h d)))
            (loop [gx 0]
              (when (< gx (long (:w d)))
                (rl/draw-rectangle gx gy clipbox/cell clipbox/cell
                                   (rl/rgba (mod (* gx 3) 256) (mod (* gy 5) 256) 180 255))
                (recur (+ gx step))))
            (recur (+ gy step)))))
      (rl/end-scissor-mode)
      ;; Put the host's own scissor back. Leaving the scene's in place would
      ;; clip everything drawn after this, including the Back button.
      (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe)))
    (stroke! (int bx) (int by) (int (+ bx bw)) (int by) (rl/rgba 230 41 55 255))
    (stroke! (int (+ bx bw)) (int by) (int (+ bx bw)) (int (+ by bh)) (rl/rgba 230 41 55 255))
    (stroke! (int (+ bx bw)) (int (+ by bh)) (int bx) (int (+ by bh)) (rl/rgba 230 41 55 255))
    (stroke! (int bx) (int (+ by bh)) (int bx) (int by) (rl/rgba 230 41 55 255))
    (rl/draw-text "only the box shows the grid"
                  (int (* 0.08 w)) (int (- h (* 0.10 h)))
                  label-size (rl/rgba 60 60 60 255))))

(defmethod draw-scene! :resize [_ {:keys [rw rh holding?]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [x y handle label-size w h]} (rsz/dimensions m)
        ink (if holding? (rl/rgba 230 41 55 255) (rl/rgba 0 82 172 255))]
    (rl/draw-rectangle (int x) (int y) (int rw) (int rh) (rl/rgba 70 130 200 90))
    (stroke! (int x) (int y) (int (+ x rw)) (int y) (rl/rgba 0 121 241 255))
    (stroke! (int (+ x rw)) (int y) (int (+ x rw)) (int (+ y rh)) (rl/rgba 0 121 241 255))
    (stroke! (int (+ x rw)) (int (+ y rh)) (int x) (int (+ y rh)) (rl/rgba 0 121 241 255))
    (stroke! (int x) (int (+ y rh)) (int x) (int y) (rl/rgba 0 121 241 255))
    ;; The corner handle, as a triangle pointing into the rectangle. Through
    ;; draw-triangle, which sorts its own winding: the order written here by
    ;; hand was culled, for the fourth time in this project.
    (let [hx (+ x rw) hy (+ y rh)]
      (rl/draw-triangle (- hx handle) hy hx (- hy handle) hx hy ink))
    (rl/draw-text (str (int rw) " x " (int rh))
                  (int (* 0.10 w)) (int (- h (* 0.20 h))) label-size (rl/rgba 60 60 60 255))
    (rl/draw-text (if holding? "resizing" "drag the corner")
                  (int (* 0.10 w)) (int (- h (* 0.20 h) (- (+ label-size 14))))
                  label-size (rl/rgba 130 130 135 255))))

(defmethod draw-scene! :align [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [box-x box-w box-y box-h gap text-size label-size h]} (align/dimensions m)
        {:keys [word]} (align/current t)
        tw (rl/measure-text word text-size)]
    ;; All three boxes at once, so the comparison does not depend on memory.
    (doseq [[i a] (map-indexed vector align/alignments)]
      (let [by (+ box-y (* i (+ box-h gap)))
            ox (align/offset a box-w tw)]
        (rl/draw-rectangle (int box-x) (int by) (int box-w) (int box-h) (rl/rgba 225 228 236 255))
        (stroke! (int box-x) (int by) (int (+ box-x box-w)) (int by) (rl/rgba 180 184 196 255))
        (stroke! (int box-x) (int (+ by box-h)) (int (+ box-x box-w)) (int (+ by box-h))
                 (rl/rgba 180 184 196 255))
        (rl/draw-text word (int (+ box-x ox))
                      (int (+ by (* 0.5 (- box-h text-size))))
                      text-size (rl/rgba 30 30 40 255))
        (rl/draw-text (name a) (int box-x) (int (- by label-size 6))
                      label-size (rl/rgba 130 130 140 255))))
    (rl/draw-text (str "MeasureText: " tw " px")
                  (int box-x) (int (- h (* 0.16 h))) label-size (rl/rgba 60 60 60 255))))
