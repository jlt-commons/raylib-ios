(ns raylib.touch
  "A touch diagnostic, and the answer to whether raylib's input works here.

  raylib's touch API is scalar rather than event-driven: nothing is delivered,
  you sample. GetTouchPointCount says how many fingers are down right now, and
  GetTouchX and GetTouchY give the first of them. That shape suits a frame loop
  and it means a press is not an event you receive but an edge you notice, by
  comparing this frame's count against the last.

  Every finger is drawn, not just the first, because a multi-touch bug looks
  exactly like a working single-touch app until someone uses two hands.

  Run it with: NS=raylib.touch TARGET=device sh tools/ios/build.sh"
  (:require [raylib.host :as rl]))

(def ^:private ring 40)

(defn- points
  "Every active touch as [id x y]. GetTouchPosition is the by-value Vector2
  call, so this is also the check that struct returns work over the FFI."
  [n]
  (into [] (for [i (range n)
                 :let [[x y] (rl/touch-position i)]]
             [(rl/get-touch-point-id i) (int x) (int y)])))

(defn- init [{:keys [scale]}]
  {:k scale :was 0 :downs 0 :ups 0 :frames-held 0 :trail [] :peak 0})

(defn- advance
  "Fold this frame's sample into the counters. Edges are derived rather than
  received: a press is count rising from zero, a release is it falling to it."
  [{:keys [was] :as s} n pts]
  (let [down?     (pos? n)
        pressed?  (and down? (zero? was))
        released? (and (not down?) (pos? was))]
    (cond-> (assoc s :was n :peak (max (:peak s) n))
      pressed?       (-> (update :downs inc) (assoc :frames-held 0))
      down?          (update :frames-held inc)
      released?      (update :ups inc)
      (seq pts)      (update :trail (fn [t] (into [] (take ring (cons (first pts) t)))))
      (and released? (seq (:trail s))) (assoc :trail []))))

(defn- draw! [{:keys [k downs ups frames-held trail peak]} n pts]
  (let [px   (fn [v] (int (* k v)))
        line (fn [i s] (rl/draw-text s (px 24) (px (+ 90 (* 34 i))) (px 20) rl/DARKGRAY))]
    (rl/clear-background rl/RAYWHITE)
    (line 0 "touch: put fingers on the screen")
    (line 1 (str "down now " n "   most at once " peak))
    (line 2 (str "presses " downs "   releases " ups))
    (line 3 (str "held " frames-held " frames   " (rl/get-fps) " fps"))

    ;; the trail first, so live touches draw over it
    (doseq [[i [_ x y]] (map-indexed vector trail)]
      (rl/draw-circle-lines x y (* k (- 30.0 (* 0.6 i))) rl/LIGHTGRAY))

    ;; every finger, with its raylib id, since ids are what a gesture would
    ;; have to track and they are not always 0..n-1
    (doseq [[id x y] pts]
      (rl/draw-circle x y (* 36.0 k) rl/MAROON)
      (rl/draw-circle-lines x y (* 54.0 k) rl/DARKGRAY)
      (rl/draw-text (str id) (+ x (px 40)) (- y (px 12)) (px 18) rl/DARKGRAY))))

(defn- frame [s]
  (let [n   (rl/get-touch-point-count)
        pts (points n)
        s'  (advance s n pts)]
    (when (and (pos? n) (zero? (:was s))) (println "touch: press at" (first pts)))
    (draw! s' n pts)
    s'))

(defn -main [& _]
  (rl/run! {:title "touch" :init init :frame frame}))
