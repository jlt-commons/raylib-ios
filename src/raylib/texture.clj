(ns raylib.texture
  "GPU textures for scenes, reached through rlgl's scalar layer rather than
  raylib's own by-value Texture2D API.

  raylib's LoadTexture returns a 20-byte Texture2D BY VALUE, which the AArch64
  ABI hands back through the x8 indirect-result register, and Chez's
  foreign-procedure cannot express that. rlgl underneath it is entirely scalar:
  rlLoadTexture takes a raw pixel pointer and returns the GL texture id as an
  unsigned int, and rlSetTexture and rlTexCoord2f draw with it in immediate
  mode. So a texture here is just that id, an int, with no struct anywhere.

  Textures are owned per scene. `id!` uploads on first use of a `[scene-id key]`
  pair and answers the cached id after that; `enter!` frees every texture that
  belongs to a scene other than the one now showing, so nothing outlives the
  scene that built it.

  Lifted from net.b12n.raylib.textures in jlt-commons/raylib-jlt (the rlgl
  declarations, the wrap and filter setters, the upload loop and the quad), with
  the id table, the pow2 rule and `triangles!` added."
  (:require [jolt.ffi :as ffi]
            [raylib.host :as host]))

(ffi/defcfn rl-load-texture       "rlLoadTexture"       [:pointer :int :int :int :int] :uint)
(ffi/defcfn rl-update-texture     "rlUpdateTexture"     [:uint :int :int :int :int :int :pointer] :void)
(ffi/defcfn rl-unload-texture     "rlUnloadTexture"     [:uint] :void)
(ffi/defcfn rl-texture-parameters "rlTextureParameters" [:uint :int :int] :void)
(ffi/defcfn rl-set-texture        "rlSetTexture"        [:uint] :void)
(ffi/defcfn rl-tex-coord-2f       "rlTexCoord2f"        [:float :float] :void)
(ffi/defcfn rl-get-texture-id-default "rlGetTextureIdDefault" [] :uint)

;; rlgl.h
(def ^:private RL-QUADS 0x0007)
(def ^:private RL-TRIANGLES 0x0004)
(def ^:private RL-TEXTURE-WRAP-S 0x2802)
(def ^:private RL-TEXTURE-WRAP-T 0x2803)
(def ^:private RL-TEXTURE-MAG-FILTER 0x2800)
(def ^:private RL-TEXTURE-MIN-FILTER 0x2801)
(def ^:private RL-FILTER {:nearest 0x2600
                          :linear 0x2601})
(def ^:private RL-WRAP {:repeat 0x2901
                        :clamp 0x812F})
;; RL_PIXELFORMAT_UNCOMPRESSED_R8G8B8A8
(def ^:private PIXELFORMAT-R8G8B8A8 7)
(def ^:private WHITE (long 0xFFFFFFFF))

(defonce ^:private table (atom {}))

(defn resident
  "The live textures, `{[scene-id key] {:gl-id :version}}`. For tests."
  []
  (into {} (map (fn [[k v]] [k (select-keys v [:gl-id :version])])) @table))

(defn pow2?
  "True when `n` is a positive power of two."
  [n]
  (and (pos? n) (zero? (bit-and n (dec n)))))

(defn- fill!
  "Write `(pixel x y)` for every texel of a `w` x `h` surface into `buf`, one
  :uint write each. A packed colour is r | g<<8 | b<<16 | a<<24, which is
  byte-for-byte what RGBA8 wants on a little-endian machine. Measured under
  jolt v0.8.16 with a 128x128 pixel fn that packs from x and y: 0.25 us per
  texel (4.1 ms per surface), against 0.34 (5.6 ms) for filling an int array and
  copying it with one write-array."
  [buf w h pixel]
  (dotimes [y h]
    (dotimes [x w]
      (ffi/write buf :uint (pixel x y) (* 4 (+ x (* y w)))))))

(defn id!
  "The rlgl texture id for `key` in scene `scene-id`, uploading it on first use.

  `spec` is `{:w :h :pixel :wrap :filter :version}`. `:pixel` is `(f x y)` to a
  packed colour, `:wrap` is :clamp (the default) or :repeat, `:filter` is
  :nearest (the default) or :linear. A later call with a different `:version`
  rewrites the pixels in place with rlUpdateTexture and keeps the id; the same
  version is a table lookup.

  GLES2 only repeats a power-of-two texture, so :repeat on any other size throws
  an ex-info with :scene, :key, :w and :h."
  [scene-id key {:keys [w h pixel wrap filter version]
                 :or {wrap :clamp
                      filter :nearest}}]
  (let [k [scene-id key]
        have (get @table k)]
    (when (and (= :repeat wrap) (not (and (pow2? w) (pow2? h))))
      (throw (ex-info (str "texture " (pr-str k) ": :wrap :repeat needs power-of-two sizes, got "
                           w "x" h)
                      {:scene scene-id
                       :key key
                       :w w
                       :h h})))
    (cond
      (nil? have)
      (let [buf (ffi/alloc (* w h 4))]
        (try
          (fill! buf w h pixel)
          (let [id (rl-load-texture buf w h PIXELFORMAT-R8G8B8A8 1)]
            (when (zero? id)
              (throw (ex-info (str "texture " (pr-str k) ": rlLoadTexture failed for " w "x" h)
                              {:scene scene-id
                               :key key
                               :w w
                               :h h})))
            (swap! table assoc k {:gl-id id
                                  :version version})
            (rl-texture-parameters id RL-TEXTURE-WRAP-S (RL-WRAP wrap))
            (rl-texture-parameters id RL-TEXTURE-WRAP-T (RL-WRAP wrap))
            (rl-texture-parameters id RL-TEXTURE-MIN-FILTER (RL-FILTER filter))
            (rl-texture-parameters id RL-TEXTURE-MAG-FILTER (RL-FILTER filter))
            id)
          (finally (ffi/free buf))))

      (= version (:version have))
      (:gl-id have)

      :else
      (let [id (:gl-id have)
            buf (ffi/alloc (* w h 4))]
        (try
          (fill! buf w h pixel)
          (rl-update-texture id 0 0 w h PIXELFORMAT-R8G8B8A8 buf)
          (swap! table assoc-in [k :version] version)
          id
          (finally (ffi/free buf)))))))

(defn enter!
  "Free every texture that does not belong to `scene-id`. Called every frame
  with the active scene's id, or nil when no scene is showing (which frees
  everything). A scene that comes back after a free uploads afresh."
  [scene-id]
  (when (seq @table)
    (doseq [[[sid :as k] {:keys [gl-id]}] @table
            :when (not= sid scene-id)]
      (swap! table dissoc k)
      (rl-unload-texture gl-id))))

(defn- unbind!
  "End a textured draw by binding rlgl's default texture. rlSetTexture 0 is not
  enough: it only resets currentTextureId and leaves the open draw call
  textured, so a following same-mode draw (draw-triangle, draw-ring) joins it
  and samples this texture. A non-zero id makes rlSetTexture start a new draw
  call, which is how the next shape gets the default white texel."
  []
  (rl-set-texture (rl-get-texture-id-default)))

(defn- unpack
  [c]
  [(bit-and c 0xff) (bit-and (bit-shift-right c 8) 0xff)
   (bit-and (bit-shift-right c 16) 0xff) (bit-and (bit-shift-right c 24) 0xff)])

(defn quad!
  "Draw texture `id` as one quad, the immediate-mode stand-in for
  DrawTexturePro (whose Rectangle and Vector2 arguments are by value). The
  corners go out in DrawTexturePro's order, top-left, bottom-left,
  bottom-right, top-right, so it batches identically.

    :x :y                where the origin lands in screen space (default 0 0)
    :width :height       destination size (default 100 100)
    :u0 :v0 :u1 :v1      source texcoords (default the whole texture)
    :rotation            degrees, clockwise, about the origin (default 0)
    :origin-x :origin-y  the pivot as an offset into the rectangle (default
                         0 0, its top-left corner)
    :tint                a packed colour multiplied into the texels (default
                         white)"
  [id {:keys [x y width height u0 v0 u1 v1 rotation origin-x origin-y tint]
       :or {x 0
            y 0
            width 100
            height 100
            u0 0.0
            v0 0.0
            u1 1.0
            v1 1.0
            rotation 0.0
            origin-x 0.0
            origin-y 0.0
            tint WHITE}}]
  (let [px (double x)
        py (double y)
        ox (double origin-x)
        oy (double origin-y)
        lx (- ox)
        rx (- (double width) ox)
        ty (- oy)
        by (- (double height) oy)
        rad (Math/toRadians (double rotation))
        c (Math/cos rad)
        s (Math/sin rad)
        ;; y grows downward, so [c -s; s c] turns clockwise on screen, the
        ;; direction DrawTexturePro's positive rotation goes
        vx (fn [dx dy] (+ px (- (* dx c) (* dy s))))
        vy (fn [dx dy] (+ py (* dx s) (* dy c)))
        [r g b a] (unpack tint)
        corner (fn [u v dx dy]
                 (rl-tex-coord-2f (double u) (double v))
                 (host/rl-vertex-2f (double (vx dx dy)) (double (vy dx dy))))]
    (try
      (rl-set-texture id)
      (host/rl-begin RL-QUADS)
      (host/rl-color-4ub r g b a)
      (corner u0 v0 lx ty)
      (corner u0 v1 lx by)
      (corner u1 v1 rx by)
      (corner u1 v0 rx ty)
      (host/rl-end)
      (finally (unbind!)))))

(defn triangles!
  "Draw texture `id` as triangles. `verts` is a flat `[x y u v ...]`, three
  vertices to a triangle, and `tint` a packed colour. Each triangle is wound the
  way `raylib.host/draw-triangle` winds, so rlgl's back-face culling keeps it
  whichever order the points arrive in; a swapped pair takes its texcoords with
  it."
  [id verts tint]
  (let [[r g b a] (unpack tint)
        v (vec verts)
        vtx (fn [i] (let [o (* 4 i)]
                      [(double (nth v o)) (double (nth v (+ o 1)))
                       (double (nth v (+ o 2))) (double (nth v (+ o 3)))]))
        emit (fn [[x y u w]]
               (rl-tex-coord-2f u w)
               (host/rl-vertex-2f x y))]
    (try
      (rl-set-texture id)
      (host/rl-begin RL-TRIANGLES)
      (host/rl-color-4ub r g b a)
      (dotimes [t (quot (count v) 12)]
        (let [p1 (vtx (* 3 t))
              p2 (vtx (+ (* 3 t) 1))
              p3 (vtx (+ (* 3 t) 2))
              cross (- (* (- (p2 0) (p1 0)) (- (p3 1) (p1 1)))
                       (* (- (p2 1) (p1 1)) (- (p3 0) (p1 0))))
              [q2 q3] (if (pos? cross) [p3 p2] [p2 p3])]
          (emit p1)
          (emit q2)
          (emit q3)))
      (host/rl-end)
      (finally (unbind!)))))
