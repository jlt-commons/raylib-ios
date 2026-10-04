(ns net.b12n.raylib-ios.scenes.spriteanim.draw
  "The draw-scene! method for the `:spriteanim` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.spriteanim :as spriteanim]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private spriteanim-cache
  "The last `[screen dims]` for `:spriteanim`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- spriteanim-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @spriteanim-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (spriteanim/dimensions m host-measure)]
        (reset! spriteanim-cache [screen dims])
        dims))))

(def ^:private spriteanim-spec
  "The strip's spec, built on first use. The grid is 69120 texels drawn through
  `net.b12n.raylib-ios.texel`, so it is made once and kept, never per frame."
  (delay (spriteanim/strip-spec)))

(defn- draw-outline! [rect t colour]
  (doseq [[x y w h] (spriteanim/outline-rects rect t)]
    (rl/draw-rectangle (int x) (int y) (max 1 (int w)) (max 1 (int h)) colour)))

(defmethod draw-scene! :spriteanim [_ state {:keys [m]}]
  (clear-to! spriteanim/background-colour)
  (let [dims (spriteanim-dims m)
        id (texture/id! :spriteanim :strip @spriteanim-spec)
        [note speed hint] (:lines dims)]
    (texture/quad! id (spriteanim/strip-quad dims))
    (draw-outline! (:strip dims) 2 (color spriteanim/strip-outline-colour))
    (draw-outline! (spriteanim/frame-box dims (:current state)) 2 (color spriteanim/frame-outline-colour))
    (draw-caption! note spriteanim/note-colour)
    (draw-caption! (assoc speed :s (spriteanim/speed-line (:speed state))) spriteanim/speed-colour)
    (let [fill (color spriteanim/box-fill-colour)
          line (color spriteanim/box-outline-colour)]
      (doseq [[i [x y w h]] (map-indexed vector (:cells dims))]
        (when (< i (:speed state))
          (rl/draw-rectangle (int x) (int y) (int w) (int h) fill))
        (draw-outline! [x y w h] 2 line)))
    (draw-caption! hint spriteanim/hint-colour)
    (doseq [k [:slower :faster]
            :let [[x y w h] (k dims)]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h) (color spriteanim/button-colour))
      (draw-caption! (get (:labels dims) k) spriteanim/button-label-colour))
    (texture/quad! id (spriteanim/quad state dims))))
