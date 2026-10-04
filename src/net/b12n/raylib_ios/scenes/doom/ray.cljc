(ns net.b12n.raylib-ios.scenes.doom.ray
  "The renderer of `net.b12n.raylib-ios.scenes.doom`: a DDA ray caster that turns one frame of
  the world into a flat list of rectangles, written into one preallocated
  buffer so a frame allocates nothing per column.

  Mirrored from raylib-jlt's `doom.clj` (see `net.b12n.raylib-ios.scenes.doom`):
  - `cast!` is `cast-column` (lines 181-219). A column's ray is
    `dir + plane * (2 i / COLS - 1)`, it steps cell by cell along whichever axis
    is nearer (`sdx < sdy`) until a solid cell, and the distance is the
    perpendicular one: `sdx - ddx` for a vertical face, `sdy - ddy` for a
    horizontal one, held to 0.0001. That distance goes to `zbuf` for the sprites
    (lines 177, 245). The original also stops a ray after 64 steps and treats
    anything off the map as wall. Neither can happen here: the map's border is
    all wall (a test checks it) and the player cannot cross a wall, so every ray
    ends on the border at most 30 cells out. The bounds test and the counter are
    left out, which changes nothing a frame can show.
  - A wall column is `H / dist` tall and centred, shaded by
    `net.b12n.raylib-ios.scenes.doom.map/shade` (lines 234-252). The original draws one
    textured quad per column. Here a column is a flat rect, and a run of columns
    whose rect is the same in pixels (the same top, bottom and lit colour) is one
    rect, which draws exactly what the columns did. A wall that faces the eye
    square on is one rect for its whole width.
  - A sprite is cut into 12 strips and a strip is drawn only where its middle
    column is nearer than that column's wall (lines 271-326), furthest imp first.
    The imp's texel (lines 136-142) is a squat body (an ellipse), a head (a
    circle) and two eyes (circles). A strip draws what the texel column at its
    middle shows, as at most a body rect, a head rect and an eye rect. Those
    extents depend only on the strip, so `profiles` works them out once.

  Everything is in the view's pixels, `[vx vy vw vh]`, which is the original's
  900 by 560 window scaled. The buffer is an int array holding `x y w h colour`
  for each rect in painter order, where the colour is an index into
  `net.b12n.raylib-ios.scenes.doom.map/palette`. A rect that reaches past the view is cut to
  it, because the original's quads are cut by the window.

  Under jolt a `long` of a float costs about 100 ns and an `int` about 25, which
  was most of a column's time, so the hot path truncates with `int`. The values
  are small positions and pixels, so it truncates the same."
  (:require [net.b12n.raylib-ios.scenes.doom.map :as m]))

(def cols
  "Rays a frame. The original casts 450 (line 45). Chosen as the most that fits
  the phone's frame budget, see `net.b12n.raylib-ios.scenes.doom`."
  180)

(def strips "The original's SPRITE-STRIPS (line 271)." 12)

(def capacity "Rects the buffer holds." 1024)

(def ^:private grid (int-array m/cells))
(def ^:private zbuf (double-array cols))
(def rect-buf "The buffer `build!` fills: `x y w h colour` for each rect." (int-array (* 5 capacity)))

(defn zbuffer
  "The wall distance of column `i` after the last `build!`."
  [i]
  (aget zbuf i))

(defn cast!
  "The DDA for column `i` (lines 181-219) from `pos-x`, `pos-y` along `dir`
  and `plane`. Writes the perpendicular distance into `zbuf` and returns
  `(tile - 1) * 2 + side`: the wall style 1 to 4, and 0 for a vertical face, 1 for
  a horizontal one."
  [i pos-x pos-y dir-x dir-y plane-x plane-y]
  (let [#?@(:jolt [^int/1 g grid ^double/1 zb zbuf]
            :default [^"[I" g grid ^"[D" zb zbuf])
        camera (- (/ (* 2.0 i) cols) 1.0)
        rdx (+ dir-x (* plane-x camera))
        rdy (+ dir-y (* plane-y camera))
        ddx (if (== rdx 0.0) 1e30 (Math/abs (/ 1.0 rdx)))
        ddy (if (== rdy 0.0) 1e30 (Math/abs (/ 1.0 rdy)))
        stepx (if (neg? rdx) -1 1)
        stepy (if (neg? rdy) (- m/map-w) m/map-w)
        ix (int pos-x)
        iy (int pos-y)]
    (loop [cell (+ (* iy m/map-w) ix)
           sdx (if (neg? rdx)
                 (* (- pos-x ix) ddx)
                 (* (- (+ ix 1.0) pos-x) ddx))
           sdy (if (neg? rdy)
                 (* (- pos-y iy) ddy)
                 (* (- (+ iy 1.0) pos-y) ddy))
           side 0]
      (let [tile (aget g cell)]
        (if (pos? tile)
          (let [d (if (== side 0) (- sdx ddx) (- sdy ddy))]
            (aset zb i (double (if (< d 0.0001) 0.0001 d)))
            (+ (* 2 (dec tile)) side))
          (if (< sdx sdy)
            (recur (+ cell stepx) (+ sdx ddx) sdy 0)
            (recur (+ cell stepy) sdx (+ sdy ddy) 1)))))))

(defn cast-column
  "Column `i`'s hit for `state` as `{:dist :tile :side}`, the original's
  `cast-column` less the texture coordinate."
  [i {:keys [pos-x pos-y dir-x dir-y plane-x plane-y]}]
  (let [k (cast! i pos-x pos-y dir-x dir-y plane-x plane-y)]
    {:dist (aget zbuf i)
     :tile (inc (bit-shift-right k 1))
     :side (bit-and k 1)}))

(defn- put!
  "Append the rect `x0 y0 w h` with palette index `colour` at `n` and return
  the new count, or `n` when the buffer is full. It cannot fill: a frame is
  at most 2 + 180 + 6 x 36 + 1 = 399 rects of 1024."
  [n x0 y0 w h colour]
  (let [#?@(:jolt [^int/1 b rect-buf] :default [^"[I" b rect-buf])]
    (if (< n capacity)
      (let [j (* 5 n)]
        (aset b j x0)
        (aset b (+ j 1) y0)
        (aset b (+ j 2) w)
        (aset b (+ j 3) h)
        (aset b (+ j 4) colour)
        (inc n))
      n)))

(defn- col-x
  "The x pixel where column `i` starts in a view `vw` wide at `vx`."
  [i vx colw]
  (+ vx (int (+ 0.5 (* i colw)))))

(defn- walls!
  "The wall pass: cast every column, then append the merged rects. A run of
  columns is open from `rx` (its first pixel) until a column whose top, bottom or
  colour differs."
  [n {:keys [pos-x pos-y dir-x dir-y plane-x plane-y]} vx vy vw vh]
  (let [#?@(:jolt [^double/1 zb zbuf] :default [^"[D" zb zbuf])
        colw (/ (double vw) cols)
        half (+ vy (* 0.5 vh))
        top-limit (double vy)
        bot-limit (double (+ vy vh))
        x-end (+ vx vw)]
    (loop [i 0
           n n
           rx -1
           rt 0
           rb 0
           rc 0]
      (if (< i cols)
        (let [k (cast! i pos-x pos-y dir-x dir-y plane-x plane-y)
              dist (aget zb i)
              colour (+ (* (bit-shift-right k 1) 256) (m/shade dist (bit-and k 1)))
              hl (* 0.5 (/ vh dist))
              t (- half hl)
              b (+ half hl)
              top (if (< t top-limit) vy (int t))
              bot (if (> b bot-limit) (+ vy vh) (int b))]
          (if (and (== top rt) (== bot rb) (== colour rc) (>= rx 0))
            (recur (inc i) n rx rt rb rc)
            (let [xi (col-x i vx colw)]
              (recur (inc i)
                     (if (and (>= rx 0) (< rt rb)) (put! n rx rt (- xi rx) (- rb rt) rc) n)
                     xi top bot colour))))
        (if (and (>= rx 0) (< rt rb))
          (put! n rx rt (- x-end rx) (- rb rt) rc)
          n)))))

;; --- sprites -----------------------------------------------------------------

(defn visible-imps
  "Living imps in camera space, furthest first, as `{:tx :ty}` (lines 273-286):
  `ty` is the depth along the heading and `tx` the sideways offset, and an imp
  nearer than 0.25 is not drawn."
  [{:keys [pos-x pos-y dir-x dir-y plane-x plane-y imps]}]
  (let [inv-det (/ 1.0 (- (* plane-x dir-y) (* dir-x plane-y)))]
    (->> imps
         (filter :alive)
         (map (fn [{:keys [x y]}]
                (let [sx (- x pos-x)
                      sy (- y pos-y)]
                  {:tx (* inv-det (- (* dir-y sx) (* dir-x sy)))
                   :ty (* inv-det (+ (* (- plane-y) sx) (* plane-x sy)))})))
         (filter #(> (:ty %) 0.25))
         (sort-by :ty >))))

(def ^:private profiles
  "For each of the 12 strips, `[body-top body-bottom head-top head-bottom eye-top
  eye-bottom]` as fractions of the sprite's height, nil where the strip shows no
  such part. The imp's texel (lines 136-142), a 64 by 64 tile, is an eye where
  `(x - 24)^2 + (y - 16)^2 < 9` or `(x - 40)^2 + (y - 16)^2 < 9`, else a head where
  `(x - 32)^2 + (y - 18)^2 < 150` (`head` there is `(x - 32)^2 + (y - 18)^2`),
  else a body where `(x - 32)^2 + ((y - 40) / 1.3)^2 < 420`, else clear. A strip
  takes the column of the tile at its middle, `64 (k + 0.5) / 12`, and the part
  it shows there is a vertical span of that column."
  (vec (for [k (range strips)]
         (let [xm (* 64.0 (/ (+ k 0.5) strips))
               dx (- xm 32.0)
               d2 (* dx dx)
               span (fn [centre half-extent lo hi]
                      [(/ (max lo (- centre half-extent)) 64.0) (/ (min hi (+ centre half-extent)) 64.0)])
               body (when (< d2 420.0) (span 40.0 (* 1.3 (Math/sqrt (- 420.0 d2))) 0.0 64.0))
               head (when (< d2 150.0) (span 18.0 (Math/sqrt (- 150.0 d2)) -1000.0 1000.0))
               eye (some (fn [ex]
                           (let [de (- xm ex)
                                 e2 (* de de)]
                             (when (< e2 9.0) (span 16.0 (Math/sqrt (- 9.0 e2)) -1000.0 1000.0))))
                         [24.0 40.0])]
           [(first body) (second body) (first head) (second head) (first eye) (second eye)]))))

(def ^:private strip-start (vec (for [k (range strips)] (/ (double k) strips))))
(def ^:private strip-end (vec (for [k (range strips)] (/ (+ k 1.0) strips))))

(defn- part!
  "Append the part from `fa` to `fb` (fractions of a sprite `h` tall from `y0`,
  nil for none) across `xl` to `xr`, cut to the view's `vy` to `vy2`."
  [n xl xr y0 h fa fb colour vy vy2]
  (if fa
    (let [ya (int (+ y0 (* h fa)))
          yb (int (+ y0 (* h fb)))
          ya (if (< ya vy) vy ya)
          yb (if (> yb vy2) vy2 yb)]
      (if (< ya yb) (put! n xl ya (- xr xl) (- yb ya) colour) n))
    n))

(defn- sprite!
  "Append one imp: for each of its 12 strips whose middle column is nearer than
  that column's wall, a body rect, a head rect and an eye rect."
  [n tx ty vx vy vw vh]
  (let [#?@(:jolt [^double/1 zb zbuf] :default [^"[D" zb zbuf])
        colw (/ (double vw) cols)
        vx2 (+ vx vw)
        vy2 (+ vy vh)
        screen-x (* (/ cols 2.0) (+ 1.0 (/ tx ty)))
        size (/ cols ty)
        w2 (/ size 2.0)
        left (- screen-x w2)
        h (/ vh ty)
        y0 (- (+ vy (* 0.5 vh)) (* 0.5 h))
        s (m/shade ty 0)
        body (+ m/imp-base s)
        head (+ m/imp-base 256 s)
        eye (+ m/imp-base 512 s)]
    (if (< (max 0 (int left)) (min cols (int (+ screen-x w2))))
      (loop [k 0
             n n]
        (if (< k strips)
          (let [a (+ left (* size (nth strip-start k)))
                b (+ left (* size (nth strip-end k)))
                mid (int (/ (+ a b) 2.0))]
            (if (and (>= mid 0) (< mid cols) (< ty (aget zb mid)))
              (let [xl0 (int (+ vx 0.5 (* a colw)))
                    xr0 (int (+ vx 0.5 (* b colw)))
                    xl (if (< xl0 vx) vx xl0)
                    xr (let [r (if (> xr0 xl0) xr0 (inc xl0))] (if (> r vx2) vx2 r))]
                (if (< xl xr)
                  (let [p (nth profiles k)]
                    (recur (inc k)
                           (-> n
                               (part! xl xr y0 h (nth p 0) (nth p 1) body vy vy2)
                               (part! xl xr y0 h (nth p 2) (nth p 3) head vy vy2)
                               (part! xl xr y0 h (nth p 4) (nth p 5) eye vy vy2))))
                  (recur (inc k) n)))
              (recur (inc k) n)))
          n))
      n)))

(defn build!
  "Fill `rect-buf` for `state` in the view `[vx vy vw vh]` (ints) and return how
  many rects it holds: the ceiling, the floor, the merged wall rects, the imps
  furthest first and, while `:flash` is above zero, the muzzle flash over all."
  [state vx vy vw vh]
  (let [mid (+ vy (quot vh 2))
        n (put! 0 vx vy vw (- mid vy) m/ceiling-index)
        n (put! n vx mid vw (- (+ vy vh) mid) m/floor-index)
        n (walls! n state vx vy vw vh)
        n (reduce (fn [n {:keys [tx ty]}] (sprite! n tx ty vx vy vw vh)) n (visible-imps state))]
    (if (pos? (:flash state))
      (put! n vx vy vw vh m/flash-index)
      n)))

(defn rects
  "The first `n` rects of `rect-buf` as `[x y w h colour]` longs with the colour
  packed, for tests."
  [n]
  (mapv (fn [i]
          (let [j (* 5 i)]
            [(long (aget rect-buf j)) (long (aget rect-buf (+ j 1))) (long (aget rect-buf (+ j 2)))
             (long (aget rect-buf (+ j 3))) (nth m/palette (aget rect-buf (+ j 4)))]))
        (range n)))
