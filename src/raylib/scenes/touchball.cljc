(ns raylib.scenes.touchball
  "A ball that follows your finger and turns green while you hold it. Ported
  from raylib-jlt's `mouse`.

  The original draws the ball wherever the mouse is and colours it by whether
  the left button is down. A finger has no hover, so there is nothing to follow
  until it lands: the ball stays where it last was, and is blue, whenever no
  finger is on the glass. It moves only on `:press` and `:down`. The `:release`
  frame carries the last hardware position, which on a device is not the touch
  that just ended, so reading it would send the ball to a stale point.")

(def lime [0 158 47 255])
(def darkblue [0 82 172 255])

(def caption "touch and drag; the ball turns green while you hold it")

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        caption-size (max 20 (int (* 0.028 side)))]
    {:w w
     :h h
     ;; 40 on the original's 450-high window.
     :radius (* 0.09 side)
     :caption-size caption-size
     :caption-x (int (* 0.04 w))
     :caption-y (int (- h (* 0.08 h)))}))

(defn advance [state input]
  (let [phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        down? (boolean (and point (#{:press :down} phase)))]
    (if down?
      (assoc state :pos (mapv double point) :touching? true)
      (assoc state :touching? false))))

(defn colour
  "The ball's `[r g b a]`: raylib's LIME while held, DARKBLUE otherwise."
  [{:keys [touching?]}]
  (if touching? lime darkblue))

(defn- init [{:keys [metrics]}]
  (let [{:keys [w h]} (dimensions metrics)]
    [{:pos [(* 0.5 w) (* 0.5 h)]
      :touching? false}
     [[:scene/init :touchball]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :touchball]]])

(defn scene []
  {:id :touchball
   :title "Touch Ball"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
