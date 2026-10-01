(ns raylib.scenes.strings
  "Bouncing text particles you can throw, cut, shatter and glue. Ported from
  raylib-jlt's `strings-management`, which is raylib's
  `text_strings_management` example. A sentence is one particle, and every
  particle bounces round the arena losing a tenth of its speed at a wall and a
  hundredth to friction on every frame.

  The original is driven by a mouse and a keyboard, and each action has a touch
  replacement or is dropped:

  - Drag with the left button and release to throw: drag a particle and lift,
    and it flies off with the velocity of the last four `:down` frames. The
    release position is never read, because on the device it is whatever the
    hardware held last.
  - Right-click to cut in half: a long press on a particle cuts it in half.
  - SHIFT and right-click to shatter into characters: the \"shatter\" button arms
    a one-shot, and the next tap on a particle shatters it into single
    characters. It is armed until a particle is hit or the button is tapped
    again.
  - Middle-click to shake everything: the \"shake\" button.
  - CTRL while dragging one particle over another to glue them: release a
    dragged particle while it overlaps another and the two are glued, the
    dragged text first. Only a drag that left the tap slop glues, so a tap on
    two particles that happen to overlap does nothing. A glue that would make a
    particle wider than the arena, or pass the 100 particle cap, is refused.
  - Keys 1 to 6 to reset the sentence through a case transform: the \"case\"
    button steps through the six in the original's order, plain, upper, lower,
    Pascal, snake and camel. Its label names the one the next tap applies. The
    plain and lower sentences are the same text, as in the original.
  - Typing a character to split the lone particle on it is dropped. There is no
    keyboard, and a touch has no character to offer. See `dropped`.

  A tap under Back is ignored because it belongs to the host. A swipe is not
  checked against Back, as in the other swipe scenes, but nothing here reads one:
  a drag only takes hold of a particle where it is pressed, and the arena starts
  below Back, so a drag that starts under Back finds no particle.

  The string operations are the original's `subs`, `str` and `clojure.string`
  versions of raylib's TextSubtext, TextSplit and TextTo* helpers. Timing is
  `:delta-seconds`, clamped at 0 below and not above, as the original uses
  `get-frame-time` unclamped. Friction is applied once per frame, not per
  second, as the original does. Speeds are the original's pixels a second scaled
  by `u`, the geometric mean of the two axes against 800 by 450, since the
  motion is free in two dimensions. Randomness is an LCG whose high bits are
  used, so a scene replays.

  Deviations: the opening sentence is centred in the arena where the original
  starts its top-left corner at the window centre, which shoved it against the
  wall on its first frame. The text size is cut back until the widest of the
  six case sentences fits the arena width (UPPER is wider than the plain one), and the arena sits below Back and above the buttons in
  place of the whole window. A grabbed particle is held inside the arena. The
  hint is one line in place of six.

  Text is laid out in `dimensions` and in the particles' own sizes, both from an
  injected `measure` `(fn [s size] -> px)`. Unlike the other text scenes the
  particles' sizes live in the state, because the walls and the hit tests need
  them, so `advance` takes `measure` too: the host puts raylib's own text width
  on every input as `:measure`, and the tests pass an estimate to `scene`. A
  particle's width is measured when it is made and kept, so the draw method
  needs no cache. Colours are `[r g b a]` vectors, so the namespace stays pure."
  (:require [clojure.string :as str]
            [raylib.gesture :as gesture]))

(def friction "The original's speed kept each frame." 0.99)
(def elasticity "The original's speed kept at a wall." 0.9)
(def max-particles "The original's cap." 100)

(def dropped
  "What has no touch replacement. Typing a character to split the lone
  sentence on it needs a keyboard, so it is dropped."
  "typing a character to split the sentence on it: there is no keyboard")

(def background-colour [245 245 245 255])
(def border-colour [0 0 0 255])
(def text-colour [0 0 0 255])
(def hint-colour [130 130 130 255])
(def count-colour [0 0 0 255])
(def button-colour [200 200 200 255])
(def armed-colour [190 33 55 255])
(def button-label-colour [80 80 80 255])
(def armed-label-colour [255 255 255 255])
(def sentence-colour [245 245 245 255])

(def hint "DRAG: THROW - HOLD: CUT - DROP ON ONE: GLUE")
(def count-prefix "TEXT PARTICLE COUNT: ")
(def shatter-label "shatter")
(def shake-label "shake")

(def ^:private sentence "raylib => fun videogames programming!")
(def ^:private snake-sentence "raylib_fun_videogames_programming")
(def ^:private pascal-sentence "RaylibFunVideogamesProgramming")

;; --- the string helpers ----------------------------------------------------

(defn- words [s] (remove str/blank? (str/split s #"_")))

(defn- capitalize-first [s] (str (str/upper-case (subs s 0 1)) (subs s 1)))

(defn- to-pascal [s] (apply str (map capitalize-first (words s))))

(defn- to-camel
  [s]
  (let [[head & tail] (words s)]
    (apply str head (map capitalize-first tail))))

(defn- upper-letter?
  [c]
  (let [s (str c)]
    (and (= s (str/upper-case s)) (not= s (str/lower-case s)))))

(defn- to-snake
  "RaylibFunX -> raylib_fun_x. Every capital starts a new word, and the leading
  underscore the first capital would add is stripped."
  [s]
  (let [marked (->> s
                    (mapcat (fn [c] (if (upper-letter? c) ["_" (str c)] [(str c)])))
                    (apply str)
                    str/lower-case)]
    (if (str/starts-with? marked "_") (subs marked 1) marked)))

(def case-names ["plain" "UPPER" "lower" "Pascal" "snake" "camel"])

(def case-sentences
  "The six sentences the case button sets, in the original's key order 1 to 6."
  [sentence
   (str/upper-case sentence)
   (str/lower-case sentence)
   (to-pascal snake-sentence)
   (to-snake pascal-sentence)
   (to-camel snake-sentence)])

(defn case-label
  "The button's label while `i` is the next transform to apply."
  [i]
  (str "case: " (nth case-names i)))

(def widest-count-line (str count-prefix max-particles))

;; --- the LCG ---------------------------------------------------------------

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- roll
  "`[v seed']`: an int in [0, n) from the LCG's high bits, since its low bit
  alternates on every step."
  [seed n]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) n) seed']))

(defn- between
  "`[v seed']`: an int in [lo, hi], both ends, as `GetRandomValue`."
  [seed lo hi]
  (let [[v seed'] (roll seed (inc (- hi lo)))]
    [(+ lo v) seed']))

(def ^:private start-seed 20261001)

;; --- layout ----------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen` that needs no text measure, which is all
  the touch handling uses. `:arena` is `[x y w h]`, below Back and the hint and
  above the count line and the buttons. `:shatter :shake :case` are the buttons
  as `[x y w h]` along the bottom. `:u` is the speed scale, the geometric mean
  of the width over 800 and the height over 450, and `:ui` is the nominal text
  size of the hint, count and labels."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        u (Math/sqrt (* (/ w 800.0) (/ h 450.0)))
        ui (max 20 (int (* 0.03 (min w h))))
        bh (* 2.4 ui)
        by (- h bh (* 0.02 h))
        bw (* 0.3 w)
        gap (* 0.03 w)
        bx (* 0.5 (- w (* 3 bw) (* 2 gap)))
        hint-y (+ top (* 0.01 h))
        count-y (- by (* 1.4 ui))
        arena-y (+ hint-y (* 1.5 ui))]
    {:w w
     :h h
     :u u
     :ui ui
     :hint-y hint-y
     :count-y count-y
     :arena [0.0 arena-y (double w) (- count-y (* 0.2 ui) arena-y)]
     :shatter [bx by bw bh]
     :shake [(+ bx bw gap) by bw bh]
     :case [(+ bx (* 2 (+ bw gap))) by bw bh]}))

(defn- fit-size
  "The largest size up to `size` at which `s` is no wider than `limit`."
  [measure s size limit]
  (max 8 (min size (int (/ (* limit 100.0) (max 1 (measure s 100)))))))

(defn dimensions
  "`geometry` plus the text. `:size-info` is `{:size :pad :u}` for the
  particles: the original's 30 scaled by `u`, cut back until the widest of the six
  case sentences and its padding fit 0.9 of the width, so the case button never
  pushes one off the glass, with the padding a sixth of
  the size. `:hint`, `:count-line` (the widest count) and the labels
  `:shatter-label`, `:shake-label` and `:case-label` (the widest case label) are
  each `{:s :x :y :size}`, and `:lines` has all six so a test can check they
  fit. The text size is cut back from `:ui` until the hint and the count fit
  0.92 of the width and each label fits its button. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w u ui hint-y count-y shatter shake]
         :as geo} (geometry metrics)
        case-rect (:case geo)
        widest-case (apply max-key #(measure % 100) (map case-label (range 6)))
        [_ _ bw bh] shatter
        limit (* 0.9 bw)
        size (min (fit-size measure hint ui (* 0.92 w))
                  (fit-size measure widest-count-line ui (* 0.92 w))
                  (fit-size measure shatter-label ui limit)
                  (fit-size measure shake-label ui limit)
                  (fit-size measure widest-case ui limit))
        widest-sentence (apply max (map #(measure % 100) case-sentences))
        psize (let [full (+ (/ widest-sentence 100.0) (/ 2.0 6.0))]
                (max 8 (min (int (* 30 u)) (int (/ (* 0.9 w) full)))))
        line (fn [s x y] {:s s
                          :x (int x)
                          :y (int y)
                          :size size})
        label (fn [s [bx by bw' _]]
                (line s (+ bx (* 0.5 (- bw' (measure s size)))) (+ by (* 0.5 (- bh size)))))
        lines {:hint (line hint (* 0.04 w) hint-y)
               :count-line (line widest-count-line (* 0.04 w) count-y)
               :shatter-label (label shatter-label shatter)
               :shake-label (label shake-label shake)
               :case-label (label widest-case case-rect)}]
    (assoc (merge geo lines)
           :size-info {:size psize
                       :pad (/ psize 6.0)
                       :u u}
           :text-size size
           :lines (vec (cons (:hint lines) (vals (dissoc lines :hint)))))))

(defn centred-x
  "The x that centres `s` in a button `[x y w h]`, for a label that changes
  while the layout does not."
  [[bx _ bw _] s size measure]
  (int (+ bx (* 0.5 (- bw (measure s size))))))

;; --- particles -------------------------------------------------------------

(defn- clamp [v lo hi] (max lo (min hi v)))

(defn- in-arena
  "`p` with its corner moved the least that puts it inside `arena`."
  [p [ax ay aw ah]]
  (assoc p
         :x (clamp (:x p) ax (max ax (- (+ ax aw) (:w p))))
         :y (clamp (:y p) ay (max ay (- (+ ay ah) (:h p))))))

(defn- spawn
  "`[particle seed']`: `text` at `[x y]`, sized by `ctx`'s measure, with the
  original's random velocity of up to 200 px a second a side, scaled by `u`. A
  nil `colour` is random."
  [{:keys [size pad u measure arena]} text x y colour seed]
  (let [[vx seed] (between seed -200 200)
        [vy seed] (between seed -200 200)
        [colour seed] (if colour
                        [colour seed]
                        (let [[r seed] (between seed 0 255)
                              [g seed] (between seed 0 255)
                              [b seed] (between seed 0 255)]
                          [[r g b 255] seed]))]
    [(in-arena {:text text
                :x (double x)
                :y (double y)
                :w (+ (measure text size) (* 2.0 pad))
                :h (+ size (* 2.0 pad))
                :vx (* u vx)
                :vy (* u vy)
                :color colour}
               arena)
     seed]))

(defn- opening
  "`[particles seed']`: the one sentence, centred in the arena."
  [ctx text seed]
  (let [[ax ay aw ah] (:arena ctx)
        {:keys [size pad measure]} ctx
        w (+ (measure text size) (* 2.0 pad))
        h (+ size (* 2.0 pad))
        [p seed] (spawn ctx text (+ ax (* 0.5 (- aw w))) (+ ay (* 0.5 (- ah h))) sentence-colour seed)]
    [[p] seed]))

(defn- inside?
  [{:keys [x y w h]} [px py]]
  (and (<= x px (+ x w)) (<= y py (+ y h))))

(defn- overlaps?
  [a b]
  (and (< (:x a) (+ (:x b) (:w b))) (> (+ (:x a) (:w a)) (:x b))
       (< (:y a) (+ (:y b) (:h b))) (> (+ (:y a) (:h a)) (:y b))))

(defn- topmost-at
  "The index of the last particle under `pt`, the one drawn on top."
  [particles pt]
  (->> (map-indexed vector particles)
       (filter (fn [[_ p]] (inside? p pt)))
       last
       first))

(defn- step
  "One frame of free flight: move, bounce off the four sides of the arena
  losing a tenth of the speed each time, then shed a hundredth to friction."
  [{:keys [x y w h vx vy]
    :as p} dt [ax ay aw ah]]
  (let [x (+ x (* vx dt))
        y (+ y (* vy dt))
        right (+ ax aw)
        bottom (+ ay ah)
        [x vx] (cond
                 (>= (+ x w) right) [(- right w) (* -1.0 vx elasticity)]
                 (<= x ax) [(double ax) (* -1.0 vx elasticity)]
                 :else [x vx])
        [y vy] (cond
                 (>= (+ y h) bottom) [(- bottom h) (* -1.0 vy elasticity)]
                 (<= y ay) [(double ay) (* -1.0 vy elasticity)]
                 :else [y vy])]
    (assoc p :x x :y y :vx (* vx friction) :vy (* vy friction))))

(defn- slice
  "`[pieces seed']`: `p` cut into chunks of `n` characters, each landing where
  its characters sat along the original box."
  [ctx {:keys [text x y w]} n seed]
  (let [len (count text)]
    (reduce (fn [[out seed] i]
              (let [[piece seed] (spawn ctx (subs text i (min len (+ i n)))
                                        (+ x (* i (/ w len))) y nil seed)]
                [(conj out piece) seed]))
            [[] seed]
            (range 0 len n))))

(defn- without [particles i] (into (subvec particles 0 i) (subvec particles (inc i))))

(defn- break-up
  "`[particles seed' broke?]`: the particle at `i` swapped for the pieces `n`
  characters long, unless there is nothing to break or the budget would be
  overrun, which is the original's `count + pieces < max` check."
  [ctx particles i n seed]
  (let [[pieces seed'] (slice ctx (nth particles i) n seed)]
    (if (and (> (count pieces) 1)
             (< (+ (count particles) (count pieces)) max-particles))
      [(into (without particles i) pieces) seed' true]
      [particles seed false])))

(defn- glue
  "`particles` with the one at `i` merged into the first other one it overlaps,
  the held text first, or unchanged when none does, the cap is reached or the
  result would be wider than the arena."
  [{:keys [size pad measure arena]} particles i]
  (let [held (nth particles i)
        target (first (keep-indexed (fn [j p] (when (and (not= j i) (overlaps? held p)) j))
                                    particles))]
    (if (and target (< (count particles) max-particles))
      (let [text (str (:text held) (:text (nth particles target)))
            w (+ (measure text size) (* 2.0 pad))]
        (if (<= w (nth arena 2))
          (conj (vec (keep-indexed (fn [j p] (when-not (#{i target} j) p)) particles))
                (in-arena (assoc held :text text :w w :color sentence-colour) arena))
          particles))
      particles)))

(defn- throw-velocity
  "`[vx vy]` from the held particle's last `:down` samples `[x y dt]`: how far it
  moved over the time those frames took."
  [samples]
  (let [[x0 y0] (first samples)
        [x1 y1] (peek samples)
        t (reduce + 0.0 (map #(nth % 2) (rest samples)))]
    (if (and (> (count samples) 1) (pos? t))
      [(/ (- x1 x0) t) (/ (- y1 y0) t)]
      [0.0 0.0])))

(defn- dist
  [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(def ^:private sample-count "How many `:down` frames a throw is read from." 4)

;; --- state -----------------------------------------------------------------

(defn- context
  [state measure]
  (let [dims (:dims state)]
    (assoc (:size-info dims) :measure measure :arena (:arena dims))))

(defn- fresh
  "A new state for `metrics`: the plain sentence alone, so the button's first
  tap is the first transform that changes it, upper."
  [metrics measure seed]
  (let [dims (dimensions metrics measure)
        ctx (assoc (:size-info dims) :measure measure :arena (:arena dims))
        [particles seed] (opening ctx (first case-sentences) seed)]
    {:dims dims
     :size-info (:size-info dims)
     :screen [(:w dims) (:h dims)]
     :particles particles
     :grab nil
     :shatter? false
     :case 1
     :seed seed
     :gesture gesture/idle}))

(defn- pin
  "The held particle on the finger at `pos`, kept inside the arena, and its
  sample for the throw."
  [state pos dt metrics]
  (let [{:keys [i ox oy start samples moved?]} (:grab state)
        [fx fy] pos
        p (in-arena (assoc (nth (:particles state) i) :x (- fx ox) :y (- fy oy))
                    (get-in state [:dims :arena]))]
    (-> state
        (assoc-in [:particles i] p)
        (assoc :grab {:i i
                      :ox ox
                      :oy oy
                      :start start
                      :moved? (or moved? (> (dist start pos) (gesture/slop metrics)))
                      :samples (vec (take-last sample-count (conj samples [(:x p) (:y p) dt])))}))))

(defn- release
  "Let go of the held particle: it keeps the velocity of its last `:down`
  frames, and glues to what it rests on when the finger really dragged it."
  [state measure]
  (let [{:keys [i samples moved?]} (:grab state)
        [vx vy] (throw-velocity samples)
        state (update-in state [:particles i] assoc :vx vx :vy vy)
        state (if moved?
                (update state :particles #(glue (context state measure) % i))
                state)]
    (assoc state :grab nil)))

(defn- begin-grab
  [state pos]
  (let [i (topmost-at (:particles state) pos)]
    (assoc state
           :grab (when i
                   (let [{:keys [x y]} (nth (:particles state) i)]
                     {:i i
                      :ox (- (first pos) x)
                      :oy (- (second pos) y)
                      :start pos
                      :moved? false
                      :samples [[x y 0.0]]})))))

(defn- cut
  "Cut the particle at `i` into chunks of `n` characters, dropping any grab when
  it was broken."
  [state i n measure]
  (let [[particles seed broke?] (break-up (context state measure) (:particles state) i n (:seed state))]
    (cond-> (assoc state :particles particles :seed seed)
      broke? (assoc :grab nil))))

(defn- shake
  "Every particle not held gets a random velocity of up to 2000 px a second a
  side, scaled by `u`."
  [state]
  (let [u (get-in state [:size-info :u])
        held (get-in state [:grab :i])
        [particles seed]
        (reduce (fn [[out seed] [i p]]
                  (if (= i held)
                    [(conj out p) seed]
                    (let [[vx seed] (between seed -2000 2000)
                          [vy seed] (between seed -2000 2000)]
                      [(conj out (assoc p :vx (* u vx) :vy (* u vy))) seed])))
                [[] (:seed state)]
                (map-indexed vector (:particles state)))]
    (assoc state :particles particles :seed seed)))

(defn- next-case
  [state measure]
  (let [i (:case state)
        ctx (context state measure)
        [particles seed] (opening ctx (nth case-sentences i) (:seed state))]
    (assoc state
           :particles particles
           :seed seed
           :grab nil
           :case (mod (inc i) (count case-sentences)))))

(defn- on-event
  "What a `track` event asks for. A tap under Back asks for nothing."
  [state event measure]
  (let [geo (:dims state)]
    (case (:type event)
      :long-press (if-let [i (or (get-in state [:grab :i]) (topmost-at (:particles state) (:at event)))]
                    (cut state i (max 1 (quot (count (:text (nth (:particles state) i))) 2)) measure)
                    state)
      :tap (let [at (:at event)]
             (cond
               (gesture/in-back-region? at) state
               (gesture/in-rect? (:shatter geo) at) (update state :shatter? not)
               (gesture/in-rect? (:shake geo) at) (shake state)
               (gesture/in-rect? (:case geo) at) (next-case state measure)
               (:shatter? state) (if-let [i (topmost-at (:particles state) at)]
                                   (-> (cut state i 1 measure)
                                       (assoc :shatter? false))
                                   state)
               :else state))
      state)))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (a different `:screen`) starts over, since the layout is in pixels.
  Otherwise the order is the original's: a press takes hold of the particle
  under it, a `:down` pins that particle to the finger, a release throws it and
  glues it, then the event the gesture revealed acts, and finally every
  particle that is not held flies for `:delta-seconds`, clamped at 0. `measure`
  is `(fn [s size] -> px)`."
  [state input measure]
  (let [metrics (:metrics input)
        [g event] (gesture/track (:gesture state) input)
        geo (geometry metrics)]
    (if (not= [(:w geo) (:h geo)] (:screen state))
      (assoc (fresh metrics measure (:seed state)) :gesture g)
      (let [{:keys [phase position]} (:pointer input)
            dt (max 0.0 (double (or (:delta-seconds input) 0.0)))
            state (assoc state :gesture g)
            state (case phase
                    :press (if (and position (not (gesture/in-back-region? position)))
                             (begin-grab state position)
                             (assoc state :grab nil))
                    :down (if (and position (:grab state)) (pin state position dt metrics) state)
                    :release (if (:grab state) (release state measure) state)
                    state)
            state (if event (on-event state event measure) state)
            held (get-in state [:grab :i])
            arena (get-in state [:dims :arena])]
        (assoc state
               :particles (vec (map-indexed (fn [i p] (if (= i held) p (step p dt arena)))
                                            (:particles state))))))))

(defn default-measure
  "The stand-in `measure` for a host that supplies none, which the tests do not
  use because they pass their own: 0.6 of the size per character."
  [s size]
  (* 0.6 size (count s)))

(defn- init [measure {:keys [metrics]
                      :as input}]
  [(fresh metrics (or (:measure input) measure) start-seed)
   [[:scene/init :strings]]])

(defn scene
  "The scene. The host supplies raylib's own text width as `:measure` on every
  input, `(fn [s size] -> px)`, because only it can measure the real font. The
  optional argument is what a host with none gets: `default-measure`, or
  whatever a test passes."
  ([] (scene default-measure))
  ([measure]
   {:id :strings
    :title "Strings Management"
    :init (partial init measure)
    :update (fn [state input] [(advance state input (or (:measure input) measure)) []])
    :draw (fn [state _] [state []])
    :dispose (fn [state] [state [[:scene/dispose :strings]]])}))
