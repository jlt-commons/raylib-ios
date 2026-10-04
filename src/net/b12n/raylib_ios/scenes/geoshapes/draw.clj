(ns net.b12n.raylib-ios.scenes.geoshapes.draw
  "The draw-scene! method for the `:geoshapes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-caption!
                                                           draw-in-field!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.geoshapes :as geoshapes]))

(def ^:private geoshapes-cache
  "The last `[screen dims draw-list]` for `:geoshapes`. The camera never moves
  and the scene reads nothing, so its draw list depends on the screen alone
  (`dims` is a function of the screen). Rebuilding 2000 items every frame cost
  several times the phone's budget, and drawing them is what is left."
  (atom nil))

(defn- geoshapes-frame [state m]
  (let [screen (:screen m)
        [cached-screen dims dl] @geoshapes-cache]
    (if (= screen cached-screen)
      [dims dl]
      (let [dims (geoshapes/dimensions m host-measure)
            dl (geoshapes/scene-list state dims)]
        (reset! geoshapes-cache [screen dims dl])
        [dims dl]))))

(defmethod draw-scene! :geoshapes [_ state {:keys [m safe]}]
  (clear-to! geoshapes/background-colour)
  (let [[dims dl] (geoshapes-frame state m)]
    (draw-in-field! safe (:viewport dims)
                    (fn [] (rl/draw-3d! dl)))
    (draw-caption! (:caption dims) geoshapes/caption-colour)))
