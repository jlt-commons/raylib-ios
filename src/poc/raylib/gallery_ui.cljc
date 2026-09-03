(ns poc.raylib.gallery-ui
  "Pure adaptive geometry and hit testing for the Raylib gallery shell.

  Geometry is derived from the current framebuffer metrics.  The returned
  rectangles are plain data so navigation can be tested without opening a
  Raylib window or depending on a particular emulator resolution.")

(defn ^:export live-presentation
  "Return the pure presentation data read by every gallery frame.

  Android debug builds call this Var dynamically, so evaluating a replacement
  definition through nREPL changes subsequent frames without entering Raylib on
  the nREPL worker. Release builds may direct-link it."
  []
  {:revision :baseline
   :title "Jolt + Raylib Gallery"
   :subtitle "Choose a touch-first scene"
   :background [245 245 245 255]
   :accent [0 82 172 255]
   :card [35 92 150 255]})

(defn- columns-for [width height]
  (if (>= (* width 3) (* height 2)) 3 2))

(defn- rectangle [x y width height]
  {:x (int x) :y (int y) :width (max 1 (int width))
   :height (max 1 (int height))})

(defn ^:export gallery-layout
  "Return adaptive header, Back target, and one card per scene ID.

  The Back target belongs to a scene view; cards belong to the gallery view.
  Both use the same safe margin, keeping controls away from system gesture
  edges without assuming a desktop 800x450 window."
  [metrics scene-ids sizes]
  (let [[width height] (:screen metrics)
        {:keys [margin title-size body-size line-gap]} sizes
        gap (max 12 (quot margin 2))
        columns (columns-for width height)
        count (count scene-ids)
        rows (if (zero? count) 0 (quot (+ count (dec columns)) columns))
        cards-y (+ margin title-size (* 2 line-gap))
        footer-space (+ margin (* 2 line-gap) body-size)
        card-width (quot (- width (* 2 margin) (* (dec columns) gap)) columns)
        card-height (if (zero? rows)
                      1
                      (quot (- height cards-y footer-space (* (dec rows) gap))
                            rows))
        back-height (max 48 (+ body-size margin))
        back-width (max 260 (* 10 body-size))
        cards (mapv (fn [index scene-id]
                      (let [column (mod index columns)
                            row (quot index columns)]
                        (assoc (rectangle (+ margin (* column (+ card-width gap)))
                                          (+ cards-y (* row (+ card-height gap)))
                                          card-width card-height)
                               :scene-id scene-id)))
                    (range count) scene-ids)]
    {:margin margin
     :title-size title-size
     :body-size body-size
     :line-gap line-gap
     :columns columns
     :rows rows
     :back (assoc (rectangle margin margin back-width back-height)
                  :action :back)
     :cards cards}))

(defn ^:export counter-controls
  "Return three metric-derived control rectangles for scene-local reducer
  interaction. The controls stay above the footer and use the same safe
  horizontal margins as the gallery cards."
  [metrics sizes]
  (let [[width height] (:screen metrics)
        {:keys [margin body-size line-gap]} sizes
        gap (max 12 (quot margin 2))
        control-y (- height (+ margin (* 2 line-gap) body-size))
        control-width (quot (- width (* 2 margin) (* 2 gap)) 3)
        labels [[:decrement "- Counter"]
                [:increment "+ Counter"]
                [:reset "Reset"]]]
    (mapv (fn [index [action label]]
            (assoc {:x (+ margin (* index (+ control-width gap)))
                    :y control-y
                    :width control-width
                    :height (+ body-size margin)}
                   :action action
                   :label label))
          (range 3) labels)))

(defn ^:export contains-point?
  [{:keys [x y width height]} [point-x point-y]]
  (and (<= x point-x) (< point-x (+ x width))
       (<= y point-y) (< point-y (+ y height))))

(defn ^:export hit-test-controls
  "Return a reducer control action or nil for a primary-pointer point."
  [controls point]
  (when point
    (some (fn [control]
            (when (contains-point? control point) (:action control)))
          controls)))

(defn ^:export hit-test
  "Return a scene ID, :back, or nil for a primary-pointer press."
  [layout point mode]
  (when point
    (if (= :scene mode)
      (when (contains-point? (:back layout) point) :back)
      (some (fn [card]
              (when (contains-point? card point) (:scene-id card)))
            (:cards layout)))))
