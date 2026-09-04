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
            [raylib.scenes.boids :as boids]
            [raylib.scenes.colorwheel :as wheel]
            [raylib.scenes.epicycles :as epi]
            [raylib.scenes.flowfield :as flow]
            [raylib.scenes.hilbert :as hil]
            [raylib.scenes.life :as life]
            [raylib.scenes.lorenz :as lor]
            [raylib.scenes.lsystem :as lsys]
            [raylib.scenes.fireworks :as fw]
            [raylib.scenes.kaleidoscope :as kal]
            [raylib.scenes.pendulum :as pend]
            [raylib.scenes.penrose :as pen]
            [raylib.scenes.spirograph :as spiro]
            [raylib.scenes.stars :as stars]
            [raylib.scenes.tesseract :as tess]
            [raylib.scenes.unitcircle :as circle]
            [raylib.scenes.tree :as tree]))

(def scenes [(eyes/scene) (trail/scene) (flappy/scene)
             (spiro/scene) (kal/scene) (fw/scene) (pen/scene) (boids/scene)
             (pend/scene) (epi/scene) (hil/scene) (tree/scene) (stars/scene)
             (lsys/scene) (flow/scene) (lor/scene) (tess/scene)
             (life/scene) (auto/scene)
             (wheel/scene) (circle/scene)])

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
             :lorenz :life]}
   {:id :fractals :title "Fractals"
    :scenes [:hilbert :tree :lsystem :automata]}
   {:id :toys :title "Toys"
    :scenes [:following-eyes :touch-trail :boids :pendulum :stars :tesseract
             :colorwheel :unitcircle]}
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

(defn tap!
  "Queue a synthetic tap at [x y] in SCREEN PIXELS, not points. The next frame
  sees a press there and the frame after sees the release, which is the edge
  the gallery opens a card on."
  [x y]
  (reset! pending-tap [x y])
  nil)

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
  present, and taking both would double-count the press."
  [previous-count]
  (let [tap (first (swap-vals! pending-tap (constantly nil)))
        w   (rl/get-screen-width)
        h   (rl/get-screen-height)]
    (merge {:screen-width w :screen-height h
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
             (sample-device previous-count)))))

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

(defn- draw-gallery! [{:keys [margin title-size body-size line-gap cards]} p top heading subheading]
  (rl/clear-background (color (:background p)))
  (rl/draw-text heading margin (+ top margin) title-size (color (:accent p)))
  (rl/draw-text subheading margin (+ top margin title-size (quot line-gap 2)) body-size rl/DARKGRAY)
  (doseq [{:keys [scene-id] :as card} cards]
    (rl/draw-rectangle (:x card) (:y card) (:width card) (:height card) (color (:card p)))
    (centered-text! (title-of scene-id) card body-size WHITE)))

(defn- below-the-safe-area
  "gallery-layout, shifted clear of the status bar.

  The pure layout function knows nothing about safe areas and should not: it is
  handed a screen and divides it. So it is given a screen shortened by the inset
  and every rectangle it returns is then moved down by the same amount. The
  result is identical to a layout that understood insets, and the pure half
  stays portable to a platform that has none.

  Both the cards and the Back target move. Missing the Back target is the
  interesting bug, because it still draws in the right place and only its
  hit-test is wrong, so it looks like an unresponsive button."
  [m sizes top ids]
  (let [[w h] (:screen m)
        shifted (ui/gallery-layout (assoc m :screen [w (- h top)]) ids sizes)
        lower (fn [rect] (update rect :y + top))]
    (-> shifted
        (update :back lower)
        (update :cards #(mapv lower %)))))

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
  [{:keys [mode active-scene-id scene-state]} category layout k m top safe]
  (let [p (ui/live-presentation)
        accent (color (:accent p))]
    (cond
      (= :scene mode)
      (do
        ;; The scene drew its geometry for the safe region starting at 0,0, so
        ;; it is translated into place here and clipped to it. Scissor as well
        ;; as translate, because a scene that overshoots its own bounds would
        ;; otherwise paint over the status bar it was moved clear of.
        (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))
        (rl/rl-push-matrix)
        (rl/rl-translatef (float (:x safe)) (float (:y safe)) 0.0)
        (draw-scene! active-scene-id scene-state {:k k :m m})
        (rl/rl-pop-matrix)
        (rl/end-scissor-mode)
        (draw-back! layout accent))

      category
      (do (draw-gallery! layout p top (title-of category) "Choose a scene")
          (draw-back! layout accent))

      :else
      (draw-gallery! layout p top (:title p) "Choose a category"))))

(defn- frame
  "One frame: sample, decide where the press goes, advance the pure gallery,
  draw. The state carried between frames is the touch count, the cached inset,
  which category is open, and the pure state itself."
  [{:keys [k insets touches gstate category] :as s}]
  (let [insets (resolve-insets k insets)
        top    (:top insets 0)
        input  (diag/normalize-input (raw-sample touches))
        m      (:metrics input)
        safe   (safe-region (:screen m) insets)
        ;; A scene is told it has the safe region and nothing else, so its own
        ;; geometry is computed for the space it will actually get. The host
        ;; then translates it into place at draw time, which is why no scene
        ;; has to know a safe area exists.
        scene-m (assoc m :screen [(:width safe) (:height safe)])
        layout (below-the-safe-area m (diag/layout m) top (visible-ids category))
        press? (= :press (get-in input [:pointer :phase]))
        point  (get-in input [:pointer :position])
        hit    (when press? (ui/hit-test layout point (:mode gstate)))
        ;; hit-test only looks for Back in :scene mode, so the scene list's own
        ;; Back is recognised here. Kept separate from the scene's Back on
        ;; purpose: see navigate.
        list-back? (and press? (= :gallery (:mode gstate)) category
                        (within? (:back layout) point))
        [category' opening?] (navigate category (:mode gstate) hit list-back?)
        input  (assoc input :delta-seconds (rl/get-frame-time) :back? (= hit :back))
        scene-input (assoc input :metrics scene-m)
        gstate (-> (if opening?
                     (gallery/open-scene registry gstate hit scene-input)
                     (gallery/run-frame registry gstate scene-input))
                   drain-events!
                   ignore-close)]
    (render! gstate category' layout k scene-m top safe)
    (assoc s :insets insets
             :category category'
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
    (rl/draw-line px py px cy blue)
    (rl/draw-line px cy cx cy green)
    (rl/draw-line cx cy px py maroon)
    (rl/draw-circle px py (* 0.02 (min (:w d) (:h d))) maroon)
    ;; and the same two values traced over time, underneath
    (doseq [[pick colour centre] [[first blue 0.28] [second green 0.72]]]
      (let [pts (circle/wave-points d trace pick centre)
            n (count pts)]
        (loop [i 1]
          (when (< i n)
            (let [a (nth pts (dec i)) b (nth pts i)]
              (rl/draw-line (int (nth a 0)) (int (nth a 1))
                            (int (nth b 0)) (int (nth b 1)) colour))
            (recur (inc i))))))))
