(ns raylib.scenes.pacman.ghosts
  "The four ghosts of `pacman`: where they start, what each one aims at, how one
  picks a direction at a tile centre, and how one moves. Everything is in tile
  units (see `raylib.scenes.pacman.maze`).

  The personalities are the original's. Blinky heads for Pac-Man's tile. Pinky
  aims four tiles ahead of him, Inky reflects Blinky's tile through the point
  two tiles ahead of him, and Clyde chases until he is within eight tiles, then
  breaks for his corner. A ghost never reverses, which is what makes it commit
  to a route. A frightened ghost picks among the open ways at random, here from
  a `:pick` integer the caller draws from the project's LCG."
  (:require [raylib.scenes.pacman.maze :as maze]))

(def speed "Tiles per second when hunting or scattering. The original's." 4.6)
(def speed-frightened "Tiles per second while frightened. The original's." 3.1)

(def specs
  "`[name colour scatter-corner slot release-delay]`. Blinky starts outside the
  door (`:door-exit`), the others in a house slot, released after the delay in
  seconds."
  [["blinky" [255 60 50 255] [(dec maze/width) 0] :door-exit 0.0]
   ["pinky" [255 160 200 255] [0 0] 0 1.5]
   ["inky" [90 220 240 255] [(dec maze/width) (dec maze/height)] 1 3.5]
   ["clyde" [255 170 60 255] [0 (dec maze/height)] 2 5.5]])

(defn initial-ghosts
  "The four ghosts at their starts."
  []
  (mapv (fn [[nm colour scatter slot delay]]
          (let [[sx sy] (maze/centre-of (if (= :door-exit slot)
                                          maze/door-exit
                                          (nth maze/house-slots slot)))]
            {:name nm
             :color colour
             :scatter scatter
             :x sx
             :y sy
             :dx 0
             :dy -1
             :frightened 0.0
             :home-timer delay}))
        specs))

(defn target
  "The tile ghost `g` aims for in chase mode, given `pac` and all the `ghosts`."
  [g pac ghosts]
  (let [{:keys [x y fx fy]} pac
        ptx (maze/tile-of x)
        pty (maze/tile-of y)]
    (case (:name g)
      "blinky" [ptx pty]
      "pinky" [(+ ptx (* 4 fx)) (+ pty (* 4 fy))]
      "inky" (let [b (first (filter #(= "blinky" (:name %)) ghosts))
                   ax (+ ptx (* 2 fx))
                   ay (+ pty (* 2 fy))]
               [(- (* 2 ax) (maze/tile-of (:x b)))
                (- (* 2 ay) (maze/tile-of (:y b)))])
      "clyde" (let [d (+ (abs (- (:x g) x)) (abs (- (:y g) y)))]
                (if (> d 8) [ptx pty] (:scatter g))))))

(defn choose-dir
  "At a tile centre, the open direction that gets closest to `:target` without
  reversing. Of equal distances the last in the order up, left, down, right wins,
  as the original's `min-key` does. With a `:pick` integer the choice is the
  `pick`th open way instead, which is the frightened ghost's random turn. A dead
  end leaves only the reversal."
  [[tx ty] [dx dy] {:keys [target pick]}]
  (let [opts (vec (for [[ndx ndy] [[0 -1] [-1 0] [0 1] [1 0]]
                        :when (and (not (and (= ndx (- dx)) (= ndy (- dy))))
                                   (not (maze/ghost-wall? (+ tx ndx) (+ ty ndy))))]
                    [ndx ndy]))
        opts (if (seq opts) opts [[(- dx) (- dy)]])]
    (if pick
      (nth opts (mod pick (count opts)))
      (let [[gx gy] target
            cost (fn [[ndx ndy]]
                   (let [ax (+ tx ndx)
                         ay (+ ty ndy)]
                     (+ (* (- ax gx) (- ax gx)) (* (- ay gy) (- ay gy)))))]
        (reduce (fn [best o] (if (<= (cost o) (cost best)) o best)) opts)))))

(defn move-ghost
  "Ghost `g` after `dt` seconds. A ghost in the house just counts its
  `:home-timer` down. Otherwise it fright-times down, and heads for the door
  while it is inside the house, for its corner in scatter and for `target` in
  chase. `ctx` is `{:pac :ghosts :chase? :dt :pick}`."
  [g {:keys [pac ghosts chase? dt pick]}]
  (if (pos? (:home-timer g))
    (update g :home-timer - dt)
    (let [g (update g :frightened #(max 0.0 (- % dt)))
          frightened? (pos? (:frightened g))
          goal (if chase? (target g pac ghosts) (:scatter g))
          decide (fn [tx ty dx dy]
                   (choose-dir [tx ty] [dx dy]
                               {:target (if (= \G (maze/tile-at tx ty)) maze/door-exit goal)
                                :pick (when frightened? pick)}))]
      (merge g (maze/step-entity g {:speed (if frightened? speed-frightened speed)
                                    :dt dt
                                    :decide decide
                                    :walls? maze/ghost-wall?})))))
