(ns raylib.gallery
  "examples/gallery: the Android experiment's scene contract on the iOS host.
  Polls raylib's scalars into diagnostics/normalize-input's raw map, runs
  gallery/run-frame, and draws the active scene or the cards. Their scenes,
  unchanged; this namespace is the owner-affine part."
  (:require [poc.raylib.diagnostics :as diag]
            [poc.raylib.flappy-bird :as flappy]
            [poc.raylib.following-eyes :as eyes]
            [poc.raylib.gallery :as gallery]
            [poc.raylib.gallery-ui :as ui]
            [poc.raylib.touch-trail :as trail]
            [raylib.flappy :as flappy-draw]
            [raylib.host :as rl]
            [raylib.scenes.spirograph :as spiro]))

(def scenes [(eyes/scene) (trail/scene) (flappy/scene) (spiro/scene)])
(def registry (gallery/make-registry scenes))
(def scene-ids (mapv :id scenes))

(def WHITE (rl/rgba 255 255 255 255))
(defn- color [[r g b a]] (rl/rgba r g b a))

;; --- driving it from an editor ------------------------------------------------
;; A synthetic tap, consumed by the next frame. The host can already read state
;; and run work on the main thread, but the scene state is threaded through the
;; loop rather than held in an atom, so an nREPL could look and not touch. This
;; is the missing half: it lets a session open a scene, press Back, or flap the
;; bird without a finger, which is what makes the examples testable from a
;; keyboard.
;;
;; One tap per frame, cleared as it is taken, so a queued tap cannot be seen
;; twice and read as a held touch.
(defonce pending-tap (atom nil))

(defn tap!
  "Queue a synthetic tap at [x y] in SCREEN PIXELS, not points. The next frame
  sees a press there and the frame after sees the release, which is the edge
  the gallery opens a card on."
  [x y]
  (reset! pending-tap [x y])
  nil)

;; --- polling: raylib's scalars, in the shape diagnostics/normalize-input expects
(defn- raw-sample [previous-count]
  (let [tap (first (swap-vals! pending-tap (constantly nil)))
        n (if tap 1 (rl/get-touch-point-count))
        w (rl/get-screen-width) h (rl/get-screen-height)]
    {:screen-width w :screen-height h :render-width w :render-height h
     :touch-count n
     :touch-ids   (if tap [0] (vec (for [i (range n)] (rl/get-touch-point-id i))))
     :pointer-x   (if tap (first tap) (rl/get-touch-x))
     :pointer-y   (if tap (second tap) (rl/get-touch-y))
     :pressed?    (and (pos? n) (zero? previous-count))
     :down?       (pos? n)
     :released?   (and (zero? n) (pos? previous-count))
     :back?       false}))

;; --- drawing: the owner-affine half of the contract
(defmulti draw-scene! (fn [id _state _env] id))

(defmethod draw-scene! :flappy-bird [_ game {:keys [k m]}]
  (flappy-draw/draw-game! k game m))

(defmethod draw-scene! :following-eyes [_ {:keys [target]} {:keys [m]}]
  (let [{:keys [eye-radius pupil-radius left right]} (eyes/layout m)]
    (rl/clear-background rl/RAYWHITE)
    (doseq [eye [left right]]
      (let [[ex ey] eye
            [px py] (eyes/pupil eye eye-radius pupil-radius target)]
        (rl/draw-circle (int ex) (int ey) (double eye-radius) WHITE)
        (rl/draw-circle-lines (int ex) (int ey) (double eye-radius) rl/DARKGRAY)
        (rl/draw-circle (int px) (int py) (double pupil-radius) rl/DARKGRAY)))))

(defmethod draw-scene! :spirograph [_ {:keys [points]} _]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (doseq [[i [[x1 y1] [x2 y2]]] (map-indexed vector (partition 2 1 points))]
    (rl/draw-line (int x1) (int y1) (int x2) (int y2) (color (spiro/rainbow i)))))

(defmethod draw-scene! :touch-trail [_ {:keys [points]} {:keys [m]}]
  (let [{:keys [radius]} (trail/layout m)
        n (count points)]
    (rl/clear-background rl/RAYWHITE)
    (doseq [[i [x y]] (map-indexed vector points)]
      (rl/draw-circle (int x) (int y) (double (* radius (/ (inc i) (max 1 n)))) rl/MAROON))))

(defn- centered-text!
  "Draw `text` centred in the rectangle, with MeasureText."
  [text {:keys [x y width height]} size colour]
  (rl/draw-text text (+ x (quot (- width (rl/measure-text text size)) 2)) (+ y (quot (- height size) 2)) size colour))

(defn- draw-back! [{:keys [back body-size]} accent]
  (let [{:keys [x y width height]} back]
    (rl/draw-rectangle x y width height accent)
    (centered-text! "< Back" back body-size WHITE)))

(defn- draw-gallery! [{:keys [margin title-size body-size line-gap cards]} p top]
  (rl/clear-background (color (:background p)))
  (rl/draw-text (:title p) margin (+ top margin) title-size (color (:accent p)))
  (rl/draw-text (:subtitle p) margin (+ top margin title-size (quot line-gap 2)) body-size rl/DARKGRAY)
  (doseq [{:keys [scene-id] :as card} cards]
    (rl/draw-rectangle (:x card) (:y card) (:width card) (:height card) (color (:card p)))
    (centered-text! (:title (gallery/scene-by-id registry scene-id)) card body-size WHITE)))

(defn- below-the-safe-area
  "Their layout, computed for the screen minus the top inset and lowered by it,
  so the Back target and the cards clear the status bar."
  [m sizes top]
  (let [[w h] (:screen m)
        lower (fn [r] (update r :y + top))
        l     (ui/gallery-layout (assoc m :screen [w (- h top)]) scene-ids sizes)]
    (-> l (update :back lower) (update :cards #(mapv lower %)))))

;; --- the scene, for raylib.host: the gallery state is the state
(defn- init [{:keys [scale inset-top]}]
  {:k scale :top inset-top :touches 0 :gstate gallery/initial-gallery-state})

(defn- frame [{:keys [k top touches gstate] :as s}]
  (let [top     (if (pos? top)                       ; the safe area is only real once the window is laid out:
                  top                                ; ask UIKit on the first frame, keep the answer
                  (let [{t :top} (rl/safe-area-insets) px (int (* k t))]
                    (when (pos? px) (println "gallery: safe-area top" t "pt =" px "px"))
                    px))
        s       (assoc s :top top)
        input   (diag/normalize-input (raw-sample touches))
        m       (:metrics input)
        sizes   (diag/layout m)
        layout  (below-the-safe-area m sizes top)
        press?  (= :press (get-in input [:pointer :phase]))
        hit     (when press? (ui/hit-test layout (get-in input [:pointer :position]) (:mode gstate)))
        input   (assoc input :delta-seconds (rl/get-frame-time) :back? (= hit :back))
        gstate  (if (and (= :gallery (:mode gstate)) (keyword? hit))
                  (gallery/open-scene registry gstate hit input)      ; the opening press is consumed
                  (gallery/run-frame registry gstate input))
        gstate  (let [events (:scene-events gstate)]
                  (doseq [e events] (println "gallery:" (pr-str e)))
                  (assoc gstate :scene-events []))
        gstate  (if (:close-requested? gstate)
                  (do (println "gallery: close requested — an iOS app does not exit; ignored")
                      (assoc gstate :close-requested? false))
                  gstate)]
    (if (= :scene (:mode gstate))
      (do (draw-scene! (:active-scene-id gstate) (:scene-state gstate) {:k k :m m})
          (draw-back! layout (color (:accent (ui/live-presentation)))))
      (draw-gallery! layout (ui/live-presentation) top))
    (assoc s :touches (get-in input [:touches :count]) :gstate gstate)))

(defn -main [& _]
  (rl/run! {:title "Gallery" :init init :frame frame}))
