(ns raylib.texture-test
  "Jolt-only tests for raylib.texture: the lifecycle (upload once per key,
  update in place on a new version, free on leaving a scene) and the two draw
  paths' vertex order, over recording stubs for the rlgl calls. `ffi/alloc` and
  `ffi/write` stay real, so the staging buffer's contents are read back from
  native memory.

  This is .clj because raylib.texture loads jolt.ffi. The runner lists it as
  jolt-only and skips it on the JVM."
  (:require [clojure.test :refer [deftest is testing]]
            [jolt.ffi :as ffi]
            [raylib.host :as host]
            [raylib.perlin :as perlin]
            [raylib.probe :as probe]
            [raylib.rlgl-model :as gl]
            [raylib.texel :as texel]
            [raylib.texture :as tex]))

(def ^:private default-id 1)

(def ^:private complete-result
  "What the stubbed `rlFramebufferComplete` answers."
  (atom 1))

(def ^:private sdl-fbo 77)

(def ^:private model
  "The rlgl matrix model the stubbed matrix calls act on."
  (gl/fresh))

(def ^:private bound
  "The framebuffer the stubs say is bound: `sdl-fbo` at the start of a test,
  0 after the calls rlgl implements with an implicit bind of 0."
  (atom sdl-fbo))

(def ^:private mode-of {0x1700 :modelview
                        0x1701 :projection})
(def ^:private screen-size [1206 2334])

(defn- recording
  "Call `(f calls)` with the texture and vertex defcfns redefined to record
  into `calls`, an atom of `[name & args]` vectors. `load-hook` is called with
  the load's args and the new id, and may read the staging buffer."
  ([f] (recording nil f))
  ([load-hook f]
   (let [calls (atom [])
         next-id (atom 100)
         rec (fn [nm] (fn [& args] (swap! calls conj (into [nm] args)) nil))]
     (with-redefs [tex/rl-load-texture (fn [& args]
                                         (let [id (swap! next-id inc)]
                                           (swap! calls conj (into [:load] args))
                                           (when load-hook (load-hook args))
                                           id))
                   tex/rl-update-texture (rec :update)
                   tex/rl-unload-texture (rec :unload)
                   tex/rl-texture-parameters (rec :param)
                   tex/rl-set-texture (rec :set-texture)
                   tex/rl-get-texture-id-default (fn [] default-id)
                   tex/rl-tex-coord-2f (rec :uv)
                   host/rl-begin (rec :begin)
                   host/rl-end (rec :end)
                   host/rl-color-4ub (rec :color)
                   host/rl-vertex-2f (rec :vertex)
                   tex/rl-load-framebuffer (fn []
                                             (let [id (swap! next-id inc)]
                                               (swap! calls conj [:load-fbo])
                                               (reset! bound 0)
                                               id))
                   tex/rl-load-texture-depth (fn [& args]
                                               (let [id (swap! next-id inc)]
                                                 (swap! calls conj (into [:load-depth] args))
                                                 id))
                   tex/rl-framebuffer-attach (fn [& args]
                                               (swap! calls conj (into [:attach] args))
                                               (reset! bound 0))
                   tex/rl-framebuffer-complete (fn [fbo]
                                                 (swap! calls conj [:complete fbo])
                                                 (reset! bound 0)
                                                 @complete-result)
                   tex/rl-enable-framebuffer (fn [fbo]
                                               (swap! calls conj [:enable-fbo fbo])
                                               (reset! bound fbo))
                   tex/rl-disable-framebuffer (fn []
                                                (swap! calls conj [:disable-fbo])
                                                (reset! bound 0))
                   tex/rl-unload-framebuffer (fn [fbo]
                                               (swap! calls conj [:unload-fbo fbo])
                                               (reset! bound 0))
                   tex/rl-viewport (rec :viewport)
                   tex/rl-set-framebuffer-width (rec :fb-width)
                   tex/rl-set-framebuffer-height (rec :fb-height)
                   tex/rl-matrix-mode (fn [m]
                                        (swap! calls conj [:matrix-mode m])
                                        (gl/matrix-mode model (mode-of m)))
                   tex/rl-load-identity (fn []
                                          (swap! calls conj [:identity])
                                          (gl/identity* model))
                   tex/rl-ortho (fn [& args]
                                  (swap! calls conj (into [:ortho] args))
                                  (apply gl/ortho model args))
                   tex/rl-draw-render-batch-active (rec :flush)
                   tex/rl-set-blend-factors (rec :blend-factors)
                   host/end-scissor-mode (rec :end-scissor)
                   host/begin-scissor-mode (rec :begin-scissor)
                   host/rl-push-matrix (fn []
                                         (swap! calls conj [:push])
                                         (gl/push model))
                   host/rl-pop-matrix (fn []
                                        (swap! calls conj [:pop])
                                        (gl/pop* model))
                   host/begin-blend-mode (rec :begin-blend)
                   host/end-blend-mode (rec :end-blend)
                   host/gl-bind-framebuffer (fn [& args]
                                              (swap! calls conj (into [:bind-fbo] args))
                                              (reset! bound (second args)))
                   host/get-screen-width (fn [] (first screen-size))
                   host/get-screen-height (fn [] (second screen-size))]
       ;; Inside the redefs: a table left dirty by an earlier failure would
       ;; otherwise reach the real rlUnloadTexture with no GL context.
       (tex/enter! nil)
       (reset! complete-result 1)
       (reset! bound sdl-fbo)
       (reset! model @(gl/fresh))
       (reset! probe/wm-info {:framebuffer sdl-fbo})
       (try
         (f calls)
         (finally
           (tex/enter! nil)
           (reset! probe/wm-info nil)))))))

(defn- of [calls nm] (filterv #(= nm (first %)) @calls))

(def ^:private red (texel/pack [255 0 0 255]))

(defn- spec [w h & {:as more}]
  (merge {:w w
          :h h
          :pixel (fn [_ _] red)} more))

(deftest pow2
  (is (every? tex/pow2? [1 2 4 64 256]))
  (is (not-any? tex/pow2? [0 3 96 100])))

(deftest upload-once-per-key
  (recording
   (fn [calls]
     (let [a (tex/id! :s :k (spec 64 64))
           b (tex/id! :s :k (spec 64 64))]
       (is (= a b))
       (is (= 1 (count (of calls :load))))
       (let [[_ ptr & more] (first (of calls :load))]
         (is (some? ptr))
         (is (= [64 64 7 1] (vec more))))
       (is (= #{[:param 0x2802 0x812F] [:param 0x2803 0x812F]
                [:param 0x2801 0x2600] [:param 0x2800 0x2600]}
              (into #{} (map (fn [[nm _ p v]] [nm p v])) (of calls :param))))
       (is (every? #(= a (second %)) (of calls :param)))
       (is (= {[:s :k] {:gl-id a
                        :version nil}} (tex/resident)))))))

(deftest a-failed-upload-throws-and-caches-nothing
  (recording
   (fn [calls]
     (with-redefs [tex/rl-load-texture (fn [& _] 0)]
       (let [e (try (tex/id! :s :k (spec 8 4)) nil (catch :default e e))]
         (is (some? e))
         (is (= {:scene :s
                 :key :k
                 :w 8
                 :h 4} (ex-data e)))
         (is (empty? (tex/resident)))
         (is (empty? (of calls :param)))))
     (is (number? (tex/id! :s :k (spec 8 4)))))))

(deftest repeat-and-linear-parameters
  (recording
   (fn [calls]
     (tex/id! :s :k (spec 64 64 :wrap :repeat :filter :linear))
     (is (= #{[0x2802 0x2901] [0x2803 0x2901] [0x2801 0x2601] [0x2800 0x2601]}
            (into #{} (map (fn [[_ _ p v]] [p v])) (of calls :param)))))))

(deftest the-staging-buffer-holds-the-pixels
  (let [seen (atom nil)
        pix (fn [x y] (texel/pack [(+ 10 x) (+ 20 y) (+ 30 x y) 255]))]
    (recording
     (fn [[ptr]] (reset! seen (mapv #(ffi/read ptr :uint (* 4 %)) (range 4))))
     (fn [_]
       (tex/id! :s :k {:w 2
                       :h 2
                       :pixel pix})
       (is (= [(pix 0 0) (pix 1 0) (pix 0 1) (pix 1 1)] @seen))))))

(deftest repeat-on-npot-throws-with-the-size
  (recording
   (fn [calls]
     (let [e (try (tex/id! :s :k (spec 96 64 :wrap :repeat))
                  nil
                  (catch :default e e))]
       (is (some? e))
       (is (= 96 (:w (ex-data e))))
       (is (= 64 (:h (ex-data e))))
       (is (= :s (:scene (ex-data e))))
       (is (= :k (:key (ex-data e))))
       (is (empty? (of calls :load))))
     (is (number? (tex/id! :s :ok (spec 64 64 :wrap :repeat)))))))

(deftest same-version-does-not-reupload
  (recording
   (fn [calls]
     (tex/id! :s :k (spec 8 8 :version 1))
     (tex/id! :s :k (spec 8 8 :version 1))
     (is (= 1 (count (of calls :load))))
     (is (empty? (of calls :update))))))

(deftest new-version-updates-in-place
  (recording
   (fn [calls]
     (let [a (tex/id! :s :k (spec 8 4 :version 1))
           b (tex/id! :s :k (spec 8 4 :version 2))]
       (is (= a b))
       (is (= 1 (count (of calls :load))))
       (is (= 1 (count (of calls :update))))
       (let [[_ id x y w h fmt ptr] (first (of calls :update))]
         (is (= [a 0 0 8 4 7] [id x y w h fmt]))
         (is (some? ptr)))
       (is (= 2 (:version (get (tex/resident) [:s :k]))))))))

;; band!: the continuous-update path. A 4 wide by 6 high texture whose texel
;; (x, y) packs x and y, so a staged row is recognisable by its y.
(defn- band-pixel [x y] (texel/pack [(+ 10 x) (+ 20 y) 7 255]))

(defn- band-spec [& {:as more}]
  (merge {:w 4
          :h 6
          :pixel band-pixel} more))

(defn- staged
  "Call `(f)` with rlUpdateTexture redefined to record its args and, with them,
  the first `n` texels of the staging buffer as `:uint`s, read before it is
  freed. Answers the recorded `[args texels]` pairs."
  [n f]
  (let [seen (atom [])]
    (with-redefs [tex/rl-update-texture
                  (fn [& args]
                    (swap! seen conj [(vec (butlast args))
                                      (mapv #(ffi/read (last args) :uint (* 4 %)) (range n))]))]
      (f))
    @seen))

(deftest band-updates-only-those-rows
  (recording
   (fn [calls]
     (let [id (tex/id! :s :k (band-spec))
           [[args texels]] (staged 8 #(tex/band! :s :k (band-spec) 2 2))]
       (testing "the offset and size are the band's, at full width"
         (is (= [id 0 2 4 2 7] args)))
       (testing "the staging buffer holds exactly rows 2 and 3, in order"
         (is (= (vec (for [y [2 3] x (range 4)] (band-pixel x y))) texels)))
       (is (= 1 (count (of calls :load))) "no second texture")))))

(deftest band-clamps-at-the-bottom
  (recording
   (fn [_]
     (let [id (tex/id! :s :k (band-spec))
           [[args texels] :as all] (staged 4 #(tex/band! :s :k (band-spec) 5 4))]
       (is (= 1 (count all)))
       (is (= [id 0 5 4 1 7] args) "y0 + rows past the bottom keeps only row 5")
       (is (= (mapv #(band-pixel % 5) (range 4)) texels))
       (testing "a band wholly below the texture uploads nothing"
         (is (empty? (staged 4 #(tex/band! :s :k (band-spec) 6 2)))))))))

(deftest band-creates-the-texture-first
  (recording
   (fn [calls]
     (let [id (tex/band! :s :k (band-spec) 0 2)]
       (is (= 1 (count (of calls :load))))
       (is (= [:load 4 6 7 1] (into [:load] (drop 2) (first (of calls :load)))))
       (is (= id (get-in (tex/resident) [[:s :k] :gl-id])))
       (is (= id (tex/id! :s :k (band-spec))) "the same texture, not a second one")))))

(deftest band-leaves-the-version-alone
  (recording
   (fn [_]
     (tex/id! :s :k (band-spec :version 3))
     (tex/band! :s :k (band-spec :version 4) 0 1)
     (is (= 3 (:version (get (tex/resident) [:s :k])))
         "a band is not a whole-texture refresh, so the version stays"))))

(deftest enter-frees-the-previous-scene
  (recording
   (fn [calls]
     (let [a1 (tex/id! :a :one (spec 4 4))
           a2 (tex/id! :a :two (spec 4 4))]
       (tex/enter! :a)
       (is (empty? (of calls :unload)))
       (tex/enter! :b)
       (is (= #{a1 a2} (set (map second (of calls :unload)))))
       (is (= 2 (count (of calls :unload))))
       (is (empty? (tex/resident)))
       (let [b1 (tex/id! :b :one (spec 4 4))]
         (tex/enter! :b)
         (is (= 2 (count (of calls :unload))))
         (tex/enter! nil)
         (is (= #{a1 a2 b1} (set (map second (of calls :unload)))))
         (is (= 3 (count (of calls :unload))))
         (is (empty? (tex/resident))))))))

(deftest enter-again-reuploads-after-a-free
  (recording
   (fn [calls]
     (tex/id! :a :k (spec 4 4))
     (tex/enter! :b)
     (tex/enter! :a)
     (tex/id! :a :k (spec 4 4))
     (is (= 2 (count (of calls :load)))))))

(defn- counting-spec
  "A 4 by 2 spec whose `:pixel` bumps `n` on every call."
  [n & {:as more}]
  (merge {:w 4
          :h 2
          :pixel (fn [x y]
                   (swap! n inc)
                   (texel/pack [(+ 1 x) (+ 2 y) 3 255]))}
         more))

(deftest re-entry-with-the-same-spec-does-not-refill
  (let [n (atom 0)
        loads (atom [])
        shared (counting-spec n)]
    (recording
     (fn [[ptr]] (swap! loads conj [ptr (mapv #(ffi/read ptr :uint (* 4 %)) (range 8))]))
     (fn [calls]
       (let [a (tex/id! :s :k shared)]
         (is (= 8 @n) "the first open fills every texel once")
         (tex/enter! :other)
         (is (empty? (tex/resident)) "the GL texture was freed")
         (is (= 1 (count (of calls :unload))))
         (let [b (tex/id! :s :k shared)]
           (is (not= a b) "a fresh GL texture")
           (is (= 8 @n) "no pixel fn calls on re-entry")
           (is (= 2 (count (of calls :load))) "one rlLoadTexture per entry")
           (is (= (second (first @loads)) (second (second @loads)))
               "the same bytes were staged")
           (is (= (first (first @loads)) (first (second @loads)))
               "from the same kept buffer")))))))

(deftest a-new-spec-object-for-the-same-key-refills
  (let [n (atom 0)]
    (recording
     (fn [calls]
       (tex/id! :s :k (counting-spec n))
       (tex/enter! :other)
       (tex/id! :s :k (counting-spec n))
       (is (= 16 @n) "an equal but not identical spec is filled again")
       (is (= 2 (count (of calls :load))))))))

(deftest a-versioned-spec-is-never-retained
  (let [n (atom 0)
        shared (counting-spec n :version 1)]
    (recording
     (fn [_]
       (tex/id! :s :k shared)
       (tex/enter! :other)
       (tex/id! :s :k shared)
       (is (= 16 @n) "the identical versioned spec is refilled on re-entry")))))

(deftest a-throwing-pixel-fn-keeps-nothing
  (let [n (atom 0)
        bad {:w 4
             :h 2
             :pixel (fn [x _]
                      (swap! n inc)
                      (when (= 2 x) (throw (ex-info "boom" {})))
                      red)}]
    (recording
     (fn [calls]
       (is (thrown? Exception (tex/id! :s :k bad)))
       (is (empty? (tex/resident)))
       (is (empty? (of calls :load)))
       (let [before @n]
         (is (thrown? Exception (tex/id! :s :k bad)))
         (is (> @n before) "the second try fills again, so nothing was kept"))))))

(deftest a-zero-id-from-the-load-keeps-nothing
  (let [n (atom 0)
        shared (counting-spec n)]
    (recording
     (fn [_]
       (with-redefs [tex/rl-load-texture (fn [& _] 0)]
         (is (thrown? Exception (tex/id! :s :k shared))))
       (is (= 8 @n))
       (tex/id! :s :k shared)
       (is (= 16 @n) "the failed load left no kept buffer to reuse")))))

(deftest a-changed-spec-replaces-the-kept-buffer
  (let [n (atom 0)
        a (counting-spec n)
        b (counting-spec n)]
    (recording
     (fn [_]
       (tex/id! :s :k a)
       (tex/enter! :other)
       (tex/id! :s :k b)
       (tex/enter! :other)
       (is (= 16 @n))
       (tex/id! :s :k b)
       (is (= 16 @n) "b is now the kept spec")
       (tex/enter! :other)
       (tex/id! :s :k a)
       (is (= 24 @n) "a was replaced, so it fills again")))))

(deftest a-banded-texture-is-never-kept
  (let [n (atom 0)
        shared (counting-spec n)
        kept-keys (fn [] (set (keys @@#'tex/kept)))]
    (recording
     (fn [calls]
       (tex/band! :s :live shared 0 1)
       (is (= 8 @n) "the first band! of a missing key fills the whole texture")
       (is (not (contains? (kept-keys) [:s :live])) "and keeps nothing")
       (tex/enter! :other)
       (tex/band! :s :live shared 0 1)
       (is (= 16 @n) "so a return fills again, though the spec is the identical object")
       (is (not (contains? (kept-keys) [:s :live])))
       (is (= 2 (count (of calls :load))))
       (testing "id! on a fresh key still keeps"
         (tex/id! :s :static shared)
         (is (contains? (kept-keys) [:s :static])))))))

(deftest the-size-of-a-key-cannot-change-under-a-version-refresh
  (recording
   (fn [calls]
     (tex/id! :s :k (spec 8 4 :version 1))
     (let [e (try (tex/id! :s :k (spec 16 4 :version 2)) nil (catch :default e e))]
       (is (some? e))
       (is (= {:scene :s
               :key :k
               :w 16
               :h 4
               :was-w 8
               :was-h 4}
              (ex-data e)))
       (is (empty? (of calls :update)) "nothing was uploaded")
       (is (= 1 (:version (get (tex/resident) [:s :k]))))
       (is (= #{:gl-id :version} (set (keys (get (tex/resident) [:s :k])))))))))

(deftest the-size-of-a-key-cannot-change-under-a-band
  (recording
   (fn [calls]
     (tex/id! :s :k (band-spec))
     (let [e (try (tex/band! :s :k (band-spec :w 8) 0 2) nil (catch :default e e))]
       (is (some? e))
       (is (= [4 6 8 6] [(:was-w (ex-data e)) (:was-h (ex-data e)) (:w (ex-data e)) (:h (ex-data e))]))
       (is (empty? (of calls :update)))))))

(defn- verts
  "The (x, y) of each :vertex call, in order."
  [calls]
  (mapv (fn [[_ x y]] [x y]) (of calls :vertex)))

(defn- near? [[ax ay] [bx by]]
  (and (< (abs (- ax bx)) 1e-6) (< (abs (- ay by)) 1e-6)))

(deftest quad-emits-raylibs-winding
  (recording
   (fn [calls]
     (tex/quad! 5 {:x 10
                   :y 20
                   :width 30
                   :height 40
                   :u0 0.0
                   :v0 0.25
                   :u1 1.0
                   :v1 0.75})
     (is (= [[10.0 20.0] [10.0 60.0] [40.0 60.0] [40.0 20.0]] (verts calls)))
     (is (= [[0.0 0.25] [0.0 0.75] [1.0 0.75] [1.0 0.25]]
            (mapv (fn [[_ u v]] [u v]) (of calls :uv))))
     (is (= [[:set-texture 5] [:begin 7]] (take 2 (filter (comp #{:set-texture :begin} first) @calls))))
     (is (= [:set-texture default-id] (last @calls)))
     (is (every? double? (mapcat rest (concat (of calls :vertex) (of calls :uv)))))
     (let [[a b c] (verts calls)
           cross (- (* (- (first b) (first a)) (- (second c) (second a)))
                    (* (- (second b) (second a)) (- (first c) (first a))))]
       (is (neg? cross))))))

(deftest quad-rotates-clockwise-about-the-origin
  (recording
   (fn [calls]
     (tex/quad! 5 {:x 100
                   :y 100
                   :width 40
                   :height 20
                   :origin-x 20
                   :origin-y 10
                   :rotation 90})
     (let [vs (verts calls)]
       (is (near? [110.0 80.0] (nth vs 0)) "top-left goes to where top-right was")
       (is (near? [110.0 120.0] (nth vs 3)) "top-right turns down to the bottom-right")
       (is (every? double? (mapcat identity vs)))))))

(deftest quad-tint-is-emitted-as-bytes
  (recording
   (fn [calls]
     (tex/quad! 5 {:width 1
                   :height 1
                   :tint (texel/pack [1 2 3 4])})
     (is (= [[:color 1 2 3 4]] (of calls :color))))))

(deftest quad-unbinds-when-a-vertex-throws
  (recording
   (fn [calls]
     (with-redefs [host/rl-vertex-2f (fn [& _] (throw (ex-info "boom" {})))]
       (is (thrown? Exception (tex/quad! 5 {:width 1
                                            :height 1}))))
     (is (= [:set-texture default-id] (last (of calls :set-texture)))))))

(deftest triangles-unbinds-when-a-vertex-throws
  (recording
   (fn [calls]
     (with-redefs [host/rl-vertex-2f (fn [& _] (throw (ex-info "boom" {})))]
       (is (thrown? Exception
                    (tex/triangles! 5 [0 0 0 0, 1 0 1 0, 0 1 0 1] red))))
     (is (= [:set-texture default-id] (last (of calls :set-texture)))))))

(defn- cross-of [[a b c]]
  (- (* (- (first b) (first a)) (- (second c) (second a)))
     (* (- (second b) (second a)) (- (first c) (first a)))))

(deftest triangles-are-front-wound
  (let [host-sign (let [seen (atom [])]
                    (with-redefs [host/rl-begin (fn [& _] nil)
                                  host/rl-end (fn [& _] nil)
                                  host/rl-color-4ub (fn [& _] nil)
                                  host/rl-vertex-2f (fn [x y] (swap! seen conj [x y]))]
                      (host/draw-triangle 0 0 10 0 0 10 red)
                      (host/draw-triangle 0 0 0 10 10 0 red))
                    (map (comp neg? cross-of) (partition 3 @seen)))]
    (is (every? true? host-sign))
    (recording
     (fn [calls]
       (tex/triangles! 5 [0 0 0.0 0.0, 10 0 1.0 0.0, 0 10 0.0 1.0
                          0 0 0.0 0.0, 0 10 0.0 1.0, 10 0 1.0 0.0] red)
       (let [tris (partition 3 (verts calls))]
         (is (= 2 (count tris)))
         (is (every? neg? (map cross-of tris))))
       (testing "uvs travel with their vertices through the swap"
         (let [vs (verts calls)
               uvs (mapv (fn [[_ u v]] [u v]) (of calls :uv))
               by-pos (into {} (map vector vs uvs))]
           (is (= [1.0 0.0] (get by-pos [10.0 0.0])))
           (is (= [0.0 1.0] (get by-pos [0.0 10.0])))))
       (is (= [:set-texture default-id] (last @calls)))))))

;; --- render targets and blend factors ----------------------------------------

(def ^:private not-param (remove #(= :param (first %))))

(deftest target-creates-fbo-texture-and-depth
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :rt {:w 256
                                   :h 128})
           [flush load-fbo load-tex load-depth a1 a2 complete rebind :as seq*] (into [] not-param @calls)
           fbo (:fbo rt)]
       (is (= 8 (count seq*)))
       (is (= [:flush] flush))
       (is (= [:bind-fbo 0x8D40 sdl-fbo] rebind))
       (is (= [:load-fbo] load-fbo))
       (is (= [:load 7 1] [(first load-tex) (nth load-tex 4) (nth load-tex 5)]))
       (is (= [256 128] [(nth load-tex 2) (nth load-tex 3)]))
       (is (= [:load-depth 256 128 1] load-depth))
       (is (= [:attach fbo (:texture rt) 0 100 0] a1))
       (is (= [:attach fbo (+ 1 (:texture rt)) 100 200 0] a2))
       (is (= [:complete fbo] complete))
       (is (every? #(= (:texture rt) (second %)) (of calls :param)))
       (is (= #{[0x2802 0x812F] [0x2803 0x812F] [0x2801 0x2601] [0x2800 0x2601]}
              (into #{} (map (fn [[_ _ p v]] [p v])) (of calls :param))))
       (is (= {:fbo fbo
               :texture (:texture rt)
               :w 256
               :h 128} rt))
       (is (= {[:s :rt] {:gl-id (:texture rt)
                         :version nil}} (tex/resident)))
       (is (= rt (tex/target! :s :rt {:w 256
                                      :h 128})))
       (is (= 1 (count (of calls :load-fbo))))))))

(deftest an-incomplete-target-throws-with-the-size
  (recording
   (fn [calls]
     (reset! complete-result 0)
     (let [e (try (tex/target! :s :rt {:w 64
                                       :h 32}) nil (catch :default e e))]
       (is (some? e))
       (is (= {:scene :s
               :key :rt
               :w 64
               :h 32} (ex-data e)))
       (is (empty? (tex/resident)))
       (is (= 1 (count (of calls :unload-fbo))))
       (is (= 1 (count (of calls :unload))))))))

(deftest a-new-size-replaces-the-target
  (recording
   (fn [calls]
     (let [a (tex/target! :s :rt {:w 64
                                  :h 64})
           b (tex/target! :s :rt {:w 128
                                  :h 64})]
       (is (not= (:fbo a) (:fbo b)))
       (is (= [[:unload (:texture a)]] (of calls :unload)))
       (is (= [[:unload-fbo (:fbo a)]] (of calls :unload-fbo)))
       (is (= 2 (count (of calls :load-fbo))))
       (is (= {[:s :rt] {:gl-id (:texture b)
                         :version nil}} (tex/resident)))))))

(deftest enter-frees-targets-and-their-textures
  (recording
   (fn [calls]
     (let [a (tex/target! :old :rt {:w 8
                                    :h 8})
           b (tex/target! :new :rt {:w 8
                                    :h 8})]
       (tex/enter! :new)
       (is (= [[:unload (:texture a)]] (of calls :unload)))
       (is (= [[:unload-fbo (:fbo a)]] (of calls :unload-fbo)))
       (is (= #{[:new :rt]} (set (keys (tex/resident)))))
       (tex/enter! nil)
       (is (= [[:unload-fbo (:fbo a)] [:unload-fbo (:fbo b)]] (of calls :unload-fbo)))
       (is (empty? (tex/resident)))))))

(deftest a-target-key-and-a-texture-key-do-not-mix
  (recording
   (fn [_]
     (tex/target! :s :rt {:w 8
                          :h 8})
     (tex/id! :s :plain (spec 8 8))
     (is (some? (try (tex/id! :s :rt (spec 8 8)) nil (catch :default e e))))
     (is (some? (try (tex/target! :s :plain {:w 8
                                             :h 8}) nil (catch :default e e)))))))

(def ^:private safe {:x 0
                     :y 100
                     :width 1206
                     :height 2000})

(deftest with-target-binds-and-restores
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :rt {:w 256
                                   :h 128})
           _ (reset! calls [])
           result (tex/with-target! rt safe (fn []
                                              (swap! calls conj [:f])
                                              :done))]
       (is (= :done result))
       (is (= [[:flush]
               [:end-scissor]
               [:push]
               [:identity]
               [:enable-fbo (:fbo rt)]
               [:viewport 0 0 256 128]
               [:fb-width 256]
               [:fb-height 128]
               [:matrix-mode 0x1701]
               [:identity]
               [:ortho 0.0 256.0 128.0 0.0 0.0 1.0]
               [:matrix-mode 0x1700]
               [:identity]
               [:f]
               [:flush]
               [:bind-fbo 0x8D40 sdl-fbo]
               [:viewport 0 0 1206 2334]
               [:fb-width 1206]
               [:fb-height 2334]
               [:matrix-mode 0x1701]
               [:identity]
               [:ortho 0.0 1206.0 2334.0 0.0 0.0 1.0]
               [:matrix-mode 0x1700]
               [:push]
               [:pop]
               [:pop]
               [:begin-scissor 0 100 1206 2000]]
              @calls))))))

(deftest a-throw-inside-a-pass-restores-the-screen
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :rt {:w 256
                                   :h 128})
           _ (reset! calls [])
           e (try (tex/with-target! rt safe (fn [] (throw (ex-info "boom" {}))))
                  nil
                  (catch :default e e))]
       (is (= "boom" (ex-message e)))
       (is (= [[:flush]
               [:bind-fbo 0x8D40 sdl-fbo]
               [:viewport 0 0 1206 2334]
               [:fb-width 1206]
               [:fb-height 2334]
               [:matrix-mode 0x1701]
               [:identity]
               [:ortho 0.0 1206.0 2334.0 0.0 0.0 1.0]
               [:matrix-mode 0x1700]
               [:push]
               [:pop]
               [:pop]
               [:begin-scissor 0 100 1206 2000]]
              (subvec @calls (- (count @calls) 13))))))))

(deftest two-passes-flush-between-them
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :rt {:w 16
                                   :h 16})]
       (reset! calls [])
       (tex/with-target! rt safe (fn [] nil))
       (tex/with-target! rt safe (fn [] nil))
       (let [n (count @calls)
             half (quot n 2)]
         (is (= (subvec @calls 0 half) (subvec @calls half)))
         (is (= [:flush] (first @calls)))
         (is (= [:begin-scissor 0 100 1206 2000] (nth @calls (dec half)))))))))

(deftest no-sdl-fbo-falls-back-to-disable
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :rt {:w 16
                                   :h 16})]
       (reset! probe/wm-info {})
       (reset! calls [])
       (tex/with-target! rt safe (fn [] nil))
       (is (empty? (of calls :bind-fbo)))
       (is (= 1 (count (of calls :disable-fbo))))))))

(deftest blend-factors-restore-the-default
  (recording
   (fn [calls]
     (is (= :r (tex/with-blend-factors! tex/RL-SRC-ALPHA 1 tex/RL-MIN
                 (fn [] (swap! calls conj [:f]) :r))))
     (is (= [[:blend-factors 0x0302 1 0x8007] [:begin-blend 6] [:f] [:end-blend]] @calls))
     (reset! calls [])
     (let [e (try (tex/with-blend-factors! tex/RL-SRC-ALPHA 1 tex/RL-MAX
                    (fn [] (throw (ex-info "boom" {}))))
                  nil
                  (catch :default e e))]
       (is (= "boom" (ex-message e)))
       (is (= [[:blend-factors 0x0302 1 0x8008] [:begin-blend 6] [:end-blend]] @calls)))
     (is (= [0x0302 0x8007 0x8008] [tex/RL-SRC-ALPHA tex/RL-MIN tex/RL-MAX])))))

(defn- gallery-translate!
  "What raylib.gallery does around a scene: push, then translate by the safe
  region's origin (a push in MODELVIEW mode sends the translate to
  `transform`)."
  [safe-y]
  (gl/push model)
  (gl/translate-y model safe-y))

(deftest a-pass-draws-untranslated-and-gives-the-gallery-transform-back
  (recording
   (fn [_]
     (let [rt (tex/target! :s :rt {:w 256
                                   :h 128})
           inside (atom nil)]
       (gallery-translate! 162)
       (let [before (gl/snapshot model)]
         (is (= 162 (gl/vertex-offset model)))
         (tex/with-target! rt safe (fn [] (reset! inside (gl/vertex-offset model))))
         (is (= 0 @inside) "inside the pass a vertex is not translated")
         (is (= 162 (gl/vertex-offset model)) "after the pass the gallery's translation is back")
         (is (= before (gl/snapshot model)) "transform, modelview, pointer and stack are as they were")
         (testing "the throw path"
           (is (thrown? Exception
                        (tex/with-target! rt safe (fn [] (throw (ex-info "boom" {}))))))
           (is (= before (gl/snapshot model))))
         (gl/pop* model)
         (is (= 0 (gl/vertex-offset model))))))))

(deftest a-pass-with-no-gallery-push-leaves-a-clean-stack
  (recording
   (fn [_]
     (let [rt (tex/target! :s :rt {:w 8
                                   :h 8})
           before (gl/snapshot model)]
       (tex/with-target! rt safe (fn [] nil))
       (is (= before (gl/snapshot model)))))))

(deftest the-screen-is-bound-after-target-and-enter
  (recording
   (fn [calls]
     (testing "a new target"
       (tex/target! :s :rt {:w 64
                            :h 64})
       (is (= sdl-fbo @bound)))
     (testing "the same target again"
       (tex/target! :s :rt {:w 64
                            :h 64})
       (is (= sdl-fbo @bound)))
     (testing "a replaced target"
       (reset! bound 5)
       (tex/target! :s :rt {:w 32
                            :h 64})
       (is (= sdl-fbo @bound)))
     (testing "an incomplete target"
       (reset! complete-result 0)
       (is (thrown? Exception (tex/target! :s :bad {:w 8
                                                    :h 8})))
       (is (= sdl-fbo @bound)))
     (testing "a throw while building"
       (with-redefs [tex/rl-load-texture-depth (fn [& _] (throw (ex-info "boom" {})))]
         (is (thrown? Exception (tex/target! :s :bad2 {:w 8
                                                       :h 8}))))
       (is (= sdl-fbo @bound)))
     (testing "enter! freeing a target flushes first and rebinds"
       (reset! calls [])
       (tex/enter! :other)
       (is (= [:flush] (first @calls)))
       (is (= sdl-fbo @bound))
       (is (= [:bind-fbo 0x8D40 sdl-fbo] (peek @calls)))))))

;; perlin-texture!: raylib's own GenImagePerlinNoise, uploaded straight from C.
;; The generator is stubbed to fill the 24-byte Image the caller passes with a
;; data pointer, width, height, mipmaps and format that differ from what was
;; asked for, so a call that uploaded the REQUESTED size or format instead of
;; the returned one is caught.
(def ^:private image-l
  (ffi/layout [:struct [[:data :pointer] [:width :int] [:height :int]
                        [:mipmaps :int] [:format :int]]]))

(def ^:private perlin-spec
  {:w 800
   :h 450
   :offset-x 0
   :offset-y 0
   :scale 6.0})

(defn- with-perlin
  "Call `(f calls pixels)` with GenImagePerlinNoise and MemFree stubbed to
  record into `calls` (`:gen` and `:mem-free`), on top of `recording`'s stubs.
  `returned` is the `{:w :h :format :null?}` the stub writes into the Image.
  `pixels` is the buffer the stub says raylib allocated."
  [returned f]
  (recording
   (fn [calls]
     (let [pixels (ffi/alloc 16)]
       (try
         (with-redefs [tex/gen-image-perlin-noise
                       (fn [img & args]
                         (swap! calls conj (into [:gen] args))
                         (ffi/write-field img image-l :data (if (:null? returned) 0 pixels))
                         (ffi/write-field img image-l :width (:w returned))
                         (ffi/write-field img image-l :height (:h returned))
                         (ffi/write-field img image-l :mipmaps 1)
                         (ffi/write-field img image-l :format (:format returned)))
                       tex/mem-free (fn [p] (swap! calls conj [:mem-free p]) nil)]
           (f calls pixels))
         (finally (ffi/free pixels)))))))

(def ^:private returned {:w 10
                         :h 6
                         :format 4})

(deftest perlin-uploads-what-raylib-returned
  (with-perlin returned
    (fn [calls pixels]
      (let [id (tex/perlin-texture! :s :p perlin-spec)]
        (testing "counts first: one generation, one upload, one free"
          (is (= 1 (count (of calls :gen))))
          (is (= 1 (count (of calls :load))))
          (is (= 1 (count (of calls :mem-free)))))
        (testing "the generator is asked for the spec's w h offsets and scale"
          (is (= [[:gen 800 450 0 0 6.0]] (of calls :gen))))
        (testing "the upload is the returned pointer, width, height and format, no mipmaps"
          (is (= [[:load pixels 10 6 4 1]] (of calls :load))))
        (testing "that pointer is the one freed, after the upload"
          (is (= [[:mem-free pixels]] (of calls :mem-free)))
          (is (< (.indexOf (mapv first @calls) :load) (.indexOf (mapv first @calls) :mem-free))))
        (testing "clamped and linear"
          (is (= #{[:param 0x2802 0x812F] [:param 0x2803 0x812F]
                   [:param 0x2801 0x2601] [:param 0x2800 0x2601]}
                 (into #{} (map (fn [[nm _ p v]] [nm p v])) (of calls :param))))
          (is (every? #(= id (second %)) (of calls :param))))
        (testing "the id lands in the table, under the scene"
          (is (= {[:s :p] {:gl-id id
                           :version nil}} (tex/resident))))))))

(deftest perlin-is-cached-and-freed-with-its-scene
  (with-perlin returned
    (fn [calls _]
      (let [a (tex/perlin-texture! :s :p perlin-spec)
            b (tex/perlin-texture! :s :p (assoc perlin-spec :w 800))]
        (is (= a b))
        (is (= 1 (count (of calls :gen))) "the same values answer the same id with no work")
        (is (= 1 (count (of calls :load))))
        (is (= 1 (count (of calls :mem-free)))))
      (let [id (:gl-id (get (tex/resident) [:s :p]))]
        (tex/enter! :other)
        (is (= [[:unload id]] (of calls :unload)) "enter! frees it with the scene")
        (is (empty? (tex/resident)))
        (let [c (tex/perlin-texture! :other :p perlin-spec)]
          (is (not= id c))
          (is (= 2 (count (of calls :gen)))))))))

(deftest a-different-perlin-spec-replaces-the-texture
  (with-perlin returned
    (fn [calls _]
      (let [a (tex/perlin-texture! :s :p perlin-spec)
            b (tex/perlin-texture! :s :p (assoc perlin-spec :scale 3.0))]
        (is (not= a b))
        (is (= 2 (count (of calls :gen))))
        (is (= 2 (count (of calls :mem-free))))
        (is (= [[:unload a]] (of calls :unload)) "the old texture is freed")
        (is (= {[:s :p] {:gl-id b
                         :version nil}} (tex/resident)))))))

(deftest perlin-frees-the-pixels-when-the-upload-fails
  (with-perlin returned
    (fn [calls pixels]
      (with-redefs [tex/rl-load-texture (fn [& _] 0)]
        (let [e (try (tex/perlin-texture! :s :p perlin-spec) nil (catch :default e e))]
          (is (some? e))
          (is (= {:scene :s
                  :key :p
                  :w 800
                  :h 450} (ex-data e)))
          (is (= [[:mem-free pixels]] (of calls :mem-free)) "freed on the failing path")
          (is (empty? (tex/resident)))
          (is (empty? (of calls :param))))))))

(deftest perlin-frees-the-pixels-when-the-parameters-throw
  (with-perlin returned
    (fn [calls pixels]
      (with-redefs [tex/rl-texture-parameters (fn [& _] (throw (ex-info "boom" {})))]
        (is (thrown? Exception (tex/perlin-texture! :s :p perlin-spec)))
        (is (= [[:mem-free pixels]] (of calls :mem-free)) "freed when a later call throws")))))

(deftest perlin-with-no-pixels-throws-and-uploads-nothing
  (with-perlin (assoc returned :null? true)
    (fn [calls _]
      (let [e (try (tex/perlin-texture! :s :p perlin-spec) nil (catch :default e e))]
        (is (some? e))
        (is (= 800 (:w (ex-data e))))
        (is (= 1 (count (of calls :gen))))
        (is (empty? (of calls :load)))
        (is (empty? (of calls :mem-free)) "a NULL pointer is not freed")
        (is (empty? (tex/resident)))))))

(deftest perlin-refuses-a-render-target-key
  (with-perlin returned
    (fn [calls _]
      (tex/target! :s :p {:w 8
                          :h 8})
      (is (thrown? Exception (tex/perlin-texture! :s :p perlin-spec)))
      (is (empty? (of calls :gen))))))

;; The native call against the pure model. This needs the real libraylib, which
;; `jolt -M:test` does not load (the project links raylib statically on the
;; phone), so it skips and says so unless it is run with the library declared:
;;   jolt -Sdeps '{:jolt/native [{:name "raylib" :darwin ["/opt/homebrew/lib/libraylib.dylib"]}]}' -M:test
(defn- native-perlin
  "The image GenImagePerlinNoise answers for `spec`, as `{:w :h :format :grey}`
  where `:grey` is a fn from x y to the first byte of that texel; nil when
  libraylib cannot be called here."
  [{:keys [w h offset-x offset-y scale]}]
  (try
    (let [img (ffi/alloc 24)]
      (try
        (tex/gen-image-perlin-noise img w h offset-x offset-y scale)
        (let [data (ffi/read-field img image-l :data)]
          (try
            {:w (ffi/read-field img image-l :width)
             :h (ffi/read-field img image-l :height)
             :format (ffi/read-field img image-l :format)
             :bytes (mapv (fn [i] (ffi/read data :uint8 i)) (range (* 4 w h)))}
            (finally (tex/mem-free data))))
        (finally (ffi/free img))))
    (catch :default _ nil)))

(deftest the-native-perlin-image-is-the-pure-models
  (let [spec {:w 64
              :h 36
              :offset-x 5
              :offset-y 9
              :scale 6.0}
        img (native-perlin spec)]
    (if (nil? img)
      (println "SKIPPED the-native-perlin-image-is-the-pure-models: libraylib is not loadable here")
      (do
        (is (= [64 36 7] [(:w img) (:h img) (:format img)]))
        (testing "every texel is the model's grey in R, G and B, and opaque"
          (is (= 0 (count (for [y (range 36) x (range 64)
                                :let [o (* 4 (+ x (* y 64)))
                                      g (perlin/perlin-grey 64 36 5 9 6.0 x y)
                                      b (:bytes img)]
                                :when (not= [g g g 255] [(nth b o) (nth b (+ o 1)) (nth b (+ o 2)) (nth b (+ o 3))])]
                            [x y])))))))))

(deftest circle-gradient-is-raylibs-fan
  ;; host/draw-circle-gradient rebuilds DrawCircleGradient from rlgl, because the
  ;; C takes its centre as a Vector2 by value. 36 wedges of 10 degrees, each the
  ;; centre in the inner colour and two rim points in the outer, in the order that
  ;; survives back-face culling (a negative cross product, y down).
  (recording
   (fn [calls]
     (host/draw-circle-gradient 100.0 200.0 50.0 (texel/pack [255 255 255 0]) (texel/pack [10 20 30 255]))
     (let [verts (mapv (fn [[_ x y]] [x y]) (of calls :vertex))
           colours (mapv (fn [[_ & c]] (vec c)) (of calls :color))]
       (testing "counts first: one triangle batch, 36 wedges of three vertices"
         (is (= [[:begin 4]] (of calls :begin)))
         (is (= 1 (count (of calls :end))))
         (is (= 108 (count verts)))
         (is (= 108 (count colours))))
       (testing "the centre is inner, the rim outer, in every wedge"
         (is (every? #(= [100.0 200.0] (nth verts (* 3 %))) (range 36)))
         (is (every? #(= [255 255 255 0] (nth colours (* 3 %))) (range 36)))
         (is (every? #(= [10 20 30 255] (nth colours (+ 1 (* 3 %)))) (range 36)))
         (is (every? #(= [10 20 30 255] (nth colours (+ 2 (* 3 %)))) (range 36))))
       (testing "the rim points are 50 out, at i + 10 degrees and then i"
         (let [[x1 y1] (nth verts 1)
               [x2 y2] (nth verts 2)]
           (is (< (abs (- x1 (+ 100.0 (* 50.0 (Math/cos (Math/toRadians 10.0)))))) 1e-4))
           (is (< (abs (- y1 (+ 200.0 (* 50.0 (Math/sin (Math/toRadians 10.0)))))) 1e-4))
           (is (< (abs (- x2 150.0)) 1e-4))
           (is (< (abs (- y2 200.0)) 1e-4))))
       (testing "every wedge survives back-face culling: a negative cross product"
         (is (every? (fn [i]
                       (let [[ax ay] (nth verts (* 3 i))
                             [bx by] (nth verts (+ 1 (* 3 i)))
                             [cx cy] (nth verts (+ 2 (* 3 i)))]
                         (neg? (- (* (- bx ax) (- cy ay)) (* (- by ay) (- cx ax))))))
                     (range 36))))))))

;; target! with :depth? false: a pass that draws only flat shapes needs no depth
;; renderbuffer (rlgl runs 2D with the depth test off).
(deftest a-target-without-depth-makes-and-attaches-none
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :flat {:w 256
                                     :h 128
                                     :depth? false})
           fbo (:fbo rt)]
       (testing "counts first: one framebuffer, one colour texture, no depth"
         (is (= 1 (count (of calls :load-fbo))))
         (is (= 1 (count (of calls :load))))
         (is (= 0 (count (of calls :load-depth)))))
       (testing "the only attach is the colour texture"
         (is (= [[:attach fbo (:texture rt) 0 100 0]] (of calls :attach))))
       (testing "it is completed and the screen bound again, like any target"
         (is (= [[:complete fbo]] (of calls :complete)))
         (is (= sdl-fbo @bound)))
       (testing "the entry is the same shape, and the same size answers the same target"
         (is (= {:fbo fbo
                 :texture (:texture rt)
                 :w 256
                 :h 128} rt))
         (is (= rt (tex/target! :s :flat {:w 256
                                          :h 128
                                          :depth? false})))
         (is (= 1 (count (of calls :load-fbo)))))))))

(deftest a-target-without-depth-binds-and-restores-like-any-target
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :flat {:w 256
                                     :h 128
                                     :depth? false})
           _ (reset! calls [])]
       (tex/with-target! rt safe (fn [] (swap! calls conj [:f])))
       (is (= [[:flush] [:end-scissor] [:push] [:identity] [:enable-fbo (:fbo rt)]
               [:viewport 0 0 256 128]]
              (subvec @calls 0 6)))
       (is (some #{[:f]} @calls))
       (is (= sdl-fbo @bound))
       (is (= [:begin-scissor 0 100 1206 2000] (peek @calls)))))))

(deftest freeing-a-target-without-depth-unloads-its-framebuffer-and-colour-only
  (recording
   (fn [calls]
     (let [rt (tex/target! :s :flat {:w 64
                                     :h 64
                                     :depth? false})
           _ (reset! calls [])]
       (tex/enter! :other)
       (is (= [[:unload (:texture rt)]] (of calls :unload)))
       (is (= [[:unload-fbo (:fbo rt)]] (of calls :unload-fbo)))
       (is (= 0 (count (of calls :load-depth))))
       (is (= sdl-fbo @bound))
       (is (empty? (tex/resident))))))
  (testing "a depth target is made and freed as before"
    (recording
     (fn [calls]
       (tex/target! :s :deep {:w 64
                              :h 64})
       (is (= [[:load-depth 64 64 1]] (of calls :load-depth)))
       (is (= 2 (count (of calls :attach))))))))

(deftest depth-defaults-to-true-and-explicit-true-matches
  (recording
   (fn [calls]
     (tex/target! :s :a {:w 8
                         :h 8})
     (tex/target! :s :b {:w 8
                         :h 8
                         :depth? true})
     (is (= 2 (count (of calls :load-depth))))
     (is (= 4 (count (of calls :attach)))))))
