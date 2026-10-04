(ns net.b12n.raylib-ios.scenes.doom.draw
  "The draw-scene! method for the `:doom` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.doom :as doom]
            [net.b12n.raylib-ios.scenes.doom.hud :as doom-hud]))

(def ^:private doom-cache
  "The last `[screen dims static]` for `:doom`: the layout, and the minimap's panel
  and walls with their colours packed, which depend on the screen alone."
  (atom nil))

(defn- doom-layout [m]
  (let [screen (:screen m)
        [cached-screen dims static] @doom-cache]
    (if (= screen cached-screen)
      [dims static]
      (let [dims (doom/dimensions m host-measure)
            static (mapv (fn [[x y w h c]] [(int x) (int y) (int w) (int h) (color c)])
                         (doom-hud/minimap-static dims))]
        (reset! doom-cache [screen dims static])
        [dims static]))))

(defn- draw-doom-rect! [x y w h c]
  (rl/draw-rectangle (int x) (int y) (int w) (int h) c))

(defmethod draw-scene! :doom [_ state {:keys [m safe]}]
  (clear-to! doom/background-colour)
  (let [[dims static] (doom-layout m)
        n (doom/build! state dims)
        thick (max 1.0 (:k dims))
        [hx hy hw hh hc] (doom-hud/hud-bar dims)
        {:keys [cx cy r]} (:fire dims)
        shape (doom/stick-shape state dims)]
    (draw-in-field! safe (:viewport dims)
                    (fn []
                      (doom/draw-rects! n draw-doom-rect!)
                      (rl/draw-rectangle (int hx) (int hy) (int hw) (int hh) (color hc))
                      (doseq [line (doom-hud/hud-lines state dims)]
                        (draw-caption! line (:colour line)))
                      (doseq [[x1 y1 x2 y2] (doom-hud/crosshair dims)]
                        (rl/draw-line-ex x1 y1 x2 y2 thick (color doom-hud/crosshair-colour)))
                      (doseq [[x y w h c] static]
                        (rl/draw-rectangle x y w h c))
                      (doseq [item (doom-hud/minimap-dynamic state dims)]
                        (case (first item)
                          :circle (let [[_ x y rad c] item]
                                    (rl/draw-circle (int x) (int y) (double rad) (color c)))
                          :line (let [[_ x1 y1 x2 y2 c] item]
                                  (rl/draw-line-ex x1 y1 x2 y2 thick (color c)))))
                      (when-let [died (doom-hud/died state dims)]
                        (draw-caption! died (:colour died)))
                      (rl/draw-circle (int cx) (int cy) (double r) (color doom/fire-colour))
                      (draw-caption! (:fire-label dims) doom/fire-label-colour)
                      (when shape
                        (let [[sx sy] (:centre shape)
                              [kx ky] (:knob shape)]
                          (rl/draw-circle (int sx) (int sy) (double (:r shape)) (color doom/stick-ring-colour))
                          (rl/draw-circle (int kx) (int ky) (* 0.4 (:r shape)) (color doom/stick-knob-colour))))))
    (draw-caption! (:caption dims) doom/caption-colour)))
