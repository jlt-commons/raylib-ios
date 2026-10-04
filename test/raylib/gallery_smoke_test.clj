(ns raylib.gallery-smoke-test
  "A jolt-only smoke test for raylib.gallery: its wiring and every scene.

  The gallery has four registration points per scene (the :require, the
  `scenes` vector, a `categories` entry and a draw-scene! method), and the pure
  test runner never loaded raylib.gallery, so a scene missing from a category
  just vanished from the menu and a missing draw method crashed on the phone.
  Running each scene for 120 frames of scripted touch also covers the
  crash-on-open path for scenes that have no test of their own. A second pass
  runs every scene's update and its `draw-scene!` over stubbed raylib for the
  same 120 frames, with each call's argument types checked against its defcfn
  signature and begin/end pairs counted. The script has a single press, so the
  stick paths (doom, voxel, fpcamera, split3d) draw only their idle frames in
  it; doom's own test below adds a held stick.

  This is .clj rather than .cljc because raylib.gallery loads jolt.ffi. The
  runner lists it as jolt-only and skips it on the JVM."
  (:require [clojure.test :refer [deftest is testing]]
            [poc.raylib.diagnostics :as diag]
            [poc.raylib.gallery :as gallery]
            [raylib.gallery :as rg]
            [raylib.host :as host]
            [raylib.probe :as probe]
            [raylib.rlgl-model :as gl]
            [raylib.scenes.doom :as doom]
            [raylib.scenes.split3d :as split3d]
            [raylib.scroll :as scroll]
            [raylib.texture :as texture]))

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

(def ^:private screen-w 1206)
(def ^:private screen-h 2334)

(defn- type-violation
  "What is wrong with `args` against the defcfn parameter `types`, or nil. The
  rules are the FFI's: a :float or :double must be a double (jolt rejects an integer), an
  :int, :uint or :uint8 an integer and a :string a string."
  [types args]
  (or (when (not= (count types) (count args)) :arity)
      (some (fn [[t a]]
              (case t
                (:float :double) (when-not (double? a)
                                   (if (integer? a) :float-got-integer :float-got-other))
                (:int :uint :uint8) (when-not (integer? a) [t :got-other])
                :pointer (when (nil? a) :pointer-got-nil)
                :string (when-not (string? a) :string-got-other)))
            (map vector types args))))

(def ^:private default-texture-id
  "What the stubbed `rlGetTextureIdDefault` answers."
  1)

(def ^:private sdl-fbo
  "The framebuffer id the stubbed host reports as SDL's drawable."
  77)

(defn- with-stubbed-raylib
  "Call `(f probe)` with the host's drawing defcfns redefined to stubs that
  record type violations and begin/end balances in `probe`, an atom of
  `{:violations [...] :balance {k n} :last-bound id-or-nil :fbo id :scissor bool}`,
  where `:last-bound` is the argument of the latest `rlSetTexture`, `:fbo` the
  framebuffer last bound (`sdl-fbo` at the start, :default after a plain
  disable) and `:scissor` whether a scissor is up (it starts up, as the
  gallery leaves it around a scene)."
  [f]
  (let [model (gl/fresh)
        probe (atom {:violations []
                     :balance {}
                     :fbo sdl-fbo
                     :scissor true
                     :model model})
        was-wm @probe/wm-info
        next-fbo (atom 500)
        note (fn [k v] (swap! probe assoc k v))
        chk (fn [nm types args]
              (when-let [v (type-violation types args)]
                (swap! probe update :violations conj [nm v])))
        stub (fn [nm types ret]
               (fn [& args] (chk nm types args) ret))
        next-tex (atom 0)
        pair (fn [nm types k d]
               (fn [& args]
                 (chk nm types args)
                 (swap! probe update-in [:balance k] (fnil + 0) d)
                 nil))]
    (with-redefs [rg/host-measure (fn [s size] (int (* 0.6 size (count s))))
                  host/clear-background (stub :clear-background [:uint] nil)
                  host/draw-text (stub :draw-text [:string :int :int :int :uint] nil)
                  host/draw-circle (stub :draw-circle [:int :int :float :uint] nil)
                  host/draw-circle-lines (stub :draw-circle-lines [:int :int :float :uint] nil)
                  host/draw-rectangle (stub :draw-rectangle [:int :int :int :int :uint] nil)
                  host/draw-line (stub :draw-line [:int :int :int :int :uint] nil)
                  host/get-screen-width (stub :get-screen-width [] screen-w)
                  host/get-screen-height (stub :get-screen-height [] screen-h)
                  host/get-frame-time (stub :get-frame-time [] 0.016)
                  host/get-fps (stub :get-fps [] 60)
                  host/measure-text (fn [s size]
                                      (chk :measure-text [:string :int] [s size])
                                      (int (* 0.6 size (count s))))
                  host/rl-begin (pair :rl-begin [:int] :rl 1)
                  host/rl-end (pair :rl-end [] :rl -1)
                  host/rl-vertex-2f (stub :rl-vertex-2f [:float :float] nil)
                  host/rl-color-4ub (stub :rl-color-4ub [:uint8 :uint8 :uint8 :uint8] nil)
                  host/rl-push-matrix (let [balanced (pair :rl-push-matrix [] :matrix 1)]
                                        (fn [& args]
                                          (gl/push model)
                                          (apply balanced args)))
                  host/rl-pop-matrix (let [balanced (pair :rl-pop-matrix [] :matrix -1)]
                                       (fn [& args]
                                         (gl/pop* model)
                                         (apply balanced args)))
                  host/rl-translatef (fn [& args]
                                       (chk :rl-translatef [:float :float :float] args)
                                       (gl/translate-y model (second args))
                                       nil)
                  host/rl-rotatef (stub :rl-rotatef [:float :float :float :float] nil)
                  host/rl-scalef (stub :rl-scalef [:float :float :float] nil)
                  host/begin-scissor-mode (fn [& args]
                                            (chk :begin-scissor-mode [:int :int :int :int] args)
                                            (note :scissor true))
                  host/end-scissor-mode (fn [& args]
                                          (chk :end-scissor-mode [] args)
                                          (note :scissor false))
                  host/gl-bind-framebuffer (fn [& args]
                                             (chk :gl-bind-framebuffer [:uint :uint] args)
                                             (note :fbo (second args)))
                  host/begin-blend-mode (pair :begin-blend-mode [:int] :blend 1)
                  host/end-blend-mode (pair :end-blend-mode [] :blend -1)
                  texture/rl-load-texture (fn [& args]
                                            (chk :rl-load-texture [:pointer :int :int :int :int] args)
                                            (swap! next-tex inc))
                  texture/rl-update-texture (stub :rl-update-texture
                                                  [:uint :int :int :int :int :int :pointer] nil)
                  texture/rl-unload-texture (stub :rl-unload-texture [:uint] nil)
                  texture/rl-texture-parameters (stub :rl-texture-parameters [:uint :int :int] nil)
                  texture/rl-set-texture (fn [& args]
                                           (chk :rl-set-texture [:uint] args)
                                           (swap! probe assoc :last-bound (first args))
                                           nil)
                  texture/rl-get-texture-id-default (stub :rl-get-texture-id-default [] default-texture-id)
                  texture/rl-tex-coord-2f (stub :rl-tex-coord-2f [:float :float] nil)
                  ;; rlgl implements these with a glBindFramebuffer(.., 0) (rlgl.h,
                  ;; 6.0), which on iOS is not the screen
                  texture/rl-load-framebuffer (fn [& args]
                                                (chk :rl-load-framebuffer [] args)
                                                (note :fbo 0)
                                                (swap! next-fbo inc))
                  texture/rl-framebuffer-attach (fn [& args]
                                                  (chk :rl-framebuffer-attach
                                                       [:uint :uint :int :int :int] args)
                                                  (note :fbo 0))
                  texture/rl-framebuffer-complete (fn [& args]
                                                    (chk :rl-framebuffer-complete [:uint] args)
                                                    (note :fbo 0)
                                                    1)
                  texture/rl-enable-framebuffer (fn [& args]
                                                  (chk :rl-enable-framebuffer [:uint] args)
                                                  (note :fbo (first args)))
                  texture/rl-disable-framebuffer (fn [& args]
                                                   (chk :rl-disable-framebuffer [] args)
                                                   (note :fbo :default))
                  texture/rl-unload-framebuffer (fn [& args]
                                                  (chk :rl-unload-framebuffer [:uint] args)
                                                  (note :fbo 0))
                  texture/rl-load-texture-depth (fn [& args]
                                                  (chk :rl-load-texture-depth [:int :int :int] args)
                                                  (swap! next-fbo inc))
                  texture/rl-viewport (stub :rl-viewport [:int :int :int :int] nil)
                  texture/rl-set-framebuffer-width (stub :rl-set-framebuffer-width [:int] nil)
                  texture/rl-set-framebuffer-height (stub :rl-set-framebuffer-height [:int] nil)
                  texture/rl-matrix-mode (fn [& args]
                                           (chk :rl-matrix-mode [:int] args)
                                           (gl/matrix-mode model (case (first args)
                                                                   0x1700 :modelview
                                                                   0x1701 :projection)))
                  texture/rl-load-identity (fn [& args]
                                             (chk :rl-load-identity [] args)
                                             (gl/identity* model))
                  texture/rl-ortho (fn [& args]
                                     (chk :rl-ortho [:double :double :double :double :double :double] args)
                                     (apply gl/ortho model args))
                  texture/rl-draw-render-batch-active (stub :rl-draw-render-batch-active [] nil)
                  texture/rl-set-blend-factors (stub :rl-set-blend-factors [:int :int :int] nil)]
      (reset! probe/wm-info {:framebuffer sdl-fbo})
      ;; A texture scene leaves its stub id in the table. Free it while the
      ;; stubs are still bound, or the next test to call `texture/enter!` would
      ;; unload it through the real FFI.
      (try
        (f probe)
        (finally
          (texture/enter! nil)
          (reset! probe/wm-info was-wm))))))

(defn- draw-args
  "The map `draw-scene!` is called with, for the phone's screen."
  [i]
  {:k 3.0
   :m (:metrics (frame-input i))
   :safe {:x 0
          :y 0
          :width screen-w
          :height screen-h}})

(def ^:private probe-for-test
  "The stub probe of the test now running, for a test-only draw method."
  (atom nil))

(defn- draw-script
  "Open `id`, run the 120-frame script and call `draw-scene!` after each
  `run-frame`. Returns `{:error [frame message] :unbalanced [[frame k n] ...]
  :bound [[frame id] ...] :screen [[frame fbo scissor matrix-ok?] ...]}`: the
  balances read after each frame's draw, any frame that left a texture bound
  (the last `rlSetTexture` was neither never made nor the default texture's
  id), and any frame that did not end with SDL's framebuffer bound, the scissor
  up and rlgl's matrix state (the stack, the pointer, `transform`) as it began.
  `registry` defaults to the gallery's."
  ([probe id] (draw-script probe id rg/registry))
  ([probe id registry]
   (loop [i 0
          gs (gallery/open-scene registry gallery/initial-gallery-state id
                                 (frame-input 0))
          out {:unbalanced []
               :bound []
               :screen []}]
     (if (= i 120)
       out
       (let [gs (gallery/run-frame registry gs (frame-input i))
             _ (swap! probe assoc :balance {} :last-bound nil :fbo sdl-fbo :scissor true)
             _ (reset! (:model @probe) @(gl/fresh))
             err (try (rg/draw-scene! id (:scene-state gs) (draw-args i))
                      nil
                      (catch :default e (str (or (ex-message e) e))))
             bal (:balance @probe)
             bound (:last-bound @probe)
             matrix-ok? (= (gl/snapshot (:model @probe)) (gl/snapshot (gl/fresh)))
             out (cond-> (update out :unbalanced into
                                 (for [[k n] bal :when (not (zero? n))] [i k n]))
                   (not (contains? #{nil default-texture-id} bound)) (update :bound conj [i bound])
                   (not (and (= sdl-fbo (:fbo @probe)) (:scissor @probe) matrix-ok?))
                   (update :screen conj [i (:fbo @probe) (:scissor @probe) matrix-ok?]))]
         (if err
           (assoc out :error [i err])
           (recur (inc i) gs out)))))))

(deftest every-draw-method-runs-over-stubbed-raylib
  ;; The pure :update and :draw run in `every-scene-survives-two-seconds-of-touch`;
  ;; this runs `draw-scene!` itself, where the FFI type traps live. The script
  ;; presses once, so stick paths draw only their idle frames here.
  (with-stubbed-raylib
    (fn [probe]
      (doseq [id rg/scene-ids]
        (testing (name id)
          (swap! probe assoc :violations [])
          (let [blend-begins (atom 0)
                counted (fn [orig] (fn [& args]
                                     (swap! blend-begins inc)
                                     (apply orig args)))
                result (with-redefs [host/begin-blend-mode
                                     (counted host/begin-blend-mode)]
                         (draw-script probe id))]
            (is (nil? (:error result))
                (str id " threw at frame " (first (:error result)) ": "
                     (second (:error result))))
            (is (empty? (:violations @probe))
                (str id " passed the FFI a bad type: "
                     (vec (take 3 (distinct (:violations @probe))))))
            (is (empty? (:unbalanced result))
                (str id " left a begin unmatched: "
                     (vec (take 3 (:unbalanced result)))))
            (is (empty? (:bound result))
                (str id " left a texture bound after a draw: "
                     (vec (take 3 (:bound result)))))
            (is (empty? (:screen result))
                (str id " left the screen unbound or the scissor down: "
                     (vec (take 3 (:screen result)))))
            (when (#{:blendmodes :blendparticles} id)
              (is (pos? @blend-begins)
                  (str id " never called begin-blend-mode")))))))))

(deftest a-render-pass-gives-the-screen-back
  ;; The pass invariants every render-texture scene relies on, on the normal
  ;; path and on a throw: SDL's framebuffer bound, the scissor up and the
  ;; matrix stack balanced.
  (with-stubbed-raylib
    (fn [probe]
      (let [safe (:safe (draw-args 0))
            rt (texture/target! :pass :rt {:w 64
                                           :h 32})
            inside (atom nil)]
        (swap! probe assoc :balance {})
        (texture/with-target! rt safe
          (fn []
            (reset! inside [(:fbo @probe) (:scissor @probe)
                            (get-in @probe [:balance :matrix])])))
        (is (= [(:fbo rt) false 1] @inside))
        (is (= sdl-fbo (:fbo @probe)))
        (is (:scissor @probe))
        (is (zero? (get-in @probe [:balance :matrix])))
        (is (empty? (:violations @probe)) (str (vec (take 3 (:violations @probe)))))
        (swap! probe assoc :fbo :other)
        (is (thrown? Exception
                     (texture/with-target! rt safe (fn [] (throw (ex-info "boom" {}))))))
        (is (= sdl-fbo (:fbo @probe)))
        (is (:scissor @probe))
        (is (zero? (get-in @probe [:balance :matrix])))))))

(deftest a-nested-render-pass-keeps-the-invariants
  ;; No shipped scene renders into a target yet, so `draw-script`'s per-frame
  ;; screen check would pass for the wrong reason. This defines a test-only
  ;; scene whose draw method does what the gallery does around a scene (a push
  ;; and a translate by the safe origin) and then creates a target, resizes it
  ;; every other frame and draws a pass inside it, so the check sees a nested
  ;; pass, a target created mid-frame and a replaced one.
  (let [id :test-render-pass
        registry (gallery/make-registry [(assoc (first rg/scenes) :id id)])
        frames (atom 0)
        inside (atom [])
        after-target (atom [])]
    (defmethod rg/draw-scene! id [_ _ _]
      (let [n (swap! frames inc)]
        (host/rl-push-matrix)
        (host/rl-translatef 0.0 162.0 0.0)
        (host/draw-rectangle 0 0 10 10 0xFF0000FF)
        (let [rt (texture/target! :test-pass :rt {:w (if (even? n) 64 32)
                                                  :h 32})]
          (swap! after-target conj (:fbo @@probe-for-test))
          (texture/with-target! rt {:x 0
                                    :y 162
                                    :width screen-w
                                    :height screen-h}
            (fn [] (swap! inside conj (gl/vertex-offset (:model @@probe-for-test))))))
        (host/draw-rectangle 20 300 10 10 0xFF00FF00)
        (host/rl-pop-matrix)))
    (try
      (with-stubbed-raylib
        (fn [probe]
          (reset! probe-for-test probe)
          (let [result (draw-script probe id registry)]
            (is (nil? (:error result)) (str (:error result)))
            (is (empty? (:violations @probe)) (str (vec (take 3 (:violations @probe)))))
            (is (empty? (:unbalanced result)) (str (vec (take 3 (:unbalanced result)))))
            (is (empty? (:screen result))
                (str "the pass left the screen, scissor or matrix changed: "
                     (vec (take 3 (:screen result)))))
            (is (= 120 @frames))
            (is (= 120 (count @inside)))
            (is (every? zero? @inside) "a pass draws untranslated")
            (is (= 120 (count @after-target)))
            (is (every? #{sdl-fbo} @after-target)
                "the screen is bound right after target! creates or replaces one"))))
      (finally
        (remove-method rg/draw-scene! id)))))

(deftest the-blend-draws-end-the-mode-when-a-draw-throws
  ;; A draw call that throws inside the blend must not leave the mode begun, or
  ;; every later draw keeps it. `call-blended!` is what guarantees that, and this
  ;; ties the two blend scenes' draw methods to it.
  (with-stubbed-raylib
    (fn [probe]
      (doseq [[id thrower] [[:blendmodes :rl-vertex-2f]
                            [:blendparticles :draw-circle]]]
        (testing (name id)
          (let [gs (reduce (fn [s i] (gallery/run-frame rg/registry s (frame-input i)))
                           (gallery/open-scene rg/registry gallery/initial-gallery-state
                                               id (frame-input 0))
                           (range 15))
                ;; Only a call made while the blend mode is begun throws: the
                ;; skyline and the labels use the same calls before and after.
                boom (fn [& _]
                       (when (pos? (get-in @probe [:balance :blend] 0))
                         (throw (ex-info "boom" {}))))]
            (swap! probe assoc :balance {})
            (let [threw? (try
                           (case thrower
                             :rl-vertex-2f
                             (with-redefs [host/rl-vertex-2f boom]
                               (rg/draw-scene! id (:scene-state gs) (draw-args 15)))
                             :draw-circle
                             (with-redefs [host/draw-circle boom]
                               (rg/draw-scene! id (:scene-state gs) (draw-args 15))))
                           false
                           (catch :default _ true))]
              (is threw? (str id " did not reach the redefined " thrower))
              (is (zero? (get-in @probe [:balance :blend] 0))
                  (str id " left the blend mode begun")))))))))

(deftest doom-draw-method-runs-over-stubbed-raylib
  ;; `every-draw-method-runs-over-stubbed-raylib` runs doom's `draw-scene!` on
  ;; idle frames only. This adds the held-stick path: 30 frames, a stick held,
  ;; with the raylib calls it makes counted instead of made.
  (let [m {:screen [1206 2334]}
        counts (atom {:rect 0
                      :text 0
                      :circle 0
                      :line 0
                      :scissor-begin 0
                      :scissor-end 0})
        bump (fn [k] (fn [& _] (swap! counts update k inc)))
        hold (fn [dy] {:metrics m
                       :delta-seconds (/ 1.0 60)
                       :pointer {:phase :down
                                 :position [300.0 (+ 1900.0 dy)]}
                       :touch-points [[300.0 (+ 1900.0 dy)]]
                       :touches {:ids [4]}})
        press (assoc-in (hold 0) [:pointer :phase] :press)
        start (first ((:init (doom/scene)) {:metrics m}))
        states (reductions (fn [st input] (first ((:update (doom/scene)) st input)))
                           start (cons press (repeat 30 (hold -200.0))))
        per-frame (atom [])]
    (with-redefs [rg/host-measure (fn [s size] (* 0.6 size (count s)))
                  host/draw-rectangle (bump :rect)
                  host/draw-text (bump :text)
                  host/draw-circle (bump :circle)
                  host/draw-line-ex (bump :line)
                  host/begin-scissor-mode (bump :scissor-begin)
                  host/end-scissor-mode (bump :scissor-end)
                  host/clear-background (fn [& _] nil)]
      (doseq [st states]
        (reset! counts (zipmap (keys @counts) (repeat 0)))
        (rg/draw-scene! :doom st {:m m
                                  :safe {:x 0
                                         :y 0
                                         :width 1206
                                         :height 2334}})
        (swap! per-frame conj @counts)))
    (is (= 32 (count @per-frame)) "every frame completed")
    (testing "plausible counts a frame: view rects plus 96 minimap cells and the bar, 7 texts, a crosshair and heading line, circles for imps, player, button and a held stick"
      (doseq [c @per-frame]
        (is (<= 120 (:rect c) 450))
        (is (= 7 (:text c)) "five HUD texts, the FIRE label and the caption")
        (is (= 5 (:line c)) "four crosshair lines and the heading line")
        (is (<= 8 (:circle c) 10) "6 imps, the player and the button, and while held the ring and knob")
        (is (= (:scissor-begin c) (inc (:scissor-end c))) "the field's scissor is closed and the safe region's put back")))
    (testing "the held stick is drawn"
      (is (= [8 10 10] [(:circle (first @per-frame)) (:circle (second @per-frame)) (:circle (last @per-frame))])))))
