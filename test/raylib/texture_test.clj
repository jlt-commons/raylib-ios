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
            [raylib.texel :as texel]
            [raylib.texture :as tex]))

(def ^:private default-id 1)

(defn- recording
  "Call `(f calls)` with the texture and vertex defcfns redefined to record
  into `calls`, an atom of `[name & args]` vectors. `load-hook` is called with
  the load's args and the new id, and may read the staging buffer."
  ([f] (recording nil f))
  ([load-hook f]
   (let [calls (atom [])
         next-id (atom 100)
         rec (fn [nm] (fn [& args] (swap! calls conj (into [nm] args)) nil))]
     (tex/enter! nil)
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
                   host/rl-vertex-2f (rec :vertex)]
       (try
         (f calls)
         (finally (tex/enter! nil)))))))

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
