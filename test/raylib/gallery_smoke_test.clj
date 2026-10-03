(ns raylib.gallery-smoke-test
  "A jolt-only smoke test for raylib.gallery: its wiring and every scene.

  The gallery has four registration points per scene (the :require, the
  `scenes` vector, a `categories` entry and a draw-scene! method), and the pure
  test runner never loaded raylib.gallery, so a scene missing from a category
  just vanished from the menu and a missing draw method crashed on the phone.
  Running each scene for 120 frames of scripted touch also covers the
  crash-on-open path for scenes that have no test of their own.

  This is .clj rather than .cljc because raylib.gallery loads jolt.ffi. The
  runner lists it as jolt-only and skips it on the JVM."
  (:require [clojure.test :refer [deftest is testing]]
            [poc.raylib.diagnostics :as diag]
            [poc.raylib.gallery :as gallery]
            [raylib.gallery :as rg]
            [raylib.scenes.split3d :as split3d]
            [raylib.scroll :as scroll]))

(defn input
  "One frame's input, built the way raylib.gallery/frame builds it minus the
  FFI. `phase` is :press, :down, :release or :idle, and `pos` is [x y].

  The release carries a position on purpose: normalize-input calls
  `(int (:pointer-x raw))` whenever :released? is true, and a nil there throws."
  [phase pos]
  (-> (diag/normalize-input {:screen-width 1206
                             :screen-height 2334
                             :render-width 1206
                             :render-height 2334
                             :touch-count (if (#{:press :down} phase) 1 0)
                             :touch-ids (if (#{:press :down} phase) [1] [])
                             :pressed? (= phase :press)
                             :down? (= phase :down)
                             :released? (= phase :release)
                             :pointer-x (first pos)
                             :pointer-y (second pos)})
      (assoc :touch-points (if (#{:press :down} phase) [pos] [])
             :local-time [10 9 30]
             ;; Deliberately not the scenes' own estimate, so a scene that prefers
             ;; the host's measure over its default runs that path here.
             :measure (fn [s sz] (* 0.5 sz (count s)))
             ;; A tap, so the gestures scene logs something over the script.
             :raylib-gesture (if (= phase :release) 1 0)
             :delta-seconds (/ 1.0 60))))

(defn- frame-input
  "Frame `i` of the script: a press at 10, a drag through 39, a release at 40
  and nothing after, so every scene sees all three edges and some idle time."
  [i]
  (cond
    (= i 10) (input :press [600 900])
    (< 10 i 40) (input :down [(+ 600 i) (+ 900 (* 3 i))])
    (= i 40) (input :release [640 1020])
    :else (input :idle nil)))

(defn- run-scene
  "Open `id` and run the 120-frame script. Returns the final gallery state."
  [id]
  (reduce (fn [s i] (gallery/run-frame rg/registry s (frame-input i)))
          (gallery/open-scene rg/registry gallery/initial-gallery-state id
                              (input :idle nil))
          (range 120)))

(deftest every-scene-is-in-exactly-one-category
  (let [listed (mapcat :scenes rg/categories)]
    (is (= (sort rg/scene-ids) (sort listed)))
    (is (= (count listed) (count (set listed)))
        (str "ids repeated across categories: "
             (vec (for [[id n] (frequencies listed) :when (> n 1)] id))))))

(deftest every-category-entry-is-a-scene
  (let [stray (remove (set rg/scene-ids) (mapcat :scenes rg/categories))]
    (is (empty? stray) (str "category entries that are not scenes: " (vec stray)))))

(deftest every-scene-has-a-draw-method
  (let [have (set (keys (methods rg/draw-scene!)))
        missing (remove have rg/scene-ids)]
    (is (empty? missing) (str "scenes with no draw-scene! method: " (vec missing)))))

(deftest every-scene-survives-two-seconds-of-touch
  (doseq [id rg/scene-ids]
    (testing (name id)
      (let [result (try (run-scene id)
                        (catch :default e
                          (str (.getName (class e)) ": " (ex-message e))))]
        (is (map? result) (str id " threw " result))
        (when (map? result)
          (is (= :scene (:mode result)))
          (is (= id (:active-scene-id result))))))))

(deftest a-scene-that-throws-returns-to-the-list
  (let [s (gallery/open-scene rg/registry gallery/initial-gallery-state :analog
                              (input :idle nil))
        result (let [w (java.io.StringWriter.)]
                 (binding [*out* w]
                   (#'rg/guard-scene s :analog (fn [] (throw (ex-info "boom" {}))))))]
    (is (= :scene (:mode s)))
    (is (= :gallery (:mode result)))
    (is (nil? (:active-scene-id result)))
    (is (nil? (:scene-state result)))
    (is (= [] (:scene-events result)))))

(deftest a-scene-that-throws-on-open-stays-on-the-list
  (let [result (let [w (java.io.StringWriter.)]
                 (binding [*out* w]
                   (#'rg/guard-scene gallery/initial-gallery-state :analog
                                     (fn [] (throw (ex-info "boom" {}))))))]
    (is (= :gallery (:mode result)))))

(deftest a-scene-that-does-not-throw-is-untouched
  (is (= :next (#'rg/guard-scene gallery/initial-gallery-state :analog (fn [] :next)))))

(deftest the-failure-line-names-the-scene-that-was-being-opened
  (let [out (with-out-str
              (#'rg/guard-scene gallery/initial-gallery-state :analog
                                (fn [] (throw (ex-info "boom" {})))))]
    (is (= "gallery: :analog failed, back to the list: boom\n" out))))

(deftest the-failure-line-has-a-detail-when-the-exception-has-no-message
  ;; Under jolt (NullPointerException.) has a nil ex-message.
  (let [e (NullPointerException.)
        out (with-out-str
              (#'rg/guard-scene gallery/initial-gallery-state :analog
                                (fn [] (throw e))))
        detail (second (re-find #"back to the list: (.*)\n" out))]
    (is (nil? (ex-message e)))
    (is (seq detail) out)))

(def ^:private tall-list
  "A list taller than its viewport, so a drag has room to move it."
  {:content-height 5000
   :viewport-height 2000})

(defn- next-scroll-after-drag
  "What `next-scroll` answers for a finger that pressed at y 1000 on a list
  scrolled to 300 and is now at y 700, in the given mode."
  [mode]
  (let [drag (scroll/begin-drag 300 [600 1000])]
    (#'rg/next-scroll mode (scroll/drag-to drag [600 700]) [600 700] :down 300 tall-list)))

(deftest a-drag-in-a-scene-leaves-the-list-scroll-alone
  ;; Swipe games drag all the time. The list is hidden behind the scene, and
  ;; where it ends up must not depend on how the game was played.
  (is (= 300 (next-scroll-after-drag :scene))))

(deftest a-drag-on-the-list-still-scrolls
  ;; The finger moved up 300 pixels, so the offset grows by 300.
  (is (= 600 (next-scroll-after-drag :gallery))))

(deftest geoshapes-cache-is-keyed-on-the-screen
  ;; host-measure calls MeasureText, which needs a window; the layout under test
  ;; only needs a width.
  (with-redefs [rg/host-measure (fn [s size] (* 0.6 size (count s)))]
    (let [frame @#'rg/geoshapes-frame
          a (frame {:frame 0} {:screen [1206 2334]})
          a2 (frame {:frame 7} {:screen [1206 2334]})
          b (frame {:frame 0} {:screen [2334 1206]})
          b2 (frame {:frame 1} {:screen [2334 1206]})]
      (testing "the same screen draws the very same list"
        (is (identical? (second a) (second a2)))
        (is (identical? (second b) (second b2))))
      (testing "a new screen rebuilds the dims and the list"
        (is (not (identical? (second a) (second b))))
        (is (not= (:viewport (first a)) (:viewport (first b))))
        (is (not= (second a) (second b))))
      (testing "and coming back rebuilds again"
        (let [a3 (frame {:frame 0} {:screen [1206 2334]})]
          (is (not (identical? (second a) (second a3))))
          (is (= (second a) (second a3))))))))

(deftest split3d-list-cache-is-keyed-on-both-players-and-the-half
  (let [m {:screen [1206 2334]}
        dims (split3d/dimensions m (fn [s size] (* 0.6 size (count s))))
        st (first ((:init (split3d/scene)) {:metrics m}))
        lst @#'rg/split3d-list
        a0 (lst st dims 0)
        a1 (lst st dims 1)]
    (testing "nobody moved: both halves come back as the very same lists"
      (is (identical? a0 (lst st dims 0)))
      (is (identical? a1 (lst st dims 1))))
    (testing "the cached list is the one scene-list builds"
      (is (= a0 (split3d/scene-list st dims 0)))
      (is (= a1 (split3d/scene-list st dims 1))))
    (testing "either player moving rebuilds both halves, for each is a cube in the other's view"
      (doseq [moved [(assoc st :z1 -2.0) (assoc st :x2 -2.0)]
              i [0 1]
              :let [dl (lst moved dims i)]]
        (is (= dl (split3d/scene-list moved dims i)))
        (is (not (identical? dl (if (zero? i) a0 a1))))))
    (testing "a new screen rebuilds"
      (let [m2 {:screen [2334 1206]}
            dims2 (split3d/dimensions m2 (fn [s size] (* 0.6 size (count s))))
            st2 (assoc st :screen (:screen m2))]
        (is (= (lst st2 dims2 0) (split3d/scene-list st2 dims2 0)))))))
