(ns raylib.scenes.palette-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.palette :as pal]))

(def d (pal/dimensions {:screen [1206 2334]}))

(deftest the-palette-is-raylibs-own
  (testing "spot-checked against raylib.h rather than transcribed from memory.
            These four are the ones a wrong transcription is most likely to
            fudge, since they are close to each other or to a primary."
    (let [by-name (into {} (map (fn [[n r g b]] [n [r g b]]) pal/colours))]
      (is (= [253 249 0] (get by-name "YELLOW")))
      (is (= [255 203 0] (get by-name "GOLD")) "not the same as YELLOW")
      (is (= [230 41 55] (get by-name "RED")))
      (is (= [190 33 55] (get by-name "MAROON")) "not the same as RED")
      (is (= [245 245 245] (get by-name "RAYWHITE")) "not 255, which is WHITE")
      (is (= [255 255 255] (get by-name "WHITE"))))))

(deftest every-entry-is-well-formed
  (is (= 25 (count pal/colours)))
  (doseq [[nm r g b :as e] pal/colours]
    (is (= 4 (count e)) (str nm " is a name and three channels"))
    (is (string? nm))
    (doseq [c [r g b]]
      (is (integer? c))
      (is (<= 0 c 255) (str nm " channel out of range")))))

(deftest names-and-values-are-both-unique
  (testing "a duplicated value means a transcription slip, and two swatches that
            look identical are exactly what nobody notices"
    (is (= 25 (count (distinct (map first pal/colours)))) "names")
    (is (= 25 (count (distinct (map rest pal/colours)))) "values")))

(deftest blank-is-deliberately-absent
  (testing "raylib names a fully transparent BLANK. A swatch of it draws
            nothing, which reads as a rendering bug rather than as a colour."
    (is (nil? (some (fn [[n]] (= "BLANK" n)) pal/colours)))))

(deftest the-grid-holds-every-colour-on-screen
  (let [n (count pal/colours)]
    (is (= 7 (pal/rows)) "25 colours, 4 across, needs 7 rows")
    (is (>= (* pal/cols (pal/rows)) n) "enough cells for every colour")
    (doseq [i (range n)]
      (let [[x y w h] (pal/cell d i)]
        (is (>= x 0.0))
        (is (>= y 0.0))
        (is (<= (+ x w) 1206.0) (str "swatch " i " runs off the right"))
        (is (<= (+ y h) 2334.0) (str "swatch " i " runs off the bottom"))))))

(deftest cells-do-not-overlap
  (let [boxes (map (fn [i] (pal/cell d i)) (range (count pal/colours)))]
    (doseq [[i [x1 y1 w1 h1]] (map-indexed vector boxes)
            [j [x2 y2 w2 h2]] (map-indexed vector boxes)
            :when (< i j)]
      (is (or (<= (+ x1 w1) x2) (<= (+ x2 w2) x1)
              (<= (+ y1 h1) y2) (<= (+ y2 h2) y1))
          (str "cells " i " and " j " overlap")))))

(deftest the-label-stays-readable-on-every-swatch
  (testing "luma, not a flat average. A flat average calls YELLOW and BLUE
            equally light, and they are not: 226 against 84."
    (is (pal/light? ["YELLOW" 253 249 0]) "dark ink on yellow")
    (is (not (pal/light? ["BLUE" 0 121 241])) "light ink on blue")
    (is (pal/light? ["WHITE" 255 255 255]))
    (is (not (pal/light? ["BLACK" 0 0 0])))
    (is (pal/light? ["RAYWHITE" 245 245 245]))
    (is (not (pal/light? ["DARKBROWN" 76 63 47]))))
  (testing "and the two measures really do disagree, on exactly two colours in
            this palette. An earlier version of this test claimed yellow was one
            of them, which is false: yellow is 167 flat and 222 by luma, light
            either way."
    (let [flat (fn [[_ r g b]] (> (/ (+ r g b) 3.0) 140.0))
          disagree (filter (fn [e] (not= (flat e) (pal/light? e))) pal/colours)]
      (is (= #{"ORANGE" "MAGENTA"} (set (map first disagree))))
      (testing "and luma is right both times"
        (is (pal/light? ["ORANGE" 255 161 0]) "orange is bright, wants dark ink")
        (is (not (flat ["ORANGE" 255 161 0])) "a flat average would say otherwise")
        (is (not (pal/light? ["MAGENTA" 255 0 255])) "magenta wants light ink")
        (is (flat ["MAGENTA" 255 0 255]) "a flat average would say otherwise")))))
