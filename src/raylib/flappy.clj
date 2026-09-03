(ns raylib.flappy
  "examples/flappy milestone 4: the Android experiment's pure Flappy Bird,
  unchanged, under the iOS owner loop. The host hands this one input snapshot
  and a delta per frame (RAY-018's contract); it draws what comes back."
  (:require [poc.raylib.flappy-bird :as flappy]
            [raylib.host :as rl]))

(def DARKGREEN (rl/rgba 0 117 44 255))
(def GOLD      (rl/rgba 255 203 0 255))

(defn- metrics [] {:screen [(rl/get-screen-width) (rl/get-screen-height)]})

(defn- init [{:keys [scale]}]
  {:k scale :touches 0 :frame 0 :game (flappy/new-game (metrics) flappy/default-seed)})

(defn- phase
  "The touch count's edge, as the sim's pointer phase."
  [n previous]
  (cond (and (pos? n) (zero? previous)) :press
        (pos? n)                         :down
        :else                            :idle))

(defn draw-game!
  "Draw one game state at scale k for metrics m. Public: examples/gallery draws
  the same scene through the contract's draw-scene!."
  [k game m]
  (let [{:keys [height bird-x bird-radius pipe-width gap-height]} (flappy/dimensions m)
        px (fn [v] (int (* k v)))]
    (rl/clear-background rl/SKYBLUE)
    (doseq [{:keys [x gap]} (:pipes game)]
      (rl/draw-rectangle (int x) 0 (int pipe-width) (int gap) DARKGREEN)
      (rl/draw-rectangle (int x) (int (+ gap gap-height)) (int pipe-width)
                         (int (- height gap gap-height)) DARKGREEN))
    (rl/draw-circle (int bird-x) (int (:y game)) (double bird-radius) GOLD)
    (rl/draw-text (str "score " (:score game) "   " (rl/get-fps) " fps") (px 24) (px 80) (px 20) rl/DARKGRAY)
    (when (:over? game)
      (rl/draw-text "GAME OVER - TAP TO RESTART" (px 24) (int (/ height 2.0)) (px 22) rl/MAROON))))

(defn- draw! [{:keys [k game]} m] (draw-game! k game m))

(defn- frame [{:keys [touches game] :as s}]
  (let [m     (metrics)
        n     (rl/get-touch-point-count)
        input {:metrics m
               :delta-seconds (rl/get-frame-time)
               :pointer {:phase (phase n touches)}
               :keyboard {:activate? false}}
        game' (flappy/step game input)
        s'    (assoc s :touches n :game game' :frame (inc (:frame s)))]
    (when (= :press (get-in input [:pointer :phase]))
      (println "flappy: flap at frame" (:frame s') "score" (:score game') (if (:over? game) "(restart)" "")))
    (when (and (:over? game') (not (:over? game)))
      (println "flappy: game over at frame" (:frame s') "score" (:score game')
               "elapsed" (format "%.1f" (:elapsed game')) "s"))
    (draw! s' m)
    s'))

(defn -main [& _]
  (rl/run! {:title "Flappy Bird" :init init :frame frame}))
