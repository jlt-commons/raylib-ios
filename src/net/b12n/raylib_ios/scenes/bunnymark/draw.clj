(ns net.b12n.raylib-ios.scenes.bunnymark.draw
  "The draw-scene! method for the `:bunnymark` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to!
                                                           draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.bunnymark :as bunnymark]))

(def ^:private bunnymark-dims-cache
  "The last `[screen dims]` for `:bunnymark`. Its text sizes need a measure,
  which depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- bunnymark-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @bunnymark-dims-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (bunnymark/dimensions m host-measure)]
        (reset! bunnymark-dims-cache [screen dims])
        dims))))

(defn- bunnymark-rect!
  "One bunny: a square of `side` at `x`, `y`, tinted `r` `g` `b`."
  [x y side r g b]
  (rl/draw-rectangle x y side side (rl/rgba r g b 255)))

(defmethod draw-scene! :bunnymark [_ {:keys [n]
                                      :as state} {:keys [m]}]
  (clear-to! bunnymark/background-colour)
  (let [dims (bunnymark-dims m)
        pack (fn [[r g b a]] (rl/rgba r g b a))
        [bar-x bar-y bar-w bar-h] (:bar dims)
        [bx by bw bh] (:button dims)
        [count-l fps-l hint-l] (:lines dims)
        text (fn [{:keys [s x y size]} colour]
               (rl/draw-text s (int x) (int y) (int size) (pack colour)))]
    (bunnymark/emit-bunnies! bunnymark-rect! state dims)
    (rl/draw-rectangle (int bar-x) (int bar-y) (int bar-w) (int bar-h) (pack bunnymark/bar-colour))
    (text (assoc count-l :s (bunnymark/count-line n)) bunnymark/text-colour)
    ;; Read every frame, which is the only way GetFPS gives a true number.
    (text (assoc fps-l :s (bunnymark/fps-line (rl/get-fps))) bunnymark/text-colour)
    (text hint-l bunnymark/hint-colour)
    (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (pack bunnymark/button-colour))
    (rl/draw-text (:label dims) (:label-x dims) (:label-y dims) (:label-size dims)
                  (pack bunnymark/button-label-colour))))
