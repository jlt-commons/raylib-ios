(ns raylib.host
  "The loop that owns thread 0, and the raylib surface this project calls.

  There is no host process here. jolt emits a whole executable and Chez owns
  main, so raylib and SDL2 are static archives in the same binary and every
  defcfn below resolves against the process image the first time it is called.

  Startup is one handoff. run! calls SDL_UIKitRunApp and never returns; SDL
  runs UIApplicationMain, and its delegate calls back into sdl-main on thread
  0. The loop then lives there for the life of the app.

  A blocking loop is normally wrong on iOS, because it starves the run loop
  that delivers touches and lifecycle events. It works here because EndDrawing
  reaches SDL_PollEvent, which at a zero timeout pumps, and on UIKit the pump
  is CFRunLoopRunInMode. The loop and the run loop take turns, once a frame.

  The window is created in PIXELS rather than points. raylib's SDL platform
  does no HighDPI translation of its own, so a window sized in points would
  render at a third of the resolution on this screen and every coordinate the
  scenes compute would be wrong by the same factor."
  (:refer-clojure :exclude [run!])          ; this namespace defines its own run!
  (:require [jolt.ffi :as ffi]
            [raylib.objc :as u]
            [raylib.probe :as probe]))

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

;; rlgl immediate mode, for filling polygons raylib's shapes API has no call
;; for. All four are scalar, so they need none of the by-value machinery.
(ffi/defcfn rl-begin            "rlBegin"           [:int] :void)
(ffi/defcfn rl-end              "rlEnd"             [] :void)
(ffi/defcfn rl-vertex-2f        "rlVertex2f"        [:float :float] :void)
(ffi/defcfn rl-color-4ub        "rlColor4ub"        [:uint8 :uint8 :uint8 :uint8] :void)

;; The matrix stack and the scissor rectangle, which together let the host put
;; a scene somewhere other than the whole screen without the scene knowing.
;; raylib's 2D shape calls go through rlgl immediate mode, so the current
;; MODELVIEW matrix applies to them.
(ffi/defcfn rl-push-matrix      "rlPushMatrix"      [] :void)
(ffi/defcfn rl-pop-matrix       "rlPopMatrix"       [] :void)
(ffi/defcfn rl-translatef       "rlTranslatef"      [:float :float :float] :void)
(ffi/defcfn begin-scissor-mode  "BeginScissorMode"  [:int :int :int :int] :void)
(ffi/defcfn end-scissor-mode    "EndScissorMode"    [] :void)
(def RL-TRIANGLES 0x0004)
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
  "The window's safe-area insets in points, as {:top :left :bottom :right}.

  Four message sends, because there is no shorter path to them: the shared
  application, its windows, the first of those, and its insets.

  Call this from inside the loop and not before. UIKit computes insets during
  layout, so a call made before the first frame gets zeros, and zeros are a
  plausible answer on a device that genuinely has no notch. Asking once and
  caching the first non-zero result is what the gallery does."
  []
  (let [win (u/objc-msg-send-0
              (u/objc-msg-send-0 (u/objc-msg-send-0 (u/cls "UIApplication") (u/sel "sharedApplication")) (u/sel "windows"))
              (u/sel "firstObject"))]
    (ffi/with-layout [i insets-l]
      (msg-0-insets i win (u/sel "safeAreaInsets"))
      (into {} (for [f [:top :left :bottom :right]] [f (ffi/read-field i insets-l f)])))))

(defn safe-area-pixels
  "The insets in PIXELS, which is what everything that draws works in. UIKit
  answers in points, and this screen is three of those to the pixel."
  [scale]
  (let [pts (safe-area-insets)]
    (into {} (for [[edge v] pts] [edge (int (* scale v))]))))

;; --- raylib: touch (RAY-010's scalar surface, plus the by-value Vector2) ----
(ffi/defcfn get-touch-point-count "GetTouchPointCount" [] :int)
(ffi/defcfn get-touch-point-id    "GetTouchPointId"    [:int] :int)
(ffi/defcfn get-touch-x           "GetTouchX"          [] :int)
(ffi/defcfn get-touch-y           "GetTouchY"          [] :int)
(ffi/defcfn get-touch-position    "GetTouchPosition"   [:int] [:by-value [:struct [[:x :float] [:y :float]]]])
(def ^:private vec2-l (ffi/layout [:struct [[:x :float] [:y :float]]]))
(defn touch-position
  "Touch point `i` as [x y].

  GetTouchPosition returns a Vector2 by value, two floats in registers, which
  is a different FFI path from every scalar call here and worth having one user
  of. GetTouchX and GetTouchY give the same numbers for point zero only."
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
  "The main display's size in points, before any window exists.

  Needed because the window has to be created at pixel size and the pixel size
  is this multiplied by the screen scale. The fallback is an iPhone-ish guess
  rather than a crash: a wrong window size draws something, and something is
  easier to diagnose from than a dead app."
  []
  (when (neg? (sdl-init SDL-INIT-VIDEO)) (println "host: SDL_Init failed:" (sdl-get-error)))
  (ffi/with-alloc [m 24]                    ; SDL_DisplayMode {u32 format; int w, h, refresh_rate; void *driverdata}
    (if (zero? (sdl-get-current-display-mode 0 m))
      [(ffi/read m :int 4) (ffi/read m :int 8)]
      (do (println "host: no display mode:" (sdl-get-error)) [390 844]))))

(defn- screen-scale [] (msg-0d (u/objc-msg-send-0 (u/cls "UIScreen") (u/sel "mainScreen")) (u/sel "scale")))

(def ^:private sdl-version-l (ffi/layout [:struct [[:major :uint8] [:minor :uint8] [:patch :uint8]]]))

(defn- view-framebuffer
  "SDL's drawable framebuffer and colour renderbuffer, as {:framebuffer :colorbuffer}.

  SDL_GetWindowWMInfo is an in-out call: the caller writes the SDL version it
  compiled against and SDL fills the rest, refusing outright if the version is
  one it does not recognise. The offsets are SDL_SysWMinfo's own layout on
  UIKit, version at 0, subsystem at 4, then the framebuffer and colorbuffer.

  The version goes through a layout rather than a raw write because jolt 0.8.0
  swapped ffi/write's value and offset arguments. Both are integers, so the two
  spellings cannot be told apart at runtime: the wrong one writes a byte to the
  wrong place and reports nothing."
  []
  (ffi/with-alloc [info 128]
    (ffi/write-field info sdl-version-l :major 2)
    (ffi/write-field info sdl-version-l :minor 32)
    (ffi/write-field info sdl-version-l :patch 10)                 ; SDL 2.32.10
    (if (pos? (sdl-get-window-wm-info (sdl-gl-get-current-window) info))
      {:framebuffer (ffi/read info :uint32 16) :colorbuffer (ffi/read info :uint32 20)}
      (do (println "host: SDL_GetWindowWMInfo failed:" (sdl-get-error)) {}))))

;; The scene, parked where the callback can reach it. SDL is handed a C
;; function pointer, which carries no closure, so the callback finds its scene
;; here rather than having been given one.
(defonce ^:private app (atom nil))

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

;; The measuring apparatus lives in raylib.probe: its flags are read below
;; and its atoms filled in, and it is off by default.

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

(def ^:private window-frames
  "Frames per timing report. Long enough that one slow frame does not dominate
  the mean, short enough to notice a scene degrading while you watch it."
  300)

(defn- fresh-window [] {:count 0 :seconds 0.0 :worst 0.0})

(defn- accumulate [{:keys [count seconds worst]} dt]
  {:count (inc count) :seconds (+ seconds dt) :worst (max worst dt)})

(defn- report-window!
  "Print the window's own numbers.

  The frame rate is computed here rather than read from GetFPS, and that is not
  a preference. GetFPS is a sampler: each call advances a 30-slot ring by one
  and returns the reciprocal of its sum, so it is a frame rate only if you call
  it every frame. Called once per window it returns a plausible number that is
  wrong by two orders of magnitude and decays slowly toward the truth. This
  project reported that as a raylib bug for an afternoon. `seconds` is the sum
  of this window's own frame times, so frames divided by it is exactly the rate
  over exactly that window, and it owes raylib nothing."
  [total {:keys [count seconds worst]}]
  (println (format "host: %d frames, mean %.2f ms, worst %.1f ms, %.1f fps"
                   total
                   (* 1000.0 (/ seconds count))
                   (* 1000.0 worst)
                   (/ (double count) seconds))))

(defn- sdl-main
  "What SDL calls back on thread 0, and where the app lives from then on.

  By the time this runs, UIApplicationMain is up and the run loop exists, which
  is why the loop at the end can block: EndDrawing gives the run loop a turn
  every frame. Everything before the loop is setup that must happen after
  UIKit is running and before any drawing: size the window in pixels, hint SDL
  toward Metal for its presentation surface, then hand raylib the window."
  [_argc _argv]
  (let [{:keys [title init frame fps] :or {fps 60}} @app
        [pw ph] (display-points)
        k       (screen-scale)
        w       (int (* pw k))
        h       (int (* ph k))]
    ;; A full-resolution drawable, and a window sized to match it, so raylib
    ;; never scales anything: one raylib pixel is one screen pixel.
    (set-config-flags FLAG-WINDOW-HIGHDPI)
    ;; Present SDL's own surface through Metal. Left to itself SDL may pick a
    ;; GLES path for it, which on this device is the slower of the two.
    (sdl-set-hint "SDL_FRAMEBUFFER_ACCELERATION" "metal")
    (init-window w h title)
    (set-target-fps fps)
    (let [{:keys [framebuffer colorbuffer] :as wm} (view-framebuffer)]
      (reset! probe/wm-info wm)
      (reset! probe/initial-framebuffer
              {:bound  (probe/rl-get-active-framebuffer)      ; before any bind of ours
               :status (probe/gl-check-framebuffer-status GL-FRAMEBUFFER)})
      (ffi/with-alloc [dw 4]
        (ffi/with-alloc [dh 4]
          (sdl-gl-get-drawable-size (sdl-gl-get-current-window) dw dh)
          (println "host:" pw "x" ph "points × scale" k "→ screen" (get-screen-width) "x" (get-screen-height)
                   "drawable" (ffi/read dw :int 0) "x" (ffi/read dh :int 0) "fbo" framebuffer
                   "(safe area: ask UIKit from inside the loop, not SDL)")))
      ;; milestone 5's numbers: every 300 frames, the mean and worst frame time
      (loop [state (init {:width w :height h :scale k :inset-top 0})
             frames 0
             window (fresh-window)]
        (begin-drawing)
        (when (and framebuffer @probe/bind-drawable?)
          (gl-bind-framebuffer GL-FRAMEBUFFER framebuffer))
        ;; queued work runs on the main thread and inside the frame, which is
        ;; the only place it is safe to touch raylib from outside the loop
        (drain-pending!)
        (let [state' (frame state)]
          (reset! current-state state')
          (when (and colorbuffer @probe/bind-drawable?)
            (gl-bind-renderbuffer GL-RENDERBUFFER colorbuffer))
          (probe/sample-frame!)
          (end-drawing)
          (let [frames  (inc frames)
                window' (accumulate window (get-frame-time))]
            (if (pos? (window-should-close))
              (do (close-window) 0)
              (if (= window-frames (:count window'))
                (do (report-window! frames window')
                    (recur state' frames (fresh-window)))
                (recur state' frames window')))))))))

(defonce ^:private sdl-main-cb
  (ffi/foreign-callable sdl-main [:int :pointer] :int :collect-safe))

(defn run!
  "Hand `scene` to UIKit. Does not return.

  SDL_UIKitRunApp calls UIApplicationMain, which owns the process from here.
  The scene is parked in an atom rather than closed over because the callback
  handed to SDL is a global C function pointer, and a C function pointer cannot
  carry a closure.

  scene is {:title, :init (fn [{:keys [width height scale inset-top]}] state),
  :frame (fn [state] state'), :fps}."
  [scene]
  (reset! app scene)
  (println "host: entering SDL_UIKitRunApp")
  (ffi/with-c-string-array [argv 1] ["Hello"]
    (sdl-uikit-run-app 1 argv sdl-main-cb)))
