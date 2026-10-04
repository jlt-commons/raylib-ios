(ns net.b12n.raylib-ios.scenes.toplights.draw
  "The draw-scene! method for the `:toplights` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [clear-to! color
                                                           draw-caption!
                                                           draw-scene!
                                                           host-measure
                                                           outline!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.toplights :as toplights]
            [net.b12n.raylib-ios.texture :as texture]))

(def ^:private toplights-cache
  "The last `[screen dims]` for `:toplights`. Its text sizes need a measure, which
  depends only on the screen, so they are not measured again each frame."
  (atom nil))

(defn- toplights-dims [m]
  (let [screen (:screen m)
        [cached-screen cached] @toplights-cache]
    (if (= screen cached-screen)
      cached
      (let [dims (toplights/dimensions m host-measure)]
        (reset! toplights-cache [screen dims])
        dims))))

(def ^:private toplights-ground (toplights/ground-spec))

(defn- draw-pass-item!
  "One item of a `toplights` pass, in the pass target's own pixels. A mask goes
  back full size with `:v0 1.0 :v1 0.0`, because a target is stored bottom-up."
  [item masks fw fh]
  (case (first item)
    :gradient (let [[_ x y r inner outer] item]
                (rl/draw-circle-gradient x y r (color inner) (color outer)))
    :quad (let [[_ corners c] item
                col (color c)]
            (doseq [[[ax ay] [bx by] [cx cy]] (toplights/quad-triangles corners)]
              (rl/draw-triangle ax ay bx by cx cy col)))
    :mask (texture/quad! (:texture (nth masks (second item))) {:x 0
                                                               :y 0
                                                               :width fw
                                                               :height fh
                                                               :v0 1.0
                                                               :v1 0.0})))

(defn- run-pass!
  "A `toplights` plan: its clears, and each blend with GL_SRC_ALPHA twice and the
  equation the plan names (GL_MIN or GL_MAX)."
  [plan masks fw fh]
  (doseq [[op a b] plan]
    (case op
      :clear (clear-to! a)
      :blend (texture/with-blend-factors! texture/RL-SRC-ALPHA texture/RL-SRC-ALPHA
               (case a
                 :min texture/RL-MIN
                 :max texture/RL-MAX)
               (fn [] (doseq [item b] (draw-pass-item! item masks fw fh)))))))

(defmethod draw-scene! :toplights [_ state {:keys [m safe]}]
  (let [dims (toplights-dims m)
        {:keys [field k button help labels]} dims
        {fx :x
         fy :y
         fw :w
         fh :h} field
        ;; 2D passes run with the depth test off, so the masks carry no depth buffer
        spec {:w fw
              :h fh
              :depth? false}
        lights (:lights state)
        master (texture/target! :toplights :master spec)
        masks (mapv (fn [i] (texture/target! :toplights [:mask i] spec)) (range (count lights)))
        dirty (:dirty state)
        show? (:show? state)]
    ;; A turn drops lights, and a size change frees a target and makes another, so
    ;; shrinking the dropped masks to a pixel gives their memory back at once.
    (doseq [i (:release state)]
      (texture/target! :toplights [:mask i] {:w 1
                                             :h 1
                                             :depth? false}))
    ;; The masks are rendered before anything of the screen's own, then merged.
    (doseq [i dirty]
      (texture/with-target! (nth masks i) safe
        (fn [] (run-pass! (toplights/mask-plan (nth lights i)) masks fw fh))))
    (when (seq dirty)
      (texture/with-target! master safe
        (fn [] (run-pass! (toplights/master-plan (count lights)) masks fw fh))))
    (clear-to! toplights/black)
    (let [tile-px (toplights/tile-size dims)]
      (texture/quad! (texture/id! :toplights :ground toplights-ground)
                     {:x fx
                      :y fy
                      :width fw
                      :height fh
                      :u1 (/ fw tile-px)
                      :v1 (/ fh tile-px)}))
    (texture/quad! (:texture master) (cond-> {:x fx
                                              :y fy
                                              :width fw
                                              :height fh
                                              :v0 1.0
                                              :v1 0.0}
                                       show? (assoc :tint (color toplights/volumes-tint))))
    (doseq [[i {[x y] :pos}] (map-indexed vector lights)]
      (rl/draw-circle (int (+ fx x)) (int (+ fy y)) (double (* toplights/marker-radius k))
                      (color (if (zero? i) toplights/yellow toplights/white))))
    (when show?
      (let [{:keys [quads reached outlined]} (toplights/volume-plan state)
            shadow (color toplights/dark-purple)
            box-fill (color toplights/purple)]
        (doseq [corners quads
                [[ax ay] [bx by] [cx cy]] (toplights/quad-triangles corners)]
          (rl/draw-triangle (+ fx ax) (+ fy ay) (+ fx bx) (+ fy by) (+ fx cx) (+ fy cy) shadow))
        (doseq [{:keys [x y w h]} reached]
          (rl/draw-rectangle (int (+ fx x)) (int (+ fy y)) (int w) (int h) box-fill))
        (doseq [{:keys [x y w h]} outlined]
          (outline! (+ fx x) (+ fy y) w h toplights/dark-blue))))
    (doseq [l help]
      (draw-caption! (assoc l :x (+ fx (:x l)) :y (+ fy (:y l))) toplights/help-colour))
    (let [{bx :x
           by :y
           bw :w
           bh :h} button
          label (get labels (if show? :hide :show))]
      (rl/draw-rectangle (int (+ fx bx)) (int (+ fy by)) (int bw) (int bh)
                         (color (if show? toplights/button-on-colour toplights/button-colour)))
      (draw-caption! (assoc label :x (+ fx (:x label)) :y (+ fy (:y label)))
                     (if show? toplights/button-on-label-colour toplights/button-label-colour)))))
