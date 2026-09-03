(ns poc.raylib.diagnostics
  "Pure touch/input normalization and adaptive-layout state for the Raylib host.")

(def initial-state
  {:tap-count 0
   :hold-frames 0
   :drag-samples 0
   :pointer-origin nil
   :last-pointer nil
   :close-requested? false})

(defn screen-metrics
  "Derive portable metrics from scalar Raylib values. Render/logical ratios are
  reported as DPI scale without carrying Raylib Vector2 values into state."
  [{:keys [screen-width screen-height render-width render-height]}]
  (let [width (max 1 (int screen-width))
        height (max 1 (int screen-height))
        render-width (max 1 (int render-width))
        render-height (max 1 (int render-height))]
    {:screen [width height]
     :render [render-width render-height]
     :dpi-scale [(/ render-width width) (/ render-height height)]
     :orientation (if (= width height)
                    :square
                    (if (> width height) :landscape :portrait))}))

(defn pointer-phase
  [{:keys [pressed? down? released?]}]
  (cond
    pressed? :press
    released? :release
    down? :down
    :else :idle))

(defn normalize-input
  "Convert one scalar polling sample into portable data. Only point zero has
  coordinates; IDs for all active touches remain available independently."
  [raw]
  (let [touch-count (max 0 (int (:touch-count raw)))
        point (when (or (pos? touch-count)
                        (:pressed? raw) (:down? raw) (:released? raw))
                [(int (:pointer-x raw)) (int (:pointer-y raw))])]
    {:metrics (screen-metrics raw)
     :pointer {:phase (pointer-phase raw)
               :position point}
     :touches {:count touch-count
               :ids (vec (take touch-count (:touch-ids raw)))
               :point-0 point
               :available-coordinates (if point :point-0 :none)
               :all-coordinates-available? false}
     :gesture {:code (int (or (:gesture-code raw) 0))}
     :keyboard {:activate? (boolean (:keyboard-activate? raw))
                :previous? (boolean (:keyboard-previous? raw))
                :next? (boolean (:keyboard-next? raw))
                :back? (boolean (:keyboard-back? raw))}
     :back? (boolean (or (:back? raw) (:keyboard-back? raw)))}))

(defn step
  "Apply one normalized frame. A press increments visible local state, holds
  and movement are counted, and Back deterministically requests loop closure."
  [state input]
  (let [phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        previous-point (:last-pointer state)
        moved? (and point previous-point (not= point previous-point))
        state (assoc state :last-pointer point
                           :close-requested? (or (:close-requested? state)
                                                 (:back? input)))]
    (case phase
      :press (-> state
                 (update :tap-count inc)
                 (assoc :hold-frames 1 :pointer-origin point))
      :down (-> state
                (update :hold-frames inc)
                (update :drag-samples + (if moved? 1 0)))
      :release (assoc state :hold-frames 0 :pointer-origin nil)
      (assoc state :hold-frames 0))))

(defn layout
  "Return positions and type sizes derived only from current screen metrics."
  [metrics]
  (let [[width height] (:screen metrics)
        margin (max 16 (quot (min width height) 30))
        title-size (max 24 (quot (min width height) 18))
        body-size (max 16 (quot title-size 2))
        line-gap (+ body-size (max 8 (quot body-size 2)))]
    {:margin margin
     :title-size title-size
     :body-size body-size
     :line-gap line-gap
     :touch-radius (max 18 (quot (min width height) 35))}))
