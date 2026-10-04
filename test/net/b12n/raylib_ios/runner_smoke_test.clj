(ns net.b12n.raylib-ios.runner-smoke-test
  "A jolt-only smoke test for net.b12n.raylib-ios.runner: one scene, full
  screen, with no menu and no Back, over stubbed raylib.

  It drives the runner's `app` frames the way the host loop does, with only
  the host calls the frame makes stubbed and recorded. Hello is the scene
  because it is the platform's proof and lives in this repo. Its draw
  namespace is required here by hand, as a per-scene app namespace does, since
  the runner loads no draw methods of its own.

  This is .clj because the runner loads jolt.ffi. The test runner lists it as
  jolt-only and skips it on the JVM."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gallery.draw-util :as du]
            [net.b12n.raylib-ios.host :as host]
            [net.b12n.raylib-ios.runner :as runner]
            [net.b12n.raylib-ios.scenes.hello :as hello]
            [net.b12n.raylib-ios.scenes.hello.draw]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private screen-w 1206)
(def ^:private screen-h 2334)

(defn- with-stubbed-host
  "Call `(f calls)` with the host calls one runner frame makes redefined to
  record into `calls`, an atom of `[name & args]` vectors. The screen is the
  phone's, with its safe-area insets, and no finger is down."
  [f]
  (let [calls (atom [])
        rec (fn [nm ret] (fn [& args] (swap! calls conj (into [nm] args)) ret))]
    (with-redefs [du/host-measure (fn [s size] (int (* 0.6 size (count s))))
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
  "Init `app` as the host would and run `n` frames. Returns the final state."
  [app n]
  (let [init-state ((:init app) {:width screen-w
                                 :height screen-h
                                 :scale 3.0
                                 ;; UIKit cannot answer before the first layout,
                                 ;; so the host starts with zeros and the frame
                                 ;; resolves the real insets
                                 :inset-top 0})]
    (reduce (fn [s _] ((:frame app) s)) init-state (range n))))

(defn- calls-named [calls nm] (filter #(= nm (first %)) calls))

(deftest the-runner-draws-one-scene
  (with-stubbed-host
    (fn [calls]
      (let [app (runner/app (hello/scene))
            end (run-frames app 120)
            texts (map second (calls-named @calls :draw-text))]
        (is (= "Basic Window" (:title app)))
        (is (nil? (:failure end)) "no throw in 120 frames")
        (is (= :scene (get-in end [:gstate :mode])))
        (is (= :hello (get-in end [:gstate :active-scene-id])))
        (testing "Hello's one line, every frame, and nothing else"
          (is (= 120 (count texts)))
          (is (every? #(= hello/line %) texts)))
        (testing "no menu: no Back button, no card grid, no list heading"
          (is (empty? (calls-named @calls :draw-rectangle)))
          (is (not-any? #(re-find #"Back|Choose a" %) texts)))
        (testing "each frame is clipped and translated into the safe region, balanced"
          (is (= 120 (count (calls-named @calls :begin-scissor-mode))))
          (is (= 120 (count (calls-named @calls :end-scissor-mode))))
          (is (= 120 (count (calls-named @calls :rl-push-matrix))))
          (is (= 120 (count (calls-named @calls :rl-pop-matrix))))
          (is (= [:begin-scissor-mode 0 186 screen-w (- screen-h 186 102)]
                 (first (calls-named @calls :begin-scissor-mode)))))
        (testing "the scene keeps its textures while it runs"
          (is (= (repeat 120 [:enter! :hello]) (calls-named @calls :enter!))))))))

(defmethod du/draw-scene! :runner-test-failing [_ state input]
  (du/draw-scene! :hello state input))

(defn- failing-scene
  "A scene that throws in :update on its `n`th frame, counting its :updates."
  [n updates]
  (assoc (hello/scene)
         :id :runner-test-failing
         :update (fn [state _]
                   (when (>= (swap! updates inc) n)
                     (throw (ex-info "boom in update" {})))
                   [state []])))

(deftest the-runner-frees-textures-on-exit
  ;; The runner has no Back, so a scene is left only by failing. Its textures
  ;; go the same way the gallery frees them: enter! with no scene.
  (with-stubbed-host
    (fn [calls]
      (let [updates (atom 0)
            app (runner/app (failing-scene 3 updates))
            end (run-frames app 10)
            entered (map second (calls-named @calls :enter!))]
        (testing "textures are held for the three frames the scene runs, then freed"
          ;; frame 1 opens it, frames 2 and 3 update it, frame 4 throws
          (is (= (concat (repeat 3 :runner-test-failing) (repeat 7 nil)) entered)))
        (testing "the scene is stopped, not retried every frame"
          (is (= 3 @updates))
          (is (= "boom in update" (:failure end))))
        (testing "the message stays on screen"
          (let [texts (map second (calls-named @calls :draw-text))]
            (is (some #(str/includes? % "failed") texts))
            (is (some #(str/includes? % "boom") texts))))))))

(deftest a-scene-that-throws-on-open-shows-its-message
  (with-stubbed-host
    (fn [calls]
      (let [bad (assoc (hello/scene) :init (fn [_] (throw (ex-info "no init" {}))))
            end (run-frames (runner/app bad) 3)]
        (is (= "no init" (:failure end)))
        (is (nil? (last (map second (calls-named @calls :enter!)))))
        (is (some #(str/includes? % "no init")
                  (map second (calls-named @calls :draw-text))))))))

(deftest wrap-lines-breaks-at-whitespace-and-cuts-long-words
  (is (= ["aa bb" "cc"] (runner/wrap-lines "aa bb cc" 5)))
  (is (= ["abcd" "efg"] (runner/wrap-lines "abcdefg" 4)))
  (is (= [""] (runner/wrap-lines "" 10)))
  (is (= ["a" "b"] (runner/wrap-lines "a b" 0))))
