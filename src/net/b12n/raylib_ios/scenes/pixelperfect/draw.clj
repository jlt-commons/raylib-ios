(ns net.b12n.raylib-ios.scenes.pixelperfect.draw
  "The draw-scene! method for the `:pixelperfect` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.pixelperfect :as pixelperfect]))

(def ^:private pixelperfect-dims-cache
  "The last `[screen dims]` for `:pixelperfect`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- pixelperfect-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @pixelperfect-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (pixelperfect/dimensions m host-measure)]
        (reset! pixelperfect-dims-cache [screen dims])
        dims))))

(defn- pixelperfect-rect!
  "One spinning rect as the virtual pixels it covers (`pixelperfect/runs`): one
  `draw-rectangle` a world pixel tall per row of cells, in world pixels, which
  the camera scales to the zoom."
  [{:keys [color]
    :as rect}]
  (let [[r g b a] color
        c (rl/rgba r g b a)]
    (doseq [[i j n] (pixelperfect/runs rect)]
      (rl/draw-rectangle (int i) (int j) (int n) 1 c))))

(defmethod draw-scene! :pixelperfect [_ state {:keys [m safe]}]
  (clear-to! pixelperfect/background-colour)
  (let [dims (pixelperfect-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        camera (pixelperfect/camera state dims)
        [clip-x clip-y clip-w clip-h] (pixelperfect/clip state dims)
        [world-x world-y] (:target camera)
        [line-a line-b line-c line-d] (:lines dims)
        text (fn [{:keys [s x y size]} colour]
               (rl/draw-text s (int x) (int y) (int size) (pack colour)))
        button (fn [rect label on?]
                 (let [[x y w h] rect]
                   (rl/draw-rectangle (int x) (int y) (int w) (int h)
                                      (pack (if on? pixelperfect/button-on-colour pixelperfect/button-colour)))
                   (text label pixelperfect/button-label-colour)))]
    ;; BeginScissorMode takes screen pixels, so the clip is moved by the safe
    ;; region's corner, and the safe region's own scissor is put back afterwards
    ;; because scissor does not nest. This is the render texture's edge.
    (let [x0 (Math/floor (+ (:x safe) clip-x))
          y0 (Math/floor (+ (:y safe) clip-y))]
      (rl/begin-scissor-mode (int x0) (int y0)
                             (int (- (Math/ceil (+ (:x safe) clip-x clip-w)) x0))
                             (int (- (Math/ceil (+ (:y safe) clip-y clip-h)) y0))))
    (try
      (rl/with-camera-2d
        camera
        (fn []
          (rl/draw-rectangle (int world-x) (int world-y) pixelperfect/virtual-w pixelperfect/virtual-h
                             (pack pixelperfect/world-colour))
          (doseq [r (pixelperfect/rects state)]
            (pixelperfect-rect! r))))
      (finally
        (rl/end-scissor-mode)
        (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))))
    (text line-a pixelperfect/screen-colour)
    (text line-b pixelperfect/world-text-colour)
    (text (assoc line-c :s (pixelperfect/status-line (:smooth? state) (:overscan? state)))
          pixelperfect/status-colour)
    ;; Read every frame, which is the only way GetFPS gives a true number.
    (text (assoc line-d :s (pixelperfect/fps-line (rl/get-fps))) pixelperfect/fps-colour)
    (button (:smooth-button dims) (:smooth-label dims) (:smooth? state))
    (button (:overscan-button dims) (:overscan-label dims) (:overscan? state))))
