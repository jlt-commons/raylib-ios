(ns net.b12n.raylib-ios.scenes.undoredo-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.undoredo :as ur]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (ur/dimensions m (fn [s size] (* 0.6 size (count s)))))
(def start (first ((:init (ur/scene)) {:metrics m})))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (ur/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position position}})))

(defn- idle [state] (step state :idle nil))

(defn- settle
  "Enough idle frames for the every-second-frame sample to have run."
  [state]
  (nth (iterate idle state) ur/sample-frames))

(def ^:private swipe-ends
  {:up [[600 1500] [600 1200]]
   :down [[600 1500] [600 1800]]
   :left [[600 1500] [300 1500]]
   :right [[600 1500] [900 1500]]})

(defn- swipe [state dir]
  (let [[from to] (swipe-ends dir)]
    (settle (-> state (step :press from) (step :down to) (step :release to)))))

(defn- tap [state pos]
  (settle (-> state (step :press pos) (step :release pos))))

(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(def field [600 1000])

(defn- undo [state] (tap state (centre (:undo d))))
(defn- redo [state] (tap state (centre (:redo d))))

(defn- pos [state] (select-keys (:player state) [:x :y]))

(deftest a-swipe-moves-one-cell-and-records-history
  (let [right (swipe start :right)]
    (is (= {:x 10
            :y 10} (pos start)))
    (is (= {:x 11
            :y 10} (pos right)))
    (is (= 2 (count (:history right))))
    (is (= 1 (:cursor right)))
    (is (= (:player right) (peek (:history right))))
    (testing "each direction is one cell"
      (is (= {:x 9
              :y 10} (pos (swipe start :left))))
      (is (= {:x 10
              :y 9} (pos (swipe start :up))))
      (is (= {:x 10
              :y 11} (pos (swipe start :down)))))
    (testing "and a wall stops the square without recording anything"
      (let [wall (nth (iterate #(swipe % :up) start) 14)]
        (is (= 0 (:y (:player wall))))
        (is (= 11 (count (:history wall))) "ten moves up from y 10, the other four did nothing")))))

(deftest a-tap-recolours-and-records-history
  (let [t (tap start field)]
    (is (= 1 (:color (:player t))))
    (is (= 2 (count (:history t))))
    (is (= 1 (:cursor t)))
    (is (= {:x 10
            :y 10} (pos t)) "it did not move")
    (testing "the colours cycle through the palette and come back"
      (is (= 0 (:color (:player (nth (iterate #(tap % field) start) (count ur/palette)))))))
    (testing "a tap on the square itself does the same"
      (let [sq (centre (ur/cell-rect (ur/geometry m) 10 10))]
        (is (= 1 (:color (:player (tap start sq)))))))
    (testing "a tap under Back does nothing"
      (let [b (tap start [100 50])]
        (is (= 1 (count (:history b))))
        (is (= 0 (:color (:player b))))))
    (testing "a tap on a button does not also recolour"
      (let [u (undo (swipe start :right))]
        (is (= 0 (:color (:player u))))
        (is (= 2 (count (:history u))))))))

(deftest undo-and-redo-walk-the-history
  (let [s3 (-> start (swipe :right) (swipe :down) (tap field))
        states (:history s3)]
    (is (= 4 (count states)))
    (is (= 3 (:cursor s3)))
    (let [u1 (undo s3)
          u2 (undo u1)
          u3 (undo u2)
          u4 (undo u3)]
      (is (= [2 1 0 0] (mapv :cursor [u1 u2 u3 u4])) "undo stops at the oldest state")
      (is (= (nth states 2) (:player u1)))
      (is (= (nth states 0) (:player u3)))
      (is (= 4 (count (:history u4))) "undo keeps the states ahead")
      (let [r1 (redo u3)
            r3 (redo (redo r1))
            r4 (redo r3)]
        (is (= (nth states 1) (:player r1)))
        (is (= 3 (:cursor r3)))
        (is (= (nth states 3) (:player r3)))
        (is (= r3 (assoc r4 :ticks (:ticks r3)
                         :gesture (:gesture r3))) "redo stops at the newest state")))
    (testing "the buttons never record, so a walk adds no state"
      (is (= 4 (count (:history (undo (undo s3)))))))))

(deftest a-new-action-after-undo-clears-redo
  (let [s3 (-> start (swipe :right) (swipe :right) (swipe :right))
        back (undo (undo s3))
        branch (swipe back :down)]
    (is (= 1 (:cursor back)))
    (is (= 4 (count (:history back))))
    (testing "a move after the undo drops what was ahead"
      (is (= 3 (count (:history branch))))
      (is (= 2 (:cursor branch)))
      (is (= {:x 11
              :y 11} (pos branch)))
      (is (= branch (assoc (redo branch) :ticks (:ticks branch) :gesture (:gesture branch)))
          "redo has nothing to go to"))
    (testing "and so does a recolour"
      (let [c (tap back field)]
        (is (= 3 (count (:history c))))
        (is (= 2 (:cursor c)))))))

(deftest the-ring-holds-the-originals-slot-count
  (is (= 26 ur/max-states))
  (let [zig (fn [n] (nth (iterate (fn [[s i]] [(swipe s (if (even? i) :right :left)) (inc i)]) [start 0]) n))
        [s25] (zig 25)
        [s26] (zig 26)
        [s40] (zig 40)]
    (is (= 26 (count (:history s25))) "the start state and 25 more fill it")
    (is (= (:player start) (first (:history s25))))
    (is (= 26 (count (:history s26))) "the next one drops the oldest")
    (is (not= (:player start) (first (:history s26))))
    (is (= 26 (count (:history s40))))
    (is (= 25 (:cursor s40)))
    (is (= (:player s40) (peek (:history s40))) "the newest state is the square")
    (testing "undo walks back at most 25 steps"
      (let [back (nth (iterate undo s40) 30)]
        (is (= 0 (:cursor back)))
        (is (= 26 (count (:history back))))))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [g (ur/geometry {:screen screen})
                [w h] screen
                [_ back-y _ back-h] gesture/back-region
                back-bottom (+ back-y back-h)]]
    (testing (str screen)
      (is (>= (:grid-y g) back-bottom) "the grid starts below Back")
      (doseq [[bx by bw bh] [(:undo g) (:redo g)]]
        (is (>= by back-bottom))
        (is (>= bx 0))
        (is (<= (+ bx bw) w))
        (is (<= (+ by bh) h)))
      (let [[ux _ uw _] (:undo g)
            [rx] (:redo g)]
        (is (<= (+ ux uw) rx) "the buttons do not overlap"))
      (testing "the grid sits above the strip and the strip above the buttons"
        (is (<= (+ (:grid-y g) (* ur/cells-y (:cell g))) (:strip-y g)))
        (is (<= (+ (:strip-y g) (:strip-h g)) (second (:undo g)))))
      (testing "a tap on a button is not in the back region"
        (is (not (gesture/in-back-region? (centre (:undo g)))))
        (is (not (gesture/in-back-region? (centre (:redo g)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (ur/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 4 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the labels fit their buttons"
        (let [[_ _ uw _] (:undo dims)
              {:keys [s size]} (:undo-label dims)]
          (is (<= (measure s size) uw))))
      (testing "the grid and the strip fit across the width"
        (is (<= (+ (:grid-x dims) (* ur/cells-x (:cell dims))) w))
        (is (>= (:grid-x dims) 0))
        (is (<= (+ (:strip-x dims) (* ur/max-states (:slot dims))) w))))))

(deftest a-rotation-starts-over
  (let [moved (swipe start :right)
        turned (step moved {:screen [2334 1206]} :idle nil)]
    (is (= 2 (count (:history moved))))
    (is (= 1 (count (:history turned))))
    (is (= {:x 10
            :y 10} (pos turned)))))

(defn- release-at
  "A touch released on a frame that starts with `:ticks` 0, so the frame counts
  the first of a sample's frames. `from` and `to` are the touch's points."
  [state [from to] at-ticks]
  (let [pressed (step state :press from)
        down (step (assoc pressed :ticks at-ticks) :down to)]
    (step (assoc down :ticks at-ticks) :release to)))

(deftest history-is-sampled-every-second-frame
  (is (= 2 ur/sample-frames))
  (let [begin (fn [s] (assoc s :ticks 0))
        moved (release-at (begin start) (swipe-ends :right) 0)]
    (testing "a swipe on the first frame of a sample records nothing yet"
      (is (= 11 (:x (:player moved))))
      (is (= 1 (count (:history moved)))))
    (testing "and the next frame, which completes the sample, records it"
      (let [next-frame (idle moved)]
        (is (= 2 (count (:history next-frame))))
        (is (= (:player next-frame) (peek (:history next-frame))))))
    (testing "a tap is sampled the same way"
      (let [t (-> (begin start) (step :press field) (assoc :ticks 0) (step :release nil))]
        (is (= 1 (count (:history t))))
        (is (= 2 (count (:history (idle t)))))))))

(deftest an-undo-on-a-first-frame-discards-the-pending-move
  (let [one (swipe start :right)
        ;; A move made on the previous frame and not yet sampled.
        pending (assoc one :ticks 0 :player (assoc (:player one) :x 20))
        [ux uy] (centre (:undo d))
        undone (-> pending (step :press [ux uy]) (assoc :ticks 0) (step :release nil))]
    (is (= 2 (count (:history one))))
    (is (= 2 (count (:history undone))) "the pending move was never recorded")
    (is (= 0 (:cursor undone)))
    (is (= (first (:history one)) (:player undone)) "undo adopted the state at the cursor")
    (is (= 2 (count (:history (idle undone)))) "and nothing is recorded after it either")))
