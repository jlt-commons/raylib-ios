(ns raylib.scenes.toplights-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.toplights :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

;; A screen whose field is exactly the original's 800 by 450: Back takes the top
;; 120, so sx = sy = k = 1 and every number is the original's own.
(def original-metrics {:screen [800 570]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- fresh
  ([] (fresh metrics))
  ([m] (first ((:init (sc/scene)) {:metrics m}))))

(defn- step
  ([state phase pos] (step state phase pos metrics))
  ([state phase pos m]
   (first ((:update (sc/scene)) state {:metrics m
                                       :pointer {:phase phase
                                                 :position pos}}))))

(defn- idle-step
  ([state] (idle-step state metrics))
  ([state m] (step state :idle nil m)))

(defn- in-field [m dx dy]
  (let [{:keys [x y]} (:field (sc/layout m))]
    [(+ x dx) (+ y dy)]))

(defn- tap
  "A press and a release at the same point, as a finger taps."
  ([state pos] (tap state pos metrics))
  ([state pos m]
   (-> state (step :press pos m) (step :release pos m))))

(defn- drag
  "A press at `from` and `n` frames down along a straight path to `to`."
  [state from to m n]
  (let [[x0 y0] from
        [x1 y1] to
        at (fn [i] [(+ x0 (* (- x1 x0) (/ i (double n)))) (+ y0 (* (- y1 y0) (/ i (double n))))])]
    (reduce (fn [s i] (step s :down (at i) m))
            (step state :press from m)
            (range 1 (inc n)))))

(deftest lights-boxes-and-tiles-are-the-originals
  (testing "MAX-LIGHTS 16, N-BOXES 20, TILE 64 and the 800 by 450 window"
    (is (= [16 20 64] [sc/max-lights sc/n-boxes sc/tile]))
    (is (= [800 450] [sc/window-w sc/window-h])))
  (testing "light 1 has radius 300 at (600, 400) and a later light 200"
    (is (= [300 200 10] [sc/first-light-radius sc/other-light-radius sc/marker-radius]))
    (let [s (fresh original-metrics)
          l (first (:lights s))]
      (is (= 1 (count (:lights s))))
      (is (= [600.0 400.0] (:pos l)))
      (is (= 300.0 (:radius l)))))
  (testing "20 boxes: two placed by hand, then 18 from the LCG in the original's ranges"
    (let [bxs (:boxes (fresh original-metrics))]
      (is (= 20 (count bxs)))
      (is (= [{:x 150.0
               :y 80.0
               :w 40.0
               :h 40.0}
              {:x 500.0
               :y 350.0
               :w 40.0
               :h 40.0}]
             (mapv #(into {} (map (fn [[k v]] [k (double v)])) %) (subvec bxs 0 2))))
      (doseq [{:keys [x y w h]} (subvec bxs 2)]
        (is (<= 0 x 800))
        (is (<= 0 y 450))
        (is (<= 10 w 100))
        (is (<= 10 h 100)))))
  (testing "the random boxes are the LCG's draws in order x, y, w, h"
    (let [nxt (fn [s] (mod (+ (* 1103515245 s) 12345) 2147483648))
          roll (fn [s lo hi] (+ lo (mod (quot s 65536) (inc (- hi lo)))))
          s1 (nxt sc/seed) s2 (nxt s1) s3 (nxt s2) s4 (nxt s3)
          first-random (nth (:boxes (fresh original-metrics)) 2)]
      (is (= {:x (roll s1 0 800)
              :y (roll s2 0 450)
              :w (roll s3 10 100)
              :h (roll s4 10 100)}
             (into {} (map (fn [[k v]] [k (long v)])) first-random)))))
  (testing "the same seed lays the same world"
    (is (= (:boxes (fresh)) (:boxes (fresh)))))
  (testing "on a larger field positions are the original's fractions and lengths scale by k"
    (let [lay (sc/layout metrics)
          bxs (:boxes (fresh))
          orig (:boxes (fresh original-metrics))]
      (doseq [[b o] (map vector bxs orig)]
        (is (near? (* (:sx lay) (:x o)) (:x b)))
        (is (near? (* (:sy lay) (:y o)) (:y b)))
        (is (near? (* (:k lay) (:w o)) (:w b)))
        (is (near? (* (:k lay) (:h o)) (:h b))))))
  (testing "the ground is a 64 tile, brown where the halves agree and gray where they do not"
    (let [spec (sc/ground-spec)
          px (:pixel spec)]
      (is (= [64 64 :repeat] [(:w spec) (:h spec) (:wrap spec)]))
      (is (= sc/dark-brown (sc/ground-pixel 0 0)))
      (is (= sc/dark-brown (sc/ground-pixel 63 63)))
      (is (= sc/dark-gray (sc/ground-pixel 63 0)))
      (is (= sc/dark-gray (sc/ground-pixel 0 63)))
      (is (= (bit-or 76 (bit-shift-left 63 8) (bit-shift-left 47 16) (bit-shift-left 255 24)) (px 0 0))))
    (is (= (* 64 1.5) (sc/tile-size {:k 1.5})))))

(deftest the-shadows-are-the-originals
  (let [box {:x 100.0
             :y 100.0
             :w 40.0
             :h 40.0}]
    (testing "an edge is added for each of the original's four comparisons that holds"
      ;; light (0, 120): ly > y (top), lx < x + w (right), ly < y + h (bottom), but not lx > x
      (is (= 4 (count (sc/box-shadows [0.0 120.0] 300.0 box))) "top, right and bottom, plus the footprint")
      ;; light (120, 0): not ly > y, but right, bottom and left hold
      (is (= 4 (count (sc/box-shadows [120.0 0.0] 300.0 box))) "right, bottom and left, plus the footprint")
      ;; light (120, 120), inside the box: all four hold
      (is (= 5 (count (sc/box-shadows [120.0 120.0] 300.0 box))) "all four edges, plus the footprint"))
    (testing "the footprint is the box's corners tl bl br tr"
      (is (= [[100.0 100.0] [100.0 140.0] [140.0 140.0] [140.0 100.0]]
             (last (sc/box-shadows [0.0 0.0] 300.0 box)))))
    (testing "an edge's volume is the edge, then both ends pushed 2 radius straight away from the light"
      (let [[start end _ _] (first (sc/box-shadows [120.0 0.0] 50.0 box))]
        ;; light above the box: the top edge does not qualify, so the first volume is the right
        ;; edge, from the top-right corner to the bottom-right one
        (is (= [140.0 100.0] start))
        (is (= [140.0 140.0] end))))
    (testing "the far corners lie 2 radius beyond the near ones, along the line from the light"
      (let [light [0.0 0.0]
            [[sx sy] [ex ey] [fex fey] [fsx fsy]] (first (sc/box-shadows light 50.0 box))
            d (fn [[x y] [a b]] (Math/sqrt (+ (* (- x a) (- x a)) (* (- y b) (- y b)))))]
        (is (near? 100.0 (d [fex fey] [ex ey])))
        (is (near? 100.0 (d [fsx fsy] [sx sy])))
        (is (near? (d [ex ey] light) (- (d [fex fey] light) 100.0)))))
    (testing "overlaps? is CheckCollisionRecs against the light's bounding square"
      (is (sc/overlaps? box [120.0 120.0] 10.0))
      (is (sc/overlaps? box [60.0 120.0] 50.0))
      (is (not (sc/overlaps? box [20.0 120.0] 50.0)))
      (is (not (sc/overlaps? box [120.0 300.0] 50.0))))
    (testing "inside? is CheckCollisionPointRec, edges in"
      (is (sc/inside? box [120.0 120.0]))
      (is (sc/inside? box [100.0 140.0]))
      (is (not (sc/inside? box [99.9 120.0]))))
    (testing "refresh: shadows only from the boxes the light reaches"
      (let [l (sc/refresh (sc/make-light 200.0 120.0 80.0) [box {:x 900.0
                                                                 :y 900.0
                                                                 :w 10.0
                                                                 :h 10.0}])]
        (is (:valid? l))
        (is (pos? (count (:shadows l))))
        (is (= (count (sc/box-shadows [200.0 120.0] 80.0 box)) (count (:shadows l))))))
    (testing "a light inside a box goes dark: not valid and no shadows"
      (let [l (sc/refresh (sc/make-light 120.0 120.0 80.0) [box])]
        (is (not (:valid? l)))
        (is (= [] (:shadows l)))))))

(deftest the-mask-passes-use-min-and-max-as-the-original
  (let [light (sc/refresh (sc/make-light 200.0 120.0 80.0) [{:x 100.0
                                                             :y 100.0
                                                             :w 40.0
                                                             :h 40.0}])
        plan (sc/mask-plan light)]
    (testing "counts first: a clear and two blends"
      (is (= 3 (count plan)))
      (is (= [:clear :blend :blend] (mapv first plan))))
    (testing "clear to WHITE, then GL_MIN around the gradient, then GL_MAX around the shadows"
      (is (= [:clear [255 255 255 255]] (first plan)))
      (is (= [:min :max] (mapv second (rest plan)))))
    (testing "the gradient is clear at the centre and WHITE at the rim, at the light's radius"
      (let [[_ _ items] (second plan)]
        (is (= [[:gradient 200.0 120.0 80.0 [255 255 255 0] [255 255 255 255]]] items))))
    (testing "every shadow quad is WHITE, one item per volume"
      (let [[_ _ items] (nth plan 2)]
        (is (= (count (:shadows light)) (count items)))
        (is (pos? (count items)))
        (is (every? (fn [[kind corners c]] (and (= :quad kind) (= 4 (count corners)) (= [255 255 255 255] c)))
                    items))
        (is (= (:shadows light) (mapv second items)))))
    (testing "a light that is not valid draws no gradient but still clears and blends"
      (let [dark (sc/refresh (sc/make-light 120.0 120.0 80.0) [{:x 100.0
                                                                :y 100.0
                                                                :w 40.0
                                                                :h 40.0}])
            p (sc/mask-plan dark)]
        (is (= [:clear :blend :blend] (mapv first p)))
        (is (= [] (nth (second p) 2)))
        (is (= [] (nth (nth p 2) 2))))))
  (testing "the master clears to BLACK and merges every mask with GL_MIN"
    (is (= [[:clear [0 0 0 255]]
            [:blend :min [[:mask 0] [:mask 1] [:mask 2]]]]
           (sc/master-plan 3)))
    (is (= 16 (count (nth (second (sc/master-plan 16)) 2)))))
  (testing "a quad is the two triangles a DrawTriangleFan of four points makes"
    (is (= [[:a :b :c] [:a :c :d]] (sc/quad-triangles [:a :b :c :d])))))

(deftest a-tap-adds-a-light-as-the-original
  (let [s0 (fresh)
        lay (sc/layout metrics)
        p (in-field metrics 300 700)
        s1 (tap s0 p)]
    (testing "counts first: one light became two"
      (is (= 1 (count (:lights s0))))
      (is (= 2 (count (:lights s1)))))
    (testing "the new light is at the tap, in field pixels, with radius 200"
      (let [l (second (:lights s1))]
        (is (= [300.0 700.0] (:pos l)))
        (is (near? (* 200 (:k lay)) (:radius l)))))
    (testing "the new light has its shadows worked out"
      (is (= (sc/refresh (sc/make-light 300.0 700.0 (* 200 (:k lay))) (:boxes s1))
             (second (:lights s1)))))
    (testing "the new light's mask is redrawn that frame, with the walking light's"
      (is (= [0 1] (:dirty s1))))
    (testing "the light that was there keeps walking, a frame at a time"
      (is (= (sc/idle-position lay 1) (:pos (first (:lights s1))))))
    (testing "a frame later nothing is dirty but the walking light"
      (is (= [0] (:dirty (idle-step s1)))))
    (testing "up to 16 and no more"
      (let [full (reduce (fn [s i] (tap s (in-field metrics (+ 100 (* 10 i)) 700))) s0 (range 20))]
        (is (= 16 (count (:lights full))))))
    (testing "a tap in the Back region adds nothing"
      (is (= 1 (count (:lights (tap s0 [100 60]))))))
    (testing "a drag adds nothing"
      (is (= 1 (count (:lights (drag s0 p (in-field metrics 700 1200) metrics 8))))))))

(deftest a-drag-moves-light-one
  (let [s0 (fresh)
        lay (sc/layout metrics)
        start (in-field metrics 300 700)
        s (drag s0 start (in-field metrics 700 1200) metrics 8)]
    (testing "until a drag, light 1 walks the original's path"
      (is (not (:steered? s0)))
      (let [w (nth (iterate idle-step s0) 30)]
        (is (= (sc/idle-position lay 29) (:pos (first (:lights w)))))
        (is (not (:steered? w)))))
    (testing "a drag past the slop takes it to the finger, in field pixels"
      (is (:steered? s))
      (is (= [700.0 1200.0] (:pos (first (:lights s)))))
      (is (= [0] (:dirty s))))
    (testing "and it stays where the finger left it"
      (let [later (nth (iterate idle-step (step s :release (in-field metrics 700 1200))) 40)]
        (is (= [700.0 1200.0] (:pos (first (:lights later)))))
        (is (= [] (:dirty later)))))
    (testing "a touch that has not travelled past the slop does not steer"
      (let [t (-> s0 (step :press start) (step :down (mapv + start [3 3])))]
        (is (not (:steered? t)))))
    (testing "a drag that starts under Back or on the button does not steer"
      (let [b (:button lay)
            on-button (in-field metrics (+ (:x b) 10) (+ (:y b) 10))]
        (is (not (:steered? (drag s0 [100 60] (in-field metrics 700 1200) metrics 8))))
        (is (not (:steered? (drag s0 on-button (in-field metrics 700 1200) metrics 8))))))
    (testing "light 1 is refreshed against the boxes where it lands"
      (is (= (sc/refresh (sc/make-light 700.0 1200.0 (* 300 (:k lay))) (:boxes s))
             (first (:lights s)))))
    (testing "the dragged light is held to the field"
      (let [d (drag s0 start [5000 9000] metrics 8)]
        (is (= [(double (get-in lay [:field :w])) (double (get-in lay [:field :h]))]
               (:pos (first (:lights d)))))))))

(deftest the-button-toggles-the-volumes
  (let [s0 (fresh)
        lay (sc/layout metrics)
        b (:button lay)
        on (in-field metrics (+ (:x b) (quot (:w b) 2)) (+ (:y b) (quot (:h b) 2)))]
    (is (not (:show? s0)))
    (let [s1 (tap s0 on)]
      (is (:show? s1))
      (is (= 1 (count (:lights s1))) "the tap on the button adds no light")
      (is (not (:show? (tap s1 on)))))
    (testing "the volumes are light 1's shadows, the boxes it reaches, and every box outlined"
      (let [{:keys [quads reached outlined]} (sc/volume-plan s0)]
        (is (= (:shadows (first (:lights s0))) quads))
        (is (= (:boxes s0) outlined))
        (is (= (filterv #(sc/overlaps? % (:pos (first (:lights s0))) (:radius (first (:lights s0)))) (:boxes s0))
               reached))))
    (is (= [255 255 255 191] sc/volumes-tint))))

(deftest a-turn-starts-again
  (let [s (-> (fresh) (tap (in-field metrics 300 700)))
        t (idle-step s {:screen [2334 1206]})]
    (is (= 2 (count (:lights s))))
    (is (= 1 (count (:lights t))))
    (is (= [0] (:dirty t)))
    (is (= [1] (:release t)) "the dropped light's mask is given back")
    (is (= [] (:release (idle-step t {:screen [2334 1206]}))) "that frame only")
    (is (= [2334 1206] (:screen t)))))

(deftest first-frame-draws
  (is (= :toplights (:id (sc/scene))))
  (is (= "Top Down Lights" (:title (sc/scene))))
  (doseq [screen screens
          :let [m {:screen screen}
                lay (sc/layout m)
                {:keys [field button]} lay
                s (idle-step (fresh m) m)]]
    (testing (str screen)
      (is (= 20 (count (:boxes s))))
      (is (= [0] (:dirty s)))
      (testing "the opening light walks out of the box at the centre and then casts, as the original's does"
        (is (some (fn [st] (and (:valid? (first (:lights st)))
                                (pos? (count (:shadows (first (:lights st)))))))
                  (take 300 (iterate idle-step s)))))
      (testing "the button lies inside the field"
        (is (<= 0 (:x button)))
        (is (<= (+ (:x button) (:w button)) (:w field)))
        (is (<= 0 (:y button)))
        (is (<= (+ (:y button) (:h button)) (:h field))))
      (testing "the walking light stays in the field through the whole path"
        (doseq [n (range 0 1600 37)
                :let [[x y] (sc/idle-position lay n)]]
          (is (<= 0 x (:w field)))
          (is (<= 0 y (:h field))))))))

(deftest text-lines-fit-the-field
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                {:keys [field help labels button]} dims
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= ["drag to move light #1" "tap to add a new light"] (mapv :s help)))
      (is (= "show shadow volumes" (:s (:show labels))))
      (is (= "hide shadow volumes" (:s (:hide labels))))
      (doseq [{:keys [s x y size]} (concat help (vals labels))]
        (is (<= 0 x))
        (is (<= (+ x (measure s size)) w) s)
        (is (<= (+ (:y field) y size) h) s)
        (is (>= (+ (:y field) y) (+ back-y back-h)) "below Back"))
      (testing "the labels sit inside the button"
        (doseq [{:keys [s x y size]} (vals labels)]
          (is (<= (:x button) x) s)
          (is (<= (+ x (measure s size)) (+ (:x button) (:w button))) s)
          (is (<= (:y button) y) s)
          (is (<= (+ y size) (+ (:y button) (:h button))) s)))
      (testing "the help lines do not overlap"
        (let [[a b] help]
          (is (<= (+ (:y a) (:size a)) (:y b))))))))
