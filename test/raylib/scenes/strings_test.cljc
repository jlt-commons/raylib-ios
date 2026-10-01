(ns raylib.scenes.strings-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.strings :as st]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def dt (/ 1.0 60))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- wide-measure
  "A font a third wider than the estimate."
  [s size]
  (* 0.8 size (count s)))

(defn- fresh
  ([] (fresh m measure))
  ([metrics measure]
   (first ((:init (st/scene measure)) {:metrics metrics}))))

(def start (fresh))
(def d (:dims start))

(defn- step
  ([state phase position] (step state m phase position dt))
  ([state metrics phase position delta]
   (st/advance state
               {:metrics metrics
                :delta-seconds delta
                :pointer {:phase phase
                          :position position}}
               measure)))

(defn- idle [state] (step state :idle nil))

(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(defn- put
  "`state` with exactly these particles, `{:text :x :y}` plus optional `:vx :vy`,
  sized as the scene sizes them."
  [state ps]
  (let [{:keys [size pad]} (:size-info state)]
    (assoc state
           :particles
           (mapv (fn [{:keys [text x y vx vy]}]
                   {:text text
                    :x (double x)
                    :y (double y)
                    :w (+ (measure text size) (* 2.0 pad))
                    :h (+ size (* 2.0 pad))
                    :vx (double (or vx 0))
                    :vy (double (or vy 0))
                    :color [245 245 245 255]})
                 ps)
           :grab nil)))

(defn- middle [state i]
  (let [{:keys [x y w h]} (nth (:particles state) i)]
    [(+ x (* 0.5 w)) (+ y (* 0.5 h))]))

(defn- tap [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- texts [state] (mapv :text (:particles state)))

(def ax (let [[x] (:arena d)] x))
(def ay (let [[_ y] (:arena d)] y))

(deftest a-drag-and-release-throws-with-last-down-velocity
  (let [s (put start [{:text "abc"
                       :x (+ ax 100)
                       :y (+ ay 100)}])
        [px py] (middle s 0)
        s (step s :press [px py])
        ;; two fast frames, then four slow ones: only the last frames count
        xs (concat [(+ px 30) (+ px 60)] (map #(+ px 60 (* 6 %)) (range 1 5)))
        s (reduce (fn [st x] (step st :down [x py])) s xs)
        before (get-in s [:particles 0 :x])
        released (step s :release [3 3])
        p (first (:particles released))]
    (is (= (+ ax 100 (- (last xs) px)) before) "the particle followed the finger")
    (is (< (abs (- (:vx p) (* 360.0 st/friction))) 1e-6)
        "6 px a frame at 60 fps is 360 px a second, less one frame of friction")
    (is (zero? (:vy p)))
    (testing "the release position is never read"
      (let [again (step s :release [(+ px 900) (+ py 900)])]
        (is (= (:vx p) (:vx (first (:particles again)))))
        (is (= (:x p) (:x (first (:particles again)))))))
    (testing "and it flies on afterwards"
      (is (> (:x (first (:particles (idle released)))) (:x p))))))

(deftest a-long-press-cuts-a-particle-in-half
  (let [s (put start [{:text "abcdef"
                       :x (+ ax 100)
                       :y (+ ay 100)}])
        c (middle s 0)
        held (reduce (fn [st _] (step st :down c)) (step s :press c) (range gesture/long-press-frames))
        done (step held :release [0 0])]
    (is (= ["abc" "def"] (texts held)))
    (is (nil? (:grab held)) "the cut lets go of the finger")
    (testing "the rest of the touch does nothing more"
      (is (= ["abc" "def"] (texts done))))
    (testing "a long press never also shatters, even with shatter armed"
      (let [armed (tap s (centre (:shatter d)))
            c (middle armed 0)
            held (reduce (fn [st _] (step st :down c)) (step armed :press c) (range gesture/long-press-frames))
            done (step held :release [0 0])]
        (is (= ["abc" "def"] (texts done)))))
    (testing "a short hold does not cut"
      (let [short (reduce (fn [st _] (step st :down c)) (step s :press c) (range 10))]
        (is (= ["abcdef"] (texts short)))))
    (testing "an odd length leaves the extra character in a third piece, as the original does"
      (let [odd (put start [{:text "abcde"
                             :x (+ ax 100)
                             :y (+ ay 100)}])
            c (middle odd 0)
            held (reduce (fn [st _] (step st :down c)) (step odd :press c) (range gesture/long-press-frames))]
        (is (= ["ab" "cd" "e"] (texts held)))))))

(deftest shatter-splits-into-characters
  (let [s (put start [{:text "abcd"
                       :x (+ ax 100)
                       :y (+ ay 100)}])
        c (middle s 0)
        plain (tap s c)
        armed (tap s (centre (:shatter d)))
        done (tap armed c)]
    (is (= ["abcd"] (texts plain)) "a tap alone does nothing to the text")
    (is (true? (:shatter? armed)))
    (is (= ["a" "b" "c" "d"] (texts done)))
    (is (false? (:shatter? done)) "one shatter per arming")
    (testing "the button toggles"
      (is (false? (:shatter? (tap armed (centre (:shatter d)))))))
    (testing "armed, a tap on empty ground keeps it armed"
      (is (true? (:shatter? (tap armed [(+ ax 5) (+ ay 5)])))))
    (testing "a single character has nothing to break"
      (let [one (put start [{:text "a"
                             :x (+ ax 100)
                             :y (+ ay 100)}])
            armed (tap one (centre (:shatter d)))]
        (is (= ["a"] (texts (tap armed (middle armed 0)))))))
    (testing "the particle budget holds"
      (let [many (put start (for [i (range 98)] {:text "x"
                                                 :x (+ ax (* 3 i))
                                                 :y (+ ay 100)}))
            many (update many :particles conj (assoc (first (:particles (put start [{:text "abcd"
                                                                                     :x (+ ax 400)
                                                                                     :y (+ ay 300)}])))
                                                     :vx 0.0))
            armed (tap many (centre (:shatter d)))
            c (middle armed 98)
            after (tap armed c)]
        (is (= 99 (count (:particles after))) "99 + 4 would pass the cap, so nothing shatters")))))

(deftest shake-moves-every-particle
  (let [s (put start (for [i (range 5)] {:text "ab"
                                         :x (+ ax (* 150 i))
                                         :y (+ ay 100)}))
        shaken (tap s (centre (:shake d)))]
    (is (every? (fn [{:keys [vx vy]}] (and (zero? vx) (zero? vy))) (:particles s)))
    (is (= 5 (count (:particles shaken))))
    (is (every? (fn [{:keys [vx vy]}] (or (not (zero? vx)) (not (zero? vy)))) (:particles shaken)))
    (testing "within the original's 2000 px a second, scaled"
      (let [limit (* 2000.0 (get-in start [:size-info :u]))]
        (is (every? (fn [{:keys [vx vy]}] (and (<= (abs vx) limit) (<= (abs vy) limit)))
                    (:particles shaken)))))
    (testing "they actually travel"
      (let [later (nth (iterate idle shaken) 5)]
        (is (every? true? (map (fn [a b] (or (not= (:x a) (:x b)) (not= (:y a) (:y b))))
                               (:particles shaken) (:particles later))))))))

(deftest releasing-over-another-glues-them
  (let [a {:text "ab"
           :x (+ ax 100)
           :y (+ ay 100)}
        b {:text "cd"
           :x (+ ax 500)
           :y (+ ay 300)}
        s (put start [a b])
        [px py] (middle s 0)
        [tx ty] (middle s 1)
        drag (fn [st to] (-> st (step :press [px py]) (step :down [px py]) (step :down to) (step :release [0 0])))
        glued (drag s [tx ty])]
    (is (= ["abcd"] (texts glued)) "the dragged text comes first")
    (is (nil? (:grab glued)))
    (testing "released over nothing, they stay two"
      (is (= 2 (count (:particles (drag s [(+ px 250) py]))))))
    (testing "a tap on two overlapping particles does not glue them"
      (let [lap (put start [a (assoc a :x (+ ax 110))])]
        (is (= 2 (count (:particles (tap lap (middle lap 1))))))))
    (testing "a particle cannot grow past the arena, so a too-long glue is refused"
      (let [long-text (apply str (repeat 40 "m"))
            big (put start [(assoc a :text long-text) (assoc b :text long-text :x ax :y (+ ay 300))])
            [px py] (middle big 0)
            [tx ty] (middle big 1)
            out (-> big (step :press [px py]) (step :down [px py]) (step :down [tx ty]) (step :release [0 0]))]
        (is (= 2 (count (:particles out))))))))

(def ^:private plain "raylib => fun videogames programming!")

(def ^:private expected-cases
  "The six transforms in the original's key order 1 to 6, from the second. The
  opening sentence is already plain, so the first tap gives the second one."
  ["RAYLIB => FUN VIDEOGAMES PROGRAMMING!"
   "raylib => fun videogames programming!"
   "RaylibFunVideogamesProgramming"
   "raylib_fun_videogames_programming"
   "raylibFunVideogamesProgramming"
   plain])

(deftest the-case-button-cycles-six-transforms
  (let [button (centre (:case d))
        run (rest (reductions (fn [st _] (tap st button)) start (range 8)))]
    (is (= expected-cases (mapv #(first (texts %)) (take 6 run))))
    (is (= (first expected-cases) (first (texts (nth run 6)))) "and round again")
    (is (= (second expected-cases) (first (texts (nth run 7)))))
    (is (= plain (first (texts start))) "the opening sentence is the plain one")
    (is (every? #(= 1 (count (:particles %))) run) "each is one fresh sentence")
    (testing "the sentence lands inside the arena"
      (let [{:keys [x y w h]} (first (:particles (first run)))
            [ax' ay' aw ah] (:arena d)]
        (is (<= ax' x (+ x w) (+ ax' aw)))
        (is (<= ay' y (+ y h) (+ ay' ah)))))
    (testing "the label names the next transform"
      (is (= 6 (count (distinct (map st/case-label (range 6)))))))))

(deftest typing-is-dropped-and-disclosed
  (is (str/includes? st/dropped "typing"))
  (is (str/includes? st/dropped "keyboard"))
  (testing "no pointer input splits a sentence by character"
    (let [s (reduce (fn [st _] (idle st)) start (range 30))]
      (is (= 1 (count (:particles s)))))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [g (st/geometry {:screen screen})
                [w h] screen
                [_ back-y _ back-h] gesture/back-region
                back-bottom (+ back-y back-h)
                [arena-x arena-y arena-w arena-h] (:arena g)]]
    (testing (str screen)
      (is (>= arena-y back-bottom) "the arena starts below Back")
      (is (>= arena-x 0))
      (is (<= (+ arena-x arena-w) w))
      (doseq [[bx by bw bh] [(:shatter g) (:shake g) (:case g)]]
        (is (>= by back-bottom))
        (is (>= bx 0))
        (is (<= (+ bx bw) w))
        (is (<= (+ by bh) h))
        (is (<= (+ arena-y arena-h) by) "the arena sits above the buttons"))
      (let [[ux _ uw _] (:shatter g)
            [kx _ kw _] (:shake g)
            [cx] (:case g)]
        (is (<= (+ ux uw) kx))
        (is (<= (+ kx kw) cx)))
      (testing "a tap on a button is not in the back region"
        (doseq [k [:shatter :shake :case]]
          (is (not (gesture/in-back-region? (centre (k g))))))))))

(deftest a-tap-under-back-is-ignored
  (let [s (put start [{:text "abcd"
                       :x (+ ax 100)
                       :y (+ ay 100)}])
        armed (tap s (centre (:shatter d)))
        under (tap armed [100 50])]
    (is (= (:particles armed) (:particles under)))
    (is (true? (:shatter? under)))))

(deftest the-hosts-measure-on-the-input-wins
  (let [sc (st/scene)
        in (fn [pointer] {:metrics m
                          :measure wide-measure
                          :delta-seconds dt
                          :pointer pointer})
        [init-state] ((:init sc) (in {:phase :idle}))
        [updated] ((:update sc) init-state (in {:phase :idle}))
        want (:w (first (:particles (fresh m wide-measure))))
        dflt (:w (first (:particles (first ((:init (st/scene)) {:metrics m})))))]
    (is (= want (:w (first (:particles init-state)))) "init measures with the input's")
    (is (not= dflt want))
    (is (= want (:w (first (:particles updated)))) "and update agrees")
    (is (= (:size-info init-state) (:size-info (fresh m wide-measure))))
    (testing "a rotation re-measures with the input's too"
      (let [[turned] ((:update sc) updated (assoc (in {:phase :idle}) :metrics {:screen [800 450]}))]
        (is (= (:w (first (:particles (fresh {:screen [800 450]} wide-measure))))
               (:w (first (:particles turned)))))))))

(deftest a-different-measure-changes-layout
  (let [narrow (fresh m measure)
        wide (fresh m wide-measure)
        n1 (first (:particles narrow))
        w1 (first (:particles wide))]
    (is (not= (:w n1) (:w w1)))
    (is (< (:size (:size-info wide)) (:size (:size-info narrow)))
        "a wider font needs a smaller size to fit the screen")
    (testing "the text lines move too"
      (is (not= (map :x (:lines (st/dimensions m measure)))
                (map :x (:lines (st/dimensions m wide-measure))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          mz [measure wide-measure]
          :let [[w h] screen
                dims (st/dimensions {:screen screen} mz)
                [_ back-y _ back-h] gesture/back-region
                {:keys [size pad]} (:size-info dims)]]
    (testing (str screen)
      (is (= 5 (count (:lines dims))) "hint, count and the three button labels, the widest case one")
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (mz s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the labels fit their buttons"
        (doseq [[k label] [[:shatter (:shatter-label dims)] [:shake (:shake-label dims)]]
                :let [[_ _ bw _] (k dims)]]
          (is (<= (mz (:s label) (:size label)) bw)))
        (doseq [i (range 6)
                :let [[_ _ bw _] (:case dims)]]
          (is (<= (mz (st/case-label i) (:size (:case-label dims))) bw))))
      (testing "the opening sentence fits the arena"
        (let [fresh-state (fresh {:screen screen} mz)
              {:keys [x y w h]} (first (:particles fresh-state))
              [arena-x arena-y arena-w arena-h] (:arena dims)]
          (is (<= (+ w) arena-w))
          (is (<= arena-x x))
          (is (<= (+ x w) (+ arena-x arena-w)))
          (is (<= arena-y y))
          (is (<= (+ y h) (+ arena-y arena-h)))
          (is (<= h arena-h)))
        (is (pos? size))
        (is (pos? pad))))))

(deftest particles-stay-inside-the-arena
  (let [shaken (tap start (centre (:shake d)))
        later (reduce (fn [s _] (idle s)) shaken (range 400))]
    (is (every? (fn [{:keys [x y w h]}]
                  (let [[arena-x arena-y arena-w arena-h] (:arena d)]
                    (and (<= arena-x x) (<= (+ x w) (+ arena-x arena-w))
                         (<= arena-y y) (<= (+ y h) (+ arena-y arena-h)))))
                (:particles later)))
    (testing "friction bleeds the speed, as the original's per-frame 0.99 does"
      (let [s (put start [{:text "ab"
                           :x (+ ax 100)
                           :y (+ ay 100)
                           :vx 100.0}])
            one (idle s)]
        (is (< (abs (- (:vx (first (:particles one))) (* 100.0 st/friction))) 1e-9))))
    (testing "a wall bounce loses a tenth of the speed, then friction"
      (let [[arena-x _ arena-w] (:arena d)
            s (put start [{:text "ab"
                           :x (+ arena-x arena-w -50)
                           :y (+ ay 100)
                           :vx 6000.0}])
            p (first (:particles (idle s)))]
        (is (< (abs (- (:vx p) (* -6000.0 st/elasticity st/friction))) 1e-6))
        (is (= (+ arena-x arena-w) (+ (:x p) (:w p))))))))

(deftest a-negative-delta-moves-nothing
  (let [s (put start [{:text "ab"
                       :x (+ ax 100)
                       :y (+ ay 100)
                       :vx 100.0
                       :vy 100.0}])
        p (first (:particles (step s m :idle nil -0.5)))]
    (is (= (+ ax 100.0) (:x p)))
    (is (= (+ ay 100.0) (:y p)))))

(deftest the-first-frame-and-a-rotation
  (testing "the first frame needs no history"
    (is (= 1 (count (:particles start))))
    (is (= 1 (count (:particles (idle start))))))
  (testing "a rotation starts over"
    (let [s (tap start (centre (:case d)))
          turned (step s {:screen [2334 1206]} :idle nil dt)]
      (is (= [2334 1206] (:screen turned)))
      (is (= plain (first (texts turned))))
      (is (= 1 (:case turned))))))
