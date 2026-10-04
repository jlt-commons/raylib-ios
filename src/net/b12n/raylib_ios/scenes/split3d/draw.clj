(ns net.b12n.raylib-ios.scenes.split3d.draw
  "The draw-scene! method for the `:split3d` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.split3d :as split3d]))

(def ^:private split3d-dims-cache
  "The last `[screen dims]` for `:split3d`. Its label sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- split3d-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @split3d-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (split3d/dimensions m host-measure)]
        (reset! split3d-dims-cache [screen dims])
        dims))))

;; The cache is two `[key list]` entries, one a half. A half's list is a pure
;; function of both players' places (each player is a cube in the other's view)
;; and of the half's rectangle, so that is the key. A frame where neither player
;; moved, which is most of them, reuses both lists and only draws them.
(def ^:private split3d-list-cache
  "The last `[key draw-list]` of each half of `:split3d`, as a vector of two."
  (atom [nil nil]))

(defn- split3d-list [state dims i]
  (let [k [(:z1 state) (:x2 state) (nth (:halves dims) i)]
        [cached-key cached] (nth @split3d-list-cache i)]
    (if (= k cached-key)
      cached
      (let [dl (split3d/scene-list state dims i)]
        (swap! split3d-list-cache assoc i [k dl])
        dl))))

(defmethod draw-scene! :split3d [_ state {:keys [m safe]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        dims (split3d-dims m)]
    (rl/clear-background (pack [0 0 0 255]))
    (doseq [i [0 1]
            :let [[hx hy hw hh] (nth (:halves dims) i)
                  {:keys [rect s x y size]} (nth (:banners dims) i)
                  [bx by bw bh] rect]]
      (draw-in-field! safe (nth (:halves dims) i)
                      (fn []
                        (rl/draw-rectangle (int hx) (int hy) (int hw) (int hh) (pack split3d/sky-colour))
                        (rl/draw-3d! (split3d-list state dims i))
                        (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack split3d/bar-colour))
                        (rl/draw-text s x y size (pack (nth split3d/label-colours i))))))
    (let [[dx dy dw dh] (:divider dims)]
      (rl/draw-rectangle (int dx) (int dy) (int dw) (int dh) (pack split3d/divider-colour)))))
