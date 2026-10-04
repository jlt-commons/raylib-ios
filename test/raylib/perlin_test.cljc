;; The reference values below come from this program, built against raylib 6.0's
;; stb_perlin.h with -ffp-contract=off (strict IEEE float, no fused multiply-add):
;;
;; Its body is transcribed from GenImagePerlinNoise in raylib 6.0's rtextures.c:992-1030, not linked against it.
;;
;; /* cc -ffp-contract=off -I <raylib 6.0>/src/external ref.c -o ref -lm */
;; #include <stdio.h>
;; #define STB_PERLIN_IMPLEMENTATION
;; #include "stb_perlin.h"
;;
;; static int grey(int width, int height, int offsetX, int offsetY, float scale, int x, int y)
;; {
;;     float aspectRatio = (float)width/(float)height;
;;     float nx = (float)(x + offsetX)*(scale/(float)width);
;;     float ny = (float)(y + offsetY)*(scale/(float)height);
;;     if (width > height) nx *= aspectRatio; else ny /= aspectRatio;
;;     float p = stb_perlin_fbm_noise3(nx, ny, 1.0f, 2.0f, 0.5f, 6);
;;     if (p < -1.0f) p = -1.0f;
;;     if (p > 1.0f) p = 1.0f;
;;     float np = (p + 1.0f)/2.0f;
;;     return (unsigned char)(np*255.0f);
;; }
;;
;; int main(void)
;; {
;;     float pts[12][3] = {
;;         {0.0f,0.0f,0.0f},{0.5f,0.5f,0.5f},{1.0f,1.0f,1.0f},{0.3f,0.7f,1.0f},
;;         {-0.3f,-1.7f,0.25f},{2.5f,3.25f,1.0f},{7.77f,1.23f,1.0f},{-5.5f,4.4f,2.2f},
;;         {10.1f,-10.1f,1.0f},{0.001f,0.999f,1.0f},{3.14159f,2.71828f,1.41421f},{100.5f,200.25f,-3.5f}};
;;     for (int i = 0; i < 12; i++)
;;         printf("fbm %.9g %.9g %.9g %.9g\n", pts[i][0], pts[i][1], pts[i][2],
;;                stb_perlin_fbm_noise3(pts[i][0], pts[i][1], pts[i][2], 2.0f, 0.5f, 6));
;;     int t[12][2] = {{0,0},{799,0},{0,449},{799,449},{400,225},{1,1},{123,45},{700,300},{399,224},{401,226},{799,225},{400,449}};
;;     for (int i = 0; i < 12; i++)
;;         printf("grey 800 450 0 0 6 %d %d %d\n", t[i][0], t[i][1], grey(800,450,0,0,6.0f,t[i][0],t[i][1]));
;;     /* a tall image and an offset, to exercise the other aspect branch */
;;     printf("grey 300 500 7 9 4 0 0 %d\n", grey(300,500,7,9,4.0f,0,0));
;;     printf("grey 300 500 7 9 4 150 250 %d\n", grey(300,500,7,9,4.0f,150,250));
;;     printf("grey 300 500 7 9 4 299 499 %d\n", grey(300,500,7,9,4.0f,299,499));
;;     /* full-image checksum for 800x450 */
;;     long sum = 0; for (int y=0;y<450;y++) for(int x=0;x<800;x++) sum += grey(800,450,0,0,6.0f,x,y);
;;     printf("sum800x450 %ld\n", sum);
;;     return 0;
;; }
;;
;; Compiled with the default flags, clang fuses a*b+c on arm64 and some fbm
;; values move in the seventh digit (and one byte in the whole 800x450 image).
;; The grey bytes listed here are the same either way.

(ns raylib.perlin-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.perlin :as pn]))

(def ^:private fbm-points
  "[x y z expected], stb_perlin_fbm_noise3 with lacunarity 2, gain 0.5, 6 octaves."
  [[0 0 0 0]
   [0.5 0.5 0.5 -0.5]
   [1 1 1 0]
   [0.300000012 0.699999988 1 -0.285505176]
   [-0.300000012 -1.70000005 0.25 0.0732171685]
   [2.5 3.25 1 0.0861816406]
   [7.76999998 1.23000002 1 0.544328749]
   [-5.5 4.4000001 2.20000005 -0.275065631]
   [10.1000004 -10.1000004 1 -0.0955708176]
   [0.00100000005 0.999000013 1 4.99407543e-06]
   [3.14159012 2.71828008 1.41420996 0.0324456394]
   [100.5 200.25 -3.5 -0.0107421875]])

(def ^:private grey-texels
  "[w h offset-x offset-y scale x y expected], GenImagePerlinNoise's byte."
  [[800 450 0 0 6 0 0 127]
   [800 450 0 0 6 799 0 109]
   [800 450 0 0 6 0 449 127]
   [800 450 0 0 6 799 449 138]
   [800 450 0 0 6 400 225 88]
   [800 450 0 0 6 1 1 124]
   [800 450 0 0 6 123 45 79]
   [800 450 0 0 6 700 300 152]
   [800 450 0 0 6 399 224 85]
   [800 450 0 0 6 401 226 87]
   [800 450 0 0 6 799 225 111]
   [800 450 0 0 6 400 449 141]
   [300 500 7 9 4 0 0 111]
   [300 500 7 9 4 150 250 153]
   [300 500 7 9 4 299 499 122]])

(deftest fbm-matches-stb-perlin
  (doseq [[x y z expected] fbm-points]
    (is (< (Math/abs (- expected (pn/fbm3 x y z 2.0 0.5 6))) 1e-6)
        (str "at " [x y z]))))

(deftest grey-matches-genimageperlinnoise
  (doseq [[w h ox oy scale x y expected] grey-texels]
    (is (= expected (pn/perlin-grey w h ox oy scale x y))
        (str "texel " [x y] " of " w "x" h))))

(deftest noise-is-pure
  (testing "the same input gives the same answer, in any order"
    (let [a (pn/noise3 0.3 0.7 1.0)
          _ (pn/noise3 5.5 6.5 7.5)
          b (pn/noise3 0.3 0.7 1.0)]
      (is (= a b))
      (is (not (zero? a)) "off the lattice there is something to be pure about")))
  (testing "a lattice point is zero, and single-octave noise stays inside -1..1"
    (is (zero? (pn/noise3 1.0 2.0 3.0)))
    (is (every? (fn [i] (<= -1.0 (pn/noise3 (* i 0.37) (* i 0.91) 1.0) 1.0)) (range 200)))))

;; Float is a JVM class, so ClojureScript (which clj-kondo also reads this as) skips it.
;; jolt 0.8.6, the CI pin, has no Float/floatToIntBits, so there the round trip
;; is skipped and says so; the JVM job and a newer jolt still run it.
#?(:cljs nil
   :default
   (deftest f32-rounds-like-a-float
     (let [f32 @#'pn/f32
           float-bits? (try (Float/floatToIntBits (float 1.0)) true (catch Exception _ false))]
       (if-not float-bits?
         (println "SKIPPED f32-rounds-like-a-float's round trip: no Float/floatToIntBits on this runtime")
         (let [round-trip (fn [x] (double (Float/intBitsToFloat (Float/floatToIntBits (float x)))))
               rf (fn [i] (round-trip (* (- (mod (* i 0.6180339887) 1.0) 0.5) (Math/pow 2 (- (mod i 17) 8)))))]
           (testing "sums, products and quotients of floats, which is every operation noise3 does"
             (doseq [i (range 1 400)
                     :let [a (rf i) b (rf (+ i 1000))]
                     v [(+ a b) (- a b) (* a b) (/ a b)]]
               (is (= (round-trip v) (f32 v)) (str a " op " b))))))
       (testing "a tie between two floats goes to the even one"
         (is (= 1.0 (f32 (+ 1.0 (Math/pow 2 -24)))))
         (is (= (+ 1.0 (Math/pow 2 -22)) (f32 (+ 1.0 (* 3 (Math/pow 2 -24))))))))))
