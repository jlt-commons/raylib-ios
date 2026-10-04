(ns net.b12n.raylib-ios.scenes.magnify.draw
  "The draw-scene! method for the `:magnify` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.magnify :as magnify]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private magnify-cache
  "The last `[screen dims]` for `:magnify`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- magnify-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @magnify-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (magnify/dimensions m host-measure)]
        (reset! magnify-cache [screen dims])
        dims))))

(defn- draw-magnify-world!
  "The backdrop as one quad, then the plan's rectangles and circles in order."
  [backdrop-id {:keys [backdrop items]}]
  (texture/quad! backdrop-id backdrop)
  (doseq [item items]
    (case (first item)
      :rect (let [[_ x y w h c] item]
              (rl/draw-rectangle (int x) (int y) (int w) (int h) (color c)))
      :circle (let [[_ x y r c] item]
                (rl/draw-circle (int x) (int y) (double r) (color c))))))

(defmethod draw-scene! :magnify [_ state {:keys [m safe]}]
  (let [dims (magnify-dims m)
        {:keys [field half lines]} dims
        [lx ly] (:lens state)
        backdrop (texture/perlin-texture! :magnify :backdrop magnify/backdrop-spec)
        rt (texture/target! :magnify :lens {:w magnify/lens
                                            :h magnify/lens})
        [ox oy] (magnify/lens-offset dims [lx ly])
        cx (+ (:x field) lx)
        cy (+ (:y field) ly)
        line (get lines (if (:moved? state) :moved :idle))]
    ;; The magnified pass first, into the lens target, with the markers only it shows.
    (texture/with-target! rt safe
      (fn []
        (clear-to! magnify/background-colour)
        (draw-magnify-world! backdrop (magnify/world-plan dims ox oy magnify/zoom true))))
    (clear-to! magnify/background-colour)
    (draw-magnify-world! backdrop (magnify/world-plan dims (:x field) (:y field) 1.0 false))
    (texture/triangles! (:texture rt) (magnify/disc-verts cx cy half magnify/segments)
                        (color [255 255 255 255]))
    (let [[rx ry inner outer start end n] (magnify/ring cx cy)]
      (rl/draw-ring rx ry inner outer start end n (color magnify/ring-colour)))
    (draw-caption! (assoc line :x (+ (:x field) (:x line)) :y (+ (:y field) (:y line)))
                   magnify/text-colour)))
