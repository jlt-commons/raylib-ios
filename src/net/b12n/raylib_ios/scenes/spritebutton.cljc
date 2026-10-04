(ns net.b12n.raylib-ios.scenes.spritebutton
  "Sprite Button, ported from raylib-jlt's `sprite_button`
  (net/b12n/raylib_jlt/sprite_button.clj, EPL 2.0), which is raylib's
  `textures_sprite_button`: one texture holds three stacked frames of a button,
  normal, hover and pressed, and the button picks its frame by sliding a window
  down the sheet, a third of the texture's height at a time. Click it and the
  counter goes up.
  The raylib C example it follows is zlib licensed, and this is an altered
  version of that too.

  Mirrored from sprite_button.clj:
  - The sheet (lines 33-62): 160 by 144, three frames of 160 by 48 stacked
    (lines 29-31). Frame 0 is [70 110 190], 1 is [95 150 235], 2 is [55 85 150];
    an edge band darkens the rim and lights the top (the bottom in frame 2), and
    a pale bar for a label sits a pixel lower in frame 2. `sheet-spec` packs it
    with `net.b12n.raylib-ios.texel/pack`, whose byte order is raylib-jlt's `rgba`.
  - The frame (lines 64-70, 99-100): 0 normal, 1 hover, 2 pressed, and the
    button's source window is `v0 = frame / 3` to `v1 = (frame + 1) / 3`.
  - The action (lines 96-97): a release over the button adds one to the click
    count. The original does not look at where the press began, and neither
    does this.
  - The idle cycle (lines 90, 95, 120): while nobody is at the pointer the frame
    is `(quot (mod frame 180) 60)`, sixty frames each of normal, hover and
    pressed, and the hint reads \"cycling the three frames until you hover it\".
  - The text (lines 109-123): `clicks: N`, the frame's name, and
    \"one texture, three frames, sliced by v\".
  - The preview (lines 126-134): the whole sheet drawn at the top right with a
    RED outline around the frame in use.

  Deviations. The mouse becomes touch. A finger down inside the button shows the
  pressed frame. A phone has no pointer to hover with, so the hover frame shows
  while a finger that PRESSED the button has slid off it and is still down, and
  nowhere else. The original's idle cycle is kept and ports exactly: its `live?`
  is \"the pointer is over the button or the mouse is down\", and on a phone
  the pointer is never over anything, so `live?` is just \"a finger is down\".
  While none is, the three frames cycle on the frame count, from the scene's
  first frame; a finger landing stops the cycle that frame and a release starts
  it again where the count has got to. The hint says \"touch\" where the
  original says \"hover\". The release's own position is not trusted, because
  the host hands back the last hardware value, so the scene keeps the position
  of the last frame the finger was down and tests that. The button is half the
  shorter side wide, the preview a quarter, and the layout keeps both below
  Back.

  Entry cost. The first open after launch pauses for about 0.2 s on the phone
  (the largest single frame, measured), while the texture's pixels are computed
  and uploaded. Opening it again takes about one frame, because the filled
  texture buffer is kept.

  The state holds `:clicks`, `:frame`, `:tick` (frames since the scene began),
  `:idle?` (no finger down this frame), `:at` (the last position a finger was
  down, or nil), `:grab` (whether this touch began on the button) and
  `:screen`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.texel :as texel]))

(def btn-w "The original's BTN-W." 160)
(def btn-h "The original's BTN-H." 48)
(def frame-count "The original's FRAMES." 3)
(def sheet-h "The sheet's height: the frames stacked." (* btn-h frame-count))

(def background-colour "RAYWHITE." [245 245 245 255])
(def clicks-colour "DARKGRAY." [80 80 80 255])
(def state-colour "GRAY." [130 130 130 255])
(def hint-colour "GRAY." [130 130 130 255])
(def outline-colour "RED." [230 41 55 255])

(def frame-names "The original's names for frames 0, 1 and 2." ["normal" "hover" "pressed"])
(def hint-line "The original's hint." "one texture, three frames, sliced by v")
(def idle-hint "The original's idle hint (line 120), for a finger." "cycling the three frames until you touch it")
(def cycle-frames "Frames each state of the idle cycle lasts (the original's 60)." 60)
(def cycle-period "Frames in one idle cycle (the original's 180)." (* cycle-frames frame-count))

(defn clicks-line [n] (str "clicks: " n))

(defn- clamp8 [n] (min 255 (max 0 n)))

(defn sheet-colour
  "The original's `sheet-pixel` (lines 33-62) at texel `x`, `y`, as `[r g b a]`."
  [x y]
  (let [frame (quot y btn-h)
        fy (mod y btn-h)
        edge (min x fy (- btn-w 1 x) (- btn-h 1 fy))
        top? (< fy (/ btn-h 2))
        [r g b] (case frame
                  0 [70 110 190]
                  1 [95 150 235]
                  [55 85 150])
        lift (cond
               (< edge 2) -35
               (and (< edge 6) (if (= frame 2) (not top?) top?)) 55
               (< edge 6) -25
               :else 0)
        label-y (if (= frame 2) (+ (/ btn-h 2) 1) (/ btn-h 2))
        label? (and (> x 40) (< x (- btn-w 40))
                    (>= fy (- label-y 3)) (< fy (+ label-y 3)))]
    (if label?
      [240 245 255 255]
      [(clamp8 (+ r lift)) (clamp8 (+ g lift)) (clamp8 (+ b lift)) 255])))

(defn sheet-spec
  "The texture for `net.b12n.raylib-ios.texture/id!`: 160 by 144, clamped (neither side is a
  power of two, so GLES2 will not repeat it), unfiltered."
  []
  {:w btn-w
   :h sheet-h
   :wrap :clamp
   :filter :nearest
   :pixel (fn [x y] (texel/pack (sheet-colour x y)))})

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`,
  `:size :pad :row` (the text's), `:top` (below Back), `:button` and `:preview`
  as `[x y w h]`, `:frame-h` (one frame of the preview) and `:thick` (the
  outline's)."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        k (/ (* 0.5 side) btn-w)
        bw (* btn-w k)
        bh (* btn-h k)
        pw (* 0.25 side)
        pk (/ pw btn-w)]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :top top
     :button [(- (/ w 2.0) (/ bw 2.0)) (- (/ h 2.0) (/ bh 2.0)) bw bh]
     :preview [(- w pad pw) (double (+ top pad)) pw (* sheet-h pk)]
     :frame-h (* btn-h pk)
     :thick (max 2 (int (* 0.004 side)))}))

(defn dimensions
  "`geometry` plus `:lines`, `{:clicks :state :hint}` each as `{:x :y :size}`
  (and `:s` for the hint), every one cut back until its widest text fits, the
  top two to the room left of the preview. `measure` is `(fn [s size] ->
  px)`."
  [metrics measure]
  (let [{:keys [w h pad size top preview]
         :as geo} (geometry metrics)
        [px] preview
        fit (fn [s room cap] (max 8 (min cap (int (/ (* room 100.0) (measure s 100))))))
        left (- px (* 2 pad))
        csize (fit (clicks-line 99999) left (int (* 1.5 size)))
        ssize (fit "pressed" left size)
        y1 (+ top pad)
        hsize (min (fit hint-line (- w (* 2 pad)) size)
                   (fit idle-hint (- w (* 2 pad)) size))]
    (assoc geo :lines {:clicks {:x pad
                                :y y1
                                :size csize}
                       :state {:x pad
                               :y (+ y1 (int (* 1.3 csize)))
                               :size ssize}
                       :hint {:s hint-line
                              :x pad
                              :y (- h pad hsize)
                              :size hsize}})))

(defn hint
  "The hint for `state`: the idle one while the frames cycle, else the original's."
  [state]
  (if (:idle? state) idle-hint hint-line))

(defn button-quad
  "The button as `net.b12n.raylib-ios.texture/quad!` takes it: the window of the sheet that
  frame `(:frame state)` lives in, `v0 = frame / 3` to `v1 = (frame + 1) / 3`."
  [state {[x y w h] :button}]
  (let [f (:frame state)]
    {:x x
     :y y
     :width w
     :height h
     :v0 (/ (double f) frame-count)
     :v1 (/ (double (inc f)) frame-count)}))

(defn preview-quad
  "The whole sheet, at the top right."
  [{[x y w h] :preview}]
  {:x x
   :y y
   :width w
   :height h})

(defn outline-rects
  "The RED outline around the frame in use in the preview, as four filled
  `[x y w h]` strips (top, bottom, left, right)."
  [state {[x y w] :preview
          :keys [frame-h thick]}]
  (let [fy (+ y (* (:frame state) frame-h))
        t (double thick)]
    [[x fy w t]
     [x (- (+ fy frame-h) t) w t]
     [x fy t frame-h]
     [(- (+ x w) t) fy t frame-h]]))

(defn advance
  "One frame. A finger down inside the button shows frame 2; a finger that began
  on the button and is down off it shows frame 1; a finger down elsewhere shows
  frame 0; with no finger down the frames cycle, `(quot (mod tick 180) 60)`. A release
  adds a click when the last position the finger was down at is inside the
  button."
  [state {:keys [metrics pointer]
          :as input}]
  (let [rect (:button (geometry metrics))
        down? (gesture/down? input)
        position (:position pointer)
        inside? (and down? (gesture/in-rect? rect position))
        grab (cond
               (= :press (:phase pointer)) (boolean inside?)
               down? (:grab state)
               :else false)
        acts? (and (= :release (:phase pointer))
                   (:at state)
                   (gesture/in-rect? rect (:at state)))]
    (assoc state
           :clicks (if acts? (inc (:clicks state)) (:clicks state))
           :frame (cond
                    inside? 2
                    (and down? grab) 1
                    down? 0
                    :else (quot (mod (:tick state) cycle-period) cycle-frames))
           :tick (inc (:tick state))
           :idle? (not down?)
           :at (cond
                 down? position
                 (= :release (:phase pointer)) (:at state)
                 :else nil)
           :grab grab
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:clicks 0
    :frame 0
    :tick 0
    :idle? true
    :at nil
    :grab false
    :screen (:screen metrics)}
   [[:scene/init :spritebutton]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :spritebutton]]])

(defn scene []
  {:id :spritebutton
   :title "Sprite Button"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
