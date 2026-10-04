(ns net.b12n.raylib-ios.scenes.blendparticles-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.blendparticles :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))
(def u (/ 1206.0 800.0))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(def dims (sc/dimensions metrics measure))
(def top (:top dims))
(def button-centre
  (let [[bx by bw bh] (:button dims)]
    [(int (+ bx (* 0.5 bw))) (int (+ by (* 0.5 bh)))]))
(def in-field [600 1500])

;; The scene's state is mutable arrays updated in place, so every use below
;; starts from a fresh one rather than sharing a value.
(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick
  ([state] (tick state :idle nil))
  ([state phase position]
   (first ((:update (sc/scene)) state {:metrics metrics
                                       :pointer {:phase phase
                                                 :position position}}))))

(defn- tap [state pt] (-> state (tick :press pt) (tick :release pt)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- particles [state] (mapv #(sc/particle state %) (range sc/max-particles)))

(defn- circles
  "Every call `emit-particles!` makes: [cx cy radius r g b a]."
  [state dims]
  (let [out (atom [])]
    (sc/emit-particles! (fn [& args] (swap! out conj (vec args))) state dims)
    @out))

(defn- throws? [thunk]
  (try (thunk) false (catch #?(:cljs js/Error :default Exception) _ true)))

;; The LCG, as the other scenes' tests rebuild it, for the opening draws.
(defn- lcg-next [s] (mod (+ (* 1103515245 s) 12345) 2147483648))
(defn- lcg-roll [s lo hi] (+ lo (mod (quot s 65536) (inc (- hi lo)))))

(defn- reference-pool
  "The opening pool from the original's `fresh-particle` (particles_blending.clj
  lines 35-44), which draws r, g, b and then size, for 200 particles."
  []
  (loop [i 0 s 20261002 out []]
    (if (= i 200)
      out
      (let [s1 (lcg-next s) r (lcg-roll s1 0 255)
            s2 (lcg-next s1) g (lcg-roll s2 0 255)
            s3 (lcg-next s2) b (lcg-roll s3 0 255)
            s4 (lcg-next s3) sz (/ (lcg-roll s4 1 30) 20.0)]
        (recur (inc i) s4 (conj out {:x 0.0
                                     :y 0.0
                                     :r r
                                     :g g
                                     :b b
                                     :alpha 1.0
                                     :size sz
                                     :active? false}))))))

;; The original's activate-first-inactive and update-particle (lines 46-64).
(defn- ref-activate [ps mx my]
  (loop [i 0]
    (if (>= i 200)
      ps
      (if (:active? (nth ps i))
        (recur (inc i))
        (assoc ps i (assoc (nth ps i) :active? true :alpha 1.0 :x (double mx) :y (double my)))))))

(defn- ref-update [p]
  (if (:active? p)
    (let [alpha (- (:alpha p) 0.005)]
      (if (<= alpha 0.0)
        (assoc p :active? false)
        (assoc p :y (+ (:y p) (/ 3.0 2.0)) :alpha alpha)))
    p))

(defn- ref-frame
  "One original frame; `at` is [x y] in the original's units, or nil for none."
  [ps at]
  (mapv ref-update (if at (ref-activate ps (first at) (second at)) ps)))

(defn- units [[px py]] [(/ px u) (/ (- py top) u)])

(deftest particles-spawn-and-age-as-the-original
  (testing "the opening pool is the original's 200 fresh particles, from the LCG in the order r g b size"
    (is (= 200 sc/max-particles))
    (is (= (reference-pool) (particles (fresh))))
    (is (zero? (sc/active-count (fresh)))))
  (testing "nothing spawns without a finger, as the pool only grows while held here"
    (is (zero? (sc/active-count (tick (tick (fresh)) :idle nil)))))
  (testing "a finger down in the field activates the first inactive particle at that point, which then ages that same frame"
    (let [s1 (tick (fresh) :press in-field)
          [ux uy] (units in-field)
          p (sc/particle s1 0)]
      (is (= 1 (sc/active-count s1)))
      (is (:active? p))
      (is (near? ux (:x p)))
      (is (near? (+ uy 1.5) (:y p)) "GRAVITY 3.0 over 2 a frame, applied after the spawn")
      (is (near? (- 1.0 0.005) (:alpha p)))
      (is (= (select-keys (first (reference-pool)) [:r :g :b :size])
             (select-keys p [:r :g :b :size])) "the colour and size are kept for life")
      (let [s2 (tick s1 :down in-field)]
        (is (= 2 (sc/active-count s2)) "one a frame")
        (is (:active? (sc/particle s2 1)))
        (is (near? (+ uy 3.0) (:y (sc/particle s2 0)))))))
  (testing "400 frames of a moving finger agree with the original's loop, particle for particle"
    (let [path (fn [k] [(+ 100 (* 2 k)) (+ 400 (* 3 (mod k 90)))])]
      (loop [s (fresh) ref (reference-pool) k 0]
        (when (< k 400)
          (let [held? (< k 330)
                pt (path k)
                s' (cond (zero? k) (tick s :press pt)
                         held? (tick s :down pt)
                         (= k 330) (tick s :release pt)
                         :else (tick s))
                ref' (ref-frame ref (when held? (units pt)))]
            (when (or (< k 3) (zero? (mod k 40)) (= k 399))
              (is (= (mapv #(select-keys % [:x :y :alpha :active?]) ref')
                     (mapv #(select-keys % [:x :y :alpha :active?]) (particles s')))
                  (str "frame " k)))
            (recur s' ref' (inc k)))))))
  (testing "a particle fades out over about the original's 200 frames, as the original's own arithmetic does, and is then free for reuse"
    (let [s1 (tick (fresh) :press in-field)
          gone (loop [s s1 n 1]
                 (if (:active? (sc/particle s 0)) (recur (tick s) (inc n)) n))
          ref-gone (loop [p (first (ref-frame (reference-pool) (units in-field))) n 1]
                     (if (:active? p) (recur (ref-update p) (inc n)) n))]
      (is (= ref-gone gone))
      (is (<= 199 gone 201) "1.0 minus 0.005 a step reaches zero on step 200, give or take a float rounding")))
  (testing "the host owns Back, and the button is not a place to emit"
    (is (zero? (sc/active-count (tick (fresh) :press [100 60]))))
    (is (zero? (sc/active-count (tick (fresh) :press button-centre))))
    (is (zero? (sc/active-count (tick (fresh) :press [600 (- top 5)])))))
  (testing "a finger that began in Back or on the button never emits wherever it slides, and one begun in the field stops over the button"
    (is (zero? (sc/active-count (-> (fresh) (tick :press [100 60]) (tick :down in-field)))))
    (is (zero? (sc/active-count (-> (fresh) (tick :press button-centre) (tick :down in-field)))))
      ;; The arrays are updated in place, so each count is read before the next tick.
    (let [a (tick (fresh) :press in-field)
          na (sc/active-count a)
          b (tick a :down button-centre)
          nb (sc/active-count b)
          c (tick b :down in-field)]
      (is (= [1 1 2] [na nb (sc/active-count c)])))))

(deftest the-toggle-switches-alpha-and-additive
  (is (zero? (sc/blending (fresh))) "alpha first, as the original's blending 0")
  (is (= "ALPHA BLENDING" (sc/label (fresh))))
  (testing "a tap on the button flips it, and a second flips it back"
    (let [s1 (tap (fresh) button-centre)
          s2 (tap s1 button-centre)]
      (is (= 1 (sc/blending s1)))
      (is (= "ADDITIVE BLENDING" (sc/label s1)))
      (is (= [0 1 0 1] (mapv sc/blending (reductions tap (fresh) (repeat 3 button-centre)))))
      (is (zero? (sc/blending s2)))))
  (testing "the toggle comes on the release, and the label's colour is the original's (BLACK, then RAYWHITE)"
    (is (zero? (sc/blending (tick (fresh) :press button-centre))))
    (is (= [0 0 0 255] (sc/label-colour (fresh))))
    (is (= [245 245 245 255] (sc/label-colour (tap (fresh) button-centre)))))
  (testing "a tap elsewhere does not flip it, nor does one in Back"
    (is (zero? (sc/blending (tap (fresh) in-field))))
    (is (zero? (sc/blending (tap (fresh) [100 60])))))
  (testing "a drag that begins in the field and lifts on the button is not a tap on it"
    (is (zero? (sc/blending (-> (fresh) (tick :press in-field) (tick :down button-centre) (tick :release button-centre))))))
  (testing "a tap on the button emits nothing"
    (is (zero? (sc/active-count (tap (fresh) button-centre)))))
  (testing "the mode is the raylib.h value: 0 is BLEND_ALPHA, 1 is BLEND_ADDITIVE"
    (is (= [0 1] (mapv sc/blend-mode [(fresh) (tap (fresh) button-centre)])))))

(deftest the-default-blend-is-restored
  (doseq [mode [0 1]]
    (testing (str "mode " mode)
      (let [log (atom [])
            begin! (fn [m] (swap! log conj [:begin m]))
            end! (fn [] (swap! log conj [:end]))]
        (sc/call-blended! begin! end! mode (fn [] (swap! log conj [:draw])))
        (is (= [[:begin mode] [:draw] [:end]] @log))
        (reset! log [])
        (is (throws? (fn [] (sc/call-blended! begin! end! mode (fn [] (swap! log conj [:draw]) (throw (ex-info "boom" {})))))))
        (is (= [[:begin mode] [:draw] [:end]] @log) "a throwing draw still ends the mode")
        (reset! log [])
        (is (throws? (fn [] (sc/call-blended! (fn [m] (swap! log conj [:begin m]) (throw (ex-info "boom" {})))
                                              end! mode (fn [] (swap! log conj [:draw]))))))
        (is (= [[:begin mode] [:end]] @log) "a begin that throws still ends, and nothing is drawn")))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= [] (circles s dims)) "nothing is active, so no circle")
    (is (= 0 (sc/blending s)))
    (doseq [screen screens
            :let [[w h] screen
                  mt {:screen screen}
                  d (sc/dimensions mt measure)
                  [bx by bw bh] (:button d)]]
      (testing (str screen)
        (testing "the button is inside the screen, clear of Back, and holds the widest label"
          (is (>= bx 0))
          (is (<= (+ bx bw) w))
          (is (>= by back-bottom))
          (is (<= (+ by bh) h))
          (is (<= (measure "ADDITIVE BLENDING" (:label-size d)) bw)))
        (testing "the field is the original's 800 units wide, taller on a tall screen"
          (is (near? (/ w 800.0) (:u d)))
          (is (near? (/ (- h (:top d)) (:u d)) (:field-h d))))))
    (testing "after one held frame exactly one circle is drawn, where the particle is, the sprite's size, in its tint"
      (let [s1 (tick (fresh) :press in-field)
            p (sc/particle s1 0)
            [[cx cy radius r g b a] & more] (circles s1 dims)]
        (is (empty? more))
        (is (near? (* u (:x p)) cx))
        (is (near? (+ top (* u (:y p))) cy))
        (is (near? (* u 0.5 32.0 (:size p)) radius) "the 32 unit sprite times size, as a circle of that diameter")
        (is (= [(:r p) (:g p) (:b p)] [r g b]))
        (is (= (int (* 255 (:alpha p))) a))
        (is (= 253 a) "255 times 0.995 is 253.725, an int cut to 253")))
    (testing "a particle that has died is not drawn"
      (is (= 1 (count (circles (tick (fresh) :press in-field) dims))))
      (is (zero? (count (circles (reduce (fn [st _] (tick st)) (tap (fresh) in-field) (range 200)) dims)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [hint button label-size]
                 :as d} (sc/dimensions {:screen screen} measure)
                [bx _ bw _] button]]
    (testing (str screen)
      (let [{:keys [s x y size]} hint]
        (is (<= 0 x))
        (is (<= (+ x (measure s size)) w))
        (is (>= y back-bottom))
        (is (<= (+ y size) h))
        (is (>= size 8)))
      (doseq [label ["ALPHA BLENDING" "ADDITIVE BLENDING"]
              :let [lx (sc/label-x d label measure)]]
        (testing label
          (is (>= lx bx))
          (is (<= (+ lx (measure label label-size)) (+ bx bw 1e-9)))
          (is (>= label-size 8)))))))
