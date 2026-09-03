(ns raylib.host
  "The iOS owner loop for raylib (RAY-018's shape). run! hands the app to
  SDL_UIKitRunApp; a :collect-safe SDL_main then owns InitWindow and the
  while loop — per frame: BeginDrawing, bind SDL's drawable FBO (iOS has no
  framebuffer 0), (frame state) -> state', bind the colour renderbuffer,
  EndDrawing. The screen is sized in PIXELS (UIScreen scale × points), since
  raylib's SDL platform does not do HighDPI itself."
  (:refer-clojure :exclude [run!])          ; run! is the entry point here, as in raylib-jlt
  (:require [jolt.ffi :as ffi]
            [raylib.objc :as u]))

;; --- SDL ---------------------------------------------------------------------
(ffi/defcfn sdl-uikit-run-app            "SDL_UIKitRunApp"           [:int :pointer :pointer] :int :blocking)
(ffi/defcfn sdl-init                     "SDL_Init"                  [:uint32] :int)
(ffi/defcfn sdl-get-current-display-mode "SDL_GetCurrentDisplayMode" [:int :pointer] :int)
(ffi/defcfn sdl-get-error                "SDL_GetError"              [] :string)
(ffi/defcfn sdl-gl-get-current-window    "SDL_GL_GetCurrentWindow"   [] :pointer)
(ffi/defcfn sdl-get-window-wm-info       "SDL_GetWindowWMInfo"       [:pointer :pointer] :int)
(ffi/defcfn sdl-gl-get-drawable-size     "SDL_GL_GetDrawableSize"    [:pointer :pointer :pointer] :void)
(ffi/defcfn gl-bind-framebuffer          "glBindFramebuffer"         [:uint :uint] :void)
(ffi/defcfn gl-bind-renderbuffer         "glBindRenderbuffer"        [:uint :uint] :void)
(ffi/defcfn msg-0d                       "objc_msgSend"              [:pointer :pointer] :double)   ; [UIScreen scale]
(ffi/defcfn sdl-set-hint                 "SDL_SetHint"               [:string :string] :uint8)
(def ^:private SDL-INIT-VIDEO 0x20)
(def ^:private GL-FRAMEBUFFER 0x8D40)
(def ^:private GL-RENDERBUFFER 0x8D41)

;; --- raylib: window, frame, drawing (the raylib-jlt subset, packed :uint colours) ---
(ffi/defcfn set-config-flags    "SetConfigFlags"    [:uint] :void)
(ffi/defcfn init-window         "InitWindow"        [:int :int :string] :void)
(ffi/defcfn window-should-close "WindowShouldClose" [] :uint8)
(ffi/defcfn close-window        "CloseWindow"       [] :void)
(ffi/defcfn set-target-fps      "SetTargetFPS"      [:int] :void)
(ffi/defcfn begin-drawing       "BeginDrawing"      [] :void)
(ffi/defcfn end-drawing         "EndDrawing"        [] :void)
(ffi/defcfn clear-background    "ClearBackground"   [:uint] :void)
(ffi/defcfn draw-text           "DrawText"          [:string :int :int :int :uint] :void)
(ffi/defcfn draw-circle         "DrawCircle"        [:int :int :float :uint] :void)
(ffi/defcfn draw-circle-lines   "DrawCircleLines"   [:int :int :float :uint] :void)
(ffi/defcfn draw-rectangle      "DrawRectangle"     [:int :int :int :int :uint] :void)
(ffi/defcfn draw-line           "DrawLine"          [:int :int :int :int :uint] :void)
(ffi/defcfn get-screen-width    "GetScreenWidth"    [] :int)
(ffi/defcfn get-screen-height   "GetScreenHeight"   [] :int)
(ffi/defcfn get-frame-time      "GetFrameTime"      [] :float)
(ffi/defcfn get-fps             "GetFPS"            [] :int)
(ffi/defcfn measure-text        "MeasureText"       [:string :int] :int)
(def FLAG-WINDOW-HIGHDPI 0x2000)

;; There is deliberately no safe-area-top here. SDL_GetDisplayUsableBounds
;; looks like the right call and cannot answer on iOS: it returns
;; uiscreen.bounds, which knows nothing of safe areas, so its y is always 0.
;; Measured, on a phone whose real inset is 62 pt: the host printed 0 pt from
;; SDL while the gallery got 62.0 pt = 186 px by asking UIKit on its first
;; frame. So :inset-top is handed out as 0 and a scene that needs the inset
;; asks safe-area-insets itself, from inside the loop, because the insets are
;; only real once the window has been laid out.

;; UIEdgeInsets {double top, left, bottom, right}: a four-double HFA, returned in
;; registers — the by-value return milestone 0 proved through libffi.
(ffi/defcfn msg-0-insets "objc_msgSend" [:pointer :pointer]
  [:by-value [:struct [[:top :double] [:left :double] [:bottom :double] [:right :double]]]])
(def ^:private insets-l (ffi/layout [:struct [[:top :double] [:left :double] [:bottom :double] [:right :double]]]))

(defn safe-area-insets
  "[[UIApplication sharedApplication].windows firstObject].safeAreaInsets, in
  points: {:top :left :bottom :right}. Ask from inside the loop — insets are
  only real once the window has been laid out, i.e. after the first frame."
  []
  (let [win (u/objc-msg-send-0
              (u/objc-msg-send-0 (u/objc-msg-send-0 (u/cls "UIApplication") (u/sel "sharedApplication")) (u/sel "windows"))
              (u/sel "firstObject"))]
    (ffi/with-layout [i insets-l]
      (msg-0-insets i win (u/sel "safeAreaInsets"))
      (into {} (for [f [:top :left :bottom :right]] [f (ffi/read-field i insets-l f)])))))

;; --- raylib: touch (RAY-010's scalar surface, plus the by-value Vector2) ----
(ffi/defcfn get-touch-point-count "GetTouchPointCount" [] :int)
(ffi/defcfn get-touch-point-id    "GetTouchPointId"    [:int] :int)
(ffi/defcfn get-touch-x           "GetTouchX"          [] :int)
(ffi/defcfn get-touch-y           "GetTouchY"          [] :int)
(ffi/defcfn get-touch-position    "GetTouchPosition"   [:int] [:by-value [:struct [[:x :float] [:y :float]]]])
(def ^:private vec2-l (ffi/layout [:struct [[:x :float] [:y :float]]]))
(defn touch-position
  "[x y] of touch point `i`, through the by-value Vector2 return."
  [i]
  (ffi/with-layout [v vec2-l]
    (get-touch-position v i)
    [(ffi/read-field v vec2-l :x) (ffi/read-field v vec2-l :y)]))

(defn rgba [r g b a] (bit-or r (bit-shift-left g 8) (bit-shift-left b 16) (bit-shift-left a 24)))
(def RAYWHITE  (rgba 245 245 245 255))
(def LIGHTGRAY (rgba 200 200 200 255))
(def DARKGRAY  (rgba 80 80 80 255))
(def MAROON    (rgba 190 33 55 255))
(def SKYBLUE   (rgba 102 191 255 255))

;; --- the host ----------------------------------------------------------------
(defn- display-points
  "[w h] of display 0 in points, from SDL, before any window exists."
  []
  (when (neg? (sdl-init SDL-INIT-VIDEO)) (println "host: SDL_Init failed:" (sdl-get-error)))
  (ffi/with-alloc [m 24]                    ; SDL_DisplayMode {u32 format; int w, h, refresh_rate; void *driverdata}
    (if (zero? (sdl-get-current-display-mode 0 m))
      [(ffi/read m :int 4) (ffi/read m :int 8)]
      (do (println "host: no display mode:" (sdl-get-error)) [390 844]))))

(defn- screen-scale [] (msg-0d (u/objc-msg-send-0 (u/cls "UIScreen") (u/sel "mainScreen")) (u/sel "scale")))

(def ^:private sdl-version-l (ffi/layout [:struct [[:major :uint8] [:minor :uint8] [:patch :uint8]]]))

(defn- view-framebuffer
  "SDL's drawable FBO and colour renderbuffer for the current window, via
  SDL_SysWMinfo (version @0, subsystem @4, uikit.framebuffer @16, colorbuffer @20).
  Only the version is ours to write — SDL fills the rest — and it goes through a
  layout, since jolt 0.8.0 swapped ffi/write's value and offset."
  []
  (ffi/with-alloc [info 128]
    (ffi/write-field info sdl-version-l :major 2)
    (ffi/write-field info sdl-version-l :minor 32)
    (ffi/write-field info sdl-version-l :patch 10)                 ; SDL 2.32.10
    (if (pos? (sdl-get-window-wm-info (sdl-gl-get-current-window) info))
      {:framebuffer (ffi/read info :uint32 16) :colorbuffer (ffi/read info :uint32 20)}
      (do (println "host: SDL_GetWindowWMInfo failed:" (sdl-get-error)) {}))))

(defonce ^:private app (atom nil))        ; {:title :init :frame :fps} — the callable is global, so this is how it finds the scene

;; --- the live seam (raylib.live, an nREPL) -----------------------------------
;; An nREPL eval runs on jolt.nrepl's accept thread, and raylib and SDL are
;; main-thread-affine: this process is parked inside SDL_UIKitRunApp, so calling
;; DrawCircle or InitWindow from an eval is a crash waiting for a race rather
;; than a working REPL. And the scene's state is threaded through the loop
;; below, so there is nothing global for an editor to look at either.
;;
;; Two small things fix both. `state` is refreshed every frame, so an eval can
;; read what the scene currently holds. `on-next-frame!` queues a thunk that the
;; loop runs on the main thread, between BeginDrawing and the scene's own frame
;; fn, which is the only safe place to touch raylib from outside.
;;
;; The drain is swap-vals! rather than deref-then-reset!: a thunk posted between
;; the read and the clear would otherwise be captured by neither, and vanish
;; with no error and no log line.

;; raylib's own reading of the bound draw framebuffer. Works on GLES2 only as of
;; raysan5/raylib#6115 (in 6.1-dev, not in 6.0), which aliased
;; GL_DRAW_FRAMEBUFFER_BINDING for ES2.
(ffi/defcfn rl-get-active-framebuffer "rlGetActiveFramebuffer" [] :uint)
(ffi/defcfn gl-check-framebuffer-status "glCheckFramebufferStatus" [:uint] :uint)
(ffi/defcfn gl-get-integerv "glGetIntegerv" [:uint :pointer] :void)

(def ^:private GL-FRAMEBUFFER-BINDING 0x8CA6)
(def ^:private GL-RENDERBUFFER-BINDING 0x8CA7)

(defn- gl-int [pname]
  (ffi/with-alloc [p 4] (gl-get-integerv pname p) (ffi/read p :int 0)))

;; What is bound at the instant SDL_GL_SwapWindow is about to be called. SDL's
;; README-ios requires BOTH the drawable framebuffer and the colour renderbuffer
;; to be bound at that moment, and they are separate bindings: the framebuffer
;; can be right while the renderbuffer is not.
(defonce pre-swap (atom nil))

;; GetFPS is a stateful sampler: each call advances a 30-slot ring by one and
;; returns 1/sum-of-ring, so it is a frame rate only when called every frame.
;; These two reproduce both halves on one binary. With fps-every-frame? on, the
;; loop calls it once per frame and parks the answer in last-fps; with it off,
;; nothing calls it at all and an nREPL can call it at whatever cadence it likes.
(defonce fps-every-frame? (atom false))
(defonce last-fps (atom nil))

;; Whether to record pre-swap every frame. Off by default: it is two
;; glGetIntegerv calls, which are synchronous driver queries, in the hot loop
;; for a value nothing reads unless someone is debugging the drawable binding.
(defonce record-pre-swap? (atom false))

;; GL_FRAMEBUFFER_COMPLETE. Anything else means the currently bound framebuffer
;; is not a usable render target.
(def GL-FRAMEBUFFER-COMPLETE 0x8CD5)


;; SDL's drawable FBO and colour renderbuffer ids, read once at startup. nil
;; before the window exists. defonce takes no docstring, unlike def.
(defonce wm-info (atom nil))

;; Whether the loop binds SDL's drawable framebuffer each frame. True is correct
;; on iOS, which has no default framebuffer. Flipping it to false at runtime
;; reproduces raylib's own behaviour, which binds neither: the screen goes black
;; while everything else carries on reporting success. A toggle rather than a
;; build flag, so the bug can be demonstrated live and both halves of the A/B
;; come from one binary.
;; Default true, but overridable from launch via RAYLIB_BIND_DRAWABLE=0 (which
;; devicectl forwards as DEVICECTL_CHILD_RAYLIB_BIND_DRAWABLE). Flipping it at
;; runtime is not enough to reproduce raylib's own behaviour, because a
;; framebuffer binding is sticky: once frame 0 has bound the drawable, turning
;; the per-frame bind off leaves it bound. Only a process that never binds it
;; shows what raylib alone does.
(defonce bind-drawable?
  (atom (not= "0" (System/getenv "RAYLIB_BIND_DRAWABLE"))))

;; What raylib leaves bound after InitWindow, before this host binds anything.
;; Captured once, on the first frame, so the question "what does raylib on SDL
;; actually render into on iOS" has a measured answer rather than an inferred
;; one.
(defonce initial-framebuffer (atom nil))

(defn framebuffer-report
  "Ask GL what it thinks of framebuffer 0 versus SDL's drawable, on this device.
  MUST run on the main thread: pass it to on-next-frame!. Leaves the drawable
  bound, so calling it does not break rendering."
  []
  (let [{:keys [framebuffer]} @wm-info
        probe (fn [id]
                (gl-bind-framebuffer GL-FRAMEBUFFER id)
                {:bound  (rl-get-active-framebuffer)
                 :status (gl-check-framebuffer-status GL-FRAMEBUFFER)})
        zero  (probe 0)
        sdl   (probe framebuffer)]
    {:sdl-drawable-id framebuffer
     :framebuffer-0   zero
     :sdl-drawable    sdl
     :complete        GL-FRAMEBUFFER-COMPLETE}))

(defonce ^:private current-state (atom nil))
(defonce ^:private pending (atom []))

(defn state
  "Whatever the running scene's frame fn returned last. nil before the first
  frame. Read-only: reset!ing this does not affect the loop, which threads its
  own state through recur."
  []
  @current-state)

(defn on-next-frame!
  "Queue zero-arg `f` to run on the main thread at the top of the next frame.
  The only safe way to call raylib or SDL from an nREPL eval. Exceptions are
  printed rather than thrown, so a bad thunk cannot take the loop down."
  [f]
  (swap! pending conj f)
  nil)

(defn- drain-pending! []
  (let [[queued _] (swap-vals! pending (constantly []))]
    (doseq [f queued]
      (try (f)
           (catch :default e (println "host: queued work failed:" (ex-message e)))))))

(defn- sdl-main
  "SDL_main: the main thread, inside UIApplicationMain, event pump on. Sizes
  the screen in pixels, then owns the loop for the life of the app."
  [_argc _argv]
  (let [{:keys [title init frame fps] :or {fps 60}} @app
        [pw ph] (display-points)
        k       (screen-scale)
        w       (int (* pw k))
        h       (int (* ph k))]
    (set-config-flags FLAG-WINDOW-HIGHDPI)       ; SDL_WINDOW_ALLOW_HIGHDPI: a full-resolution drawable
    (sdl-set-hint "SDL_FRAMEBUFFER_ACCELERATION" "metal")   ; the software rasteriser's surface: present through Metal, never GLES
    (init-window w h title)                      ; ...and a screen the same size, so raylib never scales
    (set-target-fps fps)
    (let [{:keys [framebuffer colorbuffer] :as wm} (view-framebuffer)]
      (reset! wm-info wm)
      (reset! initial-framebuffer
              {:bound  (rl-get-active-framebuffer)      ; before any bind of ours
               :status (gl-check-framebuffer-status GL-FRAMEBUFFER)})
      (ffi/with-alloc [dw 4]
        (ffi/with-alloc [dh 4]
          (sdl-gl-get-drawable-size (sdl-gl-get-current-window) dw dh)
          (println "host:" pw "x" ph "points × scale" k "→ screen" (get-screen-width) "x" (get-screen-height)
                   "drawable" (ffi/read dw :int 0) "x" (ffi/read dh :int 0) "fbo" framebuffer
                   "(safe area: ask UIKit from inside the loop, not SDL)")))
      ;; milestone 5's numbers: every 300 frames, the mean and worst frame time
      (loop [state (init {:width w :height h :scale k :inset-top 0}) n 0 sum 0.0 worst 0.0]
        (begin-drawing)
        (when (and framebuffer @bind-drawable?) (gl-bind-framebuffer GL-FRAMEBUFFER framebuffer))
        (drain-pending!)                        ; main thread, inside the frame
        (let [state' (frame state)]
          (reset! current-state state')
          (when (and colorbuffer @bind-drawable?) (gl-bind-renderbuffer GL-RENDERBUFFER colorbuffer))
          (when @fps-every-frame? (reset! last-fps (get-fps)))
          (when @record-pre-swap?
            (reset! pre-swap {:framebuffer (gl-int GL-FRAMEBUFFER-BINDING)
                              :renderbuffer (gl-int GL-RENDERBUFFER-BINDING)}))
          (end-drawing)
          (let [dt    (get-frame-time)
                n     (inc n)
                sum   (+ sum dt)
                worst (max worst dt)]
            ;; fps from this window's own frame times, never from GetFPS:
            ;; sum is 300 frame times in seconds, so 300/sum is exactly the
            ;; window's rate. GetFPS is a per-frame sampler and reading it from
            ;; a 300-frame summary returns nonsense; see the README.
            (when (zero? (mod n 300))
              (println (format "host: %d frames, mean %.2f ms, worst %.1f ms, %.1f fps"
                               n (* 1000.0 (/ sum 300)) (* 1000.0 worst) (/ 300.0 sum))))
            (if (pos? (window-should-close))
              (do (close-window) 0)
              (recur state' n (if (zero? (mod n 300)) 0.0 sum) (if (zero? (mod n 300)) 0.0 worst)))))))))

(defonce ^:private sdl-main-cb
  (ffi/foreign-callable sdl-main [:int :pointer] :int :collect-safe))

(defn run!
  "Hand `scene` to UIKit and never return.
  scene: {:title s, :init (fn [{:keys [width height scale]}] state), :frame (fn [state] state'), :fps n}"
  [scene]
  (reset! app scene)
  (println "host: entering SDL_UIKitRunApp")
  (ffi/with-c-string-array [argv 1] ["Hello"]
    (sdl-uikit-run-app 1 argv sdl-main-cb)))
