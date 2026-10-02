(ns raylib.scenes.split3d
  "Two players on a plane of cube trees, each seen by its own camera in half of
  the screen, ported from raylib-jlt's `camera_3d_split_screen` (zlib licence,
  from raylib's `core_3d_camera_split_screen`). The projection is in software,
  by `raylib.soft3d`.

  The original (camera_3d_split_screen.clj) draws, for each player, the same
  world into a 400 by 450 render texture on a sky blue clear: a 50 by 50 beige
  plane (lines 19-22), then for i and j in -5 to 5 (`COUNT` 5, lines 24-37) at
  `SPACING` 4 a lime cube of side 1 at (4i, 1.5, 4j) and a brown post of 0.25 by
  1 by 0.25 at (4i, 0.5, 4j), then the two players as cubes of side 1 (lines
  38-47): player one red at (0, 1, z1) and player two blue at (x2, 3, 0). Every
  cube is `draw-cube!`, so each is flat coloured and drawn here with
  `{:shade :flat}`. The cameras (lines 98-117) have fovy 45, perspective, up
  (0, 1, 0). Player one's eye is its cube, (0, 1, z1), looking at (0, 1, z1 + 3).
  Player two's is (x2, 3, 0) looking at (x2 + 3, 3, 0). Both start at -3
  (line 76). Each half gets a bar of 40 by 400 in white at alpha 204 with a size
  20 label at (10, 10): \"PLAYER1: W/S to move\" in MAROON and \"PLAYER2: UP/DOWN
  to move\" in DARKBLUE. The halves are blitted side by side with a 4 pixel
  LIGHTGRAY divider.

  Controls: W and S move player one along z by `10 * GetFrameTime()` (lines
  81-83), and UP and DOWN move player two along x the same way (lines 84-86).
  Neither is clamped. Here a relative thumb-stick in each half replaces the
  keys, one `raylib.stick` per half. A stick starts only on a fresh press (a
  finger that was not down the frame before) inside its own half, follows only
  that finger by its touch id (by nearness when the host gives none), and ends
  when that finger lifts or slides out of the half. It never adopts another
  finger, so a finger resting in a half never steers it, and the two halves are
  steered at once by two thumbs. A rotation of the phone drops both sticks.
  A key moves along one axis only, so the stick reads only the way the thumb
  has gone up or down from where it first touched, once it is further than
  `gesture/slop` from it: up the glass is W (or UP), forward along the
  player's own look, down is S (or DOWN). The speed is the original's 10 units
  a second times `:delta-seconds`, and the same whatever the distance. A tap
  moves nothing. The release position is never read.

  The render textures become two viewports from `raylib.soft3d/field`'s field,
  stacked in portrait (player one on top) and side by side in landscape, each
  with its own `view-proj` and its own scissor in the gallery's draw method.
  Each camera is fitted to its half by `raylib.soft3d/fit-camera` against the
  original's 400 by 450 half, so a half at least that wide keeps the original's
  45 degree fovy. The bar keeps the original's proportions, its height twice
  and its margin half of the label size, which is the original's 20 times the
  scale of the half and is cut back when the label would not fit.

  Limits of the painter, kept and disclosed rather than chased:
  - The plane is drawn by `raylib.soft3d/plane`, which drops a quad whole when a
    corner is behind the near plane. Both eyes are inside the original's 50 by 50
    plane, so the plane is cut to the part ahead of the eye, from a depth of
    half the one at which the bottom edge of the glass meets the ground, which
    is out of sight, and the picture is the original's. It is painted first,
    under everything, because the eyes are above it and every cube stands on or
    over it, whereas `raylib.soft3d/finish` would order it by its centre.
  - A cube with a corner behind the near plane is dropped whole, so a tree the
    player walks into vanishes instead of being clipped.
  - Cubes are painted whole, far to near by the distance of their centres from
    the eye, and `raylib.soft3d/finish` is not called (sorting every triangle
    cost more than the phone's budget), so where two overlap a face can paint
    over one a depth buffer would put in front.
  - The cubes a camera cannot see (wholly behind the eye or outside the sides of
    its view, by a circle round each tree) are not built, which leaves the
    picture unchanged.

  Dropped: the key text, whose words become \"PLAYER1: drag to move\" and
  \"PLAYER2: drag to move\". The state holds `:z1` and `:x2` (numbers), the
  sticks, the finger count, points and ids of the frame before, and `:screen`.
  Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def speed "The original's 10 units a second, for either player." 10.0)
(def start "Where both players start, the original's -3.0 (line 76)." -3.0)
(def count-trees "The original's COUNT: trees run -COUNT to COUNT each way." 5)
(def spacing "The original's SPACING between trees." 4.0)
(def plane-size "The original's plane, 50 by 50." 50.0)
(def half-w "The original's render texture width, fitted into a half." 400.0)
(def half-h "The original's render texture height, fitted into a half." 450.0)
(def original-aspect "The original's half, w/h." (/ 400.0 450.0))
(def divider-w "The gap between the halves. The original's." 4.0)
(def fovy "The original's fovy for both cameras." 45.0)

(def sky-colour "SKYBLUE." [102 191 255 255])
(def plane-colour "BEIGE." [211 176 131 255])
(def leaf-colour "LIME." [0 158 47 255])
(def post-colour "BROWN." [127 106 79 255])
(def player-colours "RED and BLUE." [[230 41 55 255] [0 121 241 255]])
(def bar-colour "White at alpha 204." [255 255 255 204])
(def label-colours "MAROON and DARKBLUE." [[190 33 55 255] [0 82 172 255]])
(def divider-colour "LIGHTGRAY." [200 200 200 255])
(def labels ["PLAYER1: drag to move" "PLAYER2: drag to move"])

(def trees
  "The `[x z]` of each of the original's 121 trees, in its loop order."
  (vec (for [i (range (- count-trees) (inc count-trees))
             j (range (- count-trees) (inc count-trees))]
         [(* i spacing) (* j spacing)])))

;; --- layout -------------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`: `raylib.soft3d/field`'s `:viewport`
  `[x y w h]` and `:aspect`, `:portrait?`, `:halves` (the two `[x y w h]`
  viewports, player one first) and the `:divider` rect between them."
  [metrics]
  (let [{[fx fy fw fh] :viewport
         :as field} (s3/field metrics)
        portrait? (< fw fh)
        [a b divider] (if portrait?
                        (let [hh (* 0.5 (- fh divider-w))]
                          [[fx fy fw hh]
                           [fx (+ fy hh divider-w) fw hh]
                           [fx (+ fy hh) fw divider-w]])
                        (let [hw (* 0.5 (- fw divider-w))]
                          [[fx fy hw fh]
                           [(+ fx hw divider-w) fy hw fh]
                           [(+ fx hw) fy divider-w fh]]))]
    (assoc field
           :portrait? portrait?
           :halves [a b]
           :divider divider)))

(defn dimensions
  "`geometry` plus the label bars. `:banners` has one per half, each `{:rect :s
  :x :y :size}` where `:rect` is the bar `[x y w h]` at the top of its half and
  `x`, `y` are the text's origin, and `:lines` holds the same so a test can
  check they fit. The size is the original's 20 times the half's scale (the
  smaller of its width over 400 and height over 450), cut back to 8 at the
  least so that the longer label covers no more than 0.92 of the half's width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [halves]
         :as geo} (geometry metrics)
        [_ _ hw hh] (first halves)
        widest (apply max (map #(measure % 100) labels))
        fit (int (/ (* 0.92 hw) (+ 0.5 (/ widest 100.0))))
        size (max 8 (min (int (* 20.0 (min (/ hw half-w) (/ hh half-h)))) fit))
        margin (quot size 2)
        banners (mapv (fn [[hx hy hw _] s]
                        {:rect [hx hy hw (* 2.0 size)]
                         :s s
                         :x (int (+ hx margin))
                         :y (int (+ hy margin))
                         :size size})
                      halves labels)]
    (assoc geo
           :text-size size
           :banners banners
           :lines banners)))

;; --- the cameras ----------------------------------------------------------------

(defn player-camera
  "The original's camera for player `i` (0 or 1) with the player at `p` (z1 for
  player one, x2 for player two), lines 98-117: fovy 45, perspective, up
  (0, 1, 0)."
  [i p]
  (if (zero? i)
    {:position [0.0 1.0 p]
     :target [0.0 1.0 (+ p 3.0)]
     :up [0.0 1.0 0.0]
     :fovy fovy
     :projection :perspective}
    {:position [p 3.0 0.0]
     :target [(+ p 3.0) 3.0 0.0]
     :up [0.0 1.0 0.0]
     :fovy fovy
     :projection :perspective}))

(defn camera
  "Player `i`'s camera in `state`, fitted to its half of `dims` by
  `raylib.soft3d/fit-camera` against the original's 400 by 450 half."
  [state dims i]
  (let [[_ _ hw hh] (nth (:halves dims) i)]
    (s3/fit-camera (player-camera i (if (zero? i) (:z1 state) (:x2 state)))
                   original-aspect (/ (double hw) (double hh)))))

;; --- the picture ----------------------------------------------------------------

(defn- floor-near
  "How far ahead of the eye the plane starts: half the depth at which the bottom
  edge of the glass (a ray `fovy / 2` below the look) meets the ground from an
  eye `height` up. Nearer than that is below the glass."
  [height camera]
  (* 0.5 (/ height (Math/tan (Math/toRadians (* 0.5 (:fovy camera)))))))

(defn- floor-list
  "The original's plane for player `i`, cut to the part ahead of the eye."
  [vp i p]
  (let [half (* 0.5 plane-size)
        cam (:camera vp)
        near (+ p (floor-near (nth (:position cam) 1) cam))
        lo (max (- half) near)
        len (- half lo)]
    (if (pos? len)
      (s3/plane [] vp nil
                (if (zero? i) [0.0 0.0 (+ lo (* 0.5 len))] [(+ lo (* 0.5 len)) 0.0 0.0])
                (if (zero? i) [plane-size len] [len plane-size])
                plane-colour)
      [])))

(def ^:private tree-reach
  "A tree's farthest point from its centre in plan: the lime cube's half diagonal."
  (* (Math/sqrt 2.0) 0.5))

(defn visible-trees
  "The `trees` whose circle of `tree-reach` is not wholly behind the eye or
  wholly outside the sides of the view of `cam` (half-angle `atan (tan (fovy / 2)
  * aspect)`), with the eye looking along +z for player one and +x for two."
  [i cam aspect]
  (let [[ex _ ez] (:position cam)
        tan-half (* (Math/tan (Math/toRadians (* 0.5 (:fovy cam)))) aspect)]
    (filterv (fn [[x z]]
               (let [dx (- x ex)
                     dz (- z ez)
                     along (if (zero? i) dz dx)
                     across (abs (if (zero? i) dx dz))]
                 (and (> (+ along tree-reach) 0.0)
                      (<= (- across tree-reach) (* (+ along tree-reach) tan-half)))))
             trees)))

(defn cube-order
  "The cubes half `i` of `state` paints, far to near, as `[pos size colour]`:
  for each tree in `visible-trees` its post then its leaf, then the two
  players, sorted by the squared distance from the half's eye to the cube's
  centre, farthest first. The sort is stable, so a post comes before the leaf on
  top of it when the eye is level with their join (player one's, 1 up), where
  the two are as far."
  [state dims i]
  (let [cam (camera state dims i)
        [_ _ hw hh] (nth (:halves dims) i)
        [ex ey ez] (:position cam)
        {:keys [z1 x2]} state
        d2 (fn [[x y z]]
             (let [dx (- x ex) dy (- y ey) dz (- z ez)]
               (+ (* dx dx) (* dy dy) (* dz dz))))
        cubes (-> (reduce (fn [out [x z]]
                            (-> out
                                (conj [[x 0.5 z] [0.25 1.0 0.25] post-colour])
                                (conj [[x 1.5 z] 1.0 leaf-colour])))
                          []
                          (visible-trees i cam (/ (double hw) (double hh))))
                  (conj [[0.0 1.0 z1] 1.0 (nth player-colours 0)])
                  (conj [[x2 3.0 0.0] 1.0 (nth player-colours 1)]))]
    (vec (sort-by (fn [[pos]] (d2 pos)) #(compare %2 %1) cubes))))

(defn scene-list
  "The draw list for half `i` of `state` in `dims`, in paint order: the plane
  first, then each cube of `cube-order` whole, far to near, all flat coloured.
  It does not call `raylib.soft3d/finish`: the cubes are separate boxes
  standing on or over the one plane, so painting them whole by the distance of
  their centres is right here, and sorting every triangle is the cost it saves."
  [state dims i]
  (let [vp (s3/view-proj (camera state dims i) (nth (:halves dims) i))
        flat {:shade :flat}]
    (reduce (fn [dl [pos size colour]] (s3/cube dl vp nil pos size colour flat))
            (floor-list vp i (if (zero? i) (:z1 state) (:x2 state)))
            (cube-order state dims i))))

;; --- fingers --------------------------------------------------------------------

(defn push-dir
  "+1 when `stick` has gone up the glass further than `gesture/slop` from where
  it began (W, or UP), -1 when it has gone down that far (S, or DOWN), else nil."
  [stick metrics]
  (when stick
    (let [vy (- (double (second (:at stick))) (double (second (:centre stick))))]
      (when (> (abs vy) (gesture/slop metrics))
        (if (neg? vy) 1.0 -1.0)))))

(defn advance
  "One frame. A stick in each half is begun and followed by `raylib.stick`, each
  only inside its own half, and each player moves `speed * delta-seconds` along
  its axis the way its stick is pushed. A release frame with fewer than two
  points lifts everything, and its position is never read. A rotation of the
  phone drops both sticks, whose pixels are the old screen's."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc state :sticks [nil nil] :n 0 :pts [] :ids nil)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        ids (stick/ids-of input points)
        prev {:pts (:pts state)
              :ids (:ids state)
              :n (:n state)}
        frame (fn [half]
                {:points points
                 :ids ids
                 :metrics metrics
                 :press? (= :press phase)
                 :free? #(gesture/in-rect? half %)
                 :start? (constantly true)})
        sticks (mapv (fn [s half] (stick/next-stick s prev (frame half)))
                     (:sticks state)
                     (:halves dims))
        dt (double (or (:delta-seconds input) 0.0))
        step (fn [p s] (+ p (* (or (push-dir s metrics) 0.0) speed dt)))]
    (assoc state
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :sticks sticks
           :z1 (step (:z1 state) (first sticks))
           :x2 (step (:x2 state) (second sticks)))))

(defn- init [{:keys [metrics]}]
  [{:z1 start
    :x2 start
    :sticks [nil nil]
    :n 0
    :pts []
    :ids nil
    :screen (:screen metrics)}
   [[:scene/init :split3d]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :split3d]]])

(defn scene []
  {:id :split3d
   :title "3D Split Screen"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
