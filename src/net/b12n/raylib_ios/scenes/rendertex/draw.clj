(ns net.b12n.raylib-ios.scenes.rendertex.draw
  "The draw-scene! method for the `:rendertex` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure
                                                           outline!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.rendertex :as rendertex]
            [net.b12n.raylib-ios.texture :as texture]))

;; --- render targets ------------------------------------------------------------
;; A scene here draws into `net.b12n.raylib-ios.texture/target!`'s off-screen framebuffer with
;; `with-target!`, which draws untranslated in the target's own pixels and gives
;; the screen, the scissor and the gallery's translate back afterwards, then
;; draws the target with `quad!` and `:v0 1.0 :v1 0.0` (GL stores it bottom-up).

(def ^:private rendertex-cache
  "The last `[screen dims]` for `:rendertex`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- rendertex-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @rendertex-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (rendertex/dimensions m host-measure)]
        (reset! rendertex-cache [screen dims])
        dims))))

(defmethod draw-scene! :rendertex [_ state {:keys [m safe]}]
  (let [dims (rendertex-dims m)
        rt (texture/target! :rendertex :rt (rendertex/target-spec))]
    ;; The scene is drawn once, before anything of the screen's own.
    (texture/with-target! rt safe
      (fn []
        (clear-to! rendertex/target-clear-colour)
        (doseq [[x y c] (rendertex/balls (:t state))]
          (rl/draw-circle x y (double rendertex/ball-radius) (color c)))
        (rl/draw-text rendertex/target-text 10 10 20 (color rendertex/target-text-colour))
        (outline! 0 0 rendertex/rt-w rendertex/rt-h rendertex/target-outline-colour)))
    (clear-to! rendertex/background-colour)
    (draw-caption! (:title dims) rendertex/title-colour)
    (doseq [{:keys [x y width height tint label]} (:copies dims)]
      (texture/quad! (:texture rt) (cond-> {:x x
                                            :y y
                                            :width width
                                            :height height
                                            :v0 1.0
                                            :v1 0.0}
                                     tint (assoc :tint (color tint))))
      (draw-caption! label rendertex/label-colour))))
