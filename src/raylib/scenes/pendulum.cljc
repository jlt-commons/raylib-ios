(ns raylib.scenes.pendulum
  "A chaotic double pendulum, ported from raylib-jlt's `double_pendulum`.

  Two rods, the standard Lagrangian equations of motion, and a fading trail of
  where the second bob has been. Sensitive to its initial conditions, which is
  the point: the same seed always draws the same curve, and a hair's difference
  would not.

  Pure, and the integrator is the original's verbatim. What changed is that the
  geometry derives from the live screen instead of a fixed 800x450, and the
  loop belongs to the host.")

(def gravity 1.0)
(def dt 0.06)
(def mass-1 10.0)
(def mass-2 10.0)
(def trail-length 120)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)
        span (min (double width) (double height))
        rod (* span 0.26)]
    {:ox (/ (double width) 2.0)
     ;; hung from the upper third, so a full swing stays on a tall screen
     :oy (* (double height) 0.30)
     :l1 rod :l2 rod
     :bob (max 4 (int (* span 0.018)))
     :trail-dot (* span 0.005)}))

(defn advance
  "One integration step. Returns the next state; `positions` turns it into
  points for the host to draw."
  [state metrics]
  (let [{:keys [l1 l2]} (dimensions metrics)
        {:keys [a1 a2 v1 v2 trail]} state
        d (- a1 a2)
        two-body (- (+ (* 2 mass-1) mass-2) (* mass-2 (Math/cos (- (* 2 a1) (* 2 a2)))))
        den1 (* l1 two-body)
        den2 (* l2 two-body)
        acc1 (/ (+ (* (- gravity) (+ (* 2 mass-1) mass-2) (Math/sin a1))
                   (* (- mass-2) gravity (Math/sin (- a1 (* 2 a2))))
                   (* -2 (Math/sin d) mass-2
                      (+ (* v2 v2 l2) (* v1 v1 l1 (Math/cos d)))))
                den1)
        acc2 (/ (* 2 (Math/sin d)
                   (+ (* v1 v1 l1 (+ mass-1 mass-2))
                      (* gravity (+ mass-1 mass-2) (Math/cos a1))
                      (* v2 v2 l2 mass-2 (Math/cos d))))
                den2)
        v1' (+ v1 (* dt acc1))
        v2' (+ v2 (* dt acc2))
        a1' (+ a1 v1')
        a2' (+ a2 v2')
        {:keys [ox oy]} (dimensions metrics)
        x1 (+ ox (* l1 (Math/sin a1')))
        y1 (+ oy (* l1 (Math/cos a1')))
        x2 (+ x1 (* l2 (Math/sin a2')))
        y2 (+ y1 (* l2 (Math/cos a2')))]
    (assoc state :a1 a1' :a2 a2' :v1 v1' :v2 v2'
           :trail (vec (take-last trail-length (conj trail [x2 y2]))))))

(defn positions
  "[[x1 y1] [x2 y2]] for the current angles: the two joints."
  [{:keys [a1 a2]} metrics]
  (let [{:keys [ox oy l1 l2]} (dimensions metrics)
        x1 (+ ox (* l1 (Math/sin a1)))
        y1 (+ oy (* l1 (Math/cos a1)))]
    [[x1 y1] [(+ x1 (* l2 (Math/sin a2))) (+ y1 (* l2 (Math/cos a2)))]]))

(defn- init [_input]
  ;; the original's starting angles, which is what makes the curve reproducible
  [{:a1 2.2 :a2 2.6 :v1 0.0 :v2 0.0 :trail []} [[:scene/init :pendulum]]])

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :pendulum]]])

(defn scene []
  {:id :pendulum :title "Double Pendulum"
   :init init :update update-scene :draw draw :dispose dispose})
