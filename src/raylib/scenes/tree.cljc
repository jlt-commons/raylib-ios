(ns raylib.scenes.tree
  "A binary fractal tree, ported from raylib-jlt's `recursive_tree`.

  Each branch spawns two shorter children at plus and minus `branch-spread`, ten deep,
  which is 1023 branches. The original recurses and draws in the same pass;
  here the recursion returns segments and the host draws them, which is what
  the scene contract asks for and also what lets the whole tree be built once
  at init instead of every frame.

  A slow sway is the one thing added: the original is a still, and a scene that
  never changes looks broken next to the others in the gallery. The sway is a
  per-frame angle offset, so the tree is rebuilt each frame, which is why depth
  is 9 here rather than 10.")

(def depth 9)
(def branch-spread 0.5)
(def shrink 0.72)
(def sway 0.06)

(def bark [101 67 33 255])
(def leaf [34 139 34 255])

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)]
    {:x0 (/ (double width) 2.0)
     :y0 (* (double height) 0.94)
     :len (* (min (double width) (double height)) 0.20)}))

(defn branches
  "Every branch as [x1 y1 x2 y2 leaf?], deepest last."
  [metrics wind]
  (let [{:keys [x0 y0 len]} (dimensions metrics)
        ;; a volatile around the transient, not a bare one. conj! is allowed to
        ;; return a different object than it was handed, and discarding that
        ;; return is the documented way to lose writes. Clojure's vector
        ;; transient happens to return this, so the old form worked, but the
        ;; guarantee is not one to lean on and this file is .cljc.
        out (volatile! (transient []))]
    (letfn [(go [x y l a d]
              (when (pos? d)
                (let [x2 (+ x (* l (Math/cos a)))
                      y2 (- y (* l (Math/sin a)))]
                  (vswap! out conj! [x y x2 y2 (<= d 3)])
                  (go x2 y2 (* l shrink) (+ a branch-spread wind) (dec d))
                  (go x2 y2 (* l shrink) (- a branch-spread (- wind)) (dec d)))))]
      (go x0 y0 len (/ Math/PI 2.0) depth))
    (persistent! @out)))

(defn- init [_input] [{:t 0.0} [[:scene/init :tree]]])
(defn- update-scene [state _input] [(update state :t + 0.02) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :tree]]])

(defn wind
  "The sway offset at time t."
  [t]
  (* sway (Math/sin t)))

(defn scene []
  {:id :tree :title "Fractal Tree"
   :init init :update update-scene :draw draw :dispose dispose})
