(ns net.b12n.raylib-ios.gallery
  "The scene gallery: a two-level menu over every scene, on the iOS host.

  This namespace is the half that has to touch raylib, and it exists so that
  nothing else does. It polls the scalar input, hands the result to the pure
  gallery in net.b12n.raylib-ios.gallery.core, and draws whatever comes back. Every scene
  itself is pure, and its draw-scene! method sits beside it in the scene's own
  draw namespace, which net.b12n.raylib-ios.gallery.draws loads.

  That split is why three of the scenes run as files written for
  Android, apart from their names and whitespace. Nothing in them knows what a platform is."
  (:require [net.b12n.raylib-ios.frame :as frame :refer [drain-events!
                                                         ignore-close
                                                         resolve-insets
                                                         safe-region]]
            [net.b12n.raylib-ios.gallery.core :as gallery]
            [net.b12n.raylib-ios.gallery.diagnostics :as diag]
            [net.b12n.raylib-ios.gallery.draw-util :as du :refer [color WHITE]]
            [net.b12n.raylib-ios.gallery.draws]
            [net.b12n.raylib-ios.gallery.ui :as ui]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.align :as align]
            [net.b12n.raylib-ios.scenes.analog :as analog]
            [net.b12n.raylib-ios.scenes.angles :as ang]
            [net.b12n.raylib-ios.scenes.asteroids :as astr]
            [net.b12n.raylib-ios.scenes.automata :as auto]
            [net.b12n.raylib-ios.scenes.balls :as balls]
            [net.b12n.raylib-ios.scenes.bars :as bars]
            [net.b12n.raylib-ios.scenes.bezier :as bez]
            [net.b12n.raylib-ios.scenes.bgscroll :as bgscroll]
            [net.b12n.raylib-ios.scenes.billboard :as billboard]
            [net.b12n.raylib-ios.scenes.blendmodes :as blendmodes]
            [net.b12n.raylib-ios.scenes.blendparticles :as blendparticles]
            [net.b12n.raylib-ios.scenes.boids :as boids]
            [net.b12n.raylib-ios.scenes.bounce :as bounce]
            [net.b12n.raylib-ios.scenes.boxcollide :as boxcollide]
            [net.b12n.raylib-ios.scenes.breakout :as brk]
            [net.b12n.raylib-ios.scenes.bullets :as bull]
            [net.b12n.raylib-ios.scenes.bunnymark :as bunnymark]
            [net.b12n.raylib-ios.scenes.camera2d :as c2d]
            [net.b12n.raylib-ios.scenes.camera3d :as c3d]
            [net.b12n.raylib-ios.scenes.camerazoom :as czoom]
            [net.b12n.raylib-ios.scenes.clipbox :as clipbox]
            [net.b12n.raylib-ios.scenes.clock :as clock]
            [net.b12n.raylib-ios.scenes.clockgrid :as cgrid]
            [net.b12n.raylib-ios.scenes.collision :as coll]
            [net.b12n.raylib-ios.scenes.colorwheel :as wheel]
            [net.b12n.raylib-ios.scenes.dashed :as dash]
            [net.b12n.raylib-ios.scenes.deltatime :as dtime]
            [net.b12n.raylib-ios.scenes.dirbillboard :as dirbillboard]
            [net.b12n.raylib-ios.scenes.doom :as doom]
            [net.b12n.raylib-ios.scenes.easings :as ease]
            [net.b12n.raylib-ios.scenes.easingsbox :as ebox]
            [net.b12n.raylib-ios.scenes.easingstestbed :as etb]
            [net.b12n.raylib-ios.scenes.ellipses :as ell]
            [net.b12n.raylib-ios.scenes.epicycles :as epi]
            [net.b12n.raylib-ios.scenes.fan :as fan]
            [net.b12n.raylib-ios.scenes.fbrender :as fbrender]
            [net.b12n.raylib-ios.scenes.fireworks :as fw]
            [net.b12n.raylib-ios.scenes.flappy-bird :as flappy]
            [net.b12n.raylib-ios.scenes.flowfield :as flow]
            [net.b12n.raylib-ios.scenes.fogofwar :as fogofwar]
            [net.b12n.raylib-ios.scenes.following-eyes :as eyes]
            [net.b12n.raylib-ios.scenes.fontsizes :as fsizes]
            [net.b12n.raylib-ios.scenes.formattext :as ftext]
            [net.b12n.raylib-ios.scenes.fpcamera :as fpcamera]
            [net.b12n.raylib-ios.scenes.fpmaze :as fpmaze]
            [net.b12n.raylib-ios.scenes.freecam :as freecam]
            [net.b12n.raylib-ios.scenes.game2048 :as g2048]
            [net.b12n.raylib-ios.scenes.geoshapes :as geoshapes]
            [net.b12n.raylib-ios.scenes.gestures :as gestures]
            [net.b12n.raylib-ios.scenes.gradient :as grad]
            [net.b12n.raylib-ios.scenes.helitorus :as helitorus]
            [net.b12n.raylib-ios.scenes.hello :as hello]
            [net.b12n.raylib-ios.scenes.hilbert :as hil]
            [net.b12n.raylib-ios.scenes.huewheel :as hue]
            [net.b12n.raylib-ios.scenes.inlinestyle :as istyle]
            [net.b12n.raylib-ios.scenes.invaders :as inv]
            [net.b12n.raylib-ios.scenes.kaleidoscope :as kal]
            [net.b12n.raylib-ios.scenes.letterbox :as letterbox]
            [net.b12n.raylib-ios.scenes.life :as life]
            [net.b12n.raylib-ios.scenes.logo :as still-logo]
            [net.b12n.raylib-ios.scenes.logoanim :as logoanim]
            [net.b12n.raylib-ios.scenes.lorenz :as lor]
            [net.b12n.raylib-ios.scenes.lsystem :as lsys]
            [net.b12n.raylib-ios.scenes.magnify :as magnify]
            [net.b12n.raylib-ios.scenes.minesweeper :as msw]
            [net.b12n.raylib-ios.scenes.mousepaint :as mousepaint]
            [net.b12n.raylib-ios.scenes.multitouch :as multi]
            [net.b12n.raylib-ios.scenes.npatch :as npatch]
            [net.b12n.raylib-ios.scenes.nudge :as nudge]
            [net.b12n.raylib-ios.scenes.ortho :as ortho]
            [net.b12n.raylib-ios.scenes.outlines :as outl]
            [net.b12n.raylib-ios.scenes.pacman :as pacman]
            [net.b12n.raylib-ios.scenes.palette :as pal]
            [net.b12n.raylib-ios.scenes.particles :as parts]
            [net.b12n.raylib-ios.scenes.pendulum :as pend]
            [net.b12n.raylib-ios.scenes.penrose :as pen]
            [net.b12n.raylib-ios.scenes.picking :as picking]
            [net.b12n.raylib-ios.scenes.piechart :as pie]
            [net.b12n.raylib-ios.scenes.pixelperfect :as pixelperfect]
            [net.b12n.raylib-ios.scenes.platformer :as platformer]
            [net.b12n.raylib-ios.scenes.pointcloud :as pointcloud]
            [net.b12n.raylib-ios.scenes.pong :as pong]
            [net.b12n.raylib-ios.scenes.randomvalues :as rv]
            [net.b12n.raylib-ios.scenes.rawdata :as rawdata]
            [net.b12n.raylib-ios.scenes.rectbounds :as rbounds]
            [net.b12n.raylib-ios.scenes.rendertex :as rendertex]
            [net.b12n.raylib-ios.scenes.resize :as rsz]
            [net.b12n.raylib-ios.scenes.ring :as ring]
            [net.b12n.raylib-ios.scenes.rlgltriangle :as rlgl]
            [net.b12n.raylib-ios.scenes.rotcube :as rotcube]
            [net.b12n.raylib-ios.scenes.rounded :as rnd]
            [net.b12n.raylib-ios.scenes.screenbuf :as screenbuf]
            [net.b12n.raylib-ios.scenes.screens :as screens]
            [net.b12n.raylib-ios.scenes.sector :as sector]
            [net.b12n.raylib-ios.scenes.sequence :as seqn]
            [net.b12n.raylib-ios.scenes.shapes :as shp]
            [net.b12n.raylib-ios.scenes.snake :as snk]
            [net.b12n.raylib-ios.scenes.solarsystem :as solarsystem]
            [net.b12n.raylib-ios.scenes.spheres :as spheres]
            [net.b12n.raylib-ios.scenes.spincubes :as spincubes]
            [net.b12n.raylib-ios.scenes.spirograph :as spiro]
            [net.b12n.raylib-ios.scenes.splines :as spl]
            [net.b12n.raylib-ios.scenes.split3d :as split3d]
            [net.b12n.raylib-ios.scenes.splitscreen :as split]
            [net.b12n.raylib-ios.scenes.spriteanim :as spriteanim]
            [net.b12n.raylib-ios.scenes.spritebutton :as spritebutton]
            [net.b12n.raylib-ios.scenes.spritestack :as spritestack]
            [net.b12n.raylib-ios.scenes.srcrec :as srcrec]
            [net.b12n.raylib-ios.scenes.starfield :as sfield]
            [net.b12n.raylib-ios.scenes.stars :as stars]
            [net.b12n.raylib-ios.scenes.strings :as strings]
            [net.b12n.raylib-ios.scenes.strip :as strip]
            [net.b12n.raylib-ios.scenes.survivors :as surv]
            [net.b12n.raylib-ios.scenes.tesseract :as tess]
            [net.b12n.raylib-ios.scenes.tetris :as tet]
            [net.b12n.raylib-ios.scenes.texcube :as texcube]
            [net.b12n.raylib-ios.scenes.texcurve :as texcurve]
            [net.b12n.raylib-ios.scenes.texpoly :as texpoly]
            [net.b12n.raylib-ios.scenes.texproc :as texproc]
            [net.b12n.raylib-ios.scenes.textiling :as textiling]
            [net.b12n.raylib-ios.scenes.toplights :as toplights]
            [net.b12n.raylib-ios.scenes.touch-trail :as trail]
            [net.b12n.raylib-ios.scenes.touchball :as tball]
            [net.b12n.raylib-ios.scenes.tree :as tree]
            [net.b12n.raylib-ios.scenes.undoredo :as undoredo]
            [net.b12n.raylib-ios.scenes.unitcircle :as circle]
            [net.b12n.raylib-ios.scenes.vecangle :as vang]
            [net.b12n.raylib-ios.scenes.virtualpad :as vpad]
            [net.b12n.raylib-ios.scenes.voxel :as voxel]
            [net.b12n.raylib-ios.scenes.vpscaling :as vpscaling]
            [net.b12n.raylib-ios.scenes.wavecubes :as wavecubes]
            [net.b12n.raylib-ios.scenes.wheelbox :as wbox]
            [net.b12n.raylib-ios.scenes.wireframes :as wireframes]
            [net.b12n.raylib-ios.scenes.worldscreen :as worldscreen]
            [net.b12n.raylib-ios.scenes.writing :as writ]
            [net.b12n.raylib-ios.scenes.yawpitchroll :as ypr]
            [net.b12n.raylib-ios.scroll :as scroll]
            [net.b12n.raylib-ios.texture :as texture]))

(def scenes [(eyes/scene) (trail/scene) (flappy/scene)
             (spiro/scene) (kal/scene) (fw/scene) (pen/scene) (boids/scene)
             (pend/scene) (epi/scene) (hil/scene) (tree/scene) (stars/scene)
             (lsys/scene) (flow/scene) (lor/scene) (tess/scene)
             (life/scene) (auto/scene)
             (wheel/scene) (circle/scene)
             (clock/scene) (pie/scene) (logoanim/scene)
             (ease/scene)
             (ang/scene) (writ/scene) (balls/scene) (seqn/scene)
             (bull/scene) (coll/scene) (dash/scene) (multi/scene)
             (analog/scene) (cgrid/scene) (sector/scene) (pal/scene)
             (grad/scene) (ring/scene) (spl/scene)
             (rnd/scene) (vang/scene) (bars/scene)
             (bez/scene) (fan/scene) (clipbox/scene)
             (rsz/scene) (align/scene) (dtime/scene) (rv/scene)
             (ftext/scene) (strip/scene) (tball/scene) (rlgl/scene)
             (parts/scene) (brk/scene) (bounce/scene) (snk/scene)
             (g2048/scene) (msw/scene) (pong/scene) (inv/scene)
             (tet/scene) (astr/scene) (vpad/scene) (sfield/scene)
             (ebox/scene) (etb/scene) (rbounds/scene) (hue/scene) (still-logo/scene) (fsizes/scene)
             (istyle/scene) (outl/scene) (shp/scene) (ell/scene) (screens/scene) (surv/scene) (pacman/scene)
             (hello/scene) (nudge/scene) (wbox/scene) (undoredo/scene)
             (strings/scene) (c2d/scene) (czoom/scene) (platformer/scene) (split/scene)
             (gestures/scene) (helitorus/scene)
             (rotcube/scene) (c3d/scene) (ortho/scene)
             (spincubes/scene) (worldscreen/scene) (wireframes/scene) (freecam/scene) (ypr/scene) (boxcollide/scene)
             (picking/scene) (wavecubes/scene) (solarsystem/scene) (pointcloud/scene)
             (fpcamera/scene) (fpmaze/scene) (split3d/scene) (spheres/scene)
             (bunnymark/scene) (bgscroll/scene) (spritestack/scene) (pixelperfect/scene)
             (vpscaling/scene) (letterbox/scene) (fogofwar/scene)
             (blendmodes/scene) (blendparticles/scene)
             (billboard/scene) (dirbillboard/scene) (texcube/scene) (geoshapes/scene)
             (voxel/scene) (doom/scene)
             (textiling/scene) (srcrec/scene) (spritebutton/scene)
             (npatch/scene) (texpoly/scene) (texproc/scene)
             (spriteanim/scene) (texcurve/scene) (rendertex/scene) (fbrender/scene) (mousepaint/scene)
             (magnify/scene) (toplights/scene)
             (rawdata/scene) (screenbuf/scene)])

(def registry (gallery/make-registry scenes))
(def scene-ids (mapv :id scenes))

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
(def categories
  [{:id :generative
    :title "Generative"
    :scenes [:spirograph :kaleidoscope :fireworks :penrose :epicycles :flowfield
             :lorenz :life :bullets]}
   {:id :fractals
    :title "Fractals"
    :scenes [:hilbert :tree :lsystem :automata]}
   {:id :toys
    :title "Toys"
    :scenes [:following-eyes :touch-trail :boids :pendulum :stars :tesseract
             :colorwheel :unitcircle :clock :piechart :logoanim :easings
             :angles :writing :balls :sequence :collision :dashed :multitouch
             :analog :clockgrid :sector :palette :gradient :ring :splines
             :rounded :vecangle :bars :bezier :fan :clipbox :resize :align
             :deltatime :randomvalues :formattext :strip :touchball :rlgltriangle
             :particles :bounce :virtualpad :starfield :easingsbox :easingstestbed
             :rectbounds :huewheel :logo :fontsizes :inlinestyle :outlines :shapes :ellipses :screens
             :hello :nudge :wheelbox :undoredo :strings :camera2d :camerazoom :platformer :splitscreen :gestures :helitorus
             :rotcube :camera3d :ortho :spincubes :worldscreen :wireframes :freecam :yawpitchroll :boxcollide :picking
             :wavecubes :solarsystem :pointcloud :fpcamera :fpmaze :split3d :spheres
             :bunnymark :bgscroll :spritestack :pixelperfect :vpscaling :letterbox :fogofwar
             :blendmodes :blendparticles :billboard :dirbillboard :texcube :geoshapes :voxel :doom
             :textiling :srcrec :spritebutton :npatch :texpoly :texproc
             :spriteanim :texcurve :rendertex :fbrender :mousepaint :magnify :toplights :rawdata :screenbuf]}
   {:id :games
    :title "Games"
    :scenes [:flappy-bird :breakout :snake :game2048 :minesweeper :pong :invaders :tetris :asteroids :survivors :pacman]}])

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
  [{:keys [margin title-size body-size line-gap cards content-height viewport-height]}
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
        (centered-text! (title-of scene-id) card body-size WHITE)))
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
  [category]
  (if category
    (:scenes (category-by-id category))
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
  [category mode hit list-back?]
  (cond
    ;; Back out of a category's scene list, to the categories
    list-back?                              [nil false]
    ;; Back out of a running scene: run-frame closes it, the list stays put
    (not= :gallery mode)                    [category false]
    ;; a category card at the top level
    (nil? category)                         [(when (keyword? hit) hit) false]
    ;; a scene card inside a category
    (and (keyword? hit)
         (some #{hit} (:scenes (category-by-id category))))
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

(defn- guard-scene
  "Call `f`, which runs scene code for scene `id`, and return its result. If it
  throws, return `gstate` abandoned instead. Before this, one bug in a scene's :update or
  draw-scene! method ended the process on the phone, so each porting mistake
  cost a rebuild and a redeploy just to read the message."
  [gstate id f]
  (frame/guarded (fn [e] (abandon-scene gstate id e)) f))

(defn- render!
  "Three things can be on screen: a running scene, one category's scenes, or
  the categories. The first two carry a Back target and the last does not,
  because there is nowhere above it.

  Returns the gallery state, which is the argument unless the scene's draw threw
  and was abandoned."
  [{:keys [mode active-scene-id scene-state]
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
      (do (draw-gallery! layout p top scroll (title-of category) "Choose a scene")
          (draw-back! layout accent)
          gstate)

      :else
      (do (draw-gallery! layout p top scroll (:title p) "Choose a category")
          gstate))))

(defn- next-scroll
  "The list's scroll offset after this frame's pointer.

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
  which category is open, and the pure state itself."
  [{:keys [k insets touches gstate category]
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
        layout (below-the-safe-area m (diag/layout m) top (visible-ids category)
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
        [category' opening?] (navigate category (:mode gstate) hit list-back?)
        scene-input (frame/scene-input input safe scene-m (= hit :back))
        gstate (-> (guard-scene gstate (or (:active-scene-id gstate) hit)
                                (fn []
                                  (if opening?
                                    (gallery/open-scene registry gstate hit scene-input)
                                    (gallery/run-frame registry gstate scene-input))))
                   drain-events!
                   ignore-close)
        gstate (render! gstate category' layout k scene-m top safe scroll')]
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

(defn -main [& _]
  (rl/run! {:title "Gallery"
            :init init
            :frame frame}))
