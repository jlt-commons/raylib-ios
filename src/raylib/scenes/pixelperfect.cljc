(ns raylib.scenes.pixelperfect
  "Smooth pixel-perfect camera, ported from raylib-jlt's `smooth_pixelperfect`
  (net/b12n/raylib_jlt/smooth_pixelperfect.clj, itself raylib's
  `core_smooth_pixelperfect`, zlib licence), without its render texture.

  What the original shows. Three rectangles spin in a tiny 160 by 90 world that
  is drawn into a 160 by 90 render texture and blown up to the window, 5 screen
  pixels for each world pixel. The camera sways on a sine and cosine path
  (lines 38-39). The world is drawn with the camera on the INTEGER part of that
  position (`wx`, `wy`, lines 40-41), so everything in the texture sits on whole
  pixels and the upscaled picture is crisp but steps a whole 5 pixels at a time.
  The fractional part, times the ratio (`sx`, `sy`, lines 42-43), is applied
  when the texture is blitted: the blit is drawn under a second camera whose
  target is that remainder, which slides the finished picture by minus the
  remainder on the glass. The step is gone, and the motion is smooth while the
  pixels stay crisp. S turns that second camera off, so the stepping shows, and
  O turns on overscan: the blit grows by one ratio on every side, so the slide
  never uncovers an edge.

  What is the original's here: the 160 by 90 world, the sway (`world`: cx is
  sin t * 50 - 10, cy is cos t * 30, truncated toward zero for the integer part),
  the three rects with their sizes, colours, positions and spins (`rects`: rot,
  minus rot, rot + 45, turning about their top-left corners, where
  `rect-pro!` has no origin), the spin of 60 degrees a second, the remainder
  times the ratio, the two blit rects (overscan: -R, -R, W + 2R, H + 2R of the
  window; off: the picture centred with an 80 by 45 border, which is 640 by 360
  of 800 by 450), the colours, and the four readouts: the screen and world
  resolutions, the Smooth and Overscan state and the fps.

  How the render texture is replaced. There is no texture. The world is drawn
  straight to the screen under `raylib.host/with-camera-2d` with:
  - the zoom `R`, the largest whole number of screen pixels for a world pixel
    that fits 160 by 90 into the field (`geometry`). It is the original's RATIO,
    which was 5 because 800 is 5 times 160. The window is the virtual screen at
    that zoom, 160R by 90R, centred in the field and clipped to by a scissor.
  - the camera target on the integer part of the sway, so every rect sits on a
    whole world pixel, as in the texture. With smoothing off the whole picture
    jumps R pixels at a time, which is the stairstep.
  - with smoothing on, the remainder times R taken off the camera's offset, in
    screen pixels. That is the original's second camera, folded into the one
    camera (`camera`).
  - a scissor on the picture's rectangle (`clip`), because a texture clips what
    is drawn into it and a camera does not.

  Where this differs from the render-texture version, and it matters. The texture
  rasterised the rects at 160 by 90, so their slanted edges were pixelated
  staircases. Here a rotated rect is drawn at the screen's resolution (an rlgl
  rotation, then `draw-rectangle`), so its edges are smooth; only POSITION is
  pixel-perfect, which is what the camera does. The texture was also resampled
  by the GPU, and here there is nothing to resample. The overscan blit stretched
  the texture by different factors across and down (5.0625 and 5.111); a camera
  has one zoom, so this takes the horizontal one and the picture runs 4 pixels
  short of the stretched height at the bottom, where the window's clip hides it
  and the extra pixels are there to be slid over anyway. The picture with
  overscan off is 160 by 90 at `R - 1` instead of at 4/5 of `R`: the two are the
  same at R = 5 (the original's), and `R - 1` stays a whole zoom at any R. The
  remainder is multiplied by `R`, as the original multiplies by RATIO, whatever
  the zoom the picture is drawn at, so with overscan on (5.0625 against 5) or
  off (4 against 5) the slide is not exactly the world's remainder, just as in
  the original.

  Controls. S becomes a \"smooth\" button and O an \"overscan\" button, side by
  side below Back. A tap flips the one it ends on (`raylib.gesture`, so a press
  that began elsewhere and slid onto a button does nothing). Both read their
  state from the status line, which is the original's, without its \"(S / O
  toggle)\" hint. `get-time` and `get-frame-time` become the sum of
  `:delta-seconds`, unclamped as the original's is. The fps is the host's
  `GetFPS`, read every frame as Starfield shows it. The original's window size
  and `set-target-fps` have no phone counterpart: \"Screen resolution\" reports
  the window drawn here, 160R by 90R.

  The state holds numbers only: `:t` the seconds, `:rot` the degrees,
  `:smooth?`, `:overscan?`, `:screen` and the `:gesture`. Colours are `[r g b a]`
  vectors."
  (:require [raylib.gesture :as gesture]))

(def virtual-w "The original's VW." 160)
(def virtual-h "The original's VH." 90)
(def spin "Degrees a second. The original's 60.0 times the frame time." 60.0)

(def background-colour "LIGHTGRAY." [200 200 200 255])
(def world-colour "RAYWHITE." [245 245 245 255])
(def screen-colour "DARKBLUE." [0 82 172 255])
(def world-text-colour "DARKGREEN." [0 117 44 255])
(def status-colour "RED." [230 41 55 255])
(def fps-colour "LIME, DrawFPS's." [0 158 47 255])
(def button-colour [130 130 130 255])
(def button-on-colour [0 82 172 255])
(def button-label-colour [245 245 245 255])

(def smooth-label "smooth")
(def overscan-label "overscan")

(defn status-line
  "The original's third readout, without its key hint."
  [smooth? overscan?]
  (str "Smooth: " (if smooth? "ON" "OFF") "  Overscan: " (if overscan? "ON" "OFF")))

(defn fps-line [fps] (str fps " fps"))

(defn rects
  "The three rects at spin angle `rot` (degrees) as `{:x :y :w :h :rotation
  :color}`, in world pixels, turning about their `x`, `y`. The original's, lines
  58-79."
  [{:keys [rot]}]
  (let [rot (double rot)]
    [{:x 70.0
      :y 35.0
      :w 20.0
      :h 20.0
      :rotation rot
      :color [0 0 0 255]}
     {:x 90.0
      :y 55.0
      :w 30.0
      :h 10.0
      :rotation (- rot)
      :color [230 41 55 255]}
     {:x 80.0
      :y 65.0
      :w 15.0
      :h 25.0
      :rotation (+ rot 45.0)
      :color [0 121 241 255]}]))

(defn world
  "The camera path at `(:t state)` seconds, the original's lines 38-41:
  `:cx :cy` the sway, `:wx :wy` its integer part, truncated toward zero, and
  `:fx :fy` the remainder in world pixels, which is negative on a negative side."
  [{:keys [t]}]
  (let [t (double t)
        cx (- (* (Math/sin t) 50.0) 10.0)
        cy (* (Math/cos t) 30.0)
        wx (double (long cx))
        wy (double (long cy))]
    {:cx cx
     :cy cy
     :wx wx
     :wy wy
     :fx (- cx wx)
     :fy (- cy wy)}))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text. `:smooth-button` and
  `:overscan-button` are `[x y w h]` side by side below Back, then four text
  rows, then `:field` `[x y w h]`, the full width to the bottom. `:zoom` is the
  largest whole number of screen pixels for a world pixel that fits 160 by 90 in
  the field, and `:window` is `[x y w h]` of the 160 by 90 virtual screen at that
  zoom, centred in the field. `:size`, `:pad` and `:row` are the text metrics."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        bh (* 2.4 size)
        by (+ back-bottom pad)
        bw (/ (- w (* 3.0 pad)) 2.0)
        ty (+ by bh pad)
        ftop (+ ty (* 4 row) pad)
        fh (- h ftop)
        zoom (max 1 (min (quot w virtual-w) (quot (long fh) virtual-h)))
        ww (* zoom virtual-w)
        wh (* zoom virtual-h)]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :text-y ty
     :smooth-button [(double pad) (double by) bw (double bh)]
     :overscan-button [(+ pad bw pad) (double by) bw (double bh)]
     :field [0.0 (double ftop) (double w) (double fh)]
     :zoom zoom
     :window [(* 0.5 (- w ww)) (+ ftop (* 0.5 (- fh wh))) ww wh]}))

(defn dimensions
  "`geometry` plus the text. `:lines` are the four readouts in the original's
  order (screen resolution, world resolution, status, fps) as `{:s :x :y :size}`,
  all one size, cut back until the widest each can be fits the width. The status
  and fps are placeholders the draw replaces with `status-line` and `fps-line`
  at the same position and size. `:smooth-label` and `:overscan-label` are
  centred in their buttons. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad row text-y zoom w]
         [sx sy sw sh] :smooth-button
         [ox oy ow oh] :overscan-button
         :as geo} (geometry metrics)
        screen-s (str "Screen resolution: " (* zoom virtual-w) "x" (* zoom virtual-h))
        world-s (str "World resolution: " virtual-w "x" virtual-h)
        status-s (status-line false false)
        fps-s (fps-line 0)
        room (- w (* 2 pad))
        fit (fn [s room size] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        text-size (reduce min size (map #(fit % room size)
                                        [screen-s world-s (status-line true true) status-s
                                         (fps-line 999)]))
        label-size (reduce min (min size (int sh))
                           [(fit smooth-label (* 0.8 sw) size) (fit overscan-label (* 0.8 ow) size)])
        label (fn [s bx by bw bh]
                {:s s
                 :x (int (+ bx (* 0.5 (- bw (measure s label-size)))))
                 :y (int (+ by (* 0.5 (- bh label-size))))
                 :size label-size})
        line (fn [i s] {:s s
                        :x pad
                        :y (+ text-y (* i row))
                        :size text-size})]
    (assoc geo
           :text-size text-size
           :lines [(line 0 screen-s) (line 1 world-s) (line 2 status-s) (line 3 fps-s)]
           :smooth-label (label smooth-label sx sy sw sh)
           :overscan-label (label overscan-label ox oy ow oh))))

(defn picture
  "Where the 160 by 90 world is blitted, `[x y w h]` relative to the window, as
  the original's `dx dy dw dh` (lines 46-49) scaled to this window. With overscan
  it is `-R -R` and the window plus `2R` each way. Without it, the world at `R - 1`
  (at least 1) centred, which is 80 by 45 inside at R = 5."
  [{:keys [overscan?]} {:keys [zoom]
                        [_ _ ww wh] :window}]
  (let [r (double zoom)]
    (if overscan?
      [(- r) (- r) (+ ww (* 2.0 r)) (+ wh (* 2.0 r))]
      (let [pz (double (max 1 (dec zoom)))
            pw (* pz virtual-w)
            ph (* pz virtual-h)]
        [(* 0.5 (- ww pw)) (* 0.5 (- wh ph)) pw ph]))))

(defn camera
  "The `raylib.camera2d` camera the world is drawn under. The target is the
  integer part of the sway, and the zoom is the picture's width over 160. The
  offset is the window's corner plus the picture's, and with smoothing on, minus
  the remainder times the ratio `R` (the original's second camera, whose target
  is `(cx - wx) * RATIO`)."
  [{:keys [smooth?]
    :as state} {:keys [zoom]
                [x y] :window
                :as dims}]
  (let [{:keys [wx wy fx fy]} (world state)
        [px py pw] (picture state dims)
        r (double zoom)
        sx (if smooth? (* fx r) 0.0)
        sy (if smooth? (* fy r) 0.0)]
    {:offset [(- (+ x px) sx) (- (+ y py) sy)]
     :target [wx wy]
     :rotation 0.0
     :zoom (/ pw virtual-w)}))

(defn clip
  "The scissor rectangle `[x y w h]` for the world, in scene pixels: the picture,
  slid by the same remainder as the camera, and cut to the window. A render
  texture clips to its own edges and a camera does not, so this stands in for
  that, and the window's edge stands in for the screen's."
  [{:keys [smooth?]
    :as state} {:keys [zoom]
                [x y w h] :window
                :as dims}]
  (let [{:keys [fx fy]} (world state)
        [px py pw ph] (picture state dims)
        r (double zoom)
        sx (if smooth? (* fx r) 0.0)
        sy (if smooth? (* fy r) 0.0)
        x0 (max x (- (+ x px) sx))
        y0 (max y (- (+ y py) sy))
        x1 (min (+ x w) (- (+ x px pw) sx))
        y1 (min (+ y h) (- (+ y py ph) sy))]
    [x0 y0 (max 0.0 (- x1 x0)) (max 0.0 (- y1 y0))]))

(defn advance
  "One frame. The spin and the clock add `:delta-seconds` (the original's
  `get-frame-time` and `get-time`), and a tap that began on a button flips its
  mode (S and O). A rotation of the phone drops the gesture. The release
  position is never read."
  [state {:keys [metrics delta-seconds]
          :as input}]
  (let [dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state)) (assoc state :gesture gesture/idle) state)
        dt (double (or delta-seconds 0.0))
        [g event] (gesture/track (:gesture state) input)
        tap? (= :tap (:type event))
        smooth-tap? (and tap? (gesture/in-rect? (:smooth-button dims) (:at event)))
        overscan-tap? (and tap? (gesture/in-rect? (:overscan-button dims) (:at event)))]
    (assoc state
           :screen screen
           :gesture g
           :t (+ (double (:t state)) dt)
           :rot (+ (double (:rot state)) (* spin dt))
           :smooth? (if smooth-tap? (not (:smooth? state)) (:smooth? state))
           :overscan? (if overscan-tap? (not (:overscan? state)) (:overscan? state)))))

(defn- init [{:keys [metrics]}]
  [{:t 0.0
    :rot 0.0
    :smooth? true
    :overscan? false
    :screen (:screen metrics)
    :gesture gesture/idle}
   [[:scene/init :pixelperfect]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :pixelperfect]]])

(defn scene []
  {:id :pixelperfect
   :title "Smooth Pixel-Perfect"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
