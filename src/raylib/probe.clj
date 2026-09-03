(ns raylib.probe
  "The measuring apparatus, kept out of the owner loop.

  Everything here is off by default and exists to answer questions about what
  the GL state actually is on a device, rather than what reading the source
  suggests it should be. That distinction earned its own namespace: this
  project spent a day believing raylib rendered into framebuffer 0 on iOS,
  which is what the code says and not what the driver does, and the thing that
  settled it was flipping `bind-drawable?` off and asking.

  `raylib.host` requires this, reads the flags in its loop and fills in the
  atoms. The dependency runs host -> probe and never back, which is why the
  handful of FFI declarations below are duplicated from the host rather than
  shared: a defcfn is a Chez foreign-procedure resolved by symbol name at first
  call, so two declarations of glBindFramebuffer are the same function, and
  paying that to keep the namespaces acyclic is a good trade.

  Nothing here runs at load."
  (:require [jolt.ffi :as ffi]))

;; --- the GL surface these probes need ---------------------------------------
;; rlGetActiveFramebuffer works on GLES2 only as of raysan5/raylib#6115, which
;; is in 6.1-dev and not in 6.0.
(ffi/defcfn rl-get-active-framebuffer  "rlGetActiveFramebuffer"   [] :uint)
(ffi/defcfn gl-check-framebuffer-status "glCheckFramebufferStatus" [:uint] :uint)
(ffi/defcfn gl-get-integerv             "glGetIntegerv"            [:uint :pointer] :void)
(ffi/defcfn gl-bind-framebuffer         "glBindFramebuffer"        [:uint :uint] :void)

(def GL-FRAMEBUFFER 0x8D40)
(def GL-FRAMEBUFFER-BINDING 0x8CA6)
(def GL-RENDERBUFFER-BINDING 0x8CA7)
;; Anything other than this means the bound framebuffer is not a usable target.
(def GL-FRAMEBUFFER-COMPLETE 0x8CD5)

(defn gl-int
  "One integer of GL state."
  [pname]
  (ffi/with-alloc [p 4] (gl-get-integerv pname p) (ffi/read p :int 0)))

;; --- what the host records ---------------------------------------------------

;; SDL's drawable FBO and colour renderbuffer ids, read once at startup. nil
;; before the window exists. defonce takes no docstring, unlike def.
(defonce wm-info (atom nil))

;; What raylib leaves bound after InitWindow, before the host binds anything.
;; Measured because "what does raylib on SDL actually render into on iOS" is a
;; question source-reading answered wrongly. 6.0 leaves 0 here and 6.1-dev
;; leaves SDL's drawable; both read the drawable by swap time.
(defonce initial-framebuffer (atom nil))

;; What is bound the instant before SDL_GL_SwapWindow. SDL's README-ios wants
;; both the drawable framebuffer and the colour renderbuffer bound at that
;; moment, and they are separate bindings: one can be right while the other is
;; not. Off by default because these are synchronous driver queries in the hot
;; loop for a value nothing reads unless someone is debugging.
(defonce record-pre-swap? (atom false))
(defonce pre-swap (atom nil))

;; --- the toggles -------------------------------------------------------------

;; Whether the loop binds SDL's drawable each frame. Default on, and settable
;; from launch with RAYLIB_BIND_DRAWABLE=0, which deploy.sh forwards through
;; devicectl. A runtime flip is NOT enough to reproduce stock raylib, because a
;; framebuffer binding is sticky: once frame 0 has bound the drawable, turning
;; the per-frame bind off leaves it bound. Only a process that never binds
;; shows what raylib alone does, which is why this reads the environment.
(defonce bind-drawable?
  (atom (not= "0" (System/getenv "RAYLIB_BIND_DRAWABLE"))))

;; GetFPS is a per-frame sampler: each call advances a 30-slot ring by one and
;; returns 1/sum-of-ring. With this on, the loop calls it every frame and parks
;; the answer in last-fps; with it off nothing calls it, so an nREPL can call it
;; at whatever cadence it likes and watch the ring fill. Both halves of that
;; experiment come from one binary.
(defonce fps-every-frame? (atom false))
(defonce last-fps (atom nil))

;; --- the report --------------------------------------------------------------
(defn framebuffer-report
  "What GL makes of framebuffer 0 versus SDL's drawable, on this device.

  MUST run on the main thread: hand it to raylib.host/on-next-frame!. Leaves
  the drawable bound, so calling it does not disturb rendering.

  On an iPhone this answers 33305 (GL_FRAMEBUFFER_UNDEFINED, \"the default
  framebuffer exists but its surface does not\") for 0, and 36053
  (GL_FRAMEBUFFER_COMPLETE) for SDL's drawable, which is the driver saying in
  its own words that iOS has no default framebuffer."
  []
  (let [{:keys [framebuffer]} @wm-info
        check (fn [id]
                (gl-bind-framebuffer GL-FRAMEBUFFER id)
                {:bound  (rl-get-active-framebuffer)
                 :status (gl-check-framebuffer-status GL-FRAMEBUFFER)})
        zero  (check 0)
        sdl   (check framebuffer)]
    {:sdl-drawable-id framebuffer
     :framebuffer-0   zero
     :sdl-drawable    sdl
     :complete        GL-FRAMEBUFFER-COMPLETE}))
