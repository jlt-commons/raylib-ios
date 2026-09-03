(ns raylib.scenes.tesseract
  "A rotating 4D hypercube, ported from raylib-jlt's `tesseract_view`.

  Sixteen vertices, every coordinate plus or minus one. Two are joined by an
  edge when they differ in exactly one coordinate, which gives 32 edges. It
  spins in two 4D planes at once, then projects 4D to 3D to 2D by perspective,
  so the inner cube appears to turn inside-out through the outer one.

  Inner cube red, outer cube blue, the eight edges joining them green. Nothing
  here is a special case: which cube an edge belongs to falls out of the sign of
  its endpoints' fourth coordinate.

  The cheapest scene in the gallery by a wide margin. 32 line segments a frame
  against the 1200 the Lorenz trail draws, so it holds the frame rate with the
  screen almost empty, which makes it the useful control when something else
  starts dropping frames.")

(def d4 3.0)
(def d3 4.0)

(def verts
  (vec (for [x [-1.0 1.0] y [-1.0 1.0] z [-1.0 1.0] w [-1.0 1.0]] [x y z w])))

(def edges
  "Every pair differing in exactly one coordinate. 32 of them."
  (vec (for [i (range 16) j (range (inc i) 16)
             :when (= 1 (reduce + (map (fn [p q] (if (== p q) 0 1))
                                       (nth verts i) (nth verts j))))]
         [i j])))

(defn rot4
  "Rotate a 4D point: the x,w plane by `a`, the y,z plane by `b`. Rotating in
  two planes at once is what keeps every vertex moving. A single plane would
  leave four of them fixed."
  [[x y z w] a b]
  (let [ca (Math/cos a) sa (Math/sin a)
        cb (Math/cos b) sb (Math/sin b)]
    [(- (* x ca) (* w sa))
     (- (* y cb) (* z sb))
     (+ (* y sb) (* z cb))
     (+ (* x sa) (* w ca))]))

(defn project
  "4D to 3D to 2D, both steps by perspective divide. The first divide is what
  makes the inner cube smaller than the outer one, and the reason they trade
  places as the rotation carries w through zero."
  [metrics [x y z w]]
  (let [[width height] (:screen metrics)
        cw (double width) ch (double height)
        ;; the original's 820 was tuned to 800x450; scale by the short side so
        ;; the figure fills the same fraction of a phone screen
        scale (* (min cw ch) 1.05)
        k4 (/ 1.0 (- d4 w))
        x3 (* x k4) y3 (* y k4) z3 (* z k4)
        k3 (/ scale (- d3 z3))]
    [(+ (* cw 0.5) (* x3 k3))
     (+ (* ch 0.5) (* y3 k3))]))

(defn points
  "All 16 vertices rotated and projected for angle `a`."
  [metrics a]
  (mapv (fn [v] (project metrics (rot4 v a (* a 0.6)))) verts))

(defn edge-colour
  "[r g b] for the edge joining `i` and `j`, from the sign of their w."
  [i j]
  (let [wi (nth (nth verts i) 3)
        wj (nth (nth verts j) 3)]
    (cond
      (and (neg? wi) (neg? wj)) [255 90 90]
      (and (pos? wi) (pos? wj)) [90 170 255]
      :else                     [110 240 110])))

(defn- init [_]
  [{:a 0.0} [[:scene/init :tesseract]]])

(defn- update-scene [state _] [(update state :a + 0.012) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :tesseract]]])

(defn scene []
  {:id :tesseract :title "Tesseract"
   :init init :update update-scene :draw draw :dispose dispose})
