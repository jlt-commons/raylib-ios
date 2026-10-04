(ns net.b12n.raylib-ios.scenes.helitorus.draw
  "The draw-scene! method for the `:helitorus` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.helitorus :as helitorus]))

;; --- helitorus --------------------------------------------------------------
;; The pure scene owns the figure and `compute!`; the arrays it writes and the
;; timings belong here, because they exist only to be submitted and shown.

(def ^:private helitorus-bufs
  "The arrays `helitorus/compute!` writes and the draw reads, allocated once."
  (helitorus/make-buffers))

(def ^:private helitorus-hud
  "The HUD's counters. `:frames`, `:compute-ns` and `:draw-ns` sum what has been
  drawn since `:since` (a `System/nanoTime`), and every 0.4 s they are averaged
  into `:fps`, `:compute-ms` and `:draw-ms`, which are what the HUD shows. The
  draw times its own two halves with `System/nanoTime`, which jolt has."
  (atom {:frames 0
         :since (System/nanoTime)
         :compute-ns 0
         :draw-ns 0
         :fps 0.0
         :compute-ms 0.0
         :draw-ms 0.0}))

(def ^:private helitorus-dims-cache
  "The last `[screen dims]` for `:helitorus`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- helitorus-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @helitorus-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (helitorus/dimensions m host-measure)]
        (reset! helitorus-dims-cache [screen dims])
        dims))))

(defn- helitorus-colour!
  "The flat colour of the next quad, fully opaque."
  [r g b]
  (rl/rl-color-4ub r g b 255))

(defn- helitorus-surface!
  "Every ring, far to near. One rlBegin/rlEnd batch per ring: rlgl cannot flush
  inside an open batch, and the whole surface at once overflows its vertex
  buffer."
  [bufs nu]
  (let [^double/1 sx (:sx bufs)
        ^double/1 sy (:sy bufs)
        ^int/1 shade (:shade bufs)
        ^int/1 order (:order bufs)]
    (loop [oi 0]
      (when (< oi nu)
        (rl/rl-begin rl/RL-TRIANGLES)
        (helitorus/emit-ring! helitorus-colour! rl/rl-vertex-2f sx sy shade
                              helitorus/palette-r helitorus/palette-g helitorus/palette-b
                              (aget order oi) nu)
        (rl/rl-end)
        (recur (inc oi))))))

(defn- helitorus-hud-tick!
  "Add this frame's two timings to the HUD counters, and every 0.4 s average
  them into `:fps`, `:compute-ms` and `:draw-ms` and start again."
  [compute-ns draw-ns]
  (swap! helitorus-hud
         (fn [{:keys [frames since]
               :as h}]
           (let [now (System/nanoTime)
                 frames (inc frames)
                 h (-> h
                       (assoc :frames frames)
                       (update :compute-ns + compute-ns)
                       (update :draw-ns + draw-ns))
                 span (- now since)]
             (if (< span 400000000)
               h
               (assoc h
                      :frames 0
                      :since now
                      :compute-ns 0
                      :draw-ns 0
                      :fps (/ (* 1e9 frames) span)
                      :compute-ms (/ (:compute-ns h) (* 1e6 frames))
                      :draw-ms (/ (:draw-ns h) (* 1e6 frames))))))))

(defmethod draw-scene! :helitorus [_ state {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack helitorus/background-colour))
        dims (helitorus-dims m)
        bufs helitorus-bufs
        nu (:nu state)
        c0 (System/nanoTime)
        _ (helitorus/compute! bufs (helitorus/params state dims))
        c1 (System/nanoTime)]
    ;; The scene tests visibility itself and emits each kept quad in the
    ;; winding rlgl keeps (`helitorus/emit-ring!`). Culling stays on: rlgl only
    ;; queues these vertices and draws them at a later flush, so a toggle around
    ;; this call would not be in force when they are drawn.
    (helitorus-surface! bufs nu)
    (helitorus-hud-tick! (- c1 c0) (- (System/nanoTime) c1))
    (let [{:keys [fps compute-ms draw-ms]} @helitorus-hud
          [hud status hint] (:lines dims)
          text (fn [{:keys [s x y size]} colour]
                 (rl/draw-text s (int x) (int y) (int size) (pack colour)))]
      (text (assoc hud :s (helitorus/hud-line fps compute-ms draw-ms)) helitorus/hud-colour)
      (text (assoc status :s (helitorus/status-line state)) helitorus/hud-colour)
      (text hint helitorus/hint-colour))
    (doseq [{:keys [id rect label label-x label-y label-size]} (:buttons dims)
            :let [[bx by bw bh] rect]]
      (rl/draw-rectangle (int bx) (int by) (int bw) (int bh)
                         (pack (if (contains? (:held state) id)
                                 helitorus/button-held-colour
                                 helitorus/button-colour)))
      (rl/draw-text label label-x label-y label-size (pack helitorus/button-label-colour)))))
