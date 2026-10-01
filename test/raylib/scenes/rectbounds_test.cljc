(ns raylib.scenes.rectbounds-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.rectbounds :as rb]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (rb/dimensions m))
(def start (first ((:init (rb/scene)) {:metrics m})))

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (rb/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position position}})))

(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(defn- grab-point [state dims]
  (centre (rb/grab-rect dims (:box-w state) (:box-h state))))

(defn- lines-of [layout] (mapv first layout))

(deftest the-handle-resizes-the-box
  (let [at (grab-point start d)
        [x y] at
        held (step start :press at)
        moved (step held :down [(- x 100) (- y 80)])]
    (is (:holding? held))
    (is (= (:box-w start) (:box-w held)) "the press alone moves nothing")
    (is (= (- (:box-w start) 100) (:box-w moved)) "by the finger's travel, not to the finger")
    (is (= (- (:box-h start) 80) (:box-h moved)))
    (testing "and the box stops at its minimum and its maximum"
      (let [tiny (step held :down [0 0])
            huge (step (step tiny :down at) :down [5000 5000])]
        (is (= (:min-w d) (:box-w tiny)))
        (is (= (:min-h d) (:box-h tiny)))
        (is (<= (:box-w huge) (:max-w d)))
        (is (<= (:box-h huge) (:max-h d)))))))

(deftest the-grab-is-sticky
  (let [held (step start :press (grab-point start d))
        far (step held :down [900 1500])]
    (is (:holding? far) "still held, far outside the handle's own box")
    (is (not= (:box-h start) (:box-h far)))
    (is (not (:holding? (step far :release nil))) "and a lift lets go")
    (testing "a press away from the handle grabs nothing"
      (let [miss (step start :press [600 1200])
            drag (step miss :down [100 100])]
        (is (not (:holding? miss)))
        (is (= (:box-w start) (:box-w drag)))
        (is (= (:box-h start) (:box-h drag)))))))

(deftest the-wrap-button-toggles-mode
  (let [at (centre (:button d))
        tap #(-> % (step :press at) (step :release nil))
        once (tap start)
        twice (tap once)]
    (is (= :word (:wrap start)))
    (is (= :char (:wrap once)))
    (is (= :word (:wrap twice)))
    (testing "a tap elsewhere changes nothing"
      (is (= :word (:wrap (-> start (step :press [600 1200]) (step :release nil))))))))

(def prose "alpha beta gamma delta")

(deftest word-wrap-breaks-between-words
  (let [size 20
        ;; 11 chars at 12 px is 132 px, so a 150 px box (142 px inside) holds
        ;; "alpha beta" (10 chars, 120 px) but not "alpha beta gamma".
        out (lines-of (rb/layout-text prose 150.0 500.0 size estimate :word))]
    (is (= ["alpha beta" "gamma delta"] out))
    (is (every? #(= % (str/trim %)) out))))

(deftest char-wrap-breaks-mid-word
  (let [out (lines-of (rb/layout-text prose 150.0 500.0 20 estimate :char))]
    ;; 11 characters is 132 px and a 12th would be 144 px, past the 142 px
    ;; inside the box, so the line breaks after "alpha beta " and the next
    ;; one starts mid-word at "gamma".
    (is (= ["alpha beta " "gamma delta"] out) "fills the line to the edge")
    (is (every? #(<= (estimate % 20) 142.0) out))
    (testing "and a word wider than the box still breaks in word mode"
      (let [w (lines-of (rb/layout-text "abcdefghijklmnopqrstuvwxyz" 150.0 500.0 20 estimate :word))]
        (is (> (count w) 1))
        (is (every? #(<= (estimate % 20) 142.0) w))))))

(deftest lines-past-the-box-are-dropped
  (let [size 20
        tall (rb/layout-text rb/text 300.0 1000.0 size estimate)
        short (rb/layout-text rb/text 300.0 100.0 size estimate)]
    (is (> (count tall) (count short)))
    (is (= (take (count short) tall) short) "the same lines, cut off at the bottom")
    (is (every? (fn [[_ y]] (<= (+ y size) 100.0)) short))
    (is (pos? (count short)))
    (is (empty? (rb/layout-text rb/text 300.0 10.0 size estimate)))))

(deftest a-different-measure-changes-the-layout
  (let [wide (fn [s size] (* 1.2 size (count s)))
        a (rb/layout-text rb/text 400.0 600.0 20 estimate)
        b (rb/layout-text rb/text 400.0 600.0 20 wide)]
    (is (not= a b))
    (is (> (count b) (count a)) "a wider glyph wraps onto more lines")))

(deftest controls-avoid-back
  (doseq [screen screens
          :let [dims (rb/dimensions {:screen screen})
                s (first ((:init (rb/scene)) {:metrics {:screen screen}}))
                [bx by] (:button dims)
                [back-x back-y back-w back-h] gesture/back-region
                back-bottom (+ back-y back-h)
                back-right (+ back-x back-w)]]
    (testing (str screen)
      (is (>= (:box-y dims) back-bottom) "the box starts below Back")
      (is (>= by back-bottom))
      (let [[gx gy gw gh] (rb/grab-rect dims (:box-w s) (:box-h s))]
        (is (>= gy back-bottom) "the grab box too")
        (is (not (and (< gx back-right) (< gy back-bottom) (> (+ gx gw) back-x) (> (+ gy gh) back-y)))))
      (is (>= bx 0)))
    (testing "even the smallest box keeps its handle clear of Back"
      (let [[_ gy] (rb/grab-rect dims (:min-w dims) (:min-h dims))]
        (is (>= gy back-bottom))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [dims (rb/dimensions {:screen screen})
                [w h] screen
                [bx by bw bh] (:button dims)]]
    (testing (str screen)
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (estimate s size)) w) s)
        (is (<= (+ y size) h) s))
      (let [{:keys [s size]} (second (:lines dims))]
        (is (<= (estimate s size) bw) "the widest label fits the button")
        (is (<= (+ by (* 2 size)) (+ by bh))))
      (is (<= (+ bx bw) w))
      (is (<= (+ by bh) h))
      (testing "the wrapped text fits the box at its largest and at its smallest"
        (doseq [[bw' bh'] [[(:max-w dims) (:max-h dims)] [(:min-w dims) (:min-h dims)]]
                [line y] (rb/layout-text rb/text bw' bh' (:size dims) estimate)]
          (is (<= (estimate line (:size dims)) bw'))
          (is (<= (+ y (:size dims)) bh')))))))

(deftest the-box-fits-above-the-controls-at-its-largest
  (doseq [screen screens
          :let [dims (rb/dimensions {:screen screen})
                [w _] screen
                [_ hint-y] [0 (:y (first (:lines dims)))]
                [gx gy gw gh] (rb/grab-rect dims (:max-w dims) (:max-h dims))]]
    (is (<= (+ gx gw) w))
    (is (<= (+ gy gh) hint-y))))

(deftest a-rotation-starts-over
  (let [at (grab-point start d)
        shrunk (step (step start :press at) :down [(- (first at) 200) (second at)])
        turned (step shrunk {:screen [2334 1206]} :idle nil)
        dims (rb/dimensions {:screen [2334 1206]})]
    (is (< (:box-w shrunk) (:box-w start)))
    (is (= (:max-w dims) (:box-w turned)))
    (is (not (:holding? turned)))
    (testing "and the first frame does not reset"
      (is (= (:box-w shrunk) (:box-w (step shrunk :idle nil)))))))

(deftest measure-calls-per-layout
  (let [calls (atom 0)
        counting (fn [s size] (swap! calls inc) (estimate s size))]
    (rb/layout-text rb/text (:max-w d) (:max-h d) (:size d) counting :word)
    (let [word @calls]
      (reset! calls 0)
      (rb/layout-text rb/text (:max-w d) (:max-h d) (:size d) counting :char)
      ;; Measured with the original's text at the largest box on the phone:
      ;; 53 calls in word mode and 284 in char mode. The draw method caches the
      ;; layout, so an idle frame makes none.
      (is (= 53 word))
      (is (= 284 @calls)))))
