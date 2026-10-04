(ns net.b12n.raylib-ios.scenes.spritebutton-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.scenes.spritebutton :as sc]
            [net.b12n.raylib-ios.texel :as texel]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick [state phase position]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase phase
                                                :position position}})))

(defn- centre [[x y w h]] [(+ x (/ w 2.0)) (+ y (/ h 2.0))])

(def button (:button (sc/geometry metrics)))
(def inside (centre button))
(def outside [20.0 1500.0])

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj) and `sheet-pixel`
;; (sprite_button.clj lines 33-62), copied verbatim as the reference.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(defn- ref-sheet-pixel [x y]
  (let [frame (quot y 48)
        fy (mod y 48)
        edge (min x fy (- 160 1 x) (- 48 1 fy))
        top? (< fy (/ 48 2))
        base (case frame
               0 [70 110 190]
               1 [95 150 235]
               [55 85 150])
        [r g b] base
        lift (cond
               (< edge 2) -35
               (and (< edge 6) (if (= frame 2) (not top?) top?)) 55
               (< edge 6) -25
               :else 0)
        label-y (if (= frame 2) (+ (/ 48 2) 1) (/ 48 2))
        label? (and (> x 40) (< x (- 160 40))
                    (>= fy (- label-y 3)) (< fy (+ label-y 3)))]
    (if label?
      (ref-rgba 240 245 255 255)
      (ref-rgba (min 255 (max 0 (+ r lift)))
                (min 255 (max 0 (+ g lift)))
                (min 255 (max 0 (+ b lift)))
                255))))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(deftest the-sheet-is-the-originals
  (let [{:keys [w h pixel]} (sc/sheet-spec)]
    (is (= [160 144] [w h]))
    (is (= 23040 (count (for [y (range h)
                              x (range w)]
                          (is (= (low32 (ref-sheet-pixel x y)) (low32 (pixel x y))) (str [x y]))))))
    (testing "the three frames differ, and the label bar drops a pixel when pressed"
      (is (= 3 (count (set (map #(pixel 80 (+ % 10)) [0 48 96])))))
      (is (not= (pixel 80 21) (pixel 80 (+ 96 21))))
      (is (= (low32 (ref-rgba 240 245 255 255)) (pixel 80 24)))
      (is (= (low32 (ref-rgba 240 245 255 255)) (pixel 80 (+ 96 27)))))
    (testing "the byte order is raylib-jlt's rgba"
      (is (= (low32 (ref-rgba 70 110 190 255)) (texel/pack [70 110 190 255]))))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap filter]} (sc/sheet-spec)]
    (testing "neither side is a power of two, so the sheet must clamp"
      (is (not (zero? (bit-and w (dec w)))))
      (is (not (zero? (bit-and h (dec h)))))
      (is (= :clamp wrap)))
    (is (= :nearest filter))))

(deftest press-shows-the-pressed-frame-and-acts-on-release
  (let [s (fresh)]
    (is (= {:clicks 0
            :frame 0} (select-keys s [:clicks :frame])))
    (testing "a press inside shows frame 2 at once, and holding keeps it"
      (let [p (tick s :press inside)
            d (tick p :down inside)]
        (is (= 2 (:frame p)))
        (is (= 2 (:frame d)))
        (is (= 0 (:clicks d)) "nothing acts while the finger is down")
        (testing "the release inside acts once, and the button rests"
          (let [r (tick d :release [0.0 0.0])
                i (tick r :idle nil)]
            (is (= 1 (:clicks r)) "the release's own position is not trusted")
            (is (= 0 (:frame r)))
            (is (= 1 (:clicks i)))
            (is (nil? (:at i)))))))
    (testing "a quick tap, one press frame then the release, acts"
      (is (= 1 (:clicks (tick (tick s :press inside) :release inside)))))
    (testing "a press outside shows nothing and a release outside does not act"
      (let [p (tick s :press outside)
            r (tick (tick p :down outside) :release inside)]
        (is (= 0 (:frame p)))
        (is (= 0 (:clicks r)))))
    (testing "the click count adds up over taps"
      (let [tap (fn [st] (tick (tick (tick st :press inside) :down inside) :release inside))]
        (is (= 3 (:clicks (nth (iterate tap s) 3))))))
    (testing "idle never moves the count, and the frame holds through the first sixty frames"
      (is (= (dissoc s :tick) (dissoc (tick s :idle nil) :tick))))))

(deftest a-slid-off-finger-shows-hover-and-does-not-act
  (let [s (fresh)
        held (-> s (tick :press inside) (tick :down inside))]
    (testing "slid off the button, still down: the hover frame"
      (is (= 1 (:frame (tick held :down outside)))))
    (testing "sliding back in shows pressed again"
      (is (= 2 (:frame (tick (tick held :down outside) :down inside)))))
    (testing "released off the button: no click"
      (let [r (tick (tick held :down outside) :release inside)]
        (is (= 0 (:clicks r)))
        (is (= 0 (:frame r)))))
    (testing "a finger that began elsewhere never shows hover"
      (let [e (-> s (tick :press outside) (tick :down [30.0 1600.0]))]
        (is (= 0 (:frame e)))))
    (testing "a finger that slides IN from outside shows pressed, and its release acts, as the original's does"
      (let [e (-> s (tick :press outside) (tick :down inside))]
        (is (= 2 (:frame e)))
        (is (= 1 (:clicks (tick e :release outside))))))))

(defn- frames-of
  "The `:frame` after each of `n` updates of `state` under `step`, a function
  from state to state."
  [state step n]
  (map :frame (rest (take (inc n) (iterate step state)))))

(deftest the-idle-cycle-is-the-originals
  (let [s (fresh)
        idle (fn [st] (tick st :idle nil))
        frames (frames-of s idle 360)]
    (testing "sixty frames each of 0, 1 and 2 in turn, as (quot (mod frame 180) 60)"
      (is (= (concat (repeat 60 0) (repeat 60 1) (repeat 60 2)) (take 180 frames)))
      (is (= (take 180 frames) (drop 180 frames)) "and round again"))
    (testing "the first drawn frame is the original's frame 0"
      (is (= 0 (:frame (idle s))))
      (is (= 1 (:frame (nth (iterate idle s) 61)))))
    (testing "the hint says cycling until the frames stop"
      (is (= sc/idle-hint (sc/hint s)))
      (is (= "cycling the three frames until you touch it" (sc/hint (idle s)))))))

(deftest a-finger-stops-the-cycle-and-a-release-resumes-it
  (let [idle (fn [st] (tick st :idle nil))
        s (nth (iterate idle (fresh)) 70)
        cycling (:frame (idle s))]
    (is (= 1 cycling) "70 frames in, the cycle is on hover")
    (testing "a press on the button shows pressed that frame, not the cycle"
      (let [p (tick s :press inside)]
        (is (= 2 (:frame p)))
        (is (= sc/hint-line (sc/hint p)))))
    (testing "a press elsewhere shows normal, not the cycle"
      (let [p (tick s :press outside)]
        (is (= 0 (:frame p)))
        (is (= sc/hint-line (sc/hint p)))))
    (testing "held, the cycle does not run, though the count does"
      (let [held (nth (iterate #(tick % :down outside) (tick s :press outside)) 100)]
        (is (= 0 (:frame held)))
        (is (= (+ 70 101) (:tick held)))))
    (testing "a release resumes the cycle where the count has got to"
      (let [held (nth (iterate #(tick % :down outside) (tick s :press outside)) 100)
            r (tick held :release outside)]
        (is (= (quot (mod (:tick held) 180) 60) (:frame r)))
        (is (= sc/idle-hint (sc/hint r)))))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= :spritebutton (:id (sc/scene))))
    (is (= "Sprite Button" (:title (sc/scene))))
    (is (= "clicks: 0" (sc/clicks-line 0)))
    (is (= ["normal" "hover" "pressed"] sc/frame-names))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  [bx by bw bh] (:button dims)
                  [px py pw ph] (:preview dims)]]
      (testing (str screen)
        (testing "the button shows the third of the sheet its frame lives in"
          (doseq [f [0 1 2]
                  :let [q (sc/button-quad (assoc s :frame f) dims)]]
            (is (< (abs (- (/ f 3.0) (:v0 q))) 1e-12))
            (is (< (abs (- (/ (inc f) 3.0) (:v1 q))) 1e-12))
            (is (= [bx by bw bh] ((juxt :x :y :width :height) q)))))
        (testing "the button keeps the sheet's 160:48 shape and is a finger-sized target"
          (is (< (abs (- (/ bw bh) (/ 160.0 48))) 1e-9))
          (is (>= bh 60)))
        (testing "button and preview are on the screen, below Back, and apart"
          (is (and (>= bx 0) (<= (+ bx bw) w) (>= by 120) (<= (+ by bh) h)))
          (is (and (>= px 0) (<= (+ px pw) w) (>= py 120) (<= (+ py ph) h)))
          (is (or (<= (+ bx bw) px) (<= (+ px pw) bx) (<= (+ by bh) py) (<= (+ py ph) by))))
        (testing "the preview is the whole sheet at 160:144"
          (is (< (abs (- (/ pw ph) (/ 160.0 144))) 1e-9))
          (let [q (sc/preview-quad dims)]
            (is (= [px py pw ph] ((juxt :x :y :width :height) q)))
            (is (nil? (:v0 q))))
          (is (< (abs (- (* 3 (:frame-h dims)) ph)) 1e-9)))
        (testing "the outline hugs the frame in use, inside the preview"
          (doseq [f [0 1 2]
                  :let [rects (sc/outline-rects (assoc s :frame f) dims)
                        fy (+ py (* f (:frame-h dims)))]]
            (is (= 4 (count rects)))
            (doseq [[x y rw rh] rects]
              (is (>= x px))
              (is (<= (+ x rw) (+ px pw 1e-9)))
              (is (>= y (- fy 1e-9)))
              (is (<= (+ y rh) (+ fy (:frame-h dims) 1e-9))))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines button preview pad]} (sc/dimensions {:screen screen} measure)
                {:keys [clicks state hint]} lines
                [_ by _ bh] button
                [px] preview]]
    (testing (str screen)
      (doseq [[nm s ln] [[:clicks (sc/clicks-line 99999) clicks]
                         [:state "pressed" state]
                         [:hint sc/hint-line hint]
                         [:idle-hint sc/idle-hint hint]]
              :let [{:keys [x y size]} ln]]
        (testing nm
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (<= (+ y size) h))
          (is (>= y 120) "below Back")))
      (testing "the top two stay left of the preview and above the button"
        (doseq [[s ln] [[(sc/clicks-line 99999) clicks] ["pressed" state]]]
          (is (<= (+ (:x ln) (measure s (:size ln))) (- px pad)))
          (is (<= (+ (:y ln) (:size ln)) by))))
      (testing "the hint is below the button"
        (is (>= (:y hint) (+ by bh)))))))
