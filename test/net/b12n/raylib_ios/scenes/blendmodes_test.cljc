(ns net.b12n.raylib-ios.scenes.blendmodes-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.blendmodes :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))
(def below-back [600 1500])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick
  [state phase position]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase phase
                                                :position position}})))

(defn- tap
  "A press and a release at `pt`."
  [state pt]
  (-> state (tick :press pt) (tick :release pt)))

(defn- mode-name [state] (second (sc/mode-of state)))

(defn- throws? [thunk]
  (try (thunk) false (catch #?(:cljs js/Error :default Exception) _ true)))

(def unit-dims {:ox 0.0
                :oy 0.0
                :f 1.0})

(defn- collect
  "Every call `emit!` makes to the callback it is handed, as vectors."
  [emit! dims]
  (let [out (atom [])]
    (emit! (fn [& args] (swap! out conj (vec args))) dims)
    @out))

;; The original's sky-pixel (blend_modes.clj lines 44-66), kept as a
;; reference: what class a texture pixel is.
(defn- reference-class [x y]
  (let [h153 (int (* 225 0.68))]
    (if (< y h153)
      :sky
      (let [col (quot x 22)
            span (- 225 h153)
            top (int (+ h153 (* span 0.5 (sc/hash01 col))))]
        (if (< y top)
          :sky
          (let [lx (mod x 22)
                window? (and (> lx 4) (< lx 18)
                             (zero? (mod (+ (quot (- y top) 10) (quot lx 6)) 3))
                             (< (sc/hash01 (+ (* col 977) (quot y 10))) 0.4))]
            (if window? :window :building)))))))

(deftest a-tap-cycles-the-blend-modes-in-order
  (let [s0 (fresh)]
    (testing "four modes, raylib.h's BlendMode values 0 to 3, in the original's order"
      (is (= [[0 "BLEND_ALPHA"] [1 "BLEND_ADDITIVE"] [2 "BLEND_MULTIPLIED"] [3 "BLEND_ADD_COLORS"]]
             sc/modes))
      (is (= [0 "BLEND_ALPHA"] (sc/mode-of s0))))
    (testing "each tap moves one on, and the fifth wraps to the first"
      (let [states (reductions tap s0 (repeat 5 below-back))]
        (is (= ["BLEND_ALPHA" "BLEND_ADDITIVE" "BLEND_MULTIPLIED" "BLEND_ADD_COLORS" "BLEND_ALPHA" "BLEND_ADDITIVE"]
               (map mode-name states)))
        (is (= [0 1 2 3 0 1] (map (comp first sc/mode-of) states)))))
    (testing "the mode changes on the release frame, not the press"
      (is (= "BLEND_ALPHA" (mode-name (tick s0 :press below-back))))
      (is (= "BLEND_ADDITIVE" (mode-name (tick (tick s0 :press below-back) :release below-back)))))
    (testing "a tap in Back belongs to the host and changes nothing"
      (is (= "BLEND_ALPHA" (mode-name (tap s0 [100 60])))))
    (testing "a drag that began in Back, or went far, is not a tap"
      (is (= "BLEND_ALPHA" (mode-name (-> s0 (tick :press [100 60]) (tick :down below-back) (tick :release below-back)))))
      (is (= "BLEND_ALPHA" (mode-name (-> s0 (tick :press below-back) (tick :down [600 1900]) (tick :release [600 1900]))))))
    (testing "the text names the mode"
      (is (= "Current: BLEND_ADD_COLORS" (sc/current-line 3))))))

(deftest the-textures-are-redrawn-as-the-originals-rule
  (let [w 400
        h 225
        grid (atom (vec (repeat (* w h) :sky)))
        paint (fn [colour]
                (fn [x y rw rh]
                  (dotimes [j rh]
                    (dotimes [i rw]
                      (swap! grid assoc (+ x i (* (+ y j) w)) colour)))))]
    (sc/emit-buildings! (paint :building) unit-dims)
    (sc/emit-windows! (paint :window) unit-dims)
    (testing "every one of 90000 pixels is the class the original's sky-pixel gives it"
      (let [bad (for [y (range h)
                      x (range w)
                      :when (not= (reference-class x y) (nth @grid (+ x (* y w))))]
                  [x y (reference-class x y) (nth @grid (+ x (* y w)))])]
        (is (empty? (take 5 bad)))))
    (testing "the skyline has all three classes"
      (is (= 19 (count sc/buildings)))
      (is (pos? (count sc/windows)))
      (is (= #{:sky :building :window} (set @grid))))))

(deftest the-glow-fans-are-the-originals-blobs
  (let [tris (collect sc/emit-glows! unit-dims)]
    (is (= 48 (count tris)))
    (doseq [[bi [cx cy radius [r g b]]] (map-indexed vector sc/blobs)
            :let [mine (subvec tris (* bi sc/fan-segments) (* (inc bi) sc/fan-segments))]]
      (testing (str "blob " bi)
        (is (= 16 (count mine)))
        (is (every? (fn [[x1 y1 _ _ _ _ tr tg tb]]
                      (and (= [cx cy] [x1 y1]) (= [r g b] [tr tg tb])))
                    mine)
            "the centre vertex is the blob's centre, in its colour")
        (is (every? (fn [[_ _ x2 y2 x3 y3]]
                      (and (< (abs (- radius (Math/sqrt (+ (Math/pow (- x2 cx) 2) (Math/pow (- y2 cy) 2))))) 1e-9)
                           (< (abs (- radius (Math/sqrt (+ (Math/pow (- x3 cx) 2) (Math/pow (- y3 cy) 2))))) 1e-9)))
                    mine)
            "the rim is at the blob's radius")
        (is (every? (fn [[x1 y1 x2 y2 x3 y3]]
                      (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1)))))
                    mine)
            "front winding: draw-triangle's cross product is negative")
        (is (< (abs (- (* 0.5 sc/fan-segments radius radius (Math/sin (/ (* 2 Math/PI) sc/fan-segments)))
                       (reduce + (map (fn [[x1 y1 x2 y2 x3 y3]]
                                        (abs (* 0.5 (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))))
                                      mine))))
               1e-6)
            "the sixteen triangles tile the polygon exactly")))))

(deftest the-default-blend-is-restored
  (doseq [[mode _] sc/modes]
    (testing (str "mode " mode)
      (let [log (atom [])
            begin! (fn [m] (swap! log conj [:begin m]))
            end! (fn [] (swap! log conj [:end]))]
        (sc/call-blended! begin! end! mode (fn [] (swap! log conj [:draw])))
        (is (= [[:begin mode] [:draw] [:end]] @log) "begin, the draw, then end")
        (reset! log [])
        (is (throws? (fn [] (sc/call-blended! begin! end! mode (fn [] (swap! log conj [:draw]) (throw (ex-info "boom" {})))))))
        (is (= [[:begin mode] [:draw] [:end]] @log) "a throwing draw still ends the mode")
        (reset! log [])
        (is (throws? (fn [] (sc/call-blended! (fn [m] (swap! log conj [:begin m]) (throw (ex-info "boom" {})))
                                              end! mode (fn [] (swap! log conj [:draw]))))))
        (is (= [[:begin mode] [:end]] @log) "a begin that throws still ends, and nothing is drawn")))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= 0 (:mode-idx s)))
    (is (= [0 "BLEND_ALPHA"] (sc/mode-of s)))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  {:keys [ox oy pic-w pic-h scale f]} dims
                  rects (concat (collect sc/emit-buildings! dims) (collect sc/emit-windows! dims))
                  tris (collect sc/emit-glows! dims)]]
      (testing (str screen)
        (testing "the picture is the original's 800 by 450 scaled, inside the screen and below the texts"
          (is (< (abs (- (* 800 scale) pic-w)) 1e-9))
          (is (< (abs (- (* 450 scale) pic-h)) 1e-9))
          (is (< (abs (- (* 2 scale) f)) 1e-9))
          (is (<= 0 ox))
          (is (<= (+ ox pic-w) (+ w 1e-6)))
          (is (<= (+ oy pic-h) (+ h 1e-6)))
          (let [last-line (last (:lines dims))]
            (is (>= oy (+ (:y last-line) (:size last-line))))))
        (testing "19 buildings and the windows are drawn, all inside the picture"
          (is (= 19 (count (collect sc/emit-buildings! dims))))
          (is (= (count sc/windows) (count (collect sc/emit-windows! dims))))
          (is (every? (fn [[x y rw rh]]
                        (and (pos? rw) (pos? rh)
                             (>= x (int ox)) (>= y (int oy))
                             (<= (+ x rw) (+ ox pic-w 1)) (<= (+ y rh) (+ oy pic-h 1))))
                      rects)))
        (testing "three fans of sixteen, centred on the blobs, in the original's colours"
          (is (= 48 (count tris)))
          (is (= [[0 255 255] [255 0 255] [255 230 0]]
                 (mapv (fn [i] (subvec (nth tris (* i 16)) 6 9)) [0 1 2])))
          (let [[x1 y1] (first tris)]
            (is (< (abs (- x1 (+ ox (* f 120)))) 1e-9))
            (is (< (abs (- y1 (+ oy (* f 110)))) 1e-9))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines]} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 2 (count lines)) "the hint and the widest current-mode line")
      (is (= (apply max (map #(measure (sc/current-line %) 100) (range 4)))
             (measure (:s (second lines)) 100))
          "the widest name, so no mode overflows")
      (doseq [i (range 4)
              :let [s (sc/current-line i)
                    size (:size (second lines))]]
        (is (<= (+ (:x (second lines)) (measure s size)) w) s))
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (>= y back-bottom))
          (is (<= (+ y size) h))
          (is (>= size 8)))))))
