(ns raylib.scenes.kaleidoscope
  "Six-fold symmetry over a moving stroke, ported from raylib-jlt's
  `kaleidoscope`.

  A point traces a Lissajous path; every frame the trail is redrawn rotated
  into FOLDS copies and mirrored, so the pattern reads as one symmetric whole
  without a render texture.

  Pure: the rotations are computed here and handed to the host as line
  segments, so nothing in this file touches raylib."
  (:require [clojure.string :as str]))

(def folds 6)
(def trail-length 150)
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

(defn segments
  "Every line the pattern is made of, as [[x1 y1] [x2 y2] [r g b a]].

  Each consecutive pair of trail points becomes 2 * folds segments: one per
  rotation, plus its mirror. Computed here rather than in the host so the
  geometry is testable without a screen."
  [{:keys [cx cy]} trail]
  (let [n (count trail)]
    (when (> n 1)
      (for [i (range 1 n)
            k (range folds)
            mirror? [false true]]
        (let [[ax ay] (nth trail (dec i))
              [bx by] (nth trail i)
              age (/ (double i) n)
              colour [(int (* 255 age)) 120 (int (* 255 (- 1.0 age))) 255]
              ang (* tau (/ (double k) folds))
              ca (Math/cos ang)
              sa (Math/sin ang)
              flip (fn [x] (if mirror? (- x) x))
              place (fn [x y] [(+ cx (- (* (flip x) ca) (* y sa)))
                               (+ cy (+ (* (flip x) sa) (* y ca)))])]
          [(place ax ay) (place bx by) colour])))))

(defn advance [state metrics]
  (let [{:keys [reach]} (dimensions metrics)
        frame (inc (:frame state))
        t (* 0.08 frame)]
    (assoc state
           :frame frame
           :trail (vec (take-last trail-length (conj (:trail state) (trail-point reach t)))))))

(defn- init [input]
  [{:frame 0 :trail []} [[:scene/init :kaleidoscope]]])

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :kaleidoscope]]])

(defn scene []
  {:id :kaleidoscope :title "Kaleidoscope"
   :init init :update update-scene :draw draw :dispose dispose})
