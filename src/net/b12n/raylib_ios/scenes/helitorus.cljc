;; Ported from raylib-jlt's helitorus, which is Michiel Borkent's (@borkdude)
;; examples/helitorus.clj in babashka/ffi:
;; https://github.com/babashka/ffi/blob/main/examples/helitorus.clj
;;
;; borkdude's program is the original. The geometry, the painter ordering, the
;; backface test, the lighting and the palette came from there and are
;; substantially unchanged. What changed here is how the program reaches raylib
;; and its input: the loop is inverted so the host owns it, the window's
;; constants come from the field, and the mouse and keys became touch. His own
;; header credits the figure one step further back, to a Scittle demo drawing it
;; to a 2D canvas.
;;
;; babashka/ffi is MIT licensed, Copyright (c) 2026 Michiel Borkent. See NOTICE
;; at the root of this repository for the notice in full.

(ns net.b12n.raylib-ios.scenes.helitorus
  "A helix wound around a torus, swept into a tube. Ported from raylib-jlt's
  `helitorus`, which is Michiel Borkent's `examples/helitorus.clj` in
  babashka/ffi (MIT licence); raylib-jlt's port is zlib-licensed work on top of
  it. This is an altered version of both.

  The surface is generated rather than modelled: walk the spine of a helix that
  itself follows a torus, build a local frame (tangent, normal, binormal) at
  each point, sweep a circle around that frame, and you have a tube.
  Projection, lighting and hidden-surface removal are done here rather than by
  raylib. `compute!` writes into primitive arrays that the draw side owns
  (`make-buffers`, allocated once for `max-nu` rings), so a frame of about ten
  thousand vertices allocates nothing. The ring `order` array survives the
  frame: consecutive frames differ by a small rotation, so it starts nearly
  sorted and the insertion sort over it is close to linear. The scene
  decides visibility itself, by the sign of a 2D cross product in screen space,
  and `emit-ring!` sends each kept quad in the winding rlgl keeps. Culling is
  left on: rlgl only queues vertices and draws them at a later flush, so
  switching culling off around the draw calls would not be in force when they
  are drawn, and a quad sent in the other winding would vanish.

  The original's constants are kept: 12 points round the tube, up to 900 along
  the spine, major radius 1.0, minor 0.30, tube radius 0.11, camera distance
  4.6, focal length 3.2, windings 3 to 24, detail 60 to 900, rot-x within 1.45
  radians and zoom 110 to 520. The original's 1000 by 560 window becomes the
  field: the centre of the figure is the field's centre and the zoom, which the
  state keeps in the original's units, is multiplied by `:scale`, the smaller
  of the field's width over 1000 and its height over 560.

  The original starts at 260 rings. This starts at 64 (`start-nu`), chosen from
  a measurement: at 260 the phone ran 19 fps, with compute 29.5 ms and draw 19.5
  ms, because jolt's interpreter is far slower than native on arithmetic. Both
  halves scale with the ring count. Measured at 64 on the phone (release build,
  iPhone 17 Pro, 2026-10-02): compute 7.7 ms and draw 5.0 ms at 59 to 60 fps. The
  limits are the original's 60 to 900, so \"detail +\" still reaches 260.

  Controls here:
  - A one-finger drag in the field turns the figure, in place of the mouse
    drag. Each frame the finger moves, the figure turns by the original's 0.008
    radians a pixel and remembers the motion as a spin (0.35 times the pixels
    of that frame). Both are divided by `:scale`, so the figure turns by the
    same angle for the same fraction of its own size on any screen. The press
    frame only anchors the drag. Released, the spin decays to the original's
    slow idle turn, by the original's `0.94` every 16 ms. The release position
    is never read.
  - A two-finger pinch zooms, in place of the wheel, by the change of finger
    distance (`net.b12n.raylib-ios.camera2d/pinch-step`'s ratio), held to the original's
    limits. The midpoint and the twist are ignored. Three field fingers do
    nothing, and a lifted finger ends the pinch with no jump. A finger on a
    button is no part of a drag or a pinch, and neither is one under Back.
  - Four buttons below the field replace the arrow keys, read from the input's
    `:touch-points` so a button and the field work at once. \"windings -\" and
    \"windings +\" (LEFT and RIGHT) change the windings by one on a press, and
    \"detail -\" and \"detail +\" (UP and DOWN) change the detail by 4 every
    frame they are held, as the original does with `key-down?`.
  - The original's help line is dropped, since the buttons name themselves, and
    a hint line names the two gestures. The HUD is two lines: the first is the
    original's fps, compute and draw milliseconds (`hud-line`), the second its
    windings and detail (`status-line`). The original's thousand vertices a
    second figure is dropped, and neither line uses `format`.

  The animation runs on `:delta-seconds`, as the original does on
  `get-frame-time`. The surface's colours are the three `palette-` arrays, and
  the other colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.camera2d :as cam]
            [net.b12n.raylib-ios.gesture :as gesture]))

(def nv "Points round the tube's cross-section. The original's." 12)
(def max-nu "Most rings along the spine. The original's." 900)
(def min-nu "Fewest rings along the spine. The original's." 60)
(def min-twists "Fewest windings. The original's." 3)
(def max-twists "Most windings. The original's." 24)
(def min-zoom "Smallest zoom, in the original's units." 110.0)
(def max-zoom "Largest zoom, in the original's units." 520.0)
(def rot-x-limit "Tilt limit in radians. The original's." 1.45)
(def detail-step "Rings added or removed per held frame. The original's." 4)
(def view-w "The original's window width, which the zoom scale is relative to." 1000.0)
(def view-h "The original's window height, which the zoom scale is relative to." 560.0)

(def ^:private r-major 1.0)
(def ^:private r-minor 0.30)
(def ^:private r-tube 0.11)
(def ^:private camera-dist 4.6)
(def ^:private focal 3.2)
(def ^:private tau (* 2.0 Math/PI))

(def background-colour [255 255 255 255])
(def hud-colour [55 65 81 255])
(def hint-colour [156 163 175 255])
(def button-colour [130 130 130 255])
(def button-held-colour [80 80 80 255])
(def button-label-colour [245 245 245 255])

(def button-labels
  "Each button's id and the label drawn on it, in left-to-right order."
  [[:windings-minus "windings -"] [:windings-plus "windings +"]
   [:detail-minus "detail -"] [:detail-plus "detail +"]])

(def start-nu
  "The starting ring count. The original's is 260; 64 is chosen from the phone
  measuring 19 fps at 260. At 64 it measured compute 7.7 ms and draw 5.0 ms at
  59 to 60 fps (release build, iPhone 17 Pro, 2026-10-02)."
  64)

(def hint "drag: turn - pinch: zoom")

;; --- geometry buffers --------------------------------------------------------
;; The phi grid round the cross-section is fixed; only its phase moves. The
;; hints are jolt's `double/1` and `int/1` there and the JVM's array classes
;; here, and either way they let the array reads and writes compile to stores.

(def ^#?(:jolt double/1 :default "[D") cos-phi (double-array nv))
(def ^#?(:jolt double/1 :default "[D") sin-phi (double-array nv))
;; kondo reads this .cljc as ClojureScript too, which has no aset-double.
#_{:clj-kondo/ignore [:unresolved-symbol]}
(dotimes [j nv]
  (let [a (/ (* tau j) nv)]
    (aset-double cos-phi j (Math/cos a))
    (aset-double sin-phi j (Math/sin a))))

(defn make-buffers
  "The arrays `compute!` writes into, for `max-nu` rings. Allocate once and keep:
  `:sx` and `:sy` are the projected points (ring `i`, point `j` at `i * nv + j`),
  `:shade` the palette index of each, `:ring-z` each ring's depth, `:order` the
  rings far to near and `:order-nu` the ring count `:order` was built for."
  []
  {:sx (double-array (* max-nu nv))
   :sy (double-array (* max-nu nv))
   :shade (int-array (* max-nu nv))
   :ring-z (double-array max-nu)
   :order (int-array max-nu)
   :order-nu (int-array 1)})

;; --- palette -----------------------------------------------------------------
;; hsl(337..349, 100..78%, 17..72%), precomputed into three int arrays so the
;; draw loop never converts a colour.

(defn hsl->rgb
  "`[r g b]`, each 0 to 255, for hue `h` in degrees and saturation and
  lightness `s` and `l` in 0 to 1."
  [h s l]
  (let [c (* (- 1.0 (Math/abs (- (* 2.0 l) 1.0))) s)
        h' (/ h 60.0)
        x (* c (- 1.0 (Math/abs (- (mod h' 2.0) 1.0))))
        m (- l (/ c 2.0))
        [r g b] (cond
                  (< h' 1) [c x 0.0]
                  (< h' 2) [x c 0.0]
                  (< h' 3) [0.0 c x]
                  (< h' 4) [0.0 x c]
                  (< h' 5) [x 0.0 c]
                  :else [c 0.0 x])]
    [(int (* 255 (+ r m))) (int (* 255 (+ g m))) (int (* 255 (+ b m)))]))

(def ^#?(:jolt int/1 :default "[I") palette-r (int-array 64))
(def ^#?(:jolt int/1 :default "[I") palette-g (int-array 64))
(def ^#?(:jolt int/1 :default "[I") palette-b (int-array 64))
#_{:clj-kondo/ignore [:unresolved-symbol]}
(dotimes [i 64]
  (let [t (/ i 63.0)
        [r g b] (hsl->rgb (+ 337.0 (* 12.0 t))
                          (/ (- 100.0 (* 22.0 t)) 100.0)
                          (/ (+ 17.0 (* 55.0 t)) 100.0))]
    (aset-int palette-r i r)
    (aset-int palette-g i g)
    (aset-int palette-b i b)))

;; --- one frame's worth of geometry ------------------------------------------

(defn- reset-order!
  [#?(:jolt ^int/1 order :default ^"[I" order)
   #?(:jolt ^int/1 order-nu :default ^"[I" order-nu) nu]
  (dotimes [i nu] (aset-int order i i))
  (aset-int order-nu 0 nu))

(defn- sort-rings!
  "Insertion sort of `order` by ring depth in `ring-z`, far rings first."
  [#?(:jolt ^int/1 order :default ^"[I" order)
   #?(:jolt ^double/1 ring-z :default ^"[D" ring-z) nu]
  (loop [i 1]
    (when (< i nu)
      (let [v (aget order i)
            vz (aget ring-z v)]
        (loop [k (dec i)]
          (if (and (>= k 0) (< (aget ring-z (aget order k)) vz))
            (do (aset-int order (inc k) (aget order k))
                (recur (dec k)))
            (aset-int order (inc k) v))))
      (recur (inc i)))))

(defn compute!
  "Project every vertex of the surface into `:sx` and `:sy` of `bufs` (from
  `make-buffers`), shade it into `:shade`, and sort the rings back to front in
  `:order`. `p` is `params`' map. Pure arithmetic and array writes; nothing here
  touches raylib."
  [bufs {:keys [nu twists rot-x rot-y zoom clock cx cy]}]
  (let [#?(:jolt ^double/1 sx :default ^"[D" sx) (:sx bufs)
        #?(:jolt ^double/1 sy :default ^"[D" sy) (:sy bufs)
        #?(:jolt ^int/1 shade :default ^"[I" shade) (:shade bufs)
        #?(:jolt ^double/1 ring-z :default ^"[D" ring-z) (:ring-z bufs)
        #?(:jolt ^int/1 order :default ^"[I" order) (:order bufs)
        #?(:jolt ^int/1 order-nu :default ^"[I" order-nu) (:order-nu bufs)
        nu (long nu)
        n (long twists)
        cx (double cx)
        cy (double cy)
        r (+ r-minor r-tube)
        zm (double zoom)
        ay (double rot-y)
        ax (double rot-x)
        cay (Math/cos ay) say (Math/sin ay)
        cax (Math/cos ax) sax (Math/sin ax)
        po (* (double clock) 1.1)
        cpo (Math/cos po)
        spo (Math/sin po)
        dtheta (/ tau nu)]
    (when (not= nu (aget order-nu 0)) (reset-order! order order-nu nu))
    (loop [i 0]
      (when (< i nu)
        (let [th (* i dtheta)
              wound (* n th)
              cn (Math/cos wound) sn (Math/sin wound)
              ct (Math/cos th) st (Math/sin th)
              xr (+ r-major (* r cn))
              ;; the point on the spine
              px (* xr ct) py (* xr st) pz (* r sn)
              ;; its tangent: the theta derivative, written out
              tx (- (* (- xr) st) (* n r ct sn))
              ty (- (* xr ct) (* n r st sn))
              tz (* n r cn)
              ;; the tangent of the flat circle under the spine
              bx (- st) by ct
              ;; tube normal: t cross b, normalised
              nx (- (* ty 0.0) (* tz by))
              ny (- (* tz bx) (* tx 0.0))
              nz (- (* tx by) (* ty bx))
              nl (Math/sqrt (+ (* nx nx) (* ny ny) (* nz nz)))
              nx (/ nx nl) ny (/ ny nl) nz (/ nz nl)
              ;; third axis: n cross t, normalised
              ux (- (* ny tz) (* nz ty))
              uy (- (* nz tx) (* nx tz))
              uz (- (* nx ty) (* ny tx))
              ul (Math/sqrt (+ (* ux ux) (* uy uy) (* uz uz)))
              ux (/ ux ul) uy (/ uy ul) uz (/ uz ul)
              base (* i nv)]
          ;; ring depth, for the painter's ordering
          (let [rz (+ (* px say) (* pz cay))]
            (aset-double ring-z i (- (* rz cax) (* py sax))))
          (loop [j 0]
            (when (< j nv)
              (let [c0 (aget cos-phi j)
                    s0 (aget sin-phi j)
                    cp (- (* c0 cpo) (* s0 spo))
                    sp (+ (* s0 cpo) (* c0 spo))
                    ;; the surface normal, then the point on the surface
                    vx (+ (* ux cp) (* nx sp))
                    vy (+ (* uy cp) (* ny sp))
                    vz (+ (* uz cp) (* nz sp))
                    wx (+ px (* r-tube vx))
                    wy (+ py (* r-tube vy))
                    wz (+ pz (* r-tube vz))
                    ;; rotate about Y, then about X
                    x1 (- (* wx cay) (* wz say))
                    z1 (+ (* wx say) (* wz cay))
                    y2 (+ (* wy cax) (* z1 sax))
                    z2 (- (* z1 cax) (* wy sax))
                    ;; the same rotation on the normal
                    m1 (- (* vx cay) (* vz say))
                    q1 (+ (* vx say) (* vz cay))
                    m2 (+ (* vy cax) (* q1 sax))
                    q2 (- (* q1 cax) (* vy sax))
                    ;; perspective divide
                    k (/ (* zm focal) (+ focal camera-dist z2))
                    ;; diffuse, plus a rim term
                    lum (+ (* 0.60 (max 0.0 (+ (* m1 -0.40) (* m2 -0.62) (* q2 -0.68))))
                           (* 0.28 (max 0.0 (- q2)))
                           0.12)
                    o (+ base j)]
                (aset-double sx o (+ cx (* x1 k)))
                (aset-double sy o (- cy (* y2 k)))
                (aset-int shade o (min 63 (max 0 (int (* 63.0 lum))))))
              (recur (inc j))))
          (recur (inc i)))))
    (sort-rings! order ring-z nu)))

(defn emit-ring!
  "Send ring `i` of the tube as flat-shaded quads, two triangles each, by calling
  `(colour! r g b)` once per quad and `(vertex! x y)` six times. A quad is kept
  when the 2D cross product of its first two edges (a, b, c) is positive, which
  is the side of the tube facing us.

  Every triangle goes out with a NEGATIVE cross product. That is the winding
  rlgl keeps: in screen space, with y growing downward, it culls the positive
  ones (`net.b12n.raylib-ios.host/draw-triangle`). The first triangle is a, c, b. The second
  is c, d of the quad's far edge, and on a quad seen nearly edge-on it can run
  the other way round from the first, so it goes out as a, d, c or as a, c, d,
  whichever is negative; the original draws both halves with culling off, and
  this draws them both too. The order matters because rlgl only queues these
  vertices and draws the batch at a later flush, with culling back on, so the
  draw cannot turn culling off around them. `vertex!` and `colour!` are injected
  so the order is testable without raylib."
  [colour! vertex!
   #?(:jolt ^double/1 sx :default ^"[D" sx)
   #?(:jolt ^double/1 sy :default ^"[D" sy)
   #?(:jolt ^int/1 shade :default ^"[I" shade)
   #?(:jolt ^int/1 pr :default ^"[I" pr)
   #?(:jolt ^int/1 pg :default ^"[I" pg)
   #?(:jolt ^int/1 pb :default ^"[I" pb)
   i nu]
  (let [i2 (let [x (inc i)] (if (= x nu) 0 x))
        b1 (* i nv)
        b2 (* i2 nv)]
    (loop [j 0]
      (when (< j nv)
        (let [j2 (let [x (inc j)] (if (= x nv) 0 x))
              a (+ b1 j) b (+ b1 j2)
              c (+ b2 j2) d (+ b2 j)
              xa (aget sx a) ya (aget sy a)
              xb (aget sx b) yb (aget sy b)
              xc (aget sx c) yc (aget sy c)]
          (when (pos? (- (* (- xb xa) (- yc ya))
                         (* (- yb ya) (- xc xa))))
            (let [s (aget shade a)]
              (colour! (aget pr s) (aget pg s) (aget pb s)))
            (vertex! xa ya)
            (vertex! xc yc)
            (vertex! xb yb)
            (let [xd (aget sx d)
                  yd (aget sy d)]
              (vertex! xa ya)
              (if (pos? (- (* (- xc xa) (- yd ya))
                           (* (- yc ya) (- xd xa))))
                (do (vertex! xd yd)
                    (vertex! xc yc))
                (do (vertex! xc yc)
                    (vertex! xd yd))))))
        (recur (inc j))))))

;; --- text -------------------------------------------------------------------

(defn- round-long [x] (long (Math/floor (+ 0.5 x))))

(defn- tenths
  "`x` to one decimal, rounded half up, held to 0 to 999.9, without `format`."
  [x]
  (let [n (round-long (* 10.0 (max 0.0 (min 999.9 x))))]
    (str (quot n 10) "." (rem n 10))))

(defn hud-line
  "The first HUD line: frames a second and the milliseconds a frame spent in
  `compute!` and in submitting. The fps is held to 0 to 9999 and each time to 0
  to 999.9, so `(hud-line 9999.0 999.0 999.0)` is the widest it can be."
  [fps compute-ms draw-ms]
  (str "fps " (round-long (max 0.0 (min 9999.0 fps)))
       " | compute " (tenths compute-ms)
       " ms | draw " (tenths draw-ms) " ms"))

(defn status-line
  "The second HUD line: `state`'s windings and detail."
  [{:keys [twists nu]}]
  (str "windings " twists " | detail " nu))

;; --- layout -----------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure. `:buttons` are the
  four `{:id :rect}` along the bottom, `:field` is `[x y w h]`, the full width
  between the three text lines and the buttons, `:centre` is the field's centre
  and `:scale` the zoom multiplier (see the namespace docstring). `:size` and
  `:pad` are the text size and gap, `:text-y` the first line's top and `:step`
  the distance between lines."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        step (+ size (quot pad 2))
        gap (* 0.02 side)
        bh (* 0.14 side)
        by (- h gap bh)
        bw (/ (- w (* (inc (count button-labels)) gap)) (count button-labels))
        text-y (+ back-bottom pad)
        ftop (+ text-y (* 3 step) pad)
        fh (- by gap ftop)]
    {:w w
     :h h
     :size size
     :pad pad
     :step step
     :text-y text-y
     :buttons (vec (map-indexed
                    (fn [i [id _]]
                      {:id id
                       :rect [(+ gap (* i (+ bw gap))) by bw bh]})
                    button-labels))
     :field [0.0 (double ftop) (double w) (double fh)]
     :centre [(* 0.5 w) (+ ftop (* 0.5 fh))]
     :scale (min (/ w view-w) (/ fh view-h))}))

(defn dimensions
  "`geometry` plus the text. `:lines` are the HUD line, the status line and the
  hint, each `{:s :x :y :size}`, at one size cut back from `geometry`'s when the
  widest would cover more than 0.92 of the width. The HUD and status lines hold
  the widest text they can print, which is what is measured, so a draw replaces
  them with `hud-line` and `status-line`. Each of `:buttons` gains `:label`,
  `:label-size`, `:label-x` and `:label-y`, the size cut back until the widest
  label fits inside a button. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w h size pad step text-y]
         :as geo} (geometry metrics)
        texts [(hud-line 9999.0 999.0 999.0)
               (status-line {:twists max-twists
                             :nu max-nu})
               hint]
        widest (apply max (map #(measure % 100) texts))
        size (max 8 (min size (int (/ (* 0.92 w 100.0) widest))))
        lines (vec (map-indexed (fn [i s] {:s s
                                           :x pad
                                           :y (int (+ text-y (* i step)))
                                           :size size})
                                texts))
        [_ _ bw bh] (:rect (first (:buttons geo)))
        wide-label (apply max (map #(measure (second %) 100) button-labels))
        label-size (max 8 (min (max 16 (int (* 0.034 (min w h))))
                               (int bh)
                               (int (/ (* 0.9 bw 100.0) wide-label))))]
    (assoc geo
           :text-size size
           :lines lines
           :buttons (vec (map (fn [{[bx by] :rect
                                    :as b} [_ label]]
                                (assoc b
                                       :label label
                                       :label-size label-size
                                       :label-x (int (+ bx (* 0.5 (- bw (measure label label-size)))))
                                       :label-y (int (+ by (* 0.5 (- bh label-size))))))
                              (:buttons geo) button-labels)))))

(defn params
  "The map `compute!` takes for `state` laid out in `dims` (from `geometry` or
  `dimensions`): the state's figure, its zoom times the layout's scale, and the
  field's centre."
  [state dims]
  (let [[cx cy] (:centre dims)]
    {:nu (:nu state)
     :twists (:twists state)
     :rot-x (:rot-x state)
     :rot-y (:rot-y state)
     :zoom (* (:zoom state) (:scale dims))
     :clock (:clock state)
     :cx cx
     :cy cy}))

;; --- input ------------------------------------------------------------------

(defn held-buttons
  "The set of button ids with any of the input's `:touch-points` inside."
  [{:keys [buttons]} input]
  (let [pts (:touch-points input)]
    (into #{}
          (keep (fn [{:keys [id rect]}]
                  (when (some (fn [p] (gesture/in-rect? rect p)) pts) id)))
          buttons)))

(defn- field-points
  "The touch points that belong to the figure: not on a button, not under Back."
  [{:keys [buttons]} input]
  (vec (remove (fn [p]
                 (or (gesture/in-back-region? p)
                     (some (fn [{:keys [rect]}] (gesture/in-rect? rect p)) buttons)))
               (:touch-points input))))

(defn- clamp-tilt [x] (max (- rot-x-limit) (min rot-x-limit x)))
(defn- clamp-zoom [z] (max min-zoom (min max-zoom z)))

(defn- drag
  "While a finger is held, it turns the figure and its motion is remembered as
  a spin. `dx` and `dy` are in pixels, `scale` the layout's. The press frame
  (no `anchor`) only anchors."
  [s anchor [x y] scale]
  (let [s (assoc s :drag [x y])]
    (if anchor
      (let [dx (- x (first anchor))
            dy (- y (second anchor))]
        (-> s
            (update :rot-y + (/ (* dx 0.008) scale))
            (update :rot-x #(clamp-tilt (+ % (/ (* dy 0.008) scale))))
            (assoc :vel-y (/ (* dx 0.35) scale)
                   :vel-x (/ (* dy 0.35) scale))))
      s)))

(defn- coast
  "Released, the remembered spin decays to a slow idle turn."
  [s dt]
  (let [decay (Math/pow 0.94 (/ dt 0.016))]
    (assoc s
           :drag nil
           :vel-y (+ (* (:vel-y s) decay) (* 0.16 (- 1.0 decay)))
           :vel-x (* (:vel-x s) decay))))

(defn advance
  "One frame. One finger in the field drags (`drag`), anything else coasts
  (`coast`), and the figure then turns by its spin over `:delta-seconds`. Two
  field fingers pinch: the zoom follows the change of finger distance, held to
  the limits, and the first frame of a pinch only records it. Windings change on
  the press of a button, detail on every frame one is held. A rotation of the
  phone drops the drag and the pinch, whose pixels are the old screen's."
  [state input]
  (let [metrics (:metrics input)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc state :screen screen :drag nil :pinch nil)
                state)
        dims (geometry metrics)
        held (held-buttons dims input)
        pressed? (fn [id] (and (contains? held id) (not (contains? (:held state) id))))
        pts (field-points dims input)
        dt (max 0.0 (double (or (:delta-seconds input) 0.0)))
        {now :pinch
         step :step} (cam/pinch-frame (:pinch state) pts)
        zoom (if step
               (clamp-zoom (* (:zoom state) (:ratio step)))
               (:zoom state))
        s (if (= 1 (count pts))
            (drag state (:drag state) (first pts) (:scale dims))
            (coast state dt))
        s (-> s
              (assoc :zoom zoom
                     :pinch now
                     :held held)
              (update :clock + dt)
              (update :rot-y + (* (:vel-y s) dt))
              (update :rot-x #(clamp-tilt (+ % (* (:vel-x s) dt)))))]
    (cond-> s
      (pressed? :windings-plus) (update :twists #(min max-twists (inc %)))
      (pressed? :windings-minus) (update :twists #(max min-twists (dec %)))
      (contains? held :detail-plus) (update :nu #(min max-nu (+ % detail-step)))
      (contains? held :detail-minus) (update :nu #(max min-nu (- % detail-step))))))

(defn- init [{:keys [metrics]}]
  [{:nu start-nu
    :twists 14
    :rot-x 0.55
    :rot-y 0.0
    :vel-x 0.0
    :vel-y 0.45
    :zoom 250.0
    :clock 0.0
    :drag nil
    :pinch nil
    :held #{}
    :screen (:screen metrics)}
   [[:scene/init :helitorus]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :helitorus]]])

(defn scene []
  {:id :helitorus
   :title "Helitorus"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
