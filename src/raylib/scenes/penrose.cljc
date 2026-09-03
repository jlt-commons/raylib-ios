(ns raylib.scenes.penrose
  "A Penrose P3 tiling by deflation, ported from raylib-jlt's `penrose_tiling`.

  Ten Robinson triangles around a decagon, subdivided `deflations` times by the
  golden ratio. The tiling is computed ONCE at init and never changes, so this
  scene costs nothing per frame in Clojure and everything per frame in draw
  calls, which makes it the opposite stress test to boids.

  That matters on a phone. Each deflation multiplies the triangle count by
  about 2.6, and every triangle is four rlgl calls to fill plus three lines for
  its edges. Five deflations is roughly 1500 triangles, so about 6000 fill
  calls and 4500 line calls a frame, every one of them crossing libffi. So
  `default-deflations` is lower here than the original's 5 and `tiling` takes
  the count, which means it can be tuned live over the nREPL.")

(def default-deflations 4)

(def ^:private phi (/ (+ 1.0 (Math/sqrt 5.0)) 2.0))
(def ^:private inv (/ 1.0 phi))

(def colour-thin [235 130 60 255])
(def colour-thick [70 130 200 255])
(def colour-edge [30 30 40 130])

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)]
    {:cx (/ (double width) 2.0)
     :cy (/ (double height) 2.0)
     :radius (* (min (double width) (double height)) 0.46)}))

(defn- lerp [[ax ay] [bx by] s]
  [(+ ax (* (- bx ax) s)) (+ ay (* (- by ay) s))])

(defn- wheel
  "Ten Robinson triangles around (cx, cy), forming a decagon."
  [cx cy radius]
  (vec (for [i (range 10)]
         (let [ba (/ (* (- (* 2 i) 1) Math/PI) 10.0)
               ca (/ (* (+ (* 2 i) 1) Math/PI) 10.0)
               b [(+ cx (* radius (Math/cos ba))) (+ cy (* radius (Math/sin ba)))]
               c [(+ cx (* radius (Math/cos ca))) (+ cy (* radius (Math/sin ca)))]
               a [cx cy]]
           (if (even? i) [0 a c b] [0 a b c])))))

(defn- subdivide [tris]
  (vec (mapcat (fn [[k a b c]]
                 (if (zero? k)
                   (let [p (lerp a b inv)]
                     [[0 c p b] [1 p c a]])
                   (let [q (lerp b a inv)
                         r (lerp b c inv)]
                     [[1 r c a] [1 q r b] [0 r q a]])))
               tris)))

(defn- front
  "Wind a triangle to the front face, so an rlgl fill is not backface-culled."
  [[k a b c]]
  (let [[ax ay] a [bx by] b [cx cy] c
        area (- (* (- bx ax) (- cy ay)) (* (- cx ax) (- by ay)))]
    (if (> area 0.0) [k a c b] [k a b c])))

(defn tiling
  "The finished triangle list: [kind [ax ay] [bx by] [cx cy]] each."
  [metrics deflations]
  (let [{:keys [cx cy radius]} (dimensions metrics)]
    (mapv front
          (loop [tris (wheel cx cy radius) i 0]
            (if (< i deflations) (recur (subdivide tris) (inc i)) tris)))))

(defn- init [input]
  [{:tris (tiling (:metrics input) default-deflations)
    :deflations default-deflations}
   [[:scene/init :penrose]]])

;; Nothing moves, so the update is almost a no-op. Almost: it re-deflates when
;; the requested count no longer matches what was built, which is what lets
;; default-deflations be raised or lowered from the nREPL on a --dev build and
;; take effect without a rebuild.
(defn- update-scene [state input]
  [(if (= (:deflations state) default-deflations)
     state
     (assoc state
            :tris (tiling (:metrics input) default-deflations)
            :deflations default-deflations))
   []])

(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :penrose]]])

(defn scene []
  {:id :penrose :title "Penrose"
   :init init :update update-scene :draw draw :dispose dispose})
