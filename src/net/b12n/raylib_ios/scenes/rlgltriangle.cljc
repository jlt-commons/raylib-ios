(ns net.b12n.raylib-ios.scenes.rlgltriangle
  "One Gouraud-shaded triangle whose three corners you drag. Ported from
  raylib-jlt's `rlgl_triangle`.

  The colour across the face is the point. rlgl carries a colour per vertex, so
  red, green and blue at the three corners let the GPU interpolate the whole
  gradient.

  Winding matters in filled mode. raylib's default culls back faces, so
  dragging a corner past the other two would flip the triangle away and make it
  vanish. Rather than toggle culling as the C example does, `wound` sorts the
  corners into the winding that survives before drawing, with each colour
  travelling with its corner.

  The mouse version has SPACE for outline and R for reset, and grabs a handle
  whenever the cursor is near and the button is down. Here the two keys are
  buttons along the bottom edge, and the grab is sticky: decided on the `:press`
  and held until the finger lifts, as `net.b12n.raylib-ios.scenes.resize` does, so a fast
  drag cannot outrun the corner and lose it. The `:release` frame is ignored
  because its position is stale on a device."
  (:require [clojure.string]))

(def corner-colours [[255 0 0 255] [0 255 0 255] [0 0 255 255]])

(def ^:private start-fractions [[0.5 0.35] [0.25 0.55] [0.75 0.55]])

(def buttons
  "The two buttons, in draw order, with their labels."
  [{:id :outline
    :label "outline"}
   {:id :reset
    :label "reset"}])

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        btn-w (* 0.36 w)
        btn-h (* 0.045 h)
        btn-y (* 0.88 h)
        label-size (max 20 (int (* 0.034 side)))
        xs [(* 0.10 w) (* 0.54 w)]]
    {:w w
     :h h
     :start-corners (mapv (fn [[fx fy] c] {:pos [(* fx w) (* fy h)]
                                           :color c})
                          start-fractions corner-colours)
     ;; estimate: generous for a fingertip
     :grab (* 0.06 side)
     :handle (* 0.025 side)
     :thick (max 2.0 (* 0.004 side))
     :label-size label-size
     :buttons (mapv (fn [b x]
                      (assoc b
                             :rect [x btn-y btn-w btn-h]
                             :label-x (int (+ x (* 0.08 btn-w)))
                             :label-y (int (+ btn-y (* 0.5 (- btn-h label-size))))))
                    buttons xs)}))

(defn- in-rect? [[bx by bw bh] [px py]]
  (and (>= px bx) (<= px (+ bx bw))
       (>= py by) (<= py (+ by bh))))

(defn- within? [[cx cy] radius [px py]]
  (let [dx (- (double px) cx) dy (- (double py) cy)]
    (<= (+ (* dx dx) (* dy dy)) (* radius radius))))

(defn cross
  "Cross product of the first two edges. Negative is the winding that
  `net.b12n.raylib-ios.host/draw-triangle` documents as surviving back-face culling."
  [[ax ay] [bx by] [cx cy]]
  (- (* (- bx ax) (- cy ay))
     (* (- by ay) (- cx ax))))

(defn wound
  "The three corners in the winding that survives culling, each colour carried
  with its corner."
  [[a b c :as corners]]
  (if (pos? (cross (:pos a) (:pos b) (:pos c)))
    [a c b]
    (vec corners)))

(defn- clamp-corner [w h corner]
  (update corner :pos (fn [[x y]]
                        [(max 0.0 (min (double w) (double x)))
                         (max 0.0 (min (double h) (double y)))])))

(defn advance
  "One frame. Every corner is clamped into the screen first, so a rotation to a
  smaller screen cannot strand a corner off it where no finger can reach."
  [state input]
  (let [d (dimensions (:metrics input))
        {:keys [w h]} d
        state (update state :corners (fn [cs] (mapv #(clamp-corner w h %) cs)))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        down? (boolean (and point (#{:press :down} phase)))
        pressed-button (when (and down? (= :press phase))
                         (some (fn [b] (when (in-rect? (:rect b) point) (:id b)))
                               (:buttons d)))]
    (cond
      (= :outline pressed-button)
      (-> state (update :lines? not) (assoc :dragging nil))

      (= :reset pressed-button)
      (assoc state :corners (:start-corners d) :dragging nil)

      (not down?)
      (assoc state :dragging nil)

      :else
      (let [dragging (cond
                       (some? (:dragging state)) (:dragging state)
                       (= :press phase) (first (keep-indexed
                                                (fn [i c] (when (within? (:pos c) (:grab d) point) i))
                                                (:corners state))))]
        (assoc state
               :dragging dragging
               :corners (if (some? dragging)
                          (update (:corners state) dragging
                                  #(clamp-corner w h (assoc % :pos (mapv double point))))
                          (:corners state)))))))

(defn- init [{:keys [metrics]}]
  [{:corners (:start-corners (dimensions metrics))
    :dragging nil
    :lines? false}
   [[:scene/init :rlgltriangle]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :rlgltriangle]]])

(defn scene []
  {:id :rlgltriangle
   :title "rlgl Triangle"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
