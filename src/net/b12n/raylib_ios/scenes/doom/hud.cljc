(ns net.b12n.raylib-ios.scenes.doom.hud
  "The HUD, minimap and crosshair of `net.b12n.raylib-ios.scenes.doom`, as raylib-jlt's `doom`
  draws them (lines 452-562), as plain items for the draw side. Every position is
  the original's in its 900 by 560 window, scaled by `:k` into the picture's
  `:view` of the layout `dims` (`net.b12n.raylib-ios.scenes.doom/geometry`), so the HUD sits
  on the picture and not on the field. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.scenes.doom.map :as m]
            [net.b12n.raylib-ios.scenes.doom.ray :as ray]))

(def minimap-bg "The original's MINIMAP-BG (line 452)." [12 12 16 210])
(def player-colour "The original's PLAYER (line 453)." [245 235 120 255])
(def imp-colour "The original's IMP (line 454)." [230 80 60 255])
(def crosshair-colour "The original's CROSSHAIR (line 455)." [240 240 240 200])
(def hud-bg "The original's HUD-BG (line 456)." [16 14 18 235])
(def died-colour "The YOU DIED colour (line 560)." [220 40 40 255])

(defn- scaled [k v] (int (* k v)))

(defn- hud-size [k] (max 8 (scaled k 22)))

(defn hud-lines
  "The HUD's five texts for `state` (lines 526-562), as `{:s :x :y :size :colour}`
  at the original's positions in the view of `dims`."
  [{:keys [health kills shots imps fps]} {[vx vy _ vh] :view
                                          k :k}]
  (let [alive (count (filter :alive imps))
        y (+ vy vh (- (scaled k 32)))
        size (hud-size k)
        at (fn [x s colour] {:s s
                             :x (+ vx (scaled k x))
                             :y y
                             :size size
                             :colour colour})]
    [(at 16 (str "HEALTH " health) (if (< health 40) [235 70 60 255] [220 220 210 255]))
     (at 190 (str "KILLS " kills) [220 220 210 255])
     (at 330 (str "IMPS " alive) [220 180 120 255])
     (at 460 (str "SHOTS " shots) [150 150 160 255])
     (at 620 (str (int fps) " fps  " ray/cols " cols") [120 130 140 255])]))

(defn hud-bar
  "The HUD's bar (line 527) as `[x y w h colour]` in `dims`' view."
  [{[vx vy vw vh] :view
    k :k}]
  (let [h (scaled k 42)]
    [vx (+ vy (- vh h)) vw h hud-bg]))

(defn crosshair
  "The crosshair's four lines (lines 501-524) as `[x1 y1 x2 y2]`, in `dims`' view."
  [{[vx vy vw vh] :view
    k :k}]
  (let [cx (+ vx (quot vw 2))
        cy (+ vy (quot vh 2))
        a (scaled k 3)
        b (scaled k 9)]
    [[(- cx b) cy (- cx a) cy]
     [(+ cx a) cy (+ cx b) cy]
     [cx (- cy b) cx (- cy a)]
     [cx (+ cy a) cx (+ cy b)]]))

(defn died
  "The YOU DIED text (lines 558-562) for `dims`, or nil while alive."
  [{:keys [health]} {[vx vy vw vh] :view
                     k :k}]
  (when (zero? health)
    {:s "YOU DIED"
     :x (+ vx (quot vw 2) (- (scaled k 120)))
     :y (+ vy (quot vh 2) (- (scaled k 40)))
     :size (scaled k 54)
     :colour died-colour}))

(def ^:private minimap-s "The original's minimap cell (line 470)." 7)

(defn- map-px
  "The view's pixels for the original's minimap length `v`."
  [k v]
  (* k v))

(defn minimap-static
  "The minimap's panel and walls as `[x y w h colour]` for `dims` (lines 471-488):
  the panel at (8, 8) and 120 square, then a rect of `s - 1` for each wall at
  `(12 + x s, 12 + y s)` in the colour of its style, scaled to the view."
  [{[vx vy _ _] :view
    k :k}]
  (let [px (fn [v] (+ (int (map-px k v))))
        cell (px (dec minimap-s))]
    (into [[(+ vx (px 8)) (+ vy (px 8)) (px (+ (* m/map-w minimap-s) 8)) (px (+ (* m/map-h minimap-s) 8)) minimap-bg]]
          (for [y (range m/map-h)
                x (range m/map-w)
                :let [t (m/wall-at x y)]
                :when (pos? t)]
            [(+ vx (px (+ 12 (* x minimap-s)))) (+ vy (px (+ 12 (* y minimap-s)))) cell cell
             (let [[r g b] (get m/wall-colours (min 4 t))] [r g b 255])]))))

(defn minimap-dynamic
  "The minimap's living imps, the player and the heading line for `state`
  (lines 489-499), as `[:circle x y r colour]` and `[:line x1 y1 x2 y2 colour]`."
  [{:keys [pos-x pos-y dir-x dir-y imps]} {[vx vy _ _] :view
                                           k :k}]
  (let [at (fn [v] (map-px k (+ 12 (* v minimap-s))))
        ox (fn [v] (+ vx (int (at v))))
        oy (fn [v] (+ vy (int (at v))))]
    (conj (into [] (comp (filter :alive)
                         (map (fn [{:keys [x y]}] [:circle (ox x) (oy y) (map-px k 2.0) imp-colour])))
                imps)
          [:circle (ox pos-x) (oy pos-y) (map-px k 2.5) player-colour]
          [:line (ox pos-x) (oy pos-y) (ox (+ pos-x (* 2 dir-x))) (oy (+ pos-y (* 2 dir-y))) player-colour])))
