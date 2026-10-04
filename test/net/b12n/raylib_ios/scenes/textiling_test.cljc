(ns net.b12n.raylib-ios.scenes.textiling-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.scenes.textiling :as sc]
            [net.b12n.raylib-ios.texel :as texel]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick
  ([state] (tick state :idle nil))
  ([state phase position]
   (first ((:update (sc/scene)) state {:metrics metrics
                                       :pointer {:phase phase
                                                 :position position}}))))

(defn- centre [[x y w h]] [(+ x (/ w 2.0)) (+ y (/ h 2.0))])

(defn- hold
  "`n` frames with a finger on the middle of `rect`."
  [state rect n]
  (let [at (centre rect)]
    (nth (iterate #(tick % :down at) state) n)))

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj) and `tile`
;; (texture_tiling.clj lines 22-35), copied verbatim as the reference.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(defn- ref-tile [x y]
  (let [d (mod (+ x y) 16)
        e (mod (- x y) 16)
        c (/ 64 2.0)
        r (Math/sqrt (+ (* (- x c) (- x c)) (* (- y c) (- y c))))]
    (cond
      (< r 5) (ref-rgba 255 203 0 255)
      (< d 3) (ref-rgba 0 121 241 255)
      (< e 3) (ref-rgba 102 191 255 255)
      :else (ref-rgba 20 24 34 255))))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(deftest the-tile-is-the-originals
  (let [{:keys [w h pixel]} (sc/tile-spec)]
    (is (= [64 64] [w h]))
    (is (= 4096 (count (for [y (range h)
                             x (range w)]
                         (is (= (low32 (ref-tile x y)) (low32 (pixel x y))) (str [x y]))))))
    (testing "all four colours of the ground and weave occur, and the dot"
      (is (= 4 (count (set (for [y (range h)
                                 x (range w)]
                             (pixel x y)))))))
    (testing "the byte order is raylib-jlt's rgba"
      (is (= (low32 (ref-rgba 0 121 241 255)) (texel/pack [0 121 241 255]))))
    (testing "the tile lines up with itself on all four edges"
      (is (= (pixel 0 0) (pixel 16 0) (pixel 0 16) (pixel 48 48))))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap filter]} (sc/tile-spec)]
    (is (= :repeat wrap))
    (is (= :nearest filter))
    (is (zero? (bit-and w (dec w))))
    (is (zero? (bit-and h (dec h))))))

(deftest tiles-and-scroll-follow-the-original
  (let [s (fresh)]
    (is (= 6.0 (:tiles s)))
    (is (= 0.0 (:scroll s)))
    (testing "scroll grows 0.004 a frame, buttons or not"
      (is (< (abs (- 0.04 (:scroll (nth (iterate tick s) 10)))) 1e-9))
      (let [geo (sc/geometry metrics)]
        (is (< (abs (- 0.04 (:scroll (hold s (:up geo) 10)))) 1e-9))))
    (let [geo (sc/geometry metrics)]
      (testing "UP held raises the density 0.08 a frame"
        (is (< (abs (- 6.8 (:tiles (hold s (:up geo) 10)))) 1e-9))
        (is (= :up (:held (hold s (:up geo) 1)))))
      (testing "DOWN held lowers it 0.08 a frame"
        (is (< (abs (- 5.2 (:tiles (hold s (:down geo) 10)))) 1e-9))
        (is (= :down (:held (hold s (:down geo) 1)))))
      (testing "the press frame counts, as a key's first frame does"
        (is (< (abs (- 6.08 (:tiles (tick s :press (centre (:up geo)))))) 1e-9)))
      (testing "a finger elsewhere, a release and idle change nothing"
        (is (= 6.0 (:tiles (hold s [5.0 1500.0 10.0 10.0] 10))))
        (is (= 6.0 (:tiles (tick s :release (centre (:up geo))))))
        (is (= 6.0 (:tiles (tick s)))))
      (testing "tiles are clamped to 1.0 and 24.0"
        (is (= 24.0 (:tiles (hold s (:up geo) 400))))
        (is (= 1.0 (:tiles (hold s (:down geo) 400)))))
      (testing "lifting the finger stops it"
        (let [up (hold s (:up geo) 5)]
          (is (nil? (:held (tick up))))
          (is (= (:tiles up) (:tiles (tick up)))))))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= :textiling (:id (sc/scene))))
    (is (= "Texture Tiling" (:title (sc/scene))))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  q (sc/quad s dims)
                  [_ band-y band-w band-h] (:band dims)]]
      (testing (str screen)
        (testing "one quad over the whole screen, texcoords past 1.0"
          (is (= [0 0 w h] ((juxt :x :y :width :height) q)))
          (is (= [0.0 0.0 6.0] [(:u0 q) (:v0 q) (:u1 q)]))
          (is (< (abs (- (* 6.0 (/ (double h) w)) (:v1 q))) 1e-9))
          (is (< 1.0 (:u1 q))))
        (testing "the tiles stay square: texels per pixel match on both axes"
          (is (< (abs (- (/ (:u1 q) w) (/ (:v1 q) h))) 1e-12)))
        (testing "the scroll moves both texcoords and the span stays"
          (let [q2 (sc/quad (assoc s :scroll 0.5) dims)]
            (is (= [0.5 0.5] [(:u0 q2) (:v0 q2)]))
            (is (< (abs (- 6.0 (- (:u1 q2) (:u0 q2)))) 1e-9))))
        (testing "the band is under Back and inside the screen"
          (is (>= band-y 120))
          (is (<= (+ band-y band-h) h))
          (is (= (double w) band-w)))
        (testing "both buttons are below the text, inside the band and apart"
          (let [[ux uy uw uh] (:up dims)
                [dx dy dw dh] (:down dims)
                last-line (last (:lines dims))]
            (is (>= uy (+ (:y last-line) (:size last-line))))
            (is (= uy dy))
            (is (<= (+ ux uw) dx))
            (is (<= (+ dx dw) w))
            (is (<= (+ uy uh) (+ band-y band-h)))
            (is (>= (min uh dh) 40) "a finger-sized target")))))
    (testing "the count line is the original's tiles * tiles * (h / w)"
      (is (= 18 (sc/tile-count 6.0 0.5)))
      (is (= "texture tiling - 20 tiles from one 64x64 texture, one quad"
             (sc/title-line 6.0 (/ 450.0 800)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines labels pad]
                 :as dims} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 2 (count lines)))
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (<= (+ y size) h))
          (is (>= y 120) "below Back")))
      (testing "the widest count is covered"
        (let [size (:size (first lines))]
          (doseq [tiles [1.0 6.0 24.0]
                  aspect [(/ (double h) w)]]
            (is (<= (+ pad (measure (sc/title-line tiles aspect) size)) w)))))
      (testing "each label sits inside its button"
        (doseq [k [:up :down]
                :let [{:keys [s x y size]} (k labels)
                      [bx by bw bh] (k dims)]]
          (is (>= x bx))
          (is (<= (+ x (measure s size)) (+ bx bw)))
          (is (>= y by))
          (is (<= (+ y size) (+ by bh))))))))
