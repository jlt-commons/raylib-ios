(ns raylib.perlin
  "Perlin noise, as a pure function: stb_perlin's `fbm_noise3` and raylib's
  `GenImagePerlinNoise` grey, in Clojure.

  The noise is a port of Sean Barrett's stb_perlin.h v0.5 (public domain, or
  MIT at your choice), as shipped in raylib 6.0's src/external. The permutation
  and gradient tables, the ease curve, the lerp and the gradient selection are
  copied from it. The `perlin-grey` mapping follows raylib's `GenImagePerlinNoise`
  in rtextures.c (zlib licence, Ramon Santamaria and contributors), including its
  aspect compensation and the byte truncation. This is an altered source version
  of both: it is written in Clojure and not the original C.

  stb_perlin computes in `float`, and so does this. Every intermediate is rounded
  to 32 bits (`f32`) at the point the C rounds it, because a double carries
  digits the C throws away, and those digits move the grey byte of an occasional
  texel. Rounding after each operation reproduces IEEE single arithmetic exactly,
  since a double holds the exact result of one add, subtract, multiply or divide
  of two floats. Compiled with fused multiply-add the C itself differs from this
  in about the seventh digit, so the reference values were made with
  `-ffp-contract=off`.

  No FFI and no raylib: the namespace loads on the JVM and under jolt.")

;; stb_perlin.h:96-132, `stb__perlin_randtab`. The C lists 256 values (98-113) and then
;; repeats them (116-131) so that `r0 + y0` needs no mask; `(into v v)`
;; builds the same 512.
(def ^:private randtab
  (let [t [23 125 161 52 103 117 70 37 247 101 203 169 124 126 44 123
           152 238 145 45 171 114 253 10 192 136 4 157 249 30 35 72
           175 63 77 90 181 16 96 111 133 104 75 162 93 56 66 240
           8 50 84 229 49 210 173 239 141 1 87 18 2 198 143 57
           225 160 58 217 168 206 245 204 199 6 73 60 20 230 211 233
           94 200 88 9 74 155 33 15 219 130 226 202 83 236 42 172
           165 218 55 222 46 107 98 154 109 67 196 178 127 158 13 243
           65 79 166 248 25 224 115 80 68 51 184 128 232 208 151 122
           26 212 105 43 179 213 235 148 146 89 14 195 28 78 112 76
           250 47 24 251 140 108 186 190 228 170 183 139 39 188 244 246
           132 48 119 144 180 138 134 193 82 182 120 121 86 220 209 3
           91 241 149 85 205 150 113 216 31 100 41 164 177 214 153 231
           38 71 185 174 97 201 29 95 7 92 54 254 191 118 34 221
           131 11 163 99 234 81 227 147 156 176 17 142 69 12 110 62
           27 255 0 194 59 116 242 252 19 21 187 53 207 129 64 135
           61 40 167 237 102 223 106 159 197 189 215 137 36 32 22 5]]
    (into t t)))

;; stb_perlin.h:141-177, `stb__perlin_randtab_grad_idx`, doubled the same way.
(def ^:private grad-idx
  (let [t [7 9 5 0 11 1 6 9 3 9 11 1 8 10 4 7
           8 6 1 5 3 10 9 10 0 8 4 1 5 2 7 8
           7 11 9 10 1 0 4 7 5 0 11 6 1 4 2 8
           8 10 4 9 9 2 5 7 9 1 7 2 2 6 11 5
           5 4 6 9 0 1 1 0 7 6 9 8 4 10 3 1
           2 8 8 9 10 11 5 11 11 2 6 10 3 4 2 4
           9 10 3 2 6 3 6 10 5 3 4 10 11 2 9 11
           1 11 10 4 9 4 11 0 4 11 4 0 0 0 7 6
           10 4 1 3 11 5 3 4 2 9 1 3 0 1 8 0
           6 7 8 7 0 4 6 10 8 2 3 11 11 8 0 2
           4 8 3 0 0 10 6 1 2 2 4 5 6 0 1 3
           11 9 5 5 9 6 9 8 3 8 1 8 9 6 9 11
           10 7 5 6 5 9 1 3 7 0 2 10 11 2 6 1
           3 11 7 7 2 1 7 3 0 8 1 1 5 0 6 10
           11 11 0 2 7 0 10 8 3 5 7 1 11 1 0 7
           9 0 11 5 10 3 2 3 5 9 7 9 8 4 6 5]]
    (into t t)))

;; stb_perlin.h:193-206, `basis[12]` (the fourth column of the C array is unused).
(def ^:private basis
  [[1 1 0] [-1 1 0] [1 -1 0] [-1 -1 0]
   [1 0 1] [-1 0 1] [1 0 -1] [-1 0 -1]
   [0 1 1] [0 -1 1] [0 1 -1] [0 -1 -1]])

(defn- f32
  "Round the double `x` to the nearest 32-bit float, answered as a double.

  Veltkamp's splitting: multiplying by 2^29+1 and subtracting back leaves the
  top 24 bits of the mantissa, rounded to nearest, which is what a float holds.
  It is three flops. The obvious route, a round trip through `Float/floatToIntBits`,
  costs about 0.5 microseconds a call under jolt (`(float x)` leaves a double
  alone there), and noise3 rounds some sixty times per octave. Checked against
  the Float round trip on 1.6 million sums, products, quotients and differences
  of random floats, ties included, with no mismatch (checked offline; the test
  runs a smaller sample).

  Not for a result below 2^-126 (floats go subnormal and keep fewer bits) or
  above the float range near 2^128 (a float overflows to infinity there, and
  this does not); infinity and NaN in give NaN out. Noise values stay far
  inside both limits."
  [x]
  (let [c (* x 536870913.0)]
    (- c (- c x))))

(defn- lerp
  "stb_perlin.h:179 `stb__perlin_lerp`: a + (b-a)*t."
  [a b t]
  (f32 (+ a (f32 (* (f32 (- b a)) t)))))

(defn- fastfloor
  "stb_perlin.h:184 `stb__perlin_fastfloor`: truncate, then step down when the
  truncation rounded up (a negative non-integer)."
  [a]
  (let [ai (long a)]
    (if (< a ai) (dec ai) ai)))

(defn- ease
  "stb_perlin.h:231 `stb__perlin_ease`: (((a*6-15)*a + 10) * a * a * a), left to
  right, rounding to float after each operator."
  [a]
  (let [t (f32 (- (f32 (* a 6.0)) 15.0))
        t (f32 (+ (f32 (* t a)) 10.0))
        t (f32 (* t a))
        t (f32 (* t a))]
    (f32 (* t a))))

(defn- grad
  "stb_perlin.h:191 `stb__perlin_grad`: the dot product with basis `idx`. The
  basis entries are 0 or +-1, so each product is exact and only the two sums
  round."
  [idx x y z]
  (let [[gx gy gz] (nth basis idx)]
    (f32 (+ (f32 (+ (* gx x) (* gy y))) (* gz z)))))

(defn noise3
  "stb_perlin.h:213 `stb_perlin_noise3_internal` with x_wrap = y_wrap = z_wrap = 0
  and seed 0, which is `stb_perlin_noise3(x, y, z, 0, 0, 0)`. A double in -1..1,
  holding a float's value."
  ([x y z] (noise3 x y z 0))
  ([x y z seed]
   (let [x (f32 x) y (f32 y) z (f32 z)
         px (fastfloor x) py (fastfloor y) pz (fastfloor z)
         ;; wrap 0 gives mask (0-1)&255 = 255
         x0 (bit-and px 255) x1 (bit-and (inc px) 255)
         y0 (bit-and py 255) y1 (bit-and (inc py) 255)
         z0 (bit-and pz 255) z1 (bit-and (inc pz) 255)
         x (f32 (- x px)) u (ease x)
         y (f32 (- y py)) v (ease y)
         z (f32 (- z pz)) w (ease z)
         x-1 (f32 (- x 1.0)) y-1 (f32 (- y 1.0)) z-1 (f32 (- z 1.0))
         r0 (nth randtab (+ x0 seed))
         r1 (nth randtab (+ x1 seed))
         r00 (nth randtab (+ r0 y0))
         r01 (nth randtab (+ r0 y1))
         r10 (nth randtab (+ r1 y0))
         r11 (nth randtab (+ r1 y1))
         n000 (grad (nth grad-idx (+ r00 z0)) x y z)
         n001 (grad (nth grad-idx (+ r00 z1)) x y z-1)
         n010 (grad (nth grad-idx (+ r01 z0)) x y-1 z)
         n011 (grad (nth grad-idx (+ r01 z1)) x y-1 z-1)
         n100 (grad (nth grad-idx (+ r10 z0)) x-1 y z)
         n101 (grad (nth grad-idx (+ r10 z1)) x-1 y z-1)
         n110 (grad (nth grad-idx (+ r11 z0)) x-1 y-1 z)
         n111 (grad (nth grad-idx (+ r11 z1)) x-1 y-1 z-1)
         n00 (lerp n000 n001 w)
         n01 (lerp n010 n011 w)
         n10 (lerp n100 n101 w)
         n11 (lerp n110 n111 w)
         n0 (lerp n00 n01 v)
         n1 (lerp n10 n11 v)]
     (lerp n0 n1 u))))

(defn fbm3
  "stb_perlin.h:295 `stb_perlin_fbm_noise3`: `octaves` layers of noise3, each at
  `lacunarity` times the frequency and `gain` times the amplitude of the last.
  Octave i uses seed i, as the C does with `(unsigned char)i`."
  [x y z lacunarity gain octaves]
  (let [x (f32 x) y (f32 y) z (f32 z)
        lacunarity (f32 lacunarity) gain (f32 gain)]
    (loop [i 0 frequency 1.0 amplitude 1.0 sum 0.0]
      (if (< i octaves)
        (let [n (noise3 (f32 (* x frequency)) (f32 (* y frequency)) (f32 (* z frequency))
                        (bit-and i 255))]
          (recur (inc i)
                 (f32 (* frequency lacunarity))
                 (f32 (* amplitude gain))
                 (f32 (+ sum (f32 (* n amplitude))))))
        sum))))

(defn perlin-grey
  "rtextures.c:992-1030 `GenImagePerlinNoise`: the grey byte (0..255) of texel
  (`x`, `y`) in a `w` by `h` image with offsets `offset-x`, `offset-y` and
  `scale`. The fbm is called with z 1, lacunarity 2, gain 0.5, 6 octaves; the
  value is clamped to -1..1, mapped to 0..1, and truncated (not rounded) to a
  byte after scaling by 255."
  [w h offset-x offset-y scale x y]
  (let [scale (f32 scale)
        aspect (f32 (/ (f32 w) (f32 h)))
        nx (f32 (* (f32 (+ x offset-x)) (f32 (/ scale (f32 w)))))
        ny (f32 (* (f32 (+ y offset-y)) (f32 (/ scale (f32 h)))))
        nx (if (> w h) (f32 (* nx aspect)) nx)
        ny (if (> w h) ny (f32 (/ ny aspect)))
        p (fbm3 nx ny 1.0 2.0 0.5 6)
        p (-> p (max -1.0) (min 1.0))
        np (f32 (/ (f32 (+ p 1.0)) 2.0))]
    (long (f32 (* np 255.0)))))
