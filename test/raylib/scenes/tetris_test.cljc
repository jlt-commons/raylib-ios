(ns raylib.scenes.tetris-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.tetris :as t]))

(def m {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def d (t/dimensions m))
(def dl (t/dimensions landscape))
(def cell (:cell d))

(defn- with-piece [state type rot x y]
  (assoc state :piece {:type type
                       :rot rot
                       :x x
                       :y y}))

;; The seed picks the first piece, so pin it to a T and the next to an O.
(def start (-> (first ((:init (t/scene)) {:metrics m}))
               (with-piece :t 0 3 0)
               (assoc :next :o)))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (t/advance state {:metrics metrics
                     :pointer {:phase phase
                               :position position}})))

(defn- idle [state] (step state :idle nil))

(defn- frames [state n] (nth (iterate idle state) n))

(defn- tap [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- swipe-down [state]
  (-> state (step :press [600 1500]) (step :down [600 1800]) (step :release [600 1800])))

(defn- x-at
  "The finger's x after `n` cells of travel from a press at x 600. `n` may be
  fractional."
  [n]
  (+ 600 (* n cell)))

(defn- drag-to
  "Press at x 600, then one `:down` frame `n` cells away. The release is left
  to the caller."
  [state n]
  (-> state (step :press [600 1500]) (step :down [(x-at n) 1500])))

(defn- px [state] (get-in state [:piece :x]))

(defn- block
  "`board` with the given `[row col]` cells filled."
  [board cells]
  (reduce (fn [b [r c]] (assoc-in b [r c] :t)) board cells))

(deftest a-drag-moves-the-piece-one-column-per-cell
  (is (= 3 (px start)))
  (testing "the press alone moves nothing"
    (is (= 3 (px (step start :press [600 1500])))))
  (testing "one cell of travel is one column, either way"
    (is (= 4 (px (drag-to start 1))))
    (is (= 2 (px (drag-to start -1)))))
  (testing "a frame that jumps several cells steps there column by column"
    (is (= 5 (px (drag-to start 2))))
    (is (= 0 (px (drag-to start -3)))))
  (testing "the travel is rounded to the nearest cell"
    (is (= 4 (px (drag-to start 1.4))))
    (is (= 5 (px (drag-to start 1.6))))
    (is (= 3 (px (drag-to start 0.4)))))
  (testing "the column is relative to where the piece was at the press"
    (let [st (with-piece start :t 0 5 0)]
      (is (= 6 (px (drag-to st 1))))))
  (testing "dragging back returns the piece, so it follows the finger"
    (let [there (drag-to start 2)
          back (step there :down [(x-at 0) 1500])]
      (is (= 5 (px there)))
      (is (= 3 (px back)))))
  (testing "vertical travel changes nothing"
    (is (= 3 (px (-> start (step :press [600 1500]) (step :down [600 1900])))))))

(deftest a-drag-cannot-move-through-a-wall-or-block
  (testing "the left and right walls"
    (is (= 0 (px (drag-to start -9))))
    (is (= 7 (px (drag-to start 9)))))
  (testing "a block stops the piece one column short"
    ;; The T's cells sit at columns x to x+2 on its second row. A block at row 1,
    ;; column 6 makes x = 4 illegal, so a long drag right stops at 3.
    (let [st (assoc start :board (block (:board start) [[1 6]]))]
      (is (= 3 (px (drag-to st 4))))
      (is (= 3 (px (drag-to st 1))))))
  (testing "a block further along stops it at the last free column"
    (let [st (assoc start :board (block (:board start) [[1 8]]))]
      (is (= 5 (px (drag-to st 4))))))
  (testing "it can drag back out the other way once stopped"
    (let [st (assoc start :board (block (:board start) [[1 6]]))
          stuck (drag-to st 4)]
      (is (= 1 (px (step stuck :down [(x-at -2) 1500])))))))

(deftest the-release-swipe-does-not-move-again
  (let [dragged (drag-to start 2)
        released (step dragged :release [(x-at 5) 100])]
    (is (= 5 (px dragged)))
    (testing "the gesture layer does see a horizontal swipe here"
      (let [[_ event] (gesture/track (:gesture dragged)
                                     {:metrics m
                                      :pointer {:phase :release
                                                :position [(x-at 5) 100]}})]
        (is (= {:type :swipe
                :dir :right} (select-keys event [:type :dir])))))
    (testing "and the piece stays where the drag left it"
      (is (= 5 (px released)))
      (is (= (:piece dragged) (:piece released))))
    (testing "a leftward drag and release behaves the same"
      (let [l (drag-to start -2)]
        (is (= 1 (px l)))
        (is (= 1 (px (step l :release [(x-at 0) 1500]))))))
    (testing "a stray :down after the release moves nothing"
      (is (= 5 (px (step released :down [(x-at -3) 1500])))))))

(deftest a-press-under-back-does-not-start-a-drag
  (let [st (-> start (step :press [100 60]) (step :down [(+ 100 (* 3 cell)) 60]))]
    (is (= 3 (px st)))))

(deftest a-tap-rotates
  (let [after (tap start [600 1500])]
    (is (= 1 (:rot (:piece after))))
    (is (= 3 (px after)))
    (is (= 2 (:rot (:piece (tap after [600 1500])))))
    (testing "a rotation of the O piece changes nothing visible but still counts"
      (is (= (t/piece-cells (:piece (with-piece start :o 0 3 0)))
             (t/piece-cells (:piece (tap (with-piece start :o 0 3 0) [600 1500])))))))
  (testing "a tap in the Back region is the host's, not a rotation"
    (is (= 0 (:rot (:piece (tap start [100 60]))))))
  (testing "the press alone does not rotate"
    (is (= 0 (:rot (:piece (step start :press [600 1500])))))))

(deftest a-tap-is-too-short-to-drag
  ;; A tap's travel is at most the slop. The drag rounds to the nearest cell, so
  ;; the piece stays put while the slop is under half a cell.
  (doseq [[label metrics dm] [["portrait" m d]
                              ["landscape" landscape dl]]]
    (testing label
      (is (< (gesture/slop metrics) (/ (:cell dm) 2)))))
  (testing "a finger that wanders the whole slop still taps, and does not move the piece"
    (let [slop (gesture/slop m)
          st (-> start
                 (step :press [600 1500])
                 (step :down [(+ 600 slop) 1500])
                 (step :release [(+ 600 slop) 1500]))]
      (is (= 3 (px st)))
      (is (= 1 (:rot (:piece st)))))))

(deftest a-blocked-rotation-is-refused
  ;; The I piece lying flat at x 0 becomes a column at x + 2 when turned. A block
  ;; at row 2, column 2 is in the way.
  (let [st (-> start
               (with-piece :i 0 0 0)
               (assoc :board (block (:board start) [[2 2]])))
        after (tap st [600 1500])]
    (is (= 0 (:rot (:piece after))))
    (is (= (:piece st) (:piece after)))
    (testing "and it turns once the block is gone"
      (is (= 1 (:rot (:piece (tap (with-piece start :i 0 0 0) [600 1500]))))))
    (testing "a wall refuses it too"
      (is (= 1 (:rot (:piece (tap (with-piece start :i 1 -2 0) [600 1500]))))))))

(deftest a-swipe-down-hard-drops
  (let [after (swipe-down start)]
    (testing "the T lands on the floor"
      (is (= [:t :t :t] (subvec (get (:board after) 19) 3 6)))
      (is (= :t (get-in after [:board 18 4])))
      (is (= 4 (count (filter some? (flatten (:board after)))))))
    (testing "the next piece comes up at the top"
      (is (= (:next start) (:type (:piece after))))
      (is (= 0 (:y (:piece after))))
      (is (= 3 (px after))))
    (testing "the tick starts over"
      (is (= 0 (:tick after)))))
  (testing "it stops on a block, not just the floor"
    (let [st (assoc start :board (block (:board start) [[10 4]]))
          after (swipe-down st)]
      (is (= :t (get-in after [:board 9 4])))
      (is (= :t (get-in after [:board 8 4])))))
  (testing "a swipe up does nothing"
    (let [after (-> start (step :press [600 1800]) (step :down [600 1500]) (step :release [600 1500]))]
      (is (= (:piece start) (:piece after)))
      (is (= (:board start) (:board after)))))
  (testing "the swipe's own sideways drift moves the piece, as a drag does"
    (let [st (-> start (step :press [600 1500]) (step :down [(x-at 1) 1800]))]
      (is (= 4 (px st))))))

(deftest a-full-row-clears-and-scores
  (let [row19 (fn [b cs] (block b (for [c cs] [19 c])))
        lying (-> start
                  (with-piece :i 0 0 18)
                  (assoc :board (row19 (:board start) (range 4 10))))
        after (swipe-down lying)]
    (testing "one row scores 40"
      (is (= 40 (:score after)))
      (is (= 1 (:lines after)))
      (is (every? nil? (get (:board after) 19)))
      (is (zero? (count (filter some? (flatten (:board after))))))))
  (testing "four rows score 1200"
    (let [full (for [r (range 16 20) c (range 1 10)] [r c])
          st (-> start
                 (with-piece :i 1 -2 16)
                 (assoc :board (block (:board start) full)))
          after (swipe-down st)]
      (is (= 1200 (:score after)))
      (is (= 4 (:lines after)))))
  (testing "two rows score 100 and three 300"
    (doseq [[n pts] [[2 100] [3 300]]]
      (let [full (for [r (range (- 20 n) 20) c (range 1 10)] [r c])
            st (-> start
                   (with-piece :i 1 -2 (- 20 4))
                   (assoc :board (block (:board start) full)))
            after (swipe-down st)]
        (is (= pts (:score after)) (str n " rows"))
        (is (= n (:lines after))))))
  (testing "rows above a cleared one fall down"
    (let [st (-> start
                 (with-piece :i 0 0 18)
                 (assoc :board (block (:board start)
                                      (concat [[18 9]] (for [c (range 4 10)] [19 c])))))
          after (swipe-down st)]
      (is (= :t (get-in after [:board 19 9])))
      (is (= 1 (count (filter some? (flatten (:board after))))))))
  (testing "ten lines raise the level"
    (let [lying (-> start
                    (with-piece :i 0 0 18)
                    (assoc :lines 9 :board (block (:board start) (for [c (range 4 10)] [19 c]))))
          after (swipe-down lying)]
      (is (= 10 (:lines after)))
      (is (= 1 (:level after))))))

(deftest the-piece-falls-at-the-levels-interval
  (testing "the original's interval"
    (is (= 48 (t/interval 0)))
    (is (= 44 (t/interval 1)))
    (is (= 28 (t/interval 5)))
    (is (= 6 (t/interval 11)))
    (is (= 6 (t/interval 30))))
  (testing "level 0: one row after 48 frames, not 47"
    (is (= 0 (:y (:piece (frames start 47)))))
    (is (= 1 (:y (:piece (frames start 48)))))
    (is (= 2 (:y (:piece (frames start 96))))))
  (testing "level 5: every 28 frames"
    (let [st (assoc start :level 5)]
      (is (= 0 (:y (:piece (frames st 27)))))
      (is (= 1 (:y (:piece (frames st 28)))))))
  (testing "the tick starts over after a step"
    (is (= 0 (:tick (frames start 48)))))
  (testing "a piece that cannot fall locks at the interval and the next one comes"
    (let [st (-> start (with-piece :t 0 3 18) (assoc :tick 47))
          after (idle st)]
      (is (= :t (get-in after [:board 19 4])))
      (is (= 0 (:y (:piece after))))
      (is (= (:next st) (:type (:piece after))))))
  (testing "a lock by gravity can clear a line"
    (let [st (-> start
                 (with-piece :i 0 0 18)
                 (assoc :tick 47
                        :board (block (:board start) (for [c (range 4 10)] [19 c]))))]
      (is (= 40 (:score (idle st)))))))

(deftest the-next-piece-comes-from-the-lcg
  (let [after (swipe-down start)]
    (is (not= (:seed start) (:seed after)))
    (is (contains? (set t/piece-types) (:next after))))
  (testing "the same seed gives the same piece"
    (is (= (t/draw-piece 1234) (t/draw-piece 1234))))
  (testing "all seven pieces appear, about evenly, as with the original's 0..6"
    (let [counts (loop [seed 1 n 0 acc {}]
                   (if (= n 7000)
                     acc
                     (let [[ty seed'] (t/draw-piece seed)]
                       (recur seed' (inc n) (update acc ty (fnil inc 0))))))]
      (is (= (set t/piece-types) (set (keys counts))))
      (doseq [[ty n] counts]
        (is (< 800 n 1200) (str ty " drawn " n " times")))))
  (testing "it does not alternate with the LCG's low bit"
    (let [types (take 40 (map first (rest (iterate (fn [[_ s]] (t/draw-piece s)) [nil 7]))))]
      (is (> (count (set types)) 4)))))

(deftest the-pieces-are-the-originals
  (is (= {:i 2
          :o 1
          :t 4
          :s 2
          :z 2
          :j 4
          :l 4}
         (update-vals t/pieces (comp count :rots))))
  (doseq [[ty {:keys [rots]}] t/pieces
          rot rots]
    (is (= 4 (count rot)) (str ty))))

(deftest a-blocked-spawn-ends-the-game
  ;; The O piece spawns on columns 4 and 5 of rows 0 and 1.
  (let [st (-> start
               (with-piece :i 0 0 18)
               (assoc :next :o
                      :board (block (:board start) [[1 4]])))
        after (swipe-down st)]
    (is (:over? after))
    (testing "the locked piece is on the board"
      (is (= :i (get-in after [:board 19 0]))))
    (testing "and nothing moves afterwards"
      (is (= after (frames after 100)))
      (is (= (:piece after) (:piece (drag-to after 2)))))))

(deftest a-tap-restarts-after-game-over
  (let [over (assoc start :over? true :score 500 :lines 7
                    :board (block (:board start) [[19 0] [19 1]]))
        after (tap over [600 1400])]
    (is (not (:over? after)))
    (is (= 0 (:score after)))
    (is (= 0 (:lines after)))
    (is (zero? (count (filter some? (flatten (:board after))))))
    (is (= 0 (:rot (:piece after))))
    (testing "the restart keeps drawing from the same LCG sequence"
      (is (= (first (t/draw-piece (:seed over))) (:type (:piece after)))))
    (testing "a tap while alive rotates instead"
      (is (= 1 (:rot (:piece (tap start [600 1400]))))))
    (testing "a press alone, or a swipe, does not restart"
      (is (:over? (step over :press [600 1400])))
      (is (:over? (swipe-down over))))))

(deftest a-tap-under-back-does-not-restart
  (let [over (assoc start :over? true)]
    (is (:over? (tap over [100 60])))
    (is (:over? (tap over [399 119])))
    (testing "just outside it does restart"
      (is (not (:over? (tap over [400 119]))))
      (is (not (:over? (tap over [100 120])))))))

(deftest a-lock-mid-drag-hands-the-finger-to-the-next-piece
  (let [st (-> start (with-piece :t 0 3 18) (assoc :tick 47))
        locked (step st :press [600 1500])]
    (is (= :t (get-in locked [:board 19 4])))
    (is (= 3 (px locked)))
    (testing "the new piece follows from where the finger is now"
      (is (= 4 (px (step locked :down [(x-at 1) 1500]))))
      (is (= 2 (px (step locked :down [(x-at -1) 1500])))))))

(defn- overlaps? [[ax ay aw ah] [bx by bw bh]]
  (and (< ax (+ bx bw)) (< bx (+ ax aw))
       (< ay (+ by bh)) (< by (+ ay ah))))

(deftest the-well-starts-below-back
  (let [[bx by bw bh] gesture/back-region]
    (doseq [screen [[1206 2334] [2334 1206]]
            :let [dm (t/dimensions {:screen screen})
                  {:keys [wy preview-y next-y score-y]} dm]]
      (testing (str screen)
        (is (>= wy (+ by bh)))
        (is (>= preview-y (+ by bh)))
        (is (>= next-y (+ by bh)))
        (is (>= score-y (+ by bh)))
        (doseq [c (range t/cols) r (range t/rows)]
          (is (not (overlaps? [bx by bw bh] (t/cell-rect dm c r)))
              (str "cell " [c r] " overlaps Back")))
        (doseq [ty t/piece-types
                rect (t/preview-rects dm ty)]
          (is (not (overlaps? [bx by bw bh] rect)) (str ty " preview overlaps Back")))))))

(deftest the-well-and-panel-fit-the-screen
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [[w h] screen
                dm (t/dimensions {:screen screen})
                well [(:wx dm) (:wy dm) (* t/cols (:cell dm)) (* t/rows (:cell dm))]
                [wx wy ww wh] well]]
    (testing (str screen)
      (is (<= 0 wx))
      (is (<= (+ wx ww) w))
      (is (<= (+ wy wh) h))
      (doseq [ty t/piece-types
              [x y rw rh] (t/preview-rects dm ty)]
        (is (<= 0 x))
        (is (<= (+ x rw) w))
        (is (<= (+ y rh) h))
        (is (not (overlaps? well [x y rw rh])) (str ty " preview overlaps the well"))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [[w h] screen
                dm (t/dimensions {:screen screen})
                {:keys [text-size msg-size msg-x msg-y]} dm
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                well [(:wx dm) (:wy dm) (* t/cols (:cell dm)) (* t/rows (:cell dm))]
                lines [[(t/score-line 99999) (:score-x dm) (:score-y dm)]
                       [(t/lines-line 999) (:score-x dm) (:lines-y dm)]
                       [(t/level-line 99) (:score-x dm) (:level-y dm)]
                       [t/next-line (:next-x dm) (:next-y dm)]]]]
    (testing (str screen)
      (doseq [[line x y] lines
              :let [lw (* 0.6 text-size (count line))]]
        (is (<= 0 x) line)
        (is (<= (+ x lw) w) line)
        (is (<= 0 y) line)
        (is (<= (+ y text-size) h) line)
        (is (not (overlaps? well [x y lw text-size])) (str line " overlaps the well")))
      (let [msg-w (* 0.6 msg-size (count t/over-line))]
        (is (<= 0 msg-x))
        (is (<= (+ msg-x msg-w) w))
        (is (<= 0 msg-y))
        (is (<= (+ msg-y msg-size) h))))))

(deftest rotation-starts-a-new-game
  (let [played (-> start
                   (with-piece :s 1 6 9)
                   (assoc :score 900 :lines 12 :level 1 :tick 20 :over? true
                          :board (block (:board start) [[19 0] [19 1]])))
        after (step played landscape :idle nil)
        fresh (first ((:init (t/scene)) {:metrics landscape}))]
    (is (= [2334 1206] (:screen after)))
    (is (not (:over? after)))
    (is (= 0 (:score after)))
    (is (= 0 (:lines after)))
    (is (= 0 (:level after)))
    (is (zero? (count (filter some? (flatten (:board after))))))
    (is (= (:piece fresh) (:piece after)))
    (is (< (:wx dl) 2334))
    (testing "a rotation back starts another one"
      (is (= [1206 2334] (:screen (step after m :idle nil)))))))

(deftest the-first-frame-after-init-does-not-reset
  (let [one (idle start)]
    (is (= (:piece start) (:piece one)))
    (is (= (:seed start) (:seed one)))
    (is (= 1 (:tick one)))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1500] (:start (:gesture (step start :press [600 1500]))))))

(deftest the-scene-has-its-identity
  (is (= :tetris (:id (t/scene))))
  (is (= "Tetris" (:title (t/scene)))))

(def ^:private about-to-end
  "The I piece resting on a block that will make the next spawn impossible, with
  gravity due this frame, so the game ends on whatever frame comes next."
  (-> start
      (with-piece :i 0 0 18)
      (assoc :next :o :tick 100
             :board (block (:board start) [[1 4]]))))

(deftest a-touch-held-through-game-over-does-not-restart
  (let [over (step about-to-end :press [600 1500])]
    (is (:over? over))
    (testing "still down, then lifted: the short tap is not a restart"
      (let [lifted (-> over (step :down [600 1500]) (step :release [600 1500]))]
        (is (:over? lifted))
        (is (= (:board over) (:board lifted)))))))

(deftest a-fresh-tap-after-game-over-restarts
  (let [over (-> about-to-end (step :press [600 1500]) (step :release [600 1500]))
        after (tap over [600 1400])]
    (is (:over? over))
    (is (not (:over? after)))
    (is (zero? (count (filter some? (flatten (:board after))))))))
