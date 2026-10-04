(ns net.b12n.raylib-ios.rlgl-model
  "A small model of rlgl's matrix state, for tests that stub the matrix calls.
  It follows rlgl.h (raylib 6.0) rlMatrixMode, rlPushMatrix, rlPopMatrix,
  rlLoadIdentity and rlTranslatef: three matrices (projection, modelview and
  `transform`), a current-matrix pointer, and one stack. In MODELVIEW mode a push
  points the pointer at `transform`, and a pop writes the saved matrix into
  whatever the pointer is on. Matrices are reduced to a y translation (or an
  :ortho tag for the projection), which is all the tests need.")

(defn fresh
  "A model at rlgl's start: everything identity, pointer on modelview."
  []
  (atom {:projection :identity
         :modelview 0
         :transform 0
         :cur :modelview
         :mode :modelview
         :required? false
         :stack []}))

(defn matrix-mode [m mode]
  (swap! m assoc :mode mode :cur mode))

(defn push [m]
  (swap! m (fn [s]
             (let [s (if (= :modelview (:mode s))
                       (assoc s :required? true :cur :transform)
                       s)]
               (update s :stack conj (get s (:cur s)))))))

(defn pop* [m]
  (swap! m (fn [s]
             (let [s (if (seq (:stack s))
                       (-> s
                           (assoc (:cur s) (peek (:stack s)))
                           (update :stack pop))
                       s)]
               (if (and (empty? (:stack s)) (= :modelview (:mode s)))
                 (assoc s :cur :modelview :required? false)
                 s)))))

(defn identity* [m]
  (swap! m (fn [s] (assoc s (:cur s) (if (= :projection (:cur s)) :identity 0)))))

(defn ortho [m & args]
  (swap! m (fn [s] (assoc s (:cur s) (into [:ortho] args)))))

(defn translate-y [m y]
  (swap! m (fn [s] (update s (:cur s) + y))))

(defn vertex-offset
  "The y offset a vertex drawn now gets: `transform` on the CPU when required,
  plus modelview."
  [m]
  (let [s @m]
    (+ (if (:required? s) (:transform s) 0) (:modelview s))))

(defn snapshot
  "Everything a pass must give back, except the projection (a pass ends by
  setting the screen's, which is a different matrix from the test's start)."
  [m]
  (dissoc @m :projection))
