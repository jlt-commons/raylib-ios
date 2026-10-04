(ns raylib.scenes.rendertex
  "Render Texture, ported from raylib-jlt's `render_texture`
  (net/b12n/raylib_jlt/render_texture.clj, zlib licence), which is raylib's
  `textures_render_texture`. The raylib C example it follows is zlib licensed
  too, and this is an altered version of both.

  One small scene is drawn once into an off-screen render target, then that
  target's texture is drawn back to the screen four times at different scales
  and tints. Drawing the scene is paid for once however many copies appear.

  Mirrored from render_texture.clj:
  - The target (lines 19-20): `RT-W` 320 by `RT-H` 240, so `rt-w` and `rt-h`.
  - The scene in it (lines 22-43): a clear to (20, 24, 34), six balls of radius
    18, the text \"off-screen\" at (10, 10) in size 20 RAYWHITE, and a GRAY
    outline round the whole target. Ball `i` sits at
    `(160 + 110 sin(t + 0.9 i), 120 + 70 cos(1.4 (t + 0.9 i)))`, truncated to
    whole pixels, with the colour `(i*60 mod 256, 200, 255 - i*60 mod 256)`.
    `t` is the original's `get-time`, here the seconds the scene has run.
  - The copies (lines 45-47, 73-85): `copies`, as `[x y scale label]` in the
    original's 800 by 450 window, each drawn `(int (* 320 scale))` by
    `(int (* 240 scale))`, the last one tinted (255, 180, 180). A copy is drawn
    with `:v0 1.0 :v1 0.0`, because GL stores a target bottom-up.
  - The text (lines 68-72, 86-90): `title-line` at (40, 40) in size 20 DARKGRAY,
    and each label 4 below its copy in size 14 GRAY.

  Deviations. The original's 800 by 450 window is drawn as one block, scaled by
  the largest factor of the screen that fits (`:scale`, the smaller of
  the width over 800 and the free height over 450) and centred below Back. So
  the target is still 320 by 240 texels and a \"1:1\" copy is the target at that
  block scale, not at one texel to a pixel. The text sizes scale with it and
  are cut back until the longest line fits the width. The seconds `t` come from
  `:delta-seconds`, so the balls move at the original's speed, and the first
  frame drawn is one frame in.

  The state holds `:t`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]))

(def rt-w "The original's RT-W." 320)
(def rt-h "The original's RT-H." 240)
(def design-w "The original's window width." 800)
(def design-h "The original's window height." 450)

(def title-line "The original's text." "one scene rendered once, drawn back four times")

(def copies
  "The original's `copies`: `[x y scale label]` in its 800 by 450 window."
  [[40 90 1.0 "1:1"] [400 90 0.6 "60%"] [400 260 0.35 "35%"] [610 260 0.5 "50% tinted"]])

(def tint-colour "The last copy's tint." [255 180 180 255])

(def ball-radius "The original's." 18)

(def background-colour "RAYWHITE." [245 245 245 255])
(def target-clear-colour "The target's clear, (20, 24, 34)." [20 24 34 255])
(def target-text "The text drawn inside the target." "off-screen")
(def target-text-colour "RAYWHITE." [245 245 245 255])
(def target-outline-colour "GRAY." [130 130 130 255])
(def title-colour "DARKGRAY." [80 80 80 255])
(def label-colour "GRAY." [130 130 130 255])

(defn target-spec
  "What to ask `raylib.texture/target!` for."
  []
  {:w rt-w
   :h rt-h})

(defn balls
  "The six balls at `t` seconds, as `[x y [r g b a]]` with x and y truncated to
  whole pixels, in target coordinates. The original's `draw-scene` (lines 26-34)."
  [t]
  (let [t (double t)]
    (mapv (fn [i]
            (let [ph (* i 0.9)
                  r (mod (* i 60) 256)]
              [(int (+ (/ rt-w 2.0) (* 110 (Math/sin (+ t ph)))))
               (int (+ (/ rt-h 2.0) (* 70 (Math/cos (* 1.4 (+ t ph))))))
               [r 200 (- 255 r) 255]]))
          (range 6))))

(defn dimensions
  "The layout for `metrics`' `:screen`: `:scale`, `:ox` and `:oy` (where the
  original's window origin lands), `:copies` (one `{:x :y :width :height :tint
  :label}` per row of `copies`, where `:label` is a text line) and `:lines`, the
  title and the four labels as `{:s :x :y :size}`. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        k (double (min (/ w (double design-w)) (/ (- h top) (double design-h))))
        ox (/ (- w (* k design-w)) 2.0)
        oy (+ top (/ (- h top (* k design-h)) 2.0))
        fit (fn [size s x]
              (max 8 (min size (int (/ (* (- w x) 100.0) (measure s 100))))))
        title-x (+ ox (* k 40))
        title {:s title-line
               :x title-x
               :y (+ oy (* k 40))
               :size (fit (int (* k 20)) title-line title-x)}
        cs (mapv (fn [[x y s label]]
                   (let [cx (+ ox (* k x))
                         cy (+ oy (* k y))
                         cw (* k (int (* rt-w s)))
                         ch (* k (int (* rt-h s)))]
                     {:x cx
                      :y cy
                      :width cw
                      :height ch
                      :tint (when (= label "50% tinted") tint-colour)
                      :label {:s label
                              :x cx
                              :y (+ cy ch (* k 4))
                              :size (fit (int (* k 14)) label cx)}}))
                 copies)]
    {:scale k
     :ox ox
     :oy oy
     :title title
     :copies cs
     :lines (into [title] (map :label) cs)}))

(defn advance
  "One frame: the clock moves on by the frame's seconds."
  [state {:keys [delta-seconds]}]
  (update state :t + (max 0.0 (double (or delta-seconds 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :rendertex]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :rendertex]]])

(defn scene []
  {:id :rendertex
   :title "Render Texture"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
