(ns raylib.scenes.pacman.maze
  "The maze of `pacman` and the rule every entity in it moves by. Ported from
  raylib-jlt's `pacman`, which came from Michiel Borkent's example in
  babashka/ffi (MIT).

  Everything here is in TILE units: a tile is one unit square, so an entity at
  `[9.5 15.5]` is at the centre of tile `[9 15]`. The scene scales tiles to the
  screen and nothing in this namespace knows about pixels.")

(def maze
  "The twenty-one rows of the original, byte for byte. `#` wall, `.` dot, `o`
  power pellet, `-` the ghost-house door, `P` Pac-Man's start, `G` a ghost's
  start. Row 10 is open at both ends: those are the side tunnels."
  ["###################"
   "#........#........#"
   "#o##.###.#.###.##o#"
   "#.................#"
   "#.##.#.#####.#.##.#"
   "#....#...#...#....#"
   "####.###.#.###.####"
   "#....#.......#....#"
   "#.##.#.##-##.#.##.#"
   "#.##...#GGG#...##.#"
   ".....#.#GGG#.#....."
   "#.##...#####...##.#"
   "#.##.#...#...#.##.#"
   "#....#.#####.#....#"
   "####.#...#...#.####"
   "#o.......P.......o#"
   "#.###.#######.###.#"
   "#...#....#....#...#"
   "###.#.##.#.##.#.###"
   "#.................#"
   "###################"])

(def width "Columns, which wrap through the tunnel." (count (first maze)))
(def height "Rows." (count maze))

(defn tile-of
  "The tile index a coordinate falls in."
  [v]
  (long (Math/floor (double v))))

(defn tile-at
  "The maze character at `x`, `y`. `x` wraps, which is the side tunnel. Anything
  above or below the maze reads as wall."
  [x y]
  (if (or (< y 0) (>= y height))
    \#
    (nth (nth maze y) (mod x width))))

(defn wall?
  "Blocked for Pac-Man: walls and the ghost-house door."
  [x y]
  (let [c (tile-at x y)]
    (or (= \# c) (= \- c))))

(defn ghost-wall?
  "Blocked for a ghost: walls only, so a ghost can pass its own door."
  [x y]
  (= \# (tile-at x y)))

(defn- find-tile [ch]
  (first (for [y (range height)
               x (range width)
               :when (= ch (tile-at x y))]
           [x y])))

(def pac-start (or (find-tile \P) [9 15]))
(def door (or (find-tile \-) [9 8]))

(def door-exit
  "The tile just outside the door, where a ghost heads on its way out."
  [(first door) (dec (second door))])

(def house-tiles
  (vec (for [y (range height)
             x (range width)
             :when (= \G (tile-at x y))]
         [x y])))

(def house-slots
  "The top row of the house, one slot per ghost."
  (vec (filter #(= (second (first house-tiles)) (second %)) house-tiles)))

(defn initial-dots
  "Every tile that starts with a dot or a power pellet."
  []
  (into #{} (for [y (range height)
                  x (range width)
                  :when (#{\. \o} (tile-at x y))]
              [x y])))

(defn centre-of [[x y]] [(+ 0.5 x) (+ 0.5 y)])

(defn step-entity
  "Advance an entity by `speed` * `dt`, deciding its direction only at tile
  centres, because deciding anywhere else lets an entity drift into a wall.

  `entity` is `{:x :y :dx :dy}`. `decide` is `(fn [tx ty dx dy] -> [dx dy])`,
  called when the step reaches the centre of the current tile, and a blocked or
  zero choice stops there. `walls?` is the entity's own blocking predicate.
  Answers `{:x :y :dx :dy}`, with the distance left over after the turn spent
  along the new heading."
  [{:keys [x y dx dy]} {:keys [speed dt decide walls?]}]
  (let [dist (* speed dt)
        cx (+ (Math/floor x) 0.5)
        cy (+ (Math/floor y) 0.5)
        to-centre (+ (* dx (- cx x)) (* dy (- cy y)))]
    (if (and (>= to-centre -1.0e-9) (<= to-centre dist))
      (let [tx (tile-of cx)
            ty (tile-of cy)
            [ndx ndy] (decide tx ty dx dy)
            leftover (- dist to-centre)]
        (if (or (and (zero? ndx) (zero? ndy))
                (walls? (+ tx ndx) (+ ty ndy)))
          {:x cx
           :y cy
           :dx ndx
           :dy ndy}
          {:x (mod (+ cx (* ndx leftover)) width)
           :y (+ cy (* ndy leftover))
           :dx ndx
           :dy ndy}))
      {:x (mod (+ x (* dx dist)) width)
       :y (+ y (* dy dist))
       :dx dx
       :dy dy})))
