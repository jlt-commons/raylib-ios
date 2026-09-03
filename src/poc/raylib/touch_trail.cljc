(ns poc.raylib.touch-trail
  "Pure bounded primary-pointer trail; adapted from raylib-jlt mouse-trail.")

(def max-points 64)

(defn ^:export layout [metrics]
  (let [[w h] (:screen metrics) s (/ (double (min w h)) 450.0)]
    {:radius (max 4.0 (* 12.0 s)) :max-points max-points}))

(defn step [state input]
  (let [phase (get-in input [:pointer :phase]) point (get-in input [:pointer :position])]
    (cond
      (= :press phase) {:points (if point [point] [])}
      (= :down phase) (if point (update state :points #(vec (take-last max-points (conj % point)))) state)
      :else state)))

(defn ^:export scene []
  {:id :touch-trail :title "Touch Trail"
   :init (fn [_] [{:points []} [[:scene/init :touch-trail]]])
   :update (fn [state input] [(step state input) []])
   :draw (fn [state _] [state []])
   :dispose (fn [state] [state [[:scene/dispose :touch-trail]]])})
