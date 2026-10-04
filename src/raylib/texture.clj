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
  scene that built it on the GPU.

  The CPU side is kept. A static spec (no `:version`, and the same map object
  each time, as a scene's `def` or `delay` gives) keeps its filled `w * h * 4`
  byte staging buffer for the life of the app, so coming back to a scene costs
  `rlLoadTexture` alone, with no pixel fn calls and no write loop. Specs are
  pure, so the same object means the same pixels. A different spec object under
  the same key refills and replaces the kept buffer. A versioned spec is never
  kept, and `band!` and the version refresh use temporary buffers, and a texture first made by `band!`
  is never kept (it is dynamic).

  Render targets live in the same table. `target!` makes an off-screen
  framebuffer with an RGBA8 colour texture and, unless the spec says
  `:depth? false`, a depth renderbuffer under a `[scene-id key]` pair, and
  `enter!` frees it with the rest of that scene's textures. A target is render
  output, so it is never kept on the CPU side, and `id!` refuses a key that
  holds one (and `target!` a key that holds a plain texture). `with-target!` draws into one and gives the screen back: SDL's
  drawable framebuffer, the viewport, the projection, the matrix and the
  gallery's scissor. `with-blend-factors!` sets rlgl's custom blend factors for
  the length of a call.

  `perlin-texture!` is the one place raylib's own image generator is called. The
  Image comes back by value, which jolt takes as a buffer passed first, and its
  pixels go straight to rlLoadTexture and are freed with MemFree, so no
  by-value argument is ever needed to consume it.

  Lifted from net.b12n.raylib.textures in jlt-commons/raylib-jlt (the rlgl
  declarations, the wrap and filter setters, the upload loop and the quad, and
  the render-texture, with-render-texture and restore-screen-projection!
  bodies), with the id table, the pow2 rule and `triangles!` added."
  (:require [jolt.ffi :as ffi]
            [raylib.host :as host]
            [raylib.probe :as probe]))

(ffi/defcfn rl-load-texture       "rlLoadTexture"       [:pointer :int :int :int :int] :uint)
(ffi/defcfn rl-update-texture     "rlUpdateTexture"     [:uint :int :int :int :int :int :pointer] :void)
(ffi/defcfn rl-unload-texture     "rlUnloadTexture"     [:uint] :void)
(ffi/defcfn rl-texture-parameters "rlTextureParameters" [:uint :int :int] :void)
(ffi/defcfn rl-set-texture        "rlSetTexture"        [:uint] :void)
(ffi/defcfn rl-tex-coord-2f       "rlTexCoord2f"        [:float :float] :void)
(ffi/defcfn rl-get-texture-id-default "rlGetTextureIdDefault" [] :uint)

;; rlgl framebuffers (render targets), all scalar
(ffi/defcfn rl-load-framebuffer       "rlLoadFramebuffer"       [] :uint)
(ffi/defcfn rl-framebuffer-attach     "rlFramebufferAttach"     [:uint :uint :int :int :int] :void)
(ffi/defcfn rl-framebuffer-complete   "rlFramebufferComplete"   [:uint] :uint8)
(ffi/defcfn rl-enable-framebuffer     "rlEnableFramebuffer"     [:uint] :void)
(ffi/defcfn rl-disable-framebuffer    "rlDisableFramebuffer"    [] :void)
(ffi/defcfn rl-unload-framebuffer     "rlUnloadFramebuffer"     [:uint] :void)
(ffi/defcfn rl-load-texture-depth     "rlLoadTextureDepth"      [:int :int :int] :uint)
(ffi/defcfn rl-viewport               "rlViewport"              [:int :int :int :int] :void)
(ffi/defcfn rl-set-framebuffer-width  "rlSetFramebufferWidth"   [:int] :void)
(ffi/defcfn rl-set-framebuffer-height "rlSetFramebufferHeight"  [:int] :void)
(ffi/defcfn rl-matrix-mode            "rlMatrixMode"            [:int] :void)
(ffi/defcfn rl-load-identity          "rlLoadIdentity"          [] :void)
(ffi/defcfn rl-ortho                  "rlOrtho"                 [:double :double :double :double :double :double] :void)
(ffi/defcfn rl-draw-render-batch-active "rlDrawRenderBatchActive" [] :void)
(ffi/defcfn rl-set-blend-factors      "rlSetBlendFactors"       [:int :int :int] :void)

;; raylib's own perlin image. GenImagePerlinNoise returns the 24-byte Image by
;; value, which jolt takes as a buffer passed first (the convention
;; net.b12n.raylib.images documents). The Image is never handed to raylib again:
;; its `data` goes straight to rlLoadTexture and is freed with MemFree, so no
;; by-value argument is needed and LoadTextureFromImage and UnloadImage are not.
(def ^:private image-layout
  (ffi/layout [:struct [[:data :pointer] [:width :int] [:height :int]
                        [:mipmaps :int] [:format :int]]]))

(ffi/defcfn gen-image-perlin-noise "GenImagePerlinNoise" [:int :int :int :int :float]
  [:by-value [:struct [[:data :pointer] [:width :int] [:height :int]
                       [:mipmaps :int] [:format :int]]]])
(ffi/defcfn mem-free "MemFree" [:pointer] :void)

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

(def ^:private RL-MODELVIEW 0x1700)
(def ^:private RL-PROJECTION 0x1701)
(def ^:private RL-ATTACHMENT-COLOR-CHANNEL0 0)
(def ^:private RL-ATTACHMENT-DEPTH 100)
(def ^:private RL-ATTACHMENT-TEXTURE2D 100)
(def ^:private RL-ATTACHMENT-RENDERBUFFER 200)
(def ^:private GL-FRAMEBUFFER 0x8D40)
;; raylib.h BlendMode BLEND_CUSTOM
(def ^:private BLEND-CUSTOM 6)

;; rlgl.h blend factors and equations, for `with-blend-factors!`
(def RL-SRC-ALPHA 0x0302)
(def RL-MIN 0x8007)
(def RL-MAX 0x8008)

(defonce ^:private table (atom {}))

;; {[scene-id key] {:spec spec :buf ptr}}: the filled staging buffer of the last
;; static spec uploaded under each key. Never freed by `enter!`.
(defonce ^:private kept (atom {}))

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

(defn- check-size!
  "Throw unless `w` by `h` is the size `have` was uploaded at. rlUpdateTexture
  past the texture's edge is a GL error that raises nothing."
  [scene-id key have w h]
  (when (or (not= w (:w have)) (not= h (:h have)))
    (throw (ex-info (str "texture " (pr-str [scene-id key]) ": size changed from "
                         (:w have) "x" (:h have) " to " w "x" h)
                    {:scene scene-id
                     :key key
                     :w w
                     :h h
                     :was-w (:w have)
                     :was-h (:h have)}))))

(defn- upload!
  "`id!`, with `retain?` saying whether a first upload keeps its staging buffer.
  `band!` passes false: a banded texture is dynamic, so a kept buffer would be
  stale and never reused."
  [scene-id key {:keys [w h pixel wrap filter version]
                 :or {wrap :clamp
                      filter :nearest}
                 :as spec} retain?]
  (let [k [scene-id key]
        have (get @table k)]
    (when (:fbo have)
      (throw (ex-info (str "texture " (pr-str k) ": the key holds a render target")
                      {:scene scene-id
                       :key key})))
    (when (and (= :repeat wrap) (not (and (pow2? w) (pow2? h))))
      (throw (ex-info (str "texture " (pr-str k) ": :wrap :repeat needs power-of-two sizes, got "
                           w "x" h)
                      {:scene scene-id
                       :key key
                       :w w
                       :h h})))
    (cond
      (nil? have)
      (let [old (get @kept k)
            reuse? (and retain? (nil? version) (identical? spec (:spec old)))
            buf (if reuse? (:buf old) (ffi/alloc (* w h 4)))
            owned? (atom (not reuse?))]
        (try
          (when-not reuse?
            (fill! buf w h pixel))
          (let [id (rl-load-texture buf w h PIXELFORMAT-R8G8B8A8 1)]
            (when (zero? id)
              (throw (ex-info (str "texture " (pr-str k) ": rlLoadTexture failed for " w "x" h)
                              {:scene scene-id
                               :key key
                               :w w
                               :h h})))
            (swap! table assoc k {:gl-id id
                                  :version version
                                  :w w
                                  :h h})
            (rl-texture-parameters id RL-TEXTURE-WRAP-S (RL-WRAP wrap))
            (rl-texture-parameters id RL-TEXTURE-WRAP-T (RL-WRAP wrap))
            (rl-texture-parameters id RL-TEXTURE-MIN-FILTER (RL-FILTER filter))
            (rl-texture-parameters id RL-TEXTURE-MAG-FILTER (RL-FILTER filter))
            (when (and retain? (nil? version) (not reuse?))
              (when-let [prev (:buf old)]
                (ffi/free prev))
              (swap! kept assoc k {:spec spec
                                   :buf buf})
              (reset! owned? false))
            id)
          (finally
            (when @owned?
              (ffi/free buf)))))

      (= version (:version have))
      (:gl-id have)

      :else
      (let [id (:gl-id have)
            buf (do (check-size! scene-id key have w h)
                    (ffi/alloc (* w h 4)))]
        (try
          (fill! buf w h pixel)
          (rl-update-texture id 0 0 w h PIXELFORMAT-R8G8B8A8 buf)
          (swap! table assoc-in [k :version] version)
          id
          (finally (ffi/free buf)))))))

(defn id!
  "The rlgl texture id for `key` in scene `scene-id`, uploading it on first use.

  `spec` is `{:w :h :pixel :wrap :filter :version}`. `:pixel` is `(f x y)` to a
  packed colour, `:wrap` is :clamp (the default) or :repeat, `:filter` is
  :nearest (the default) or :linear. A later call with a different `:version`
  rewrites the pixels in place with rlUpdateTexture and keeps the id; the same
  version is a table lookup. A size that differs from the one uploaded under the
  same key throws, since rlUpdateTexture would write outside the texture.

  A spec without a `:version` is kept: its filled staging buffer stays for the
  life of the app, and a later first-use of the same key (after `enter!` freed
  the GL texture) with the identical spec object loads from that buffer without
  calling `:pixel`. See the ns docstring.

  GLES2 only repeats a power-of-two texture, so :repeat on any other size throws
  an ex-info with :scene, :key, :w and :h."
  [scene-id key spec]
  (upload! scene-id key spec true))

(defn band!
  "Refill rows [`y0`, `y0` + `rows`) of the texture for `key` in scene
  `scene-id` from `spec`'s `:pixel`, and upload only those rows with
  rlUpdateTexture's offset and size. For a continuous-update texture: a phone
  cannot afford `id!`'s whole-texture rewrite every frame (about 0.25 us a texel
  here, so a 128 by 128 surface is 4 ms on a laptop and over 100 ms on the
  phone), but it can afford a few rows. A scene walks the band down the texture,
  so every row refreshes every `h / rows` frames and no frame pays for more
  than the band.

  `:pixel` is called with the texture's own y, not the band's. The band is cut
  off at the bottom (a `y0` and `rows` that run past the last row keep only the
  rows that exist, and a band wholly below uploads nothing). If the texture does
  not exist yet, it is made first, filling all of it, and its staging buffer is
  not kept, as `id!` would keep a static one. A band never changes
  the texture's `:version`; that belongs to `id!`'s whole-texture refresh.
  Answers the GL id."
  [scene-id key {:keys [w h pixel]
                 :as spec} y0 rows]
  (if-let [{:keys [gl-id]
            :as have} (get @table [scene-id key])]
    (let [_ (when (:fbo have)
              (throw (ex-info (str "texture " (pr-str [scene-id key]) ": the key holds a render target")
                              {:scene scene-id
                               :key key})))
          _ (check-size! scene-id key have w h)
          y0 (max 0 y0)
          n (- (min h (+ y0 rows)) y0)]
      (when (pos? n)
        (let [buf (ffi/alloc (* w n 4))]
          (try
            (fill! buf w n (fn [x y] (pixel x (+ y0 y))))
            (rl-update-texture gl-id 0 y0 w n PIXELFORMAT-R8G8B8A8 buf)
            (finally (ffi/free buf)))))
      gl-id)
    (upload! scene-id key spec false)))

(defn perlin-texture!
  "The rlgl texture id of raylib's own Perlin image for `key` in scene
  `scene-id`, generated and uploaded on first use. `spec` is `{:w :h :offset-x
  :offset-y :scale}`, the arguments of GenImagePerlinNoise, and the image is the
  one `raylib.perlin/perlin-grey` models texel by texel.

  This runs the C because the pure model is too slow for a whole image: 800 by
  450 is 360000 texels of fbm, and `perlin-grey` costs about 17 microseconds a
  texel under laptop jolt (6 s for the image) and far more on the phone.
  The image's pixels are uploaded straight from the buffer raylib allocated, in
  the format it reports, and that buffer is freed with MemFree on every path.
  The texture is clamped and linear, with no mipmaps.

  It is filed in the same table as `id!`, so `enter!` frees it with the scene's
  other textures, and the same `spec` values (compared by value) answer the same
  id with no work, so asking every frame costs a lookup. A different spec under
  the key replaces the texture. A key holding a render target throws, as `id!`
  does. Throws an ex-info with :scene, :key, :w and :h when raylib answers no
  pixels or the upload fails, and caches nothing."
  [scene-id key {:keys [w h offset-x offset-y scale]
                 :as spec}]
  (let [k [scene-id key]
        have (get @table k)
        wanted (select-keys spec [:w :h :offset-x :offset-y :scale])
        fail (fn [what]
               (ex-info (str "texture " (pr-str k) ": " what " for " w "x" h)
                        {:scene scene-id
                         :key key
                         :w w
                         :h h}))]
    (when (:fbo have)
      (throw (ex-info (str "texture " (pr-str k) ": the key holds a render target")
                      {:scene scene-id
                       :key key})))
    (if (and have (= wanted (:perlin have)))
      (:gl-id have)
      (let [img (ffi/alloc (ffi/layout-size image-layout))]
        (try
          (gen-image-perlin-noise img (int w) (int h) (int offset-x) (int offset-y) (float scale))
          (let [data (ffi/read-field img image-layout :data)
                iw (ffi/read-field img image-layout :width)
                ih (ffi/read-field img image-layout :height)
                fmt (ffi/read-field img image-layout :format)
                ;; a NULL pointer reads back as 0
                null? (or (nil? data) (and (number? data) (zero? data)))]
            (try
              (when null?
                (throw (fail "GenImagePerlinNoise answered no pixels")))
              (let [id (rl-load-texture data iw ih fmt 1)]
                (when (zero? id)
                  (throw (fail "rlLoadTexture failed")))
                (when have
                  (swap! table dissoc k)
                  (rl-unload-texture (:gl-id have)))
                (swap! table assoc k {:gl-id id
                                      :version nil
                                      :w iw
                                      :h ih
                                      :perlin wanted})
                (rl-texture-parameters id RL-TEXTURE-WRAP-S (RL-WRAP :clamp))
                (rl-texture-parameters id RL-TEXTURE-WRAP-T (RL-WRAP :clamp))
                (rl-texture-parameters id RL-TEXTURE-MIN-FILTER (RL-FILTER :linear))
                (rl-texture-parameters id RL-TEXTURE-MAG-FILTER (RL-FILTER :linear))
                id)
              (finally
                (when-not null?
                  (mem-free data)))))
          (finally (ffi/free img)))))))

(defn- bind-screen!
  "Bind the framebuffer the screen is: SDL's drawable on the phone, and the
  default framebuffer when the host recorded none."
  []
  (if-let [fbo (:framebuffer @probe/wm-info)]
    (host/gl-bind-framebuffer GL-FRAMEBUFFER fbo)
    (rl-disable-framebuffer)))

(defn- unload-entry!
  "Free one table entry's GL objects. A target's framebuffer takes its depth
  renderbuffer with it; the colour texture is ours to free.

  rlUnloadFramebuffer binds framebuffer 0 when it is done (rlgl.h, 6.0:
  glBindFramebuffer(GL_FRAMEBUFFER, 0) at the end of the function), and on iOS 0
  is not the screen. This runs mid-frame, so the batch is flushed first and
  SDL's framebuffer is bound again after, on every path."
  [gl-id fbo]
  (when fbo
    (rl-draw-render-batch-active))
  (try
    (rl-unload-texture gl-id)
    (when fbo
      (rl-unload-framebuffer fbo))
    (finally
      (when fbo
        (bind-screen!)))))

(defn enter!
  "Free every texture and render target that does not belong to `scene-id`. Called every frame
  with the active scene's id, or nil when no scene is showing (which frees
  everything). A scene that comes back after a free uploads afresh."
  [scene-id]
  (when (seq @table)
    (doseq [[[sid :as k] {:keys [gl-id fbo]}] @table
            :when (not= sid scene-id)]
      (swap! table dissoc k)
      (unload-entry! gl-id fbo))))

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

;; --- render targets ----------------------------------------------------------

(defn- make-target
  "The GL objects of a `w` x `h` target: a framebuffer, an RGBA8 colour texture
  (linear, clamped, no mipmaps, transparent black from the zeroed upload buffer)
  and, unless `depth?` is false, a depth renderbuffer. Answers the table entry, or throws and frees what it
  made when the framebuffer is incomplete.

  rlLoadFramebuffer, rlFramebufferAttach and rlFramebufferComplete each leave
  framebuffer 0 bound (rlgl.h, 6.0), which on iOS is not the screen. So the
  batch is flushed before the first of them, while the screen is still bound,
  and SDL's framebuffer is bound again on the way out, whichever way it goes."
  [scene-id key w h depth?]
  (rl-draw-render-batch-active)
  (try
    (let [fbo (rl-load-framebuffer)
          buf (ffi/alloc (* w h 4))
          tex (try (rl-load-texture buf w h PIXELFORMAT-R8G8B8A8 1)
                   (finally (ffi/free buf)))
          depth (when depth? (rl-load-texture-depth w h 1))]
      (rl-texture-parameters tex RL-TEXTURE-WRAP-S (RL-WRAP :clamp))
      (rl-texture-parameters tex RL-TEXTURE-WRAP-T (RL-WRAP :clamp))
      (rl-texture-parameters tex RL-TEXTURE-MIN-FILTER (RL-FILTER :linear))
      (rl-texture-parameters tex RL-TEXTURE-MAG-FILTER (RL-FILTER :linear))
      (rl-framebuffer-attach fbo tex RL-ATTACHMENT-COLOR-CHANNEL0 RL-ATTACHMENT-TEXTURE2D 0)
      (when depth
        (rl-framebuffer-attach fbo depth RL-ATTACHMENT-DEPTH RL-ATTACHMENT-RENDERBUFFER 0))
      (when (zero? (bit-and (rl-framebuffer-complete fbo) 0xff))
        (unload-entry! tex fbo)
        (throw (ex-info (str "texture " (pr-str [scene-id key]) ": framebuffer incomplete for "
                             w "x" h)
                        {:scene scene-id
                         :key key
                         :w w
                         :h h})))
      {:gl-id tex
       :fbo fbo
       :version nil
       :w w
       :h h
       :depth? (boolean depth?)})
    (finally (bind-screen!))))

(defn target!
  "The render target for `key` in scene `scene-id`, made on first use, as
  `{:fbo :texture :w :h}`. `spec` is `{:w :h :depth?}`; `:depth?` defaults to
  true, and false makes a target with no depth renderbuffer (rlgl runs 2D with
  the depth test off, so a pass that draws only flat shapes loses nothing, and
  saves the depth buffer, 2 to 4 bytes a texel depending on the depth format
  the driver offers). The same size and depth answer the same target, a new
  size or depth frees the old pair and makes a new one, and `enter!` frees it
  with the scene's other textures. Ask for it every frame and don't keep the
  map: a new size or `enter!` frees the framebuffer it names. Use the scene's
  own registry id, because a target filed under any other id is freed and made
  again every frame. A new size starts empty. Throws an ex-info with :scene,
  :key, :w and :h when the driver calls the framebuffer incomplete, and caches
  nothing.

  The colour texture starts transparent black. GL stores it bottom-up, so draw
  it back with `quad!` and `:v0 1.0 :v1 0.0`."
  [scene-id key {:keys [w h depth?]
                 :or {depth? true}}]
  (let [k [scene-id key]
        have (get @table k)]
    (when (and have (not (:fbo have)))
      (throw (ex-info (str "texture " (pr-str k) ": the key holds a plain texture")
                      {:scene scene-id
                       :key key})))
    (let [entry (if (and have (= w (:w have)) (= h (:h have))
                         (= (boolean depth?) (:depth? have)))
                  have
                  (do
                    (when have
                      (swap! table dissoc k)
                      (unload-entry! (:gl-id have) (:fbo have)))
                    (let [made (make-target scene-id key w h depth?)]
                      (swap! table assoc k made)
                      made)))]
      {:fbo (:fbo entry)
       :texture (:gl-id entry)
       :w (:w entry)
       :h (:h entry)})))

(defn- restore-screen-view!
  "The screen's viewport and projection, as EndTextureMode leaves them. This host
  opens the window at the drawable's pixel size, so raylib runs at scale 1 and
  GetScreenWidth is the render width, with no HiDPI modelview scale to put
  back. Leaves the matrix mode on MODELVIEW; `with-target!` puts the gallery's
  `transform` matrix back after this."
  []
  (let [sw (host/get-screen-width)
        sh (host/get-screen-height)]
    (rl-viewport 0 0 sw sh)
    (rl-set-framebuffer-width sw)
    (rl-set-framebuffer-height sh)
    (rl-matrix-mode RL-PROJECTION)
    (rl-load-identity)
    (rl-ortho 0.0 (double sw) (double sh) 0.0 0.0 1.0)
    (rl-matrix-mode RL-MODELVIEW)))

(defn with-target!
  "Run `(f)` with drawing redirected into render target `rt` (from `target!`)
  and answer its result. `safe` is the `{:x :y :width :height}` scissor the
  gallery has up around the scene.

  The batch is flushed on the way in and on the way out, because rlgl defers
  geometry and would otherwise draw it into whichever framebuffer is bound
  later. Inside, the scissor is down and the matrix is pushed and reset, so `f`
  draws in the target's own pixels from (0, 0), whatever translation the gallery
  has put on the scene. On every path, throws included, it then binds the
  screen, restores the screen viewport and projection, gives back the matrix
  state and puts the scissor back, so the rest of the scene's draw sees what it
  saw before. `f` must not call BeginScissorMode (on Apple the y flips against
  the screen height, not the target's) or nest `with-target!` (the inner exit
  binds the screen, not the outer target). It assumes modelview is identity on
  entry, as the gallery leaves it (raylib runs at scale 1 here). Never draw
  `rt`'s own texture inside its pass; reading and writing one texture at once
  is undefined in GL."
  [{:keys [fbo w h]} safe f]
  (rl-draw-render-batch-active)
  (host/end-scissor-mode)
  ;; The gallery's translate lives in rlgl's `transform` matrix, not modelview
  ;; (rlgl.h 6.0, rlPushMatrix 1236-1249: in MODELVIEW mode it saves `transform`
  ;; and points currentMatrix at it). Load identity straight after the push,
  ;; before any rlMatrixMode call, so `f` draws untranslated.
  (host/rl-push-matrix)
  (rl-load-identity)
  (try
    (rl-enable-framebuffer fbo)
    (rl-viewport 0 0 w h)
    (rl-set-framebuffer-width w)
    (rl-set-framebuffer-height h)
    (rl-matrix-mode RL-PROJECTION)
    (rl-load-identity)
    (rl-ortho 0.0 (double w) (double h) 0.0 0.0 1.0)
    (rl-matrix-mode RL-MODELVIEW)
    (rl-load-identity)
    (f)
    (finally
      (rl-draw-render-batch-active)
      (bind-screen!)
      (restore-screen-view!)
      ;; currentMatrix is modelview here, and rlPopMatrix writes the saved
      ;; matrix into currentMatrix (1251-1265). A push first points it back at
      ;; `transform` (and pushes a copy), the first pop discards that copy, the
      ;; second restores the gallery's `transform`. With an empty stack outside,
      ;; the last pop also resets the pointer and transformRequired, as before.
      (host/rl-push-matrix)
      (host/rl-pop-matrix)
      (host/rl-pop-matrix)
      (host/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe)))))

(defn with-blend-factors!
  "Run `(f)` blended with source factor `src`, destination factor `dst` and
  blend equation `equation` (GL enums; see `RL-SRC-ALPHA`, `RL-MIN`, `RL-MAX`),
  then put the default blend mode back, throw or not. Answers `f`'s result. It
  doesn't nest: the exit puts the default blend mode back, not an enclosing
  custom one."
  [src dst equation f]
  (rl-set-blend-factors src dst equation)
  (host/begin-blend-mode BLEND-CUSTOM)
  (try
    (f)
    (finally (host/end-blend-mode))))
