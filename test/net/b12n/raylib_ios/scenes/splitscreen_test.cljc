(ns net.b12n.raylib-ios.scenes.splitscreen-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.camera2d :as cam]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.splitscreen :as ss]))

(def portrait {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (ss/dimensions portrait (fn [s size] (* 0.6 size (count s)))))
(def start (first ((:init (ss/scene)) {:metrics portrait})))
(def slop (gesture/slop portrait))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))
(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])
(defn- plus [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- step
  "One frame with `points` as the touch points."
  ([state points] (step state portrait points))
  ([state metrics points]
   (ss/advance state {:metrics metrics
                      :pointer {:phase (if (seq points) :down :idle)
                                :position (first points)}
                      :touch-points (vec points)})))

(def c1 (centre (first (:halves d))))
(def c2 (centre (second (:halves d))))

(defn- p1 [s] (get-in s [:players 0]))
(defn- p2 [s] (get-in s [:players 1]))

(deftest the-worlds-are-the-originals
  (is (= [200.0 200.0] (p1 start)))
  (is (= [250.0 200.0] (p2 start)))
  (is (= 3.0 ss/player-speed))
  (is (= [20 11 40] [ss/cols ss/rows ss/cell])))

(deftest each-half-steers-its-own-player
  (let [a (step start [c1])
        a (step a [(plus c1 300 0)])]
    (testing "a drag in half one right moves player one at 3 units, player two stays"
      (is (near? 203.0 (first (p1 a))))
      (is (near? 200.0 (second (p1 a))))
      (is (= (p2 start) (p2 a)))))
  (let [b (-> start (step [c2]) (step [(plus c2 0 -300)]))]
    (testing "a drag in half two up moves player two, player one stays"
      (is (near? 197.0 (second (p2 b))))
      (is (near? 250.0 (first (p2 b))))
      (is (= (p1 start) (p1 b)))))
  (testing "the stick is normalised: a diagonal is no faster"
    (let [s (-> start (step [c1]) (step [(plus c1 300 300)]))
          [x y] (p1 s)
          r (/ 3.0 (Math/sqrt 2.0))]
      (is (near? (+ 200.0 r) x))
      (is (near? (+ 200.0 r) y))))
  (testing "it keeps going while held and stops when the finger lifts"
    (let [held (-> start (step [c1]) (step [(plus c1 300 0)]) (step [(plus c1 300 0)]))
          lifted (step held [])]
      (is (near? 206.0 (first (p1 held))))
      (is (= (p1 held) (p1 lifted)))
      (is (= [nil nil] (:sticks lifted))))))

(deftest both-thumbs-at-once
  (let [a (plus c1 0 0)
        b (plus c2 0 0)
        s (-> start
              (step [a b])
              (step [(plus a 300 0) (plus b -300 0)]))
        swapped (-> start
                    (step [a b])
                    (step [(plus b -300 0) (plus a 300 0)]))]
    (testing "both players move in the same frame"
      (is (near? 203.0 (first (p1 s))))
      (is (near? 247.0 (first (p2 s)))))
    (testing "the order of the points never matters, between frames either"
      (is (= (:players s) (:players swapped)))
      (let [flip (-> start
                     (step [a b])
                     (step [(plus a 300 0) (plus b -300 0)])
                     (step [(plus b -300 0) (plus a 300 0)]))
            same (-> start
                     (step [a b])
                     (step [(plus a 300 0) (plus b -300 0)])
                     (step [(plus a 300 0) (plus b -300 0)]))]
        (is (= (:players same) (:players flip)))))
    (testing "lifting one thumb stops only its player"
      (let [one (step s [(plus a 300 0)])]
        (is (near? 206.0 (first (p1 one))))
        (is (= (p2 s) (p2 one)))
        (is (nil? (second (:sticks one))))))))

(deftest a-tap-does-not-move
  (let [tap (-> start (step [c1]) (step [c1]) (step []))
        shaky (-> start (step [c1]) (step [(plus c1 slop 0)]))
        both (-> start (step [c1 c2]) (step [c1 c2]))]
    (is (= (:players start) (:players tap)))
    (is (= (:players start) (:players shaky)) "exactly the slop is still inside it")
    (is (= (:players start) (:players both)))
    (testing "the press frame moves nothing even for a finger far from the centre"
      (is (= (:players start) (:players (step start [(plus c1 500 0)])))))
    (testing "just past the slop it moves"
      (is (not= (:players start)
                (:players (-> start (step [c1]) (step [(plus c1 (+ slop 5) 0)]))))))))

(deftest a-thumb-sliding-across-the-divider
  (let [down (-> start (step [c1]) (step [(plus c1 300 0)]))
        [_ dy _ _] (second (:halves d))
        over [(first c1) (+ dy 50)]
        crossed (step down [over])
        after (step crossed [(plus over 300 0)])]
    (testing "the stick it left ends, so that player stops"
      (is (= (p1 down) (p1 crossed)))
      (is (nil? (first (:sticks crossed)))))
    (testing "the other half starts a fresh stick at the crossing, so nothing jumps"
      (is (= (p2 start) (p2 crossed)))
      (is (= over (get-in crossed [:sticks 1 :centre]))))
    (testing "and it steers from there"
      (is (near? 253.0 (first (p2 after)))))))

(deftest two-thumbs-in-one-half-follow-the-nearer
  (let [a (plus c1 0 0)
        held (-> start (step [a]) (step [(plus a 300 0)]))
        ;; a second finger lands far away in the same half; the first moves on.
        far (plus a -400 0)
        both (step held [far (plus a 310 0)])]
    (testing "the stick keeps the finger nearest where it was, in either order"
      (is (= (:sticks both) (:sticks (step held [(plus a 310 0) far]))))
      (is (= (plus a 310 0) (get-in both [:sticks 0 :finger])))
      (is (= a (get-in both [:sticks 0 :centre]))))
    (testing "the player does not jump to the stranger"
      (is (near? 206.0 (first (p1 both)))))))

(deftest a-touch-under-back-starts-nothing
  (let [s (-> start (step [[100.0 60.0]]) (step [[300.0 60.0]]))]
    (is (= (:players start) (:players s)))
    (is (= [nil nil] (:sticks s)))))

(deftest a-rotation-restarts-the-sticks
  (let [held (-> start (step [c1]) (step [(plus c1 300 0)]))
        turned (step held landscape [(plus c1 300 0)])]
    (is (some? (first (:sticks held))))
    (testing "a finger held through it starts a fresh stick where it is now"
      (let [at (plus c1 300 0)]
        (is (= [{:centre at
                 :finger at} nil] (:sticks turned)))))
    (testing "and with no finger there is none"
      (is (= [nil nil] (:sticks (step held landscape [])))))
    (is (= (p1 held) (p1 turned)))
    (is (= [2334 1206] (:screen turned)))))

(deftest portrait-stacks-landscape-sides
  (doseq [screen screens
          :let [[w h] screen
                dims (ss/dimensions {:screen screen} measure)
                [[ax ay aw ah] [bx by bw bh]] (:halves dims)
                [_ fy fw fh] (:field dims)]]
    (testing (str screen)
      (if (< w h)
        (do (is (true? (:portrait? dims)))
            (is (near? ax bx))
            (is (< ay by) "player one on top")
            (is (near? aw w))
            (is (near? (+ ay ah) (- by (nth (:divider dims) 3)))))
        (do (is (false? (:portrait? dims)))
            (is (near? ay by))
            (is (< ax bx) "player one on the left")
            (is (near? ah fh))))
      (is (near? aw bw))
      (is (near? ah bh))
      (is (near? (+ by bh) (+ fy fh)) "the second half runs to the field's bottom")
      (is (>= ay (+ (nth gesture/back-region 1) (nth gesture/back-region 3))) "below Back")
      (is (<= (+ bx bw) (+ fw 1e-6)))
      (testing "a point is in the half it lies in"
        (is (= 0 (ss/half-of dims (centre [ax ay aw ah]))))
        (is (= 1 (ss/half-of dims (centre [bx by bw bh])))))
      (testing "the base zoom fits the original's 400x440 view in a half"
        (is (<= (* (:base-zoom dims) 400.0) (+ aw 1e-6)))
        (is (<= (* (:base-zoom dims) 440.0) (+ ah 1e-6)))
        (is (or (near? (* (:base-zoom dims) 400.0) aw)
                (near? (* (:base-zoom dims) 440.0) ah)))))))

(deftest banners-fit
  (doseq [screen screens
          :let [dims (ss/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 2 (count (:banners dims))))
      (doseq [[i {:keys [rect s x y size]}] (map-indexed vector (:banners dims))
              :let [[hx hy hw hh] (nth (:halves dims) i)
                    [bx by bw bh] rect]]
        (is (near? hx bx))
        (is (near? hy by))
        (is (near? hw bw) "the banner spans its half")
        (is (<= bh hh))
        (is (>= x bx))
        (is (<= (+ x (measure s size)) (+ bx bw)) s)
        (is (>= y by))
        (is (<= (+ y size) (+ by bh)) s)))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (ss/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 2 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s)))))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  s (first ((:init (ss/scene)) {:metrics metrics}))
                  dims (ss/dimensions metrics measure)]]
      (is (= 2 (count (:players s))) (str screen))
      (is (every? number? (flatten (:players s))))
      (is (= [nil nil] (:sticks s)))
      (doseq [i [0 1]
              :let [c (ss/camera s dims i)
                    [hx hy hw hh] (nth (:halves dims) i)
                    [sx sy] (cam/world->screen c (nth (:players s) i))]]
        (testing (str "camera " i)
          (is (pos? (:zoom c)))
          (is (= (centre [hx hy hw hh]) (:offset c)))
          (is (= (nth (:players s) i) (:target c)))
          (is (every? number? (concat (:offset c) (:target c) [(:rotation c) (:zoom c)])))
          (is (near? sx (first (:offset c))) "the player sits at the viewport centre")
          (is (near? sy (second (:offset c)))))))))

(deftest a-stick-belongs-to-the-finger-that-started-it
  (let [a c1
        held (-> start (step [a]) (step [(plus a 300 0)]))
        far (plus a -400 0)]
    (testing "the followed thumb swapped for another finger in one frame: the player stops"
      (let [swapped (step held [far])]
        (is (= (p1 held) (p1 swapped)) "no reversal")
        (is (= {:centre far
                :finger far} (first (:sticks swapped))) "a fresh stick at the stranger")
        (testing "and it steers from there"
          (let [on (step swapped [(plus far 300 0)])]
            (is (near? (+ 3.0 (first (p1 swapped))) (first (p1 on))))))))
    (testing "the thumb lifts while a stranger rests in the half: the player stops"
      (let [rest (-> start (step [a]) (step [(plus a 300 0) far]))
            lifted (step rest [far])]
        (is (near? 203.0 (first (p1 rest))))
        (is (= (p1 rest) (p1 lifted)) "no reversal")
        (is (= {:centre far
                :finger far} (first (:sticks lifted))))))
    (testing "a fast but plausible move within one frame is still the same finger"
      (let [moved (step held [(plus a 300 200)])]
        (is (= a (get-in moved [:sticks 0 :centre])))
        (is (= (plus a 300 200) (get-in moved [:sticks 0 :finger])))))))

(deftest the-follow-bound-is-sized-to-the-half
  (doseq [screen screens
          :let [dims (ss/dimensions {:screen screen} measure)
                half (first (:halves dims))
                [_ _ w h] half
                b (ss/follow-bound half)]]
    (is (near? (* 0.4 (min w h)) b) (str screen))
    (is (> b (gesture/slop {:screen screen})) "well beyond a tap's wobble")))
