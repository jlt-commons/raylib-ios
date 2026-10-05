(ns net.b12n.raylib-ios.frame
  "The per-frame machinery every iOS owner loop shares: the gallery shell in
  net.b12n.raylib-ios.gallery and the one-scene loop in net.b12n.raylib-ios.runner.

  It samples the touch screen (or a queued synthetic tap), resolves the safe
  area once UIKit can answer, moves the pointer into the safe region's
  coordinates, builds the input map a scene receives, and draws a scene inside
  the scissored, translated safe region. None of it knows what a menu is, so a
  loop that shows one scene reuses it as it stands. Lifted from the gallery
  shell unchanged except that the pieces are public and `poll-input`,
  `scene-metrics`, `scene-input`, `draw-in-safe-region!` and `guarded` now name
  what the shell used to do inline."
  (:require [net.b12n.raylib-ios.draw :refer [host-measure]]
            [net.b12n.raylib-ios.gallery.diagnostics :as diag]
            [net.b12n.raylib-ios.host :as rl]))

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
;; here is the raw shape net.b12n.raylib-ios.gallery.diagnostics/normalize-input consumes, and
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

(defn raw-sample
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
    [(merge {:screen-width w
             :screen-height h
             ;; no HighDPI translation to do: the window is created at pixel
             ;; size, so the render surface and the screen are the same numbers
             :render-width w
             :render-height h
             :back? false}
            ;; Each sampler reports :down? from the count it already read. An
            ;; earlier draft derived it here with a second GetTouchPointCount
            ;; call, which polls the hardware twice in one frame and can
            ;; disagree with itself when a finger lifts between the two.
            (if tap
              (sample-synthetic tap previous-count)
              (sample-device previous-count)))
     tap]))

(defn touch-points
  "Every active touch as [x y], in screen pixels.

  This sits beside `:touches` rather than inside it. The Android contract in
  `net.b12n.raylib-ios.gallery.diagnostics` reports `:all-coordinates-available? false` and gives
  coordinates for point zero only, which was honest on the platform it was
  written for: it has GetTouchX and GetTouchY and no binding for the by-value
  Vector2 that GetTouchPosition returns. That file is one of the six checked
  against the upstream original (identical apart from names and whitespace), so the extra data goes in a key of our
  own and the contract keeps its word.

  A synthetic tap reports itself as the one point, so `tap!` drives this the
  same way it drives everything else."
  [tap]
  (if tap
    [[(double (first tap)) (double (second tap))]]
    (mapv rl/touch-position (range (rl/get-touch-point-count)))))

(defn resolve-insets
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
        (println (format "frame: safe area %s px" (pr-str px))))
      px)))

(defn safe-region
  "Where a scene may draw, in screen pixels."
  [[w h] {:keys [top bottom left right]
          :or {top 0
               bottom 0
               left 0
               right 0}}]
  {:x left
   :y top
   :width  (- w left right)
   :height (- h top bottom)})

(defn into-safe-region
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

(defn drain-events!
  "Print and clear the scene's events. They are the pure half's only way of
  saying anything, so dropping them silently would hide a scene's own
  diagnostics."
  [gstate]
  (doseq [e (:scene-events gstate)] (println "frame:" (pr-str e)))
  (assoc gstate :scene-events []))

(defn ignore-close
  "A scene asking to close means nothing here. On a desktop the loop would end;
  an iOS app does not exit, and one that did would be rejected."
  [gstate]
  (if (:close-requested? gstate)
    (do (println "frame: close requested, which an iOS app cannot do. Ignored.")
        (assoc gstate :close-requested? false))
    gstate))

(defn poll-input
  "One frame's input from the screen, or from a queued tap!/drag!, as the
  normalised map a scene's gallery contract expects, plus `:touch-points` and
  `:local-time`. `touches` is the previous frame's touch count, which the edge
  detection needs.

  `:local-time` is the wall clock, for scenes that sweep between its ticks. It
  has to be the same clock the seconds come from: a sub-second fraction
  accumulated from frame deltas drifts out of phase with it and the hand jumps
  backward mid-second. Both go here rather than into diag/normalize-input,
  which is checked against upstream (identical apart from names and
  whitespace)."
  [touches]
  (let [[raw tap] (raw-sample touches)]
    (assoc (diag/normalize-input raw)
           :touch-points (touch-points tap)
           :local-time (rl/local-time))))

(defn scene-metrics
  "`m` with the screen replaced by the safe region's size. A scene is told it
  has the safe region and nothing else, so its own geometry is computed for the
  space it will actually get. The host then translates it into place at draw
  time, which is why no scene has to know a safe area exists."
  [m safe]
  (assoc m :screen [(:width safe) (:height safe)]))

(defn scene-input
  "The input map a scene's :update and :draw receive: `input` from
  `poll-input` plus this frame's delta, the Back edge, raylib's own text width
  (`:measure`, for scenes that lay out text in their state and so need it
  outside the draw method) and its gesture recogniser's code (0 at rest, for
  :gestures), with `scene-m` as the metrics and the pointer moved into the safe
  region's coordinates."
  [input safe scene-m back?]
  (-> input
      (assoc :delta-seconds (rl/get-frame-time)
             :back? back?
             :measure host-measure
             :raylib-gesture (rl/get-gesture-detected))
      (assoc :metrics scene-m)
      (into-safe-region safe)))

(defn guarded
  "Call `f`, which runs scene code, and return its result. If it throws, return
  `(on-throw e)` instead. The caller decides what a failure means: the gallery
  drops back to its list and the one-scene runner stays and shows the message."
  [on-throw f]
  (try
    (f)
    (catch :default e
      (on-throw e))))

(defn draw-in-safe-region!
  "Clear to `background`, then call `f` clipped to and translated into `safe`,
  and return what it returned.

  The inset bands are cleared before the scissor goes up. A scene's own
  ClearBackground is subject to the scissor, because it becomes a glClear and
  glClear respects it. So the bands keep whatever was drawn there last, which
  for a scene opened from the gallery is the card grid. In portrait those bands
  are thin strips behind the status bar and nobody noticed for weeks. Rotating
  the phone to landscape puts 186 pixels down each side and the stale cards are
  unmissable.

  The scene drew its geometry for the safe region starting at 0,0, so it is
  translated into place here and clipped to it. Scissor as well as translate,
  because a scene that overshoots its own bounds would otherwise paint over the
  status bar it was moved clear of. rlgl's scissor does not nest:
  BeginScissorMode inside another one simply takes over, so a scene clipping to
  its own box would be free to paint over the status bar the host just moved it
  clear of; the scene gets `:safe` for that reason.

  `f` must catch its own throws (see `guarded`), so the pop and the scissor end
  below run on both paths. Without them every later frame would stay translated
  and clipped."
  [background safe f]
  (rl/clear-background background)
  (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))
  (rl/rl-push-matrix)
  (rl/rl-translatef (float (:x safe)) (float (:y safe)) 0.0)
  (let [result (f)]
    (rl/rl-pop-matrix)
    (rl/end-scissor-mode)
    result))
