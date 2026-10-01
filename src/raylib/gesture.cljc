(ns raylib.gesture
  "Tap, swipe and long-press, worked out from the per-frame pointer stream.

  A scene sees one pointer per frame: `:press` on the frame a finger lands,
  `:down` while it stays, `:release` on the frame it lifts and `:idle` after.
  The games in batch 3 need to know what a whole touch MEANT, and a press alone
  cannot say, because a swipe and a tap both begin with one. So `track` keeps a
  small gesture value and returns an event only once the touch has revealed
  itself: at the release for a tap or a swipe, mid-hold for a long press.

  It is pure and carries no clock. A long press is counted in frames, which is
  all a scene's `advance` ever sees.

  The thresholds are fractions of the shorter side, so a tap feels the same on
  a phone held either way up and on a tablet. The tap slop is the one the
  gallery's list already uses (`raylib.scroll/tap-slop`)."
  (:require [raylib.scroll :as scroll]))

(def long-press-frames
  "How many `:down` frames a finger must stay put before it counts as a long
  press. estimate: about 0.45 s at the 58-60 fps measured on the phone. A plain
  def so it can be tuned from the nREPL while holding the device."
  27)

(defn- shorter-side [{[w h] :screen}]
  (min w h))

(defn slop
  "How far a finger may travel and still be a tap or a long press, in pixels.
  Needs `:metrics` with `:screen`, and the gallery passes the SAFE-REGION size
  there, so the threshold is relative to the safe region. Reuses the list's threshold so a tap on a scene feels like a tap on a card."
  [metrics]
  (* scroll/tap-slop (shorter-side metrics)))

(defn swipe-min
  "How far a finger must travel, along its dominant axis, to count as a swipe.
  estimate: 0.08 of the shorter side, about 96 px on the phone, which is well
  clear of the slop so a shaky tap is never read as a swipe. Needs `:metrics`
  with `:screen`, which the gallery sets to the SAFE-REGION size, so the
  threshold is relative to the safe region."
  [metrics]
  (* 0.08 (shorter-side metrics)))

(defn down?
  "True when a finger is on the glass this frame and we know where.
  `:release` is excluded on purpose: its position is the last hardware value,
  not where the touch ended."
  [input]
  (let [{:keys [phase position]} (:pointer input)]
    (boolean (and position (#{:press :down} phase)))))

(defn in-rect?
  "Whether `[px py]` is inside `[x y w h]`. Half-open, so the left and top edges
  are in and the right and bottom edges are out. Two cells that share an edge
  therefore never both claim the same touch, which a grid of tiles needs."
  [[x y w h] [px py]]
  (and (>= px x) (< px (+ x w))
       (>= py y) (< py (+ y h))))

(def back-region
  "The area a scene must leave alone, in safe-area coordinates. The Back button
  measures 330x73 at safe offset (40, 40) on the phone and this holds it with
  room to spare. A touch in here belongs to the host, not the scene."
  [0 0 400 120])

(defn in-back-region?
  "Whether `pt` falls inside `back-region`."
  [pt]
  (in-rect? back-region pt))

(def idle
  "The gesture value at rest: no finger, nothing being tracked."
  {})

(defn- dist [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn- swipe-event
  "A swipe from the gesture's start to its last `:down` point, or nil when the
  finger did not go far enough. The larger axis decides the direction. Screen y
  grows downward, so a positive dy is `:down`. An exact tie, |dx| = |dy|, goes
  to the horizontal axis."
  [{:keys [start last]} metrics]
  (let [dx (- (double (first last)) (double (first start)))
        dy (- (double (second last)) (double (second start)))
        ax (abs dx)
        ay (abs dy)]
    (when (>= (max ax ay) (swipe-min metrics))
      {:type :swipe
       :dir (if (>= ax ay)
              (if (pos? dx) :right :left)
              (if (pos? dy) :down :up))
       :from start})))

(defn- finish
  "What a lifted finger meant. The release frame's own position is never read:
  on device it is whatever the hardware held last, so everything here comes from
  the `:start` and `:last` points recorded while the finger was down."
  [g metrics]
  (when-not (:fired? g)
    (or (swipe-event g metrics)
        (when (<= (:travel g) (slop metrics))
          {:type :tap
           :at (:start g)}))))

(defn- hold
  "One more `:down` frame of a started gesture."
  [g point metrics]
  (let [g' (-> g
               (assoc :last point)
               (update :frames inc)
               (update :travel max (dist (:start g) point)))]
    (if (and (not (:fired? g'))
             (>= (:frames g') long-press-frames)
             (<= (:travel g') (slop metrics)))
      [(assoc g' :fired? true) {:type :long-press
                                :at (:start g')}]
      [g' nil])))

(defn track
  "Advance gesture `g` by one frame of `input`. Returns `[g' event]`, where `g'`
  is the value to keep in scene state and `event` is nil or one of

    {:type :tap :at [x y]}               at where the finger STARTED
    {:type :swipe :dir :left|:right|:up|:down :from [x y]}
    {:type :long-press :at [x y]}

  A `:down` with no gesture in progress is ignored. A scene's first frames can
  see the finger that opened it still lifting, and treating that as a gesture
  would swipe or tap something the player never touched. A `:press` while a
  gesture is in progress replaces it silently, with no event for the old one:
  there is a single pointer, so the latest press wins. `:idle` leaves the
  gesture unchanged, so one whose release was missed (the app backgrounded, say)
  can resume on a later `:down`. Scenes start from `idle`, so that only matters
  within one scene. A long press that has
  fired swallows its release, so holding to flag a tile never also reveals it."
  [g input]
  (let [{:keys [phase position]} (:pointer input)
        metrics (:metrics input)]
    (case phase
      :press (if position
               [{:start position
                 :last position
                 :travel 0
                 :frames 0
                 :fired? false}
                nil]
               [idle nil])
      :down (if (and position (:start g))
              (hold g position metrics)
              [g nil])
      :release [idle (when (:start g) (finish g metrics))]
      [g nil])))
