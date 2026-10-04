(ns net.b12n.raylib-ios.scenes.spritestack.draw
  "The draw-scene! method for the `:spritestack` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.spritestack :as spritestack]))

(def ^:private spritestack-dims-cache
  "The last `[screen dims]` for `:spritestack`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- spritestack-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @spritestack-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (spritestack/dimensions m host-measure)]
        (reset! spritestack-dims-cache [screen dims])
        dims))))

(defn- spritestack-layer!
  "Turns the matrix to slice `_`'s centre and rotation, runs `f` and puts the
  matrix back. rlgl transforms each vertex as it is added, so the slice's rects
  and circles are drawn in its own pixels; a rotation keeps the winding, so
  nothing is culled."
  [_ x y rotation f]
  (rl/rl-push-matrix)
  (try
    (rl/rl-translatef (double x) (double y) 0.0)
    (rl/rl-rotatef (double rotation) 0.0 0.0 1.0)
    (f)
    (finally (rl/rl-pop-matrix))))

(defn- spritestack-rect! [x y w h [r g b a]]
  (rl/draw-rectangle x y w h (rl/rgba r g b a)))

(defn- spritestack-circle! [x y radius [r g b a]]
  (rl/draw-circle x y (double radius) (rl/rgba r g b a)))

(defmethod draw-scene! :spritestack [_ state {:keys [m]}]
  (clear-to! spritestack/background-colour)
  (let [dims (spritestack-dims m)
        [hint-l spacing-l speed-l note-l] (:lines dims)]
    (spritestack/emit-stack! spritestack-layer! spritestack-rect! spritestack-circle!
                             state dims)
    (draw-caption! hint-l spritestack/text-colour)
    (draw-caption! (assoc spacing-l :s (spritestack/spacing-line (:spacing state)))
                   spritestack/text-colour)
    (draw-caption! (assoc speed-l :s (spritestack/speed-line (:speed state)))
                   spritestack/text-colour)
    (draw-caption! note-l spritestack/note-colour)))
