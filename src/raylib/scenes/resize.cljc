(ns raylib.scenes.resize
  "A rectangle you resize by dragging its corner handle. Ported from raylib-jlt's
  `rectangle_scaling`.

  The original tracks a mouse, which is always somewhere even when no button is
  down, so it can highlight the handle on hover and only resize on a press. A
  finger has no hover: it is either on the glass or it does not exist. So the
  handle highlights while it is being held rather than while it is being pointed
  at, and the scene reads the same without pretending to a state the hardware
  does not have.

  The grab is sticky. Once a finger has taken the handle it keeps it until the
  finger lifts, even if it wanders outside the handle's own box. Testing
  containment every frame instead is the obvious version and it drops the
  rectangle the moment you drag faster than the corner follows."
  (:require [clojure.string]))

(def min-w 0.18)
(def min-h 0.06)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :x (* 0.12 w) :y (* 0.20 h)
     :handle (* 0.055 w)
     :min-w (* min-w w) :min-h (* min-h h)
     :label-size (max 20 (int (* 0.028 (min w h))))}))

(defn handle-box
  "The grab target at the rectangle's far corner, as [x y w h].

  Larger than the drawn triangle, because a finger is not a cursor: the visible
  handle is what you aim at and this is what you actually hit."
  [{:keys [x y handle]} rw rh]
  [(- (+ x rw) handle) (- (+ y rh) handle) (* 1.6 handle) (* 1.6 handle)])

(defn in-box? [[bx by bw bh] [px py]]
  (and (>= px bx) (<= px (+ bx bw))
       (>= py by) (<= py (+ by bh))))

(defn resize-to
  "The rectangle a finger at `point` implies, floored so it cannot invert."
  [{:keys [x y w h min-w min-h]} [px py]]
  [(max min-w (min (- w x (* 0.04 w)) (- (double px) x)))
   (max min-h (min (- h y (* 0.10 h)) (- (double py) y)))])

(defn advance [state input]
  (let [d (dimensions (:metrics input))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        down? (and point (#{:press :down} phase))
        ;; Sticky: taken on the press that lands in the box, released only when
        ;; the finger lifts. Re-testing containment every frame loses the
        ;; rectangle as soon as you drag faster than the corner can follow.
        holding? (cond
                   (not down?) false
                   (:holding? state) true
                   (= :press phase) (in-box? (handle-box d (:rw state) (:rh state)) point)
                   :else false)
        [rw rh] (if (and holding? point)
                  (resize-to d point)
                  [(:rw state) (:rh state)])]
    (assoc state :holding? holding? :rw rw :rh rh)))

(defn- init [{:keys [metrics]}]
  (let [{:keys [w h]} (dimensions metrics)]
    [{:rw (* 0.50 w) :rh (* 0.28 h) :holding? false}
     [[:scene/init :resize]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :resize]]])

(defn scene []
  {:id :resize :title "Resize"
   :init init :update update-scene :draw draw :dispose dispose})
