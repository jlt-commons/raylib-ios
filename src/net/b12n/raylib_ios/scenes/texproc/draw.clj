(ns net.b12n.raylib-ios.scenes.texproc.draw
  "The draw-scene! method for the `:texproc` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.texproc :as texproc]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private texproc-cache
  "The last `[screen dims]` for `:texproc`. Its text sizes need a measure, which
  depends only on the screen, so it is not measured again each frame."
  (atom nil))

(defn- texproc-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @texproc-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (texproc/dimensions m host-measure)]
        (reset! texproc-cache [screen dims])
        dims))))

(def ^:private texproc-specs
  {:checker (texproc/checker-spec)
   :gradient (texproc/gradient-spec)
   :rings (texproc/rings-spec)})

(def ^:private texproc-noise
  "The last `[version spec]` for the noise panel. Building the spec fills 16384
  values, so it happens once per tap, not once per frame.

  It is keyed on `:version` alone because the seed is a pure function of the
  version (`default-seed` reseeded `version` times, and a scene restarts at
  version 0), so equal versions mean equal noise, including after the scene is
  left and entered again. It also hands `id!` the identical spec object while
  the version holds, so the spec is not rebuilt. `id!` does not keep a versioned
  spec's texels between visits; only the three static panels' are kept."
  (atom nil))

(defn- texproc-noise-spec [state]
  (let [[version cached] @texproc-noise]
    (if (and cached (= version (:version state)))
      cached
      (let [sp (texproc/noise-spec state)]
        (reset! texproc-noise [(:version state) sp])
        sp))))

(defmethod draw-scene! :texproc [_ state {:keys [m]}]
  (clear-to! texproc/background-colour)
  (let [dims (texproc-dims m)
        outline (color texproc/outline-colour)]
    (doseq [k texproc/panel-keys
            :let [sp (if (= :noise k) (texproc-noise-spec state) (texproc-specs k))
                  id (texture/id! :texproc k sp)]]
      (texture/quad! id (texproc/quad dims k))
      (doseq [[x y w h] (texproc/outline-rects dims k)]
        (rl/draw-rectangle (int x) (int y) (int w) (int h) outline))
      (draw-caption! (get (:labels dims) k) texproc/label-colour))
    (let [[head sub hint] (:lines dims)]
      (draw-caption! head texproc/title-colour)
      (draw-caption! sub texproc/subtitle-colour)
      (draw-caption! hint texproc/hint-colour))))
