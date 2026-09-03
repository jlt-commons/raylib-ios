(ns raylib.touch
  "examples/flappy milestone 3: RAY-010's scalar touch polling, on the host.
  Press edges (touch count 0 → 1) and releases are counted and logged; the
  console is the witness the simulator can no longer be."
  (:require [raylib.host :as rl]))

(defn- init [{:keys [scale]}]
  {:k scale :touches 0 :presses 0 :releases 0 :held 0 :last nil})

(defn- px [k v] (int (* k v)))

(defn- frame [{:keys [k touches] :as s}]
  (let [n         (rl/get-touch-point-count)
        down?     (pos? n)
        x         (rl/get-touch-x)
        y         (rl/get-touch-y)
        pressed?  (and down? (zero? touches))
        released? (and (not down?) (pos? touches))
        s         (cond-> (assoc s :touches n)
                    down?     (assoc :last [x y])
                    pressed?  (-> (update :presses inc) (assoc :held 0))
                    down?     (update :held inc)
                    released? (update :releases inc))]
    (when pressed?
      (println "touch: press" (:presses s) "at" x y "— GetTouchPosition" (rl/touch-position 0) "id" (rl/get-touch-point-id 0)))
    (when released?
      (println "touch: release" (:releases s) "after" (:held s) "frames, last" (:last s)))
    (rl/clear-background rl/RAYWHITE)
    (let [f (px k 20) m (px k 24)]
      (rl/draw-text "touch me" m (px k 100) f rl/DARKGRAY)
      (rl/draw-text (str "touches " n "  presses " (:presses s) "  releases " (:releases s)) m (px k 140) f rl/DARKGRAY)
      (rl/draw-text (str "at " x "," y "   " (rl/get-fps) " fps") m (px k 180) f rl/DARKGRAY))
    (when-let [[lx ly] (:last s)]
      (rl/draw-circle-lines lx ly (* 40.0 k) rl/LIGHTGRAY))
    (when down?
      (rl/draw-circle x y (* 40.0 k) rl/MAROON)
      (rl/draw-circle-lines x y (* 60.0 k) rl/DARKGRAY))
    s))

(defn -main [& _]
  (rl/run! {:title "touch" :init init :frame frame}))
