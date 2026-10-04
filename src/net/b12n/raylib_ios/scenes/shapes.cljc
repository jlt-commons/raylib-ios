(ns net.b12n.raylib-ios.scenes.shapes
  "A static tour of the basic shapes: a filled and an outlined rectangle, a
  filled and an outlined circle, an ellipse, a line and a triangle. Ported from
  raylib-jlt's `shapes`, itself raylib's `shapes_basic_shapes`. It is the
  side-by-side of the plain primitives, where `rounded`, `ring`, `sector` and
  `rlgltriangle` each animate one of them in depth and `outlines` studies a
  stroke's thickness. Nothing moves and nothing is read, so there is no input
  handling to get wrong.

  Two of the original's calls are not bound here and are rebuilt. The ellipse is
  a triangle fan, which is the example: `ellipse-fan` returns wedges from the
  centre to consecutive rim points, and the draw method hands each to
  `rl/draw-triangle`, which fixes its own winding. The wedges are wound
  negative-cross natively too, so a culled one would show in a test as well as
  as a pie-slice hole on the phone. The segment count is `ellipse-segments`: the
  fewest chords that keep every one within half a pixel of the curve, which is
  `ceil(pi / acos(1 - 0.5 / r))` for the larger radius `r`, floored at 12 so a
  tiny ellipse is still round and capped at 180 so a huge one costs a bounded
  number of triangles.

  The rectangle outline is `net.b12n.raylib-ios.scenes.outlines/rect-lines`, four lines that
  grow inward from the rectangle's own edge, as `DrawRectangleLines` does. The
  circle outline is `rl/draw-ring` rather than the bound `rl/draw-circle-lines`,
  because that is a one pixel line, which is hard to see on a phone. The ring
  keeps the circle's outer edge and grows inward by `thick`. The original's
  line is also a hairline and is `thick` wide here, for the same reason. The
  triangle is the original's, drawn with `rl/draw-triangle` where the original
  used rlgl immediate mode by hand.

  The original is laid out for 800 by 450. This is laid out for portrait, five
  bands one above another: two rectangles, two circles, the ellipse, the line
  and the triangle. A scale `f`, in pixels per original pixel, is the smaller of
  a 300th of the width and a 130th of a band's height, so the pairs fit across
  and every shape fits in its band. On a landscape screen it is small, and
  that is accepted. The title is below `gesture/back-region`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def title "The original's heading." "scalar shape primitives")

(def background-colour "The original's RAYWHITE." [245 245 245 255])
(def title-colour "The original's DARKGRAY." [80 80 80 255])
(def rect-colour "The original's RED." [230 41 55 255])
(def rect-outline-colour "The original's BLUE." [0 121 241 255])
(def circle-colour "The original's GREEN." [0 228 48 255])
(def ring-colour "The original's PURPLE." [200 122 255 255])
(def ellipse-colour "The original's ORANGE." [255 161 0 255])
(def line-colour "The original's DARKGRAY." [80 80 80 255])
(def triangle-colour "The original's VIOLET." [135 60 190 255])

(def ring-segments "Chords round the circle outline." 48)

(defn ellipse-segments
  "How many wedges the ellipse of radii `rx` and `ry` is cut into: the fewest
  that keep every chord within half a pixel of the curve at the larger radius,
  `ceil(pi / acos(1 - 0.5 / r))`, from 12 up to 180."
  [rx ry]
  (let [r (double (max rx ry))]
    (if (<= r 0.5)
      12
      (max 12 (min 180 (long (Math/ceil (/ Math/PI (Math/acos (- 1.0 (/ 0.5 r)))))))))))

(defn ellipse-fan
  "The ellipse at `[cx cy]` with radii `rx` and `ry` as `n` triangles
  `[x1 y1 x2 y2 x3 y3]`. Each is the centre and two neighbouring rim points. The
  rim runs clockwise from twelve o'clock, wedge `i` is the centre, rim `i+1` and
  rim `i`, which is the order that is negative-cross in screen space, and the
  index wraps so the last wedge closes on the first with no float error from a
  full turn."
  [cx cy rx ry n]
  (let [rim (fn [i]
              (let [a (* 2.0 Math/PI (/ (double (mod i n)) n))]
                [(+ cx (* rx (Math/sin a)))
                 (- cy (* ry (Math/cos a)))]))]
    (mapv (fn [i]
            (let [[x2 y2] (rim (inc i))
                  [x3 y3] (rim i)]
              [cx cy x2 y2 x3 y3]))
          (range n))))

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h`, the scale `:f`, the line width
  `:thick`, the `:title` as `{:s :x :y :size}` and every shape. `:rect` and
  `:rect-outline` are `[x y w h]`, `:circle` and `:circle-ring` are `[cx cy r]`,
  `:ellipse` is `[cx cy rx ry n]`, `:line` is `[x1 y1 x2 y2]` and `:triangle` is
  `[x1 y1 x2 y2 x3 y3]`. The title is below `gesture/back-region` and the five
  bands share the room under it."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        ts (max 20 (int (* 0.03 side)))
        [_ back-y _ back-h] gesture/back-region
        title-y (int (+ back-y back-h (* 0.5 ts)))
        margin (* 0.03 side)
        region-top (+ title-y ts margin)
        band (/ (- h margin region-top) 5.0)
        f (min (/ w 300.0) (/ band 130.0))
        cy (fn [i] (+ region-top (* (+ i 0.5) band)))
        left (* 0.27 w)
        right (* 0.73 w)
        mid (* 0.5 w)
        u (fn [n] (* n f))]
    {:w w
     :h h
     :f f
     :thick (max 2.0 (* 0.004 side))
     :title {:s title
             :x (int (* 0.04 w))
             :y title-y
             :size ts}
     :rect [(- left (u 60)) (- (cy 0) (u 45)) (u 120) (u 90)]
     :rect-outline [(- right (u 60)) (- (cy 0) (u 45)) (u 120) (u 90)]
     :circle [left (cy 1) (u 50)]
     :circle-ring [right (cy 1) (u 50)]
     :ellipse [mid (cy 2) (u 60) (u 40) (ellipse-segments (u 60) (u 40))]
     :line [(* 0.1 w) (cy 3) (* 0.9 w) (cy 3)]
     :triangle [mid (- (cy 4) (u 60))
                (- mid (u 70)) (+ (cy 4) (u 60))
                (+ mid (u 70)) (+ (cy 4) (u 60))]}))

(defn- init [_] [{} [[:scene/init :shapes]]])
(defn- update-scene [state _] [state []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :shapes]]])

(defn scene []
  {:id :shapes
   :title "Basic Shapes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
