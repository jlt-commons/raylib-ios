(ns raylib.scenes.inlinestyle-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.inlinestyle :as st]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(def black [0 0 0 255])

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- run [text fg bg] {:text text
                         :fg fg
                         :bg bg})

(deftest markup-parses-into-runs
  (testing "text without tags is one run in the base colour"
    (is (= [(run "no tags" black nil)] (st/parse "no tags" black))))
  (testing "nothing in, nothing out"
    (is (= [] (st/parse "" black))))
  (testing "a foreground tag changes the colour from there on"
    (is (= [(run "a " black nil) (run "red" [255 0 0 255] nil)]
           (st/parse "a [cFF0000FF]red" black))))
  (testing "a background tag paints behind the run, and hex is case-blind"
    (is (= [(run "x" black nil) (run "y" black [255 0 255 255])]
           (st/parse "x[bff00ffFF]y" black))))
  (testing "two tags in a row give one run with both"
    (is (= [(run "both" [0 255 0 255] [255 0 0 255])]
           (st/parse "[c00ff00ff][bff0000ff]both" black))))
  (testing "a tag touches its own slot and leaves the other one alone"
    (is (= [(run "b" [0 255 0 255] [255 0 0 255])]
           (st/parse "[bFF0000FF][c00FF00FF]b" black)))))

(deftest reset-restores-defaults
  (is (= [(run "x" [255 0 0 255] [0 0 255 255]) (run "y" black nil)]
         (st/parse "[cFF0000FF][b0000FFFF]x[r]y" black))
      "both colours go back, the foreground to the base and the background to nothing")
  (is (= [] (st/parse "[r]" black)) "a reset alone leaves no run")
  (is (= [(run "z" black nil)] (st/parse "[r][r]z" black)) "and costs nothing twice"))

(deftest alpha-multiplies-by-base
  (let [faded [0 0 0 100]]
    (is (= [(run "z" [255 0 0 53] nil)] (st/parse "[cFF000088]z" faded))
        "136 times 100 over 255, rounded down")
    (is (= [(run "z" [255 255 255 100] [0 0 0 100])]
           (st/parse "[cffffffff][b000000ff]z" faded))
        "an opaque tag takes the base alpha")
    (is (= [(run "z" faded nil)] (st/parse "z" faded)) "untagged text keeps the base")
    (is (= [(run "z" [255 0 0 136] nil)] (st/parse "[cFF000088]z" black))
        "and an opaque base leaves the tag's alpha as it was")))

(deftest a-malformed-tag-is-text
  (doseq [s ["a[cF00]b" "[xFF0000FF]" "[cFF0000FF" "[cGG0000FF]" "[" "[R]" "[c12345678"
             "[cFF0000FF ]" "[c FF0000F]" "[]"]]
    (testing s
      (is (= [(run s black nil)] (st/parse s black)) "kept whole, and nothing else is read into it")))
  (is (= [(run "[" black nil) (run "x" [255 0 0 255] nil)]
         (st/parse "[[cFF0000FF]x" black))
      "a stray bracket does not swallow the tag after it")
  (is (= [(run "a[c12]b" [255 0 0 255] nil)]
         (st/parse "[cFF0000FF]a[c12]b" black))
      "a bad tag inside a styled run stays in it"))

(deftest runs-lay-out-by-measure
  (doseq [screen screens
          :let [dims (st/dimensions {:screen screen})
                {:keys [lines]} (st/layout dims estimate [255 0 0 255])]]
    (testing (str screen)
      (is (= 6 (count lines)))
      (is (some #(< 1 (count (:runs %))) lines) "some line has several runs")
      (doseq [{:keys [size x runs]} lines]
        (is (= x (:x (first runs))) "the first run starts at the line's margin")
        (doseq [[a b] (partition 2 1 runs)]
          (is (<= (Math/abs (- (:x b) (+ (:x a) (estimate (:text a) size)))) 1)
              "each run starts where the one before it ends"))))))

(deftest a-different-measure-changes-layout
  (doseq [screen screens
          :let [dims (st/dimensions {:screen screen})
                wide (fn [s size] (* 1.2 size (count s)))
                a (st/layout dims estimate [255 0 0 255])
                b (st/layout dims wide [255 0 0 255])
                multi (fn [l] (filter #(< 1 (count (:runs %))) (:lines l)))]]
    (testing (str screen)
      (is (seq (multi a)))
      (is (every? true? (map (fn [p q] (< (:x (second (:runs p))) (:x (second (:runs q)))))
                             (multi a) (multi b)))
          "a wider measure pushes every later run to the right")
      (is (= (map #(:x (first (:runs %))) (:lines a))
             (map #(:x (first (:runs %))) (:lines b)))
          "and leaves the first runs alone")
      (is (< (nth (:box a) 2) (nth (:box b) 2)) "the box round the last styled line grows"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (st/dimensions {:screen screen})
                {:keys [lines box]} (st/layout dims estimate [255 0 0 255])]]
    (testing (str screen)
      (doseq [{:keys [y size runs]} lines
              {:keys [text x]} runs]
        (is (>= x 0) text)
        (is (<= (+ x (estimate text size)) w) text)
        (is (>= y back-bottom) text)
        (is (<= (+ y size) h) text))
      (let [[bx by bw bh] box]
        (is (and (>= bx 0) (>= by back-bottom) (<= (+ bx bw) w) (<= (+ by bh) h)) "the box")))))

(deftest the-tint-changes-every-twenty-frames
  (let [s0 {:frame 0
            :seed 2026
            :tint [230 41 55 255]}
        states (take 61 (iterate #(st/advance % {}) s0))
        tints (map :tint states)]
    (testing "the first frame already rerolls, as the original's frame 0 does"
      (is (not= (:tint s0) (:tint (second states)))))
    (testing "and holds the colour for the frames in between"
      (is (apply = (map :tint (take 20 (rest states)))))
      (is (not= (:tint (nth states 20)) (:tint (nth states 21))))
      (is (apply = (map :tint (take 20 (drop 21 states))))))
    (is (every? (fn [[r g b a]] (and (every? #(<= 0 % 255) [r g b]) (= 255 a))) tints))
    (is (= (map :tint states)
           (map :tint (take 61 (iterate #(st/advance % {}) s0))))
        "the same seed replays")
    (let [reds (map (fn [seed] (first (:tint (st/advance {:frame 0
                                                          :seed seed
                                                          :tint nil} {}))))
                    (range 1 200))]
      (is (< 20 (count (distinct reds))) "the high bits vary"))))

(deftest the-scene-follows-the-screen
  (let [sc (st/scene)
        [state _] ((:init sc) {:metrics {:screen [800 450]}})
        [next _] ((:update sc) state {:metrics {:screen [450 800]}})]
    (is (= [450 800] (:screen next)))
    (is (= 1 (:frame next)) "one frame counted")
    (is (= "Inline Styling" (:title sc)))
    (is (= :inlinestyle (:id sc)))))
