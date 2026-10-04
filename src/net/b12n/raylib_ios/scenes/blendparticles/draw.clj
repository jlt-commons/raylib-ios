(ns net.b12n.raylib-ios.scenes.blendparticles.draw
  "The draw-scene! method for the `:blendparticles` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.blendparticles :as blendparticles]))

(def ^:private blendparticles-dims-cache
  "The last `[screen dims]` for `:blendparticles`. Its text sizes need a
  measure, which depends only on the screen, so they are not measured again each
  frame."
  (atom nil))

(defn- blendparticles-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @blendparticles-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (blendparticles/dimensions m host-measure)]
        (reset! blendparticles-dims-cache [screen dims])
        dims))))

(defn- blendparticles-circle!
  "One particle: a circle of `radius` at `cx`, `cy`, tinted `r` `g` `b` `a`."
  [cx cy radius r g b a]
  (rl/draw-circle (int cx) (int cy) (double radius) (rl/rgba r g b a)))

(defmethod draw-scene! :blendparticles [_ state {:keys [m]}]
  (clear-to! blendparticles/background-colour)
  (let [dims (blendparticles-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        {:keys [hint label-size label-y]} dims
        [bx by bw bh] (:button dims)
        label (blendparticles/label state)]
    (blendparticles/call-blended!
     rl/begin-blend-mode rl/end-blend-mode (blendparticles/blend-mode state)
     (fn [] (blendparticles/emit-particles! blendparticles-circle! state dims)))
    (rl/draw-text (:s hint) (int (:x hint)) (int (:y hint)) (int (:size hint)) (pack blendparticles/hint-colour))
    (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack blendparticles/button-colour))
    (rl/draw-text label (blendparticles/label-x dims label host-measure) (int label-y) (int label-size)
                  (pack (blendparticles/label-colour state)))))
