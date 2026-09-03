(ns raylib.scenes.lsystem
  "An L-system plant, ported from raylib-jlt's `l_system`.

  A string is rewritten by production rules, then read as turtle graphics: F
  draws forward, + and - turn, [ and ] push and pop. The plant reveals itself
  segment by segment and regrows.

  The rewrite and the turtle walk happen ONCE at init, because the string does
  not change: only how much of it is shown does. That keeps the per-frame cost
  to the draw loop, which is the shape this runtime rewards.")

(def rules {\X "F+[[X]-X]-F[-FX]+X" \F "FF"})
(def iterations 5)
(def turn (* 25.0 (/ Math/PI 180.0)))
(def grow-frames 40)
(def hold-frames 260)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)]
    {:x0 (* (double width) 0.5)
     :y0 (* (double height) 0.97)
     ;; the original's 3.2 against a 450-tall window, kept proportional
     :step (* (double height) 0.0071)}))

(defn- expand [s] (apply str (map (fn [ch] (get rules ch (str ch))) s)))

(defn segments
  "Every [x1 y1 x2 y2] the turtle draws, in order."
  [metrics]
  (let [{:keys [x0 y0 step]} (dimensions metrics)
        s (nth (iterate expand "X") iterations)]
    (loop [chars (seq s) x x0 y y0 a (- (/ Math/PI 2.0)) stack [] segs (transient [])]
      (if (empty? chars)
        (persistent! segs)
        (let [ch (first chars) more (rest chars)]
          (cond
            (= ch \F) (let [nx (+ x (* step (Math/cos a)))
                            ny (+ y (* step (Math/sin a)))]
                        (recur more nx ny a stack (conj! segs [x y nx ny])))
            (= ch \+) (recur more x y (+ a turn) stack segs)
            (= ch \-) (recur more x y (- a turn) stack segs)
            (= ch \[) (recur more x y a (conj stack [x y a]) segs)
            (= ch \]) (let [[px py pa] (peek stack)] (recur more px py pa (pop stack) segs))
            :else (recur more x y a stack segs)))))))

(defn shown
  "How many segments are visible at frame `f`: grow, hold, then start over."
  [f total]
  (let [cycle (+ grow-frames hold-frames)
        phase (mod f cycle)]
    (if (>= phase grow-frames)
      total
      (min total (int (* total (/ (double phase) grow-frames)))))))

(defn- init [input]
  [{:segments (segments (:metrics input)) :frame 0} [[:scene/init :lsystem]]])

(defn- update-scene [state _input] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :lsystem]]])

(defn scene []
  {:id :lsystem :title "L-system Plant"
   :init init :update update-scene :draw draw :dispose dispose})
