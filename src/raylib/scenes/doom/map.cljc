(ns raylib.scenes.doom.map
  "The level, the shading and the colours of `raylib.scenes.doom`, as raylib-jlt's
  `doom` has them. Pure data and two small functions, so the ray caster
  (`raylib.scenes.doom.ray`) and the scene can both read them.

  The sixteen rows are the original's (lines 51-67). A digit is a wall style, a
  dot is open floor, and anything off the map is wall style 1 so a ray that
  escapes still ends (`wall-at`, lines 79-85). The original textures its walls
  from a procedural atlas (lines 95-150). Here a wall is one flat colour per
  style, the one the original's own minimap uses for that style
  (`wall-color`, lines 461-467), multiplied by the original's distance and side
  shade (`shade`, lines 221-226) exactly as the vertex colour multiplies the
  texel. Packed colours are raylib's `rgba`: red, green << 8, blue << 16 and
  alpha << 24.")

(def level
  "The original's sixteen rows (lines 51-67)."
  ["1111111111111111"
   "1..............1"
   "1..2222...44...1"
   "1..2......4....1"
   "1..2..33..4....1"
   "1.....3........1"
   "1.....3...222..1"
   "1..............1"
   "1...44444......1"
   "1.......4...33.1"
   "1.......4......1"
   "1..333..4..22..1"
   "1....3.....2...1"
   "1....3.........1"
   "1..............1"
   "1111111111111111"])

(def map-w "The original's MAP-W (line 69)." (count (first level)))
(def map-h "The original's MAP-H (line 70)." (count level))

(def imp-spawns "The original's six imps (line 152)."
  [[8.5 2.5] [12.5 6.5] [5.5 12.5] [11.5 11.5] [13.5 3.5] [4.5 8.5]])

(def move-speed "The original's MOVE-SPEED (line 340), cells a second." 3.4)
(def imp-speed "The original's IMP-SPEED (line 375), cells a second." 0.9)
(def bite-range "How near an imp bites (line 398)." 0.9)
(def fire-cone "The dot of a shot's cone (line 366)." 0.985)
(def flash-time "How long a shot's muzzle flash lasts, seconds (line 357)." 0.06)
(def plane-length "The original's camera plane, half the field of view (line 159)." 0.66)

(def cells
  "The grid as a vector of wall styles, row by row: 0 for floor (lines 71-77)."
  (vec (for [row level
             c row]
         (if (= \. c) 0 (- (int c) (int \0))))))

(defn wall-at
  "The wall style at cell `x`, `y`: 0 for open floor, 1 outside the map (lines 79-85)."
  [x y]
  (if (or (< x 0) (< y 0) (>= x map-w) (>= y map-h))
    1
    (nth cells (+ (* y map-w) x))))

(defn shade
  "Distance and side shading, the 30 to 255 factor multiplied into the texel
  (lines 221-226): `f = 1 / (1 + 0.11 dist^2)`, times 0.72 on a horizontal face
  (side 1), then `255 * (0.12 + 0.95 f)` truncated and held to 30..255."
  [dist side]
  (let [f (/ 1.0 (+ 1.0 (* 0.11 dist dist)))
        f (if (zero? side) f (* f 0.72))]
    (max 30 (min 255 (int (* 255 (+ 0.12 (* 0.95 f))))))))

(defn pack
  "raylib's `rgba` of channels 0..255."
  [r g b a]
  (bit-or (long r) (bit-shift-left (long g) 8) (bit-shift-left (long b) 16) (bit-shift-left (long a) 24)))

(defn unpack
  "The `[r g b a]` of a packed colour."
  [c]
  (let [c (long c)]
    [(bit-and c 255) (bit-and (bit-shift-right c 8) 255)
     (bit-and (bit-shift-right c 16) 255) (bit-and (bit-shift-right c 24) 255)]))

(def wall-colours
  "The base colour of wall style 1 to 4 as `[r g b]`: the original's minimap
  colours (lines 461-467)."
  {1 [150 70 55]
   2 [120 120 130]
   3 [90 110 150]
   4 [70 180 110]})

(defn lit
  "`[r g b]` multiplied by shade `s` over 255, as a texel by a vertex colour,
  packed opaque."
  [[r g b] s]
  (pack (quot (* r s) 255) (quot (* g s) 255) (quot (* b s) 255) 255))

(def wall-packed
  "Every lit wall colour: index `(tile - 1) * 256 + s` for tile 1 to 4 and
  shade 0 to 255."
  (vec (for [t (range 1 5)
             s (range 256)]
         (lit (get wall-colours t) s))))

(def imp-body "The imp texel's body colour, tile 4 (line 141)." [112 52 40])
(def imp-head "The imp texel's head colour (line 140)." [150 74 52])
(def imp-eye "The imp texel's eye colour (line 139)." [255 226 92])

(def imp-packed
  "Every lit imp colour: index `part * 256 + s` for part 0 body, 1 head, 2 eye."
  (vec (for [c [imp-body imp-head imp-eye]
             s (range 256)]
         (lit c s))))

(def ceiling "The original's CEILING (line 457)." (pack 28 26 32 255))
(def floor "The original's FLOOR (line 458)." (pack 48 42 38 255))
(def muzzle-flash "The original's MUZZLE-FLASH (line 459)." (pack 255 220 140 40))

(def palette
  "Every colour a rect of the picture can have, packed, so the rect buffer can
  hold a small integer for a colour: the 1024 lit walls (`wall-packed`, index
  `(tile - 1) * 256 + s`), then the 768 lit imp parts (`imp-packed`, 1024 and
  up), then the ceiling, the floor and the muzzle flash."
  (vec (concat wall-packed imp-packed [ceiling floor muzzle-flash])))

(def imp-base "The palette index of the imp colours." 1024)
(def ceiling-index "The palette index of `ceiling`." 1792)
(def floor-index "The palette index of `floor`." 1793)
(def flash-index "The palette index of `muzzle-flash`." 1794)
