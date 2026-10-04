(ns raylib.texel
  "Packed RGBA8 texels and raylib's integer image rasterisers, as pure
  functions over a grid.

  A grid is `{:w :h :px}` where `:px` is a vector of `w*h` packed longs in row
  order. A texel packs as `r | g<<8 | b<<16 | a<<24`, which is the byte order of
  `RL_PIXELFORMAT_UNCOMPRESSED_R8G8B8A8` on a little-endian machine, so the host
  can upload the vector as it stands.

  The `draw-*` functions mirror `ImageDraw*` from raylib 6.0's `rtextures.c`
  integer loop for integer loop, quirks included, so a texture painted here
  matches one painted by raylib. Each takes a grid and returns a new one, and
  each clips at the edges the way `ImageDrawPixel` does. A colour is `[r g b a]`.

  Each `draw-*` has a transient twin, `draw-*!`, for painting many shapes into
  one grid: it takes a grid whose `:px` is a transient vector (`transient-grid`)
  and answers one the same way, to be passed on and finally `persistent-grid`ed.
  Use the answer, not the argument, as `assoc!` allows. The persistent `draw-*`
  are wrappers around the twins, so there is one copy of each loop.

  No FFI and no raylib: the namespace loads on the JVM and under jolt.")

(defn pack4
  "`pack` for four separate components (each 0..255), with no vector built.
  For a pixel fn that runs once per texel."
  [r g b a]
  (bit-or r (bit-shift-left g 8) (bit-shift-left b 16) (bit-shift-left a 24)))

(defn pack
  "Pack `[r g b a]` (each 0..255) into one long, `r | g<<8 | b<<16 | a<<24`."
  [[r g b a]]
  (pack4 r g b a))

(defn unpack
  "The inverse of `pack`: a packed long to `[r g b a]`."
  [n]
  [(bit-and n 0xFF)
   (bit-and (bit-shift-right n 8) 0xFF)
   (bit-and (bit-shift-right n 16) 0xFF)
   (bit-and (bit-shift-right n 24) 0xFF)])

(defn grid
  "A `w` by `h` grid with every texel set to colour `fill`."
  [w h fill]
  {:w w
   :h h
   :px (vec (repeat (* w h) (pack fill)))})

(defn pixel-of
  "A function `(fn [x y] packed)` reading texels from grid `g`. It does not
  clip: `x` and `y` must be inside the grid."
  [g]
  (let [px (:px g)
        w  (:w g)]
    (fn [x y] (nth px (+ (* y w) x)))))

(defn transient-grid
  "`g` with its `:px` made transient, for the `draw-*!` fns."
  [g]
  (update g :px transient))

(defn persistent-grid
  "The inverse of `transient-grid`. The transient grid is spent afterwards."
  [g]
  (update g :px persistent!))

(defn draw-pixel!
  "`draw-pixel` over a transient grid."
  [g x y c]
  (if (or (neg? x) (>= x (:w g)) (neg? y) (>= y (:h g)))
    g
    (assoc g :px (assoc! (:px g) (+ (* y (:w g)) x) (pack c)))))

(defn draw-pixel
  "`ImageDrawPixel` (rtextures.c:3345): set one texel, ignoring anything outside
  the grid."
  [g x y c]
  (persistent-grid (draw-pixel! (transient-grid g) x y c)))

(defn draw-line!
  "`draw-line` over a transient grid."
  [g x0 y0 x1 y1 c]
  (let [short0   (- y1 y0)
        long0    (- x1 x0)
        y-longer (> (abs short0) (abs long0))
        short-len (if y-longer long0 short0)
        long-len  (if y-longer short0 long0)
        end-val  long-len
        sgn      (if (neg? long-len) -1 1)
        long-abs (abs long-len)
        dec-inc  (if (zero? long-abs)
                   0
                   (quot (bit-shift-left short-len 16) long-abs))]
    (loop [g g, i 0, j 0]
      (if (= i end-val)
        g
        (let [off (bit-shift-right j 16)]
          (recur (if y-longer
                   (draw-pixel! g (+ x0 off) (+ y0 i) c)
                   (draw-pixel! g (+ x0 i) (+ y0 off) c))
                 (+ i sgn)
                 (+ j dec-inc)))))))

(defn draw-line
  "`ImageDrawLine` (rtextures.c:3491). The longer axis steps one texel at a time
  and the shorter advances by a 16.16 fixed-point slope, `(short << 16) / long`
  truncated toward zero, then `>> 16` (arithmetic). The loop is `i != endVal`,
  so the far endpoint is never lit and a zero-length line lights nothing."
  [g x0 y0 x1 y1 c]
  (persistent-grid (draw-line! (transient-grid g) x0 y0 x1 y1 c)))

(defn draw-rect!
  "`draw-rect` over a transient grid."
  [g x y w h c]
  (let [gw (:w g)
        gh (:h g)
        [x w] (if (neg? x) [0 (+ w x)] [x w])
        [y h] (if (neg? y) [0 (+ h y)] [y h])
        w (max w 0)
        h (max h 0)
        w (if (>= (+ x w) gw) (- gw x) w)
        h (if (>= (+ y h) gh) (- gh y) h)]
    (if (or (>= x gw) (>= y gh) (<= (+ x w) 0) (<= (+ y h) 0))
      g
      (let [v (pack c)
            row (max w 1)]
        (assoc g :px
               (reduce (fn [px idx] (assoc! px idx v))
                       (:px g)
                       (concat
                        (for [xx (range x (+ x row))] (+ (* y gw) xx))
                        (for [yy (range (inc y) (+ y h))
                              xx (range x (+ x w))]
                          (+ (* yy gw) xx)))))))))

(defn draw-rect
  "`ImageDrawRectangle` via `ImageDrawRectangleRec` (rtextures.c:3675, 3687).
  A negative origin eats into the size, the size clamps to the grid, and a rect
  wholly outside lights nothing. Past those checks the C draws the first texel
  (3708), doubles it along the row to `width` texels (3719-3722, whatever the
  height), then copies that row down for rows 1 .. height-1 (3726-3729). So a
  zero-height rect lights one row of `width` texels, and a zero-width rect
  lights just its first texel. A rect with `x+w <= 0` or `y+h <= 0` after
  clipping returns early and lights nothing, so a zero height at y=0 does too."
  [g x y w h c]
  (persistent-grid (draw-rect! (transient-grid g) x y w h c)))

(defn draw-circle!
  "`draw-circle` over a transient grid."
  [g cx cy r c]
  (loop [g g, x 0, y r, d (- 3 (* 2 r))]
    (if (< y x)
      g
      (let [g  (-> g
                   (draw-rect! (- cx x) (+ cy y) (* x 2) 1 c)
                   (draw-rect! (- cx x) (- cy y) (* x 2) 1 c)
                   (draw-rect! (- cx y) (+ cy x) (* y 2) 1 c)
                   (draw-rect! (- cx y) (- cy x) (* y 2) 1 c))
            x' (inc x)]
        (if (pos? d)
          (let [y' (dec y)]
            (recur g x' y' (+ d (* 4 (- x' y')) 10)))
          (recur g x' y (+ d (* 4 x') 6)))))))

(defn draw-circle
  "`ImageDrawCircleV` (rtextures.c:3635), which calls `ImageDrawCircle`
  (rtextures.c:3611). In raylib 6.0 it FILLS: the midpoint walk emits four
  one-row `ImageDrawRectangle` spans per step, each `2x` or `2y` wide from
  `centre - x`. A span of width `2x` covers `cx-x .. cx+x-1`, so the disc is one
  texel short on the right and the bottom, and width-0 spans still light one
  texel."
  [g cx cy r c]
  (persistent-grid (draw-circle! (transient-grid g) cx cy r c)))
