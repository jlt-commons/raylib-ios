(ns net.b12n.raylib-ios.gallery
  "The scene gallery: a two-level menu over the scenes it is handed, on the iOS host.

  This namespace is the half that has to touch raylib, and it exists so that
  nothing else does. It polls the scalar input, hands the result to the pure
  gallery in net.b12n.raylib-ios.gallery.core, and draws whatever comes back.
  Every scene itself is pure, and its draw-scene! method sits beside it in the
  scene's own draw namespace.

  The shell holds no scenes of its own beyond Hello. `run!` takes the scene
  maps and the categories that group them, so an app lists exactly what it
  wants in its menu and requires exactly those scenes' draw namespaces.
  raylib-ios-demo's gallery passes all of its scenes this way, and `-main`
  runs the platform gallery below, which has Hello alone.

  That split is why three of raylib-ios-demo's scenes run as files written for
  Android, apart from their names and whitespace. Nothing in them knows what a
  platform is."
  (:refer-clojure :exclude [run!])
  (:require [net.b12n.raylib-ios.frame :as frame :refer [drain-events!
                                                         ignore-close
                                                         resolve-insets
                                                         safe-region]]
            [net.b12n.raylib-ios.gallery.core :as gallery]
            [net.b12n.raylib-ios.gallery.diagnostics :as diag]
            [net.b12n.raylib-ios.gallery.draw-util :as du :refer [color WHITE]]
            [net.b12n.raylib-ios.gallery.ui :as ui]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.hello :as hello]
            [net.b12n.raylib-ios.scenes.hello.draw]
            [net.b12n.raylib-ios.scroll :as scroll]
            [net.b12n.raylib-ios.texture :as texture]))

(def platform-gallery
  "What `-main` runs: Hello alone, in one category. It is the platform's proof
  that the shell, the host loop and the build tools work, with no other scene
  needed."
  {:scenes [(hello/scene)]
   :categories [{:id :platform
                 :title "Platform"
                 :scenes [:hello]}]})

(defn- prepare
  "The shell's working set for a gallery: the registry, and the categories with
  their ids. `scenes` is a vector of scene maps (each `:id` and the contract's
  fns) and `categories` a vector of `{:id :title :scenes [scene-id ...]}`."
  [{:keys [scenes categories]}]
  {:registry (gallery/make-registry scenes)
   :categories categories
   :category-ids (mapv :id categories)})

;; --- a level above the scene contract ----------------------------------------
;; net.b12n.raylib-ios.gallery.core knows two modes, :gallery and :scene, and its layout fits
;; every card on one screen by dividing the height by the row count. That is
;; right for three scenes and unreadable for fifty: it never scrolls, it just
;; shrinks. So categories sit ABOVE that contract rather than inside it, and
;; the pure file stays identical to upstream apart from names and whitespace.
;;
;; The whole trick is that ui/gallery-layout takes the ids to lay out as an
;; argument. Hand it category ids and it lays out categories; hand it the ids
;; in a category and it lays out those. Same untouched function, twice.

(defn- category-by-id [{:keys [categories]} id]
  (some (fn [c] (when (= id (:id c)) c)) categories))

(defn- title-of
  "Cards are drawn from an id, and an id is a category at the top level and a
  scene inside one."
  [{:keys [registry]
    :as cfg} id]
  (or (:title (category-by-id cfg id))
      (:title (gallery/scene-by-id registry id))
      (name id)))

(defn- within?
  "hit-test only checks the Back target in :scene mode, but a category's scene
  list needs cards AND a Back. contains-point? is private to the pure file, so
  this is the same four comparisons."
  [{:keys [x y width height]} [px py]]
  (and px (>= px x) (< px (+ x width)) (>= py y) (< py (+ y height))))

;; --- driving it from an editor ------------------------------------------------
;; The synthetic tap and drag live in net.b12n.raylib-ios.frame, beside the
;; polling they replace, so the one-scene runner can be driven the same way.
;; These are the names the docs and the nREPL recipes use.
(def tap! frame/tap!)
(def drag! frame/drag!)

;; --- drawing: the owner-affine half of the contract
;; The multimethod is defined in net.b12n.raylib-ios.gallery.draw-util, which
;; every draw namespace can require without requiring this one.
(def draw-scene! du/draw-scene!)

(defn- centered-text!
  "Draw `text` centred in the rectangle, with MeasureText."
  [text {:keys [x y width height]} size colour]
  (rl/draw-text text (+ x (quot (- width (rl/measure-text text size)) 2)) (+ y (quot (- height size) 2)) size colour))

(defn- draw-back! [{:keys [back body-size]} accent]
  (let [{:keys [x y width height]} back]
    (rl/draw-rectangle x y width height accent)
    (centered-text! "< Back" back body-size WHITE)))

(defn- draw-gallery!
  "The heading and the card grid, with the grid clipped to the area below the
  heading so scrolled cards do not slide up over it.

  The heading is drawn after the cards rather than before. Drawing it first and
  letting a card scroll over it is the obvious ordering and the wrong one: the
  scissor already stops that, and the heading then has to be redrawn anyway
  because the card that overlapped it has repainted the background it sat on."
  [cfg {:keys [margin title-size body-size line-gap cards content-height viewport-height]}
   p top scroll heading subheading]
  (rl/clear-background (color (:background p)))
  (let [grid-top (+ top margin title-size (* 2 line-gap))
        grid-h (- (+ top viewport-height) grid-top)]
    (rl/begin-scissor-mode 0 grid-top (rl/get-screen-width) grid-h)
    (doseq [{:keys [scene-id]
             :as card} cards]
      ;; Cards outside the window are skipped rather than drawn and clipped. A
      ;; long list is mostly off screen, and the scissor discards those pixels
      ;; only after paying for the draw call and the text measurement.
      (when (and (< (:y card) (+ grid-top grid-h))
                 (> (+ (:y card) (:height card)) grid-top))
        (rl/draw-rectangle (:x card) (:y card) (:width card) (:height card) (color (:card p)))
        (centered-text! (title-of cfg scene-id) card body-size WHITE)))
    (rl/end-scissor-mode)
    (when-let [[ty th] (scroll/thumb scroll content-height viewport-height)]
      (let [w (rl/get-screen-width)
            bar-w (max 6 (quot w 160))
            x (- w bar-w (quot margin 3))]
        (rl/draw-rectangle x (+ grid-top (int (* ty (/ (double grid-h) viewport-height))))
                           bar-w (int (* th (/ (double grid-h) viewport-height)))
                           (rl/rgba 140 140 150 160)))))
  (rl/draw-text heading margin (+ top margin) title-size (color (:accent p)))
  (rl/draw-text subheading margin (+ top margin title-size (quot line-gap 2)) body-size rl/DARKGRAY))

(defn- below-the-safe-area
  "gallery-layout, shifted clear of the status bar.

  The pure layout function knows nothing about safe areas and should not: it is
  handed a screen and divides it. So it is given a screen shortened by the inset
  and every rectangle it returns is then moved down by the same amount. The
  result is identical to a layout that understood insets, and the pure half
  stays portable to a platform that has none.

  Both the cards and the Back target move. Missing the Back target is the
  interesting bug, because it still draws in the right place and only its
  hit-test is wrong, so it looks like an unresponsive button.

  Scrolling rides the same trick from the other direction. gallery-layout sizes
  cards to FIT, dividing the height it is given by the row count, so a fixed
  screen makes them shrink without limit: at twenty-seven scenes each card is
  about 140 pixels tall. Handing it a screen TALLER than the real one gets a
  comfortable grid for a screen that does not exist, and `scroll` then moves a
  window over it. The Back target does not scroll, because it is chrome rather
  than content."
  ([m sizes top ids] (below-the-safe-area m sizes top ids 0))
  ([m sizes top ids scroll]
   (let [[w h] (:screen m)
         viewport (- h top)
         content (scroll/content-height {:width w
                                         :height viewport} sizes (count ids)
                                        (if (>= (* w 3) (* viewport 2)) 3 2))
         shifted (ui/gallery-layout (assoc m :screen [w content]) ids sizes)
         lower (fn [rect] (update rect :y + top))
         ;; long, not whatever arithmetic produced. gallery-layout rounds its
         ;; own rectangles to ints, and this adds an offset afterwards: a
         ;; fractional scroll makes :y a double, draw-rectangle is bound
         ;; [:int :int :int :int :uint], and the process dies at the FFI
         ;; boundary rather than drawing in the wrong place.
         shift (long (- top scroll))
         lower-scrolled (fn [rect] (update rect :y + shift))]
     (assoc (-> shifted
                (update :back lower)
                (update :cards #(mapv lower-scrolled %)))
            :content-height content
            :viewport-height viewport))))

;; --- the scene, for net.b12n.raylib-ios.host: the gallery state is the state
(defn- init [{:keys [scale inset-top]}]
  ;; :category nil is the top level, showing categories. Set, it is that
  ;; category's scene list. The pure gstate is unaware of either.
  {:k scale
   :insets {:top inset-top}
   :touches 0
   :category nil
   :gstate gallery/initial-gallery-state})

(defn- visible-ids
  "The ids this level lays out: the categories at the top, or one category's
  scenes inside it. In :scene mode nothing but the Back target is read from the
  layout, so either list would do."
  [{:keys [category-ids]
    :as cfg} category]
  (if category
    (:scenes (category-by-id cfg category))
    category-ids))

(defn- navigate
  "Where a press takes the level, as [category opening?].

  Level changes are resolved before the press reaches a scene, so one tap
  cannot both change level and land on whatever the change puts under the
  finger. `opening?` says a scene is being opened, which the caller turns into
  open-scene rather than run-frame.

  There are TWO Back presses here and they do different things. In :scene mode
  Back closes the scene, and the category must survive so the reader lands back
  on the list they opened it from. In :gallery mode Back leaves the category,
  and only then is it cleared. Collapsing the two sends every scene's Back
  straight to the top level, which looks like the list was never there."
  [cfg category mode hit list-back?]
  (cond
    ;; Back out of a category's scene list, to the categories
    list-back?                              [nil false]
    ;; Back out of a running scene: run-frame closes it, the list stays put
    (not= :gallery mode)                    [category false]
    ;; a category card at the top level
    (nil? category)                         [(when (keyword? hit) hit) false]
    ;; a scene card inside a category
    (and (keyword? hit)
         (some #{hit} (:scenes (category-by-id cfg category))))
    [category true]
    :else                                   [category false]))

(defn- abandon-scene
  "Drop back to the scene list after a scene threw, and say why.

  The scene's :dispose is not called. It is scene code too, and one that has
  just failed is a poor bet to tidy up after itself without throwing again. The
  outer frame state keeps :category, so the reader lands on the list they
  opened the scene from.

  `id` is passed in rather than read from `gstate`, because a scene that throws
  in :init never finished opening and the state still says nothing is active.
  The detail falls back to the exception's string, since an NPE has no message
  and the line would otherwise end in nothing."
  [gstate id e]
  (println (str "gallery: " (pr-str id)
                " failed, back to the list: " (or (ex-message e) (str e))))
  (assoc gstate :mode :gallery :active-scene-id nil :scene-state nil
         :scene-events []))

(defn guard-scene
  "Call `f`, which runs scene code for scene `id`, and return its result. If it
  throws, return `gstate` abandoned instead. Before this, one bug in a scene's :update or
  draw-scene! method ended the process on the phone, so each porting mistake
  cost a rebuild and a redeploy just to read the message.

  Public so raylib-ios-demo's gallery tests can check the abandonment path."
  [gstate id f]
  (frame/guarded (fn [e] (abandon-scene gstate id e)) f))

(defn- render!
  "Three things can be on screen: a running scene, one category's scenes, or
  the categories. The first two carry a Back target and the last does not,
  because there is nowhere above it.

  Returns the gallery state, which is the argument unless the scene's draw threw
  and was abandoned."
  [cfg {:keys [mode active-scene-id scene-state]
        :as gstate} category layout k m top safe scroll]
  ;; Every frame, so a scene's textures are freed the frame after it is left,
  ;; however it was left: Back, or abandoned by guard-scene. An abandoned scene
  ;; comes through here with :mode :gallery, either this frame (it threw in
  ;; update) or the next (it threw in draw), so nil frees everything.
  (texture/enter! (when (= :scene mode) active-scene-id))
  (let [p (ui/live-presentation)
        accent (color (:accent p))]
    (cond
      (= :scene mode)
      ;; frame/draw-in-safe-region! clears, clips and translates, and runs the
      ;; scene's draw inside guard-scene so a throw cannot skip its pop.
      (let [result (frame/draw-in-safe-region!
                    (color (:background p)) safe
                    (fn []
                      (guard-scene gstate active-scene-id
                                   (fn []
                                     (draw-scene! active-scene-id scene-state
                                                  {:k k
                                                   :m m
                                                   :safe safe})
                                     gstate))))]
        (draw-back! layout accent)
        result)

      category
      (do (draw-gallery! cfg layout p top scroll (title-of cfg category) "Choose a scene")
          (draw-back! layout accent)
          gstate)

      :else
      (do (draw-gallery! cfg layout p top scroll (:title p) "Choose a category")
          gstate))))

(defn next-scroll
  "The list's scroll offset after this frame's pointer. Public so
  raylib-ios-demo's gallery tests can check the rule.

  A drag moves the list only while the list is what is showing. Inside a scene
  the list is hidden behind it, and a swipe game drags constantly, so following
  the finger would leave the list somewhere arbitrary once the game ends. The
  drag itself still runs in `frame`, because it also decides whether a release
  was a tap on Back."
  [mode drag point phase scroll layout]
  (let [content (:content-height layout)
        viewport (:viewport-height layout)]
    (if (and (= :gallery mode) drag point (= :down phase))
      (scroll/scroll-for drag point content viewport)
      (scroll/clamp scroll content viewport))))

(defn- frame
  "One frame: sample, decide where the press goes, advance the pure gallery,
  draw. The state carried between frames is the touch count, the cached inset,
  which category is open, and the pure state itself. `cfg` is the gallery
  `prepare` made, the same for every frame."
  [{:keys [registry]
    :as cfg}
   {:keys [k insets touches gstate category]
    :as s}]
  (let [insets (resolve-insets k insets)
        top    (:top insets 0)
        input  (frame/poll-input touches)
        m      (:metrics input)
        safe   (safe-region (:screen m) insets)
        ;; A scene is told it has the safe region and nothing else, so its own
        ;; geometry is computed for the space it will actually get. The host
        ;; then translates it into place at draw time, which is why no scene
        ;; has to know a safe area exists.
        scene-m (frame/scene-metrics m safe)
        layout (below-the-safe-area m (diag/layout m) top (visible-ids cfg category)
                                    (:scroll s 0))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        ;; A list that scrolls cannot open a card on press: at press time there
        ;; is no way to know whether a drag is starting. So a press only begins
        ;; a gesture, movement scrolls, and the release opens a card if and only
        ;; if the finger stayed inside the slop. Back is chrome and does not
        ;; scroll, but it goes through the same rule so a drag that happens to
        ;; start on it does not navigate.
        ;; Travel accumulates on :down only, never on :release. A release
        ;; carries no finger, and raylib still answers GetTouchX and GetTouchY
        ;; with whatever it last had, which on device is not the touch that just
        ;; ended. Feeding that into the drag inflated travel to 1941 pixels on a
        ;; motionless tap and every tap was read as a scroll.
        drag (case phase
               :press (scroll/begin-drag (:scroll s 0) point)
               :down (if (and (:drag s) point)
                       (scroll/drag-to (:drag s) point)
                       (:drag s))
               :release (:drag s)
               nil)
        ;; The mode is the one the frame STARTED in, from the destructured
        ;; gstate. gstate is rebound below once a card opens or a scene runs.
        scroll' (next-scroll (:mode gstate) drag point phase (:scroll s 0) layout)
        tapped? (and (= :release phase) (scroll/tap? drag (min (first (:screen m))
                                                               (second (:screen m)))))
        tap-at (when tapped? (scroll/tap-point drag))
        hit (when tapped? (ui/hit-test layout tap-at (:mode gstate)))
        ;; hit-test only looks for Back in :scene mode, so the scene list's own
        ;; Back is recognised here. Kept separate from the scene's Back on
        ;; purpose: see navigate.
        list-back? (and tapped? (= :gallery (:mode gstate)) category
                        (within? (:back layout) tap-at))
        [category' opening?] (navigate cfg category (:mode gstate) hit list-back?)
        scene-input (frame/scene-input input safe scene-m (= hit :back))
        gstate (-> (guard-scene gstate (or (:active-scene-id gstate) hit)
                                (fn []
                                  (if opening?
                                    (gallery/open-scene registry gstate hit scene-input)
                                    (gallery/run-frame registry gstate scene-input))))
                   drain-events!
                   ignore-close)
        gstate (render! cfg gstate category' layout k scene-m top safe scroll')]
    (assoc s :insets insets
           :category category'
             ;; The offset belongs to the level being shown, so moving between
             ;; levels starts at the top rather than halfway down a list of a
             ;; different length. Without this, opening a scene from the bottom
             ;; of Toys and coming back lands on a blank stretch below the last
             ;; card of a shorter category.
           :scroll (if (= category category') scroll' 0)
           :drag (when-not (= :release phase) drag)
           :touches (get-in input [:touches :count])
           :gstate gstate)))

(defn app
  "The host app for a gallery, `{:title :init :frame}`, which `run!` hands to
  the host loop. A function of its own so a test can drive the frames over
  stubbed raylib, as it does the runner's."
  [gallery]
  (let [cfg (prepare gallery)]
    {:title "Gallery"
     :init init
     :frame (fn [s] (frame cfg s))}))

(defn run!
  "Run a gallery on the host loop. Does not return.

  `gallery` is `{:scenes [...] :categories [...]}`:

  - `:scenes` is a vector of scene maps, each the result of a scene's `(scene)`.
  - `:categories` is a vector of `{:id :title :scenes [scene-id ...]}`, in menu
    order. The top level lists the categories and a category lists its scenes.

  Every scene's draw namespace must be loaded by the caller, since the shell
  requires none beyond Hello's. A scene in `:scenes` with no draw-scene! method
  is abandoned on open with its message printed, like any scene that throws."
  [gallery]
  (rl/run! (app gallery)))

(defn -main [& _]
  (run! platform-gallery))
