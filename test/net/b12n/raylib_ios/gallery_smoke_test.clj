(ns net.b12n.raylib-ios.gallery-smoke-test
  "A jolt-only smoke test for the gallery shell, which takes its scenes as data.

  The shell's own registry holds one scene, Hello, because the rest live in
  raylib-ios-demo and that repo's gallery passes them to `gallery/run!`. What
  this covers is the shell: the platform gallery shows Hello, a scene that
  throws returns to the list, and a drag in a scene leaves the list's scroll
  alone. raylib-ios-demo runs every scene through the same shell.

  This is .clj because net.b12n.raylib-ios.gallery loads jolt.ffi. The test
  runner lists it as jolt-only and skips it on the JVM."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gallery :as rg]
            [net.b12n.raylib-ios.gallery.core :as gallery]
            [net.b12n.raylib-ios.gallery.diagnostics :as diag]
            [net.b12n.raylib-ios.gallery.draw-util :as du]
            [net.b12n.raylib-ios.host :as host]
            [net.b12n.raylib-ios.scenes.hello :as hello]
            [net.b12n.raylib-ios.scroll :as scroll]
            [net.b12n.raylib-ios.texture :as texture]))

(defn input
  "One frame's input, built the way the gallery's frame builds it minus the
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
             :measure (fn [s sz] (* 0.5 sz (count s)))
             :raylib-gesture 0
             :delta-seconds (/ 1.0 60))))

(deftest the-platform-gallery-shows-hello
  (let [{:keys [scenes categories]} rg/platform-gallery
        registry (gallery/make-registry scenes)]
    (testing "one scene, Hello, in one category"
      (is (= [:hello] (mapv :id scenes)))
      (is (= [[:hello]] (mapv :scenes categories))))
    (testing "its draw method is loaded with the shell"
      (is (contains? (set (keys (methods du/draw-scene!))) :hello)))
    (testing "it opens and runs 120 frames of touch"
      (let [opened (gallery/open-scene registry gallery/initial-gallery-state :hello
                                       (input :idle nil))
            result (reduce (fn [s i]
                             (gallery/run-frame registry s
                                                (cond
                                                  (= i 10) (input :press [600 900])
                                                  (< 10 i 40) (input :down [(+ 600 i) 900])
                                                  (= i 40) (input :release [640 900])
                                                  :else (input :idle nil))))
                           opened (range 120))]
        (is (= :scene (:mode result)))
        (is (= :hello (:active-scene-id result)))))))

(def ^:private screen-w 1206)
(def ^:private screen-h 2334)

(defn- with-stubbed-host
  "Call `(f calls)` with the host calls one gallery frame makes redefined to
  record into `calls`, an atom of `[name & args]` vectors. The screen is the
  phone's, with its safe-area insets, and no finger is down (a queued
  `rg/tap!` is the only touch)."
  [f]
  (let [calls (atom [])
        rec (fn [nm ret] (fn [& args] (swap! calls conj (into [nm] args)) ret))]
    (with-redefs [du/host-measure (fn [s size] (int (* 0.6 size (count s))))
                  host/measure-text (fn [s size] (int (* 0.6 size (count s))))
                  host/get-screen-width (rec :get-screen-width screen-w)
                  host/get-screen-height (rec :get-screen-height screen-h)
                  host/get-touch-point-count (rec :get-touch-point-count 0)
                  host/get-touch-x (rec :get-touch-x 0)
                  host/get-touch-y (rec :get-touch-y 0)
                  host/get-frame-time (rec :get-frame-time 0.016)
                  host/get-gesture-detected (rec :get-gesture-detected 0)
                  host/local-time (rec :local-time [10 9 30])
                  host/safe-area-pixels (rec :safe-area-pixels
                                             {:top 186
                                              :bottom 102
                                              :left 0
                                              :right 0})
                  host/clear-background (rec :clear-background nil)
                  host/draw-text (rec :draw-text nil)
                  host/draw-rectangle (rec :draw-rectangle nil)
                  host/begin-scissor-mode (rec :begin-scissor-mode nil)
                  host/end-scissor-mode (rec :end-scissor-mode nil)
                  host/rl-push-matrix (rec :rl-push-matrix nil)
                  host/rl-pop-matrix (rec :rl-pop-matrix nil)
                  host/rl-translatef (rec :rl-translatef nil)
                  texture/enter! (rec :enter! nil)]
      (f calls))))

(defn- run-frames
  "Init `app` as the host would, then run `n` frames from `state`, or from the
  fresh state when there is none. Returns the last state."
  ([app n] (run-frames app nil n))
  ([app state n]
   (let [start (or state
                   ((:init app) {:width screen-w
                                 :height screen-h
                                 :scale 3.0
                                 :inset-top 0}))]
     (reduce (fn [s _] ((:frame app) s)) start (range n)))))

(defn- texts-of [calls] (mapv second (filter #(= :draw-text (first %)) calls)))

(defn- first-card-centre
  "The centre of the first rectangle drawn after the last clear, which is the
  first card of the list on screen."
  [calls]
  (let [after (->> calls (drop-while #(not= :clear-background (first %))) vec)
        [_ x y w h] (first (filter #(= :draw-rectangle (first %)) after))]
    [(+ x (quot w 2)) (+ y (quot h 2))]))

(deftest the-platform-gallery-walks-from-category-to-scene
  ;; The frames over stubbed raylib: the categories, a tap on the one card, the
  ;; category's scenes, a tap on Hello's card, and Hello running. This is the
  ;; shell handed its scenes as data, end to end except for the FFI.
  (with-stubbed-host
    (fn [calls]
      (let [app (rg/app rg/platform-gallery)
            s1 (run-frames app 1)
            _ (is (= "Gallery" (:title app)))
            _ (is (some #{"Choose a category"} (texts-of @calls)))
            _ (is (some #{"Platform"} (texts-of @calls)) "the category's title is on its card")
            [cx cy] (first-card-centre @calls)
            _ (do (reset! calls []) (rg/tap! cx cy))
            s2 (run-frames app s1 3)
            _ (is (= :platform (:category s2)) "the tap opened the category")
            _ (is (some #{"Choose a scene"} (texts-of @calls)))
            _ (is (some #{"Basic Window"} (texts-of @calls)) "the scene's title is on its card")
            [sx sy] (first-card-centre @calls)
            _ (do (reset! calls []) (rg/tap! sx sy))
            s3 (run-frames app s2 3)]
        (is (= :scene (get-in s3 [:gstate :mode])))
        (is (= :hello (get-in s3 [:gstate :active-scene-id])))
        (is (some #{hello/line} (texts-of @calls)) "Hello drew its line")))))

(deftest run!-takes-its-scenes-as-data
  (testing "a gallery is the two vectors, in the shape the shell has always used"
    (let [{:keys [scenes categories]} rg/platform-gallery]
      (is (vector? scenes))
      (is (every? :id scenes))
      (is (every? (fn [c] (and (keyword? (:id c)) (string? (:title c)) (vector? (:scenes c))))
                  categories))
      (is (= (map :id scenes) (mapcat :scenes categories)))))
  (testing "the platform gallery is the one -main runs, and it holds Hello's scene map"
    (is (= (:id (hello/scene)) (:id (first (:scenes rg/platform-gallery)))))))

(deftest no-scene-namespaces-remain
  ;; Everything but Hello moved to raylib-ios-demo. A scene file coming back
  ;; here would be a second copy, so the directory is held to Hello alone.
  (let [dir (io/file "src/net/b12n/raylib_ios/scenes")
        names (sort (map #(.getName ^java.io.File %) (.listFiles dir)))]
    (is (= ["hello" "hello.cljc"] names))
    (is (not (.exists (io/file "src/net/b12n/raylib_ios/gallery/draws.clj")))))
  (let [tests (io/file "test/net/b12n/raylib_ios/scenes")
        names (sort (map #(.getName ^java.io.File %) (.listFiles tests)))]
    (is (= ["hello_test.cljc"] names))))

(deftest a-scene-that-throws-returns-to-the-list
  (let [registry (gallery/make-registry (:scenes rg/platform-gallery))
        s (gallery/open-scene registry gallery/initial-gallery-state :hello
                              (input :idle nil))
        result (binding [*out* (java.io.StringWriter.)]
                 (rg/guard-scene s :hello (fn [] (throw (ex-info "boom" {})))))]
    (is (= :scene (:mode s)))
    (is (= :gallery (:mode result)))
    (is (nil? (:active-scene-id result)))
    (is (nil? (:scene-state result)))
    (is (= [] (:scene-events result)))))

(deftest a-scene-that-throws-on-open-stays-on-the-list
  (let [result (binding [*out* (java.io.StringWriter.)]
                 (rg/guard-scene gallery/initial-gallery-state :hello
                                 (fn [] (throw (ex-info "boom" {})))))]
    (is (= :gallery (:mode result)))))

(deftest a-scene-that-does-not-throw-is-untouched
  (is (= :next (rg/guard-scene gallery/initial-gallery-state :hello (fn [] :next)))))

(deftest the-failure-line-names-the-scene-that-was-being-opened
  (let [out (with-out-str
              (rg/guard-scene gallery/initial-gallery-state :hello
                              (fn [] (throw (ex-info "boom" {})))))]
    (is (= "gallery: :hello failed, back to the list: boom\n" out))))

(deftest the-failure-line-has-a-detail-when-the-exception-has-no-message
  ;; Under jolt (NullPointerException.) has a nil ex-message.
  (let [e (NullPointerException.)
        out (with-out-str
              (rg/guard-scene gallery/initial-gallery-state :hello
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
    (rg/next-scroll mode (scroll/drag-to drag [600 700]) [600 700] :down 300 tall-list)))

(deftest a-drag-in-a-scene-leaves-the-list-scroll-alone
  ;; Swipe games drag all the time. The list is hidden behind the scene, and
  ;; where it ends up must not depend on how the game was played.
  (is (= 300 (next-scroll-after-drag :scene))))

(deftest a-drag-on-the-list-still-scrolls
  ;; The finger moved up 300 pixels, so the offset grows by 300.
  (is (= 600 (next-scroll-after-drag :gallery))))

;; The pre-rename names. A namespace that moves leaves its old name in
;; docstrings, shell defaults, docs and the CI file, where nothing compiles it,
;; so the only thing that notices is a walk over the text.
(def ^:private old-namespace
  "`raylib.<x>` and `poc.raylib.<x>` as a symbol, and the old directories. Not
  `libraylib`, not raylib-jlt's `net/b12n/raylib/...` (a slash, not a dot), and
  not `raylib.h` or `raylib.clj`, which are file names."
  #"(?:^|[^A-Za-z0-9_.])(?:poc\.)?raylib\.([a-z][a-z0-9-]*)|(?:src|test)/(?:poc/)?raylib/")

(def ^:private not-a-namespace #{"h" "clj"})

(def ^:private checked-extension #"\.(clj|cljc|edn|md|sh|html|yml|plist)$|/(nrepl-eval|nrepl-repl|extract-from-notebooks)$")

(defn- stale-references
  "`path:line: text` for every old-namespace mention under `root`. The identity
  table keeps the upstream names on purpose, and CHANGELOG entries are dated
  history."
  [root]
  (let [skip #{"tools/jasalt-identity.edn" "CHANGELOG.md"}
        files (->> ["src" "test" "tools" "docs" ".clj-kondo" ".github"
                    "deps.edn" "README.md" "CONTRIBUTING.md" "ROADMAP.md" "NOTICE"]
                   (mapcat #(file-seq (java.io.File. (str root "/" %))))
                   (filter #(.isFile ^java.io.File %))
                   (map #(.getPath ^java.io.File %)))]
    (vec (for [path files
               :let [rel (subs path (inc (count root)))]
               :when (and (not (skip rel)) (re-find checked-extension path))
               [n line] (map-indexed vector (str/split-lines (slurp path)))
               [_ x] (re-seq old-namespace line)
               :when (not (and x (not-a-namespace x)))]
           (str rel ":" (inc n) ": " (str/trim line))))))

(deftest no-old-namespace-remains
  (let [stale (stale-references ".")]
    (is (empty? stale) (str (count stale) " stale reference(s), first: " (first stale)))))
