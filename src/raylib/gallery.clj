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
            [raylib.scenes.boids :as boids]
            [raylib.scenes.epicycles :as epi]
            [raylib.scenes.hilbert :as hil]
            [raylib.scenes.fireworks :as fw]
            [raylib.scenes.kaleidoscope :as kal]
            [raylib.scenes.pendulum :as pend]
            [raylib.scenes.penrose :as pen]
            [raylib.scenes.spirograph :as spiro]
            [raylib.scenes.stars :as stars]
            [raylib.scenes.tree :as tree]))

(def scenes [(eyes/scene) (trail/scene) (flappy/scene)
             (spiro/scene) (kal/scene) (fw/scene) (pen/scene) (boids/scene)
             (pend/scene) (epi/scene) (hil/scene) (tree/scene) (stars/scene)])

(def registry (gallery/make-registry scenes))
(def scene-ids (mapv :id scenes))

;; --- a level above the scene contract ----------------------------------------
;; poc.raylib.gallery knows two modes, :gallery and :scene, and its layout fits
;; every card on one screen by dividing the height by the row count. That is
;; right for three scenes and unreadable for fifty: it never scrolls, it just
;; shrinks. So categories sit ABOVE that contract rather than inside it, and
;; the pure file stays byte-identical.
;;
;; The whole trick is that ui/gallery-layout takes the ids to lay out as an
;; argument. Hand it category ids and it lays out categories; hand it the ids
;; in a category and it lays out those. Same untouched function, twice.
(def categories
  [{:id :generative :title "Generative"
    :scenes [:spirograph :kaleidoscope :fireworks :penrose :epicycles]}
   {:id :fractals :title "Fractals"
    :scenes [:hilbert :tree]}
   {:id :toys :title "Toys"
    :scenes [:following-eyes :touch-trail :boids :pendulum :stars]}
   {:id :games :title "Games"
    :scenes [:flappy-bird]}])

(def ^:private category-ids (mapv :id categories))

(defn- category-by-id [id] (some (fn [c] (when (= id (:id c)) c)) categories))

(defn- title-of
  "Cards are drawn from an id, and an id is a category at the top level and a
  scene inside one."
  [id]
  (or (:title (category-by-id id))
      (:title (gallery/scene-by-id registry id))
      (name id)))

(defn- within?
  "hit-test only checks the Back target in :scene mode, but a category's scene
  list needs cards AND a Back. contains-point? is private to the pure file, so
  this is the same four comparisons."
  [{:keys [x y width height]} [px py]]
  (and px (>= px x) (< px (+ x width)) (>= py y) (< py (+ y height))))

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

(defmethod draw-scene! :kaleidoscope [_ {:keys [trail]} {:keys [m]}]
  ;; Everything on the per-line path is inlined here on purpose, and the
  ;; measurements are in docs/guide/performance-on-a-phone.md. Briefly: this
  ;; scene draws twelve lines per trail segment, and calling a helper that
  ;; returns [x y] allocated two vectors per line. Removing that took 1068
  ;; lines from 22 fps to 47. kaleidoscope/place is the readable statement of
  ;; the same transform, kept for the tests.
  (rl/clear-background (rl/rgba 12 12 20 255))
  (let [{:keys [cx cy]} (kal/dimensions m)
        rots (kal/rotations)
        rn (count rots)
        n (count trail)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth trail (dec i))
              b (nth trail i)
              ax (double (nth a 0)) ay (double (nth a 1))
              bx (double (nth b 0)) by (double (nth b 1))
              age (/ (double i) n)
              packed (rl/rgba (int (* 255 age)) 120 (int (* 255 (- 1.0 age))) 255)]
          (loop [r 0]
            (when (< r rn)
              (let [rot (nth rots r)
                    ca (double (nth rot 0))
                    sa (double (nth rot 1))
                    ax (if (nth rot 2) (- ax) ax)
                    bx (if (nth rot 2) (- bx) bx)]
                (rl/draw-line (int (+ cx (- (* ax ca) (* ay sa)))) (int (+ cy (+ (* ax sa) (* ay ca))))
                              (int (+ cx (- (* bx ca) (* by sa)))) (int (+ cy (+ (* bx sa) (* by ca))))
                              packed))
              (recur (inc r)))))
        (recur (inc i))))))

(defmethod draw-scene! :fireworks [_ {:keys [rockets parts]} {:keys [m]}]
  (let [{:keys [rocket-radius particle-radius]} (fw/dimensions m)]
    (rl/clear-background (rl/rgba 0 0 0 255))
    (doseq [{:keys [x y color]} rockets]
      (let [[r g b] color]
        (rl/draw-circle (int x) (int y) (double rocket-radius) (rl/rgba r g b 255))))
    (doseq [{:keys [x y life color]} parts]
      (let [[r g b] color]
        (rl/draw-circle (int x) (int y) (double particle-radius)
                        (rl/rgba r g b (int (* 255 (max 0.0 life)))))))))

(defmethod draw-scene! :boids [_ {:keys [flock]} {:keys [m]}]
  (let [dims (boids/dimensions m)
        body (:body dims)
        wing (rl/rgba 120 200 255 255)]
    (rl/clear-background (rl/rgba 20 20 30 255))
    (doseq [b flock]
      (let [[hx hy] (boids/heading b dims)]
        (rl/draw-line (int (:x b)) (int (:y b)) (int hx) (int hy) wing)
        (rl/draw-circle (int (:x b)) (int (:y b)) (double body) rl/SKYBLUE)))))

(defmethod draw-scene! :penrose [_ {:keys [tris]} _]
  (rl/clear-background (rl/rgba 18 18 24 255))
  ;; one rlgl batch for every fill, then the edges as ordinary lines
  (rl/rl-begin rl/RL-TRIANGLES)
  (doseq [[k [ax ay] [bx by] [cx cy]] tris]
    (let [[r g b a] (if (zero? k) pen/colour-thin pen/colour-thick)]
      (rl/rl-color-4ub r g b a)
      (rl/rl-vertex-2f (float ax) (float ay))
      (rl/rl-vertex-2f (float bx) (float by))
      (rl/rl-vertex-2f (float cx) (float cy))))
  (rl/rl-end)
  (let [edge (color pen/colour-edge)]
    (doseq [[_ [ax ay] [bx by] [cx cy]] tris]
      (rl/draw-line (int ax) (int ay) (int bx) (int by) edge)
      (rl/draw-line (int bx) (int by) (int cx) (int cy) edge)
      (rl/draw-line (int cx) (int cy) (int ax) (int ay) edge))))

(defmethod draw-scene! :hilbert [_ {:keys [points colours]} _]
  (rl/clear-background (rl/rgba 12 12 20 255))
  (let [n (count points)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth points (dec i))
              b (nth points i)
              [r g bl al] (nth colours i)]
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1))
                        (rl/rgba r g bl al)))
        (recur (inc i))))))

(defmethod draw-scene! :tree [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [segs (tree/branches m (tree/wind t))
        n (count segs)
        bark (color tree/bark)
        leaf (color tree/leaf)]
    (loop [i 0]
      (when (< i n)
        (let [s (nth segs i)]
          (rl/draw-line (int (nth s 0)) (int (nth s 1)) (int (nth s 2)) (int (nth s 3))
                        (if (nth s 4) leaf bark)))
        (recur (inc i))))))

(defmethod draw-scene! :stars [_ {:keys [stars]} {:keys [m]}]
  (rl/clear-background (rl/rgba 0 0 8 255))
  (let [dims (stars/dimensions m)
        n (count stars)
        white (rl/rgba 255 255 255 255)]
    (loop [i 0]
      (when (< i n)
        (when-let [p (stars/project dims (nth stars i))]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (double (nth p 2)) white))
        (recur (inc i))))))

(defmethod draw-scene! :pendulum [_ {:keys [trail] :as st} {:keys [m]}]
  ;; Indexed loops throughout, per docs/guide/performance-on-a-phone.md.
  (let [{:keys [ox oy bob trail-dot]} (pend/dimensions m)
        [[x1 y1] [x2 y2]] (pend/positions st m)
        n (count trail)]
    (rl/clear-background (rl/rgba 20 20 30 255))
    (loop [i 0]
      (when (< i n)
        (let [p (nth trail i)
              t (/ (double (inc i)) n)]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (* 2.0 trail-dot t)
                          (rl/rgba 80 200 255 (int (* 200 t)))))
        (recur (inc i))))
    (rl/draw-line (int ox) (int oy) (int x1) (int y1) rl/RAYWHITE)
    (rl/draw-line (int x1) (int y1) (int x2) (int y2) rl/RAYWHITE)
    (rl/draw-circle (int x1) (int y1) (double bob) (rl/rgba 255 203 0 255))
    (rl/draw-circle (int x2) (int y2) (double bob) rl/MAROON)))

(defmethod draw-scene! :epicycles [_ {:keys [theta trace]} {:keys [m]}]
  (let [dims (epi/dimensions m)
        {:keys [trace-top trace-step]} dims
        {:keys [centers radii]} (epi/chain dims theta)
        rn (count radii)
        faint (rl/rgba 70 70 80 255)
        rod (rl/rgba 130 130 130 255)
        wave (rl/rgba 255 203 0 255)]
    (rl/clear-background (rl/rgba 0 0 0 255))
    (loop [i 0]
      (when (< i rn)
        (let [a (nth centers i)
              b (nth centers (inc i))]
          (rl/draw-circle-lines (int (nth a 0)) (int (nth a 1)) (double (nth radii i)) faint)
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1)) rod))
        (recur (inc i))))
    ;; the pen, and the line joining it to where the wave starts
    (let [pen (nth centers rn)
          px (int (nth pen 0))
          py (int (nth pen 1))]
      (rl/draw-line px py px (int trace-top) (rl/rgba 90 90 90 255))
      (let [n (count trace)]
        (loop [i 1]
          (when (< i n)
            (rl/draw-line (int (nth trace (dec i))) (int (+ trace-top (* (dec i) trace-step)))
                          (int (nth trace i)) (int (+ trace-top (* i trace-step)))
                          wave)
            (recur (inc i))))))))

(defmethod draw-scene! :spirograph [_ {:keys [points]} _]
  ;; An indexed loop rather than (map-indexed vector (partition 2 1 points)).
  ;; Measured on device: the lazy sequence and its per-segment tuple took this
  ;; from about 50 fps to 14 at a thousand points, while the draw calls
  ;; themselves were never the problem.
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (count points)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth points (dec i))
              b (nth points i)
              [r g b' a'] (spiro/rainbow i)]
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1))
                        (rl/rgba r g b' a')))
        (recur (inc i))))))

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

(defn- draw-gallery! [{:keys [margin title-size body-size line-gap cards]} p top heading subheading]
  (rl/clear-background (color (:background p)))
  (rl/draw-text heading margin (+ top margin) title-size (color (:accent p)))
  (rl/draw-text subheading margin (+ top margin title-size (quot line-gap 2)) body-size rl/DARKGRAY)
  (doseq [{:keys [scene-id] :as card} cards]
    (rl/draw-rectangle (:x card) (:y card) (:width card) (:height card) (color (:card p)))
    (centered-text! (title-of scene-id) card body-size WHITE)))

(defn- below-the-safe-area
  "Their layout, computed for the screen minus the top inset and lowered by it,
  so the Back target and the cards clear the status bar."
  [m sizes top ids]
  (let [[w h] (:screen m)
        lower (fn [r] (update r :y + top))
        l     (ui/gallery-layout (assoc m :screen [w (- h top)]) ids sizes)]
    (-> l (update :back lower) (update :cards #(mapv lower %)))))

;; --- the scene, for raylib.host: the gallery state is the state
(defn- init [{:keys [scale inset-top]}]
  ;; :category nil is the top level, showing categories. Set, it is that
  ;; category's scene list. The pure gstate is unaware of either.
  {:k scale :top inset-top :touches 0 :category nil
   :gstate gallery/initial-gallery-state})

(defn- frame [{:keys [k top touches gstate] :as s}]
  (let [top     (if (pos? top)                       ; the safe area is only real once the window is laid out:
                  top                                ; ask UIKit on the first frame, keep the answer
                  (let [{t :top} (rl/safe-area-insets) px (int (* k t))]
                    (when (pos? px) (println "gallery: safe-area top" t "pt =" px "px"))
                    px))
        s        (assoc s :top top)
        category (:category s)
        input    (diag/normalize-input (raw-sample touches))
        m        (:metrics input)
        sizes    (diag/layout m)
        ;; the ids this level shows: categories at the top, a category's scenes
        ;; inside one. In :scene mode only the Back target is read, so either
        ;; list serves.
        ids      (if category (:scenes (category-by-id category)) category-ids)
        layout   (below-the-safe-area m sizes top ids)
        press?   (= :press (get-in input [:pointer :phase]))
        point    (get-in input [:pointer :position])
        hit      (when press? (ui/hit-test layout point (:mode gstate)))
        ;; a category's scene list carries a Back of its own, which hit-test
        ;; does not look for outside :scene mode
        list-back? (and press? (= :gallery (:mode gstate)) category
                        (within? (:back layout) point))
        input    (assoc input :delta-seconds (rl/get-frame-time) :back? (= hit :back))
        ;; navigation first, so a tap that changes level is not also read as a
        ;; tap on whatever now sits under the finger
        s        (cond
                   list-back?                                    (assoc s :category nil)
                   (and (= :gallery (:mode gstate)) (nil? category) (keyword? hit))
                   (assoc s :category hit)
                   :else s)
        category (:category s)
        opening? (and (= :gallery (:mode gstate)) category (keyword? hit)
                      (not list-back?) (some #{hit} (:scenes (category-by-id category))))
        gstate  (if opening?
                  (gallery/open-scene registry gstate hit input)      ; the opening press is consumed
                  (gallery/run-frame registry gstate input))
        gstate  (let [events (:scene-events gstate)]
                  (doseq [e events] (println "gallery:" (pr-str e)))
                  (assoc gstate :scene-events []))
        gstate  (if (:close-requested? gstate)
                  (do (println "gallery: close requested — an iOS app does not exit; ignored")
                      (assoc gstate :close-requested? false))
                  gstate)]
    (let [p (ui/live-presentation)]
      (cond
        (= :scene (:mode gstate))
        (do (draw-scene! (:active-scene-id gstate) (:scene-state gstate) {:k k :m m})
            (draw-back! layout (color (:accent p))))

        category
        (do (draw-gallery! layout p top (title-of category) "Choose a scene")
            (draw-back! layout (color (:accent p))))

        :else
        (draw-gallery! layout p top (:title p) "Choose a category")))
    (assoc s :touches (get-in input [:touches :count]) :gstate gstate)))

(defn -main [& _]
  (rl/run! {:title "Gallery" :init init :frame frame}))
