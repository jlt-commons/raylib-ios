(ns raylib.scenes.balls
  "Balls under gravity, ported from raylib-jlt's `ball_physics`.

  Each ball carries a position, a velocity and a radius. Gravity is added to
  the vertical velocity every frame, and a wall reverses the relevant component
  and scales it by a restitution below one, so the bounces decay.

  The original respawns on SPACE. There is no keyboard here, so it watches the
  total energy instead: once every ball has settled it starts over. That is
  more interesting than a key anyway, because it means the scene shows the
  whole arc rather than whatever moment you happen to look at.

  Positions are in the safe region's own coordinates, which is why the floor is
  at its height rather than the screen's. See the safe-area guide."
  (:require [clojure.string]))

(def default-seed 4242
  )
(def ball-count 9)
(def gravity 0.45)
(def restitution 0.78)
(def friction 0.995)

;; [r g b] each, raylib's own palette values
(def palette
  [[230 41 55] [255 161 0] [255 203 0] [0 228 48]
   [102 191 255] [135 60 190] [255 109 194] [0 158 47] [255 133 0]])

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :base-radius (* 0.045 (min w h))}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- pick [seed lo hi]
  (let [s (next-random seed)]
    [(+ lo (* (- hi lo) (/ (double (mod s 100000)) 100000.0))) s]))

(defn spawn
  "A fresh set, from the seeded generator rather than GetRandomValue, so a run
  replays identically and the tests can say anything at all about it."
  [{:keys [w h base-radius]} seed]
  (loop [i 0 s seed out []]
    (if (= i ball-count)
      [out s]
      (let [[r s1] (pick s (* 0.6 base-radius) (* 1.4 base-radius))
            [x s2] (pick s1 r (- w r))
            [y s3] (pick s2 r (* 0.35 h))
            [vx s4] (pick s3 -4.0 4.0)]
        (recur (inc i) s4
               (conj out {:x x :y y :vx vx :vy 0.0 :r r
                          :colour (nth palette (mod i (count palette)))}))))))

(defn step
  "One frame for one ball. A wall reverses the component that crosses it and
  scales it by restitution; the position is clamped to the wall rather than
  left outside it, or a ball that lands fast enough tunnels through and never
  comes back."
  [{:keys [w h]} {:keys [x y vx vy r] :as b}]
  (let [vy (+ vy gravity)
        nx (+ x vx)
        ny (+ y vy)
        [nx vx] (cond
                  (< (- nx r) 0)  [r (* (- vx) restitution)]
                  (> (+ nx r) w)  [(- w r) (* (- vx) restitution)]
                  :else           [nx vx])
        [ny vy] (if (> (+ ny r) h)
                  [(- h r) (* (- vy) restitution)]
                  [ny vy])]
    (assoc b :x nx :y ny :vx (* vx friction) :vy vy)))

(defn energy
  "A rough total, used only to notice when everything has stopped. Height above
  the floor plus speed, per ball, unweighted: the absolute number means nothing
  and only its decay to near zero is read."
  [{:keys [h]} balls]
  (reduce + (map (fn [{:keys [y vx vy r]}]
                   (+ (max 0.0 (- h y r))
                      (* 8.0 (+ (abs vx) (abs vy)))))
                 balls)))

(defn advance [state metrics]
  (let [dims (dimensions metrics)
        balls (mapv #(step dims %) (:balls state))
        e (energy dims balls)]
    (if (< e (* 0.02 (:h dims)))
      ;; everything has settled, so start again
      (let [[fresh seed] (spawn dims (:seed state))]
        (assoc state :balls fresh :seed seed :settled 0))
      (assoc state :balls balls :settled (:settled state 0)))))

(defn- init [{:keys [metrics]}]
  (let [[balls seed] (spawn (dimensions metrics) default-seed)]
    [{:balls balls :seed seed :settled 0} [[:scene/init :balls]]]))

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :balls]]])

(defn scene []
  {:id :balls :title "Ball Physics"
   :init init :update update-scene :draw draw :dispose dispose})
