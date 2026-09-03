(ns raylib.scenes.kaleidoscope
  "Six-fold symmetry over a moving stroke, ported from raylib-jlt's
  `kaleidoscope`.

  A point traces a Lissajous path; every frame the trail is redrawn rotated
  into FOLDS copies and mirrored, so the pattern reads as one symmetric whole
  without a render texture.

  Pure: the rotations are computed here and handed to the host as line
  segments, so nothing in this file touches raylib.")

(def folds 6)
;; 60 leaves 708 lines a frame, which holds 59 fps on an iPhone 17 Pro. 90 is
;; 1068 lines and about 47. The measurements are in
;; docs/guide/performance-on-a-phone.md.
(def trail-length 60)
(def tau 6.283185307179586)

(defn dimensions
  [metrics]
  (let [[width height] (:screen metrics)
        span (min (double width) (double height))]
    {:cx (/ (double width) 2.0)
     :cy (/ (double height) 2.0)
     ;; the original's 160 of a 450-tall window, kept proportional
     :reach (* span 0.36)}))

(defn- trail-point [reach t]
  [(* reach (Math/cos t))
   (* reach (Math/sin (* 1.7 t)))])

(defn rotations
  "The fold-and-mirror transforms, as [cos sin mirror?] triples.

  Twelve of them for six folds. Computed once per frame and reused across every
  trail segment, which matters more than it looks: the first version of this
  namespace returned a lazy sequence of 1788 ready-made segment tuples, and on
  portable bytecode building that sequence cost far more than the 1788 draw
  calls it fed. Handing the host a dozen small triples and letting it loop by
  index instead took the scene from 14 fps to about 50."
  []
  (vec (for [k (range folds)
             mirror? [false true]]
         (let [ang (* tau (/ (double k) folds))]
           [(Math/cos ang) (Math/sin ang) mirror?]))))

(defn place
  "One trail point through one rotation, as [x y]."
  [{:keys [cx cy]} [ca sa mirror?] x y]
  (let [x (if mirror? (- x) x)]
    [(+ cx (- (* x ca) (* y sa)))
     (+ cy (+ (* x sa) (* y ca)))]))

(defn segment-colour
  "The colour of the i'th segment of an n-long trail, as [r g b a]."
  [i n]
  (let [age (/ (double i) n)]
    [(int (* 255 age)) 120 (int (* 255 (- 1.0 age))) 255]))

(defn advance [state metrics]
  (let [{:keys [reach]} (dimensions metrics)
        frame (inc (:frame state))
        t (* 0.08 frame)]
    (assoc state
           :frame frame
           :trail (vec (take-last trail-length (conj (:trail state) (trail-point reach t)))))))

(defn- init [_input]
  [{:frame 0 :trail []} [[:scene/init :kaleidoscope]]])

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :kaleidoscope]]])

(defn scene []
  {:id :kaleidoscope :title "Kaleidoscope"
   :init init :update update-scene :draw draw :dispose dispose})
