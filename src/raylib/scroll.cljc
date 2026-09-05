(ns raylib.scroll
  "Vertical scrolling for the gallery's card list, and the tap-versus-drag rule
  that has to come with it.

  `poc.raylib.gallery-ui/gallery-layout` sizes cards to FIT: it divides the
  height it is given by the number of rows, so more scenes means shorter cards
  rather than a taller list. At twenty-seven scenes each card is about 140
  pixels tall and the grid is unreadable. That file is one of the six verified
  byte-identical against the notebooks and does not change.

  It does not need to. `below-the-safe-area` already hands it a screen SHORTER
  than the real one and shifts the result down; this hands it a screen TALLER
  than the real one and shifts the result up. The pure function lays out a
  comfortable grid for a screen that does not exist, and the host scrolls a
  window over it.

  The second half is that a list which scrolls cannot open a card on press. A
  press is now the start of a gesture that might be a drag, so the decision
  moves to the release and is only a tap if the finger stayed put."
  (:require [clojure.string]))

(def min-card-height
  "The shortest a card may be before the list starts scrolling instead.

  A fraction of the screen's shorter side rather than a pixel count, so it means
  the same thing on a phone and on a tablet.

  0.16 of 1206 is about 193 pixels, which puts ten rows in a portrait viewport
  and leaves a card comfortably bigger than the text on it. The first value
  tried was 0.115, which is 138 pixels: that is what the unscrolled layout was
  already producing at thirty-two scenes, so it scrolled without fixing the
  cramping that prompted the work."
  0.16)

(def tap-slop
  "How far a finger may travel and still count as a tap, as a fraction of the
  shorter side. Below this a scroll is indistinguishable from a shaky finger."
  0.018)

(defn content-height
  "The height the pure layout should be given for `n` cards.

  Never less than the viewport, because a short list must not be stretched: two
  cards on a tall screen should be two cards, not two half-screen slabs."
  [{:keys [width height]} sizes n columns]
  (let [{:keys [margin title-size body-size line-gap]} sizes
        gap (max 12 (quot margin 2))
        rows (if (zero? n) 0 (quot (+ n (dec columns)) columns))
        card-h (max 1 (long (* min-card-height (min width height))))
        cards-y (+ margin title-size (* 2 line-gap))
        footer (+ margin (* 2 line-gap) body-size)]
    (max height (+ cards-y footer (* rows card-h) (* (max 0 (dec rows)) gap)))))

(defn max-scroll
  "How far the list can travel. Zero when everything already fits."
  [content viewport]
  (max 0 (- content viewport)))

(defn clamp
  "Scroll offset held inside its range, so the list cannot be flung into
  emptiness at either end.

  Returns a long. The offset is added to rectangles the pure layout has already
  rounded to ints, and a fractional one turns a card's :y into a double, which
  raylib's DrawRectangle binding rejects at the FFI boundary. Rounding here
  rather than at the call site keeps every caller honest."
  [offset content viewport]
  (long (min (max-scroll content viewport) (max 0 offset))))

(defn begin-drag
  "State at the moment a finger lands.

  The whole start point is kept, not just its y. A tap is hit-tested at where
  the gesture STARTED rather than where it ended, which is both more faithful to
  what the finger meant and more robust: the release frame does not always carry
  a useful position, since raylib reports the touch count falling to zero and
  the coordinates are whatever the hardware last had. Travel is bounded by the
  slop anyway, so the two points are within a few pixels by construction."
  [scroll [x y]]
  {:from [x y] :from-y (double y) :from-scroll scroll :travel 0.0})

(defn drag-to
  "Update a drag as the finger moves.

  `:travel` is the furthest the finger has been from where it started, not the
  distance from the last frame. A finger that wanders away and comes back has
  still scrolled, and should not then count as a tap on whatever it lands on."
  [drag [_ y]]
  (let [dy (- (double y) (:from-y drag))]
    (assoc drag :travel (max (:travel drag) (abs dy)))))

(defn scroll-for
  "The offset a drag implies, clamped.

  Dragging up moves the content up, which means increasing the offset, so the
  list follows the finger rather than opposing it."
  [drag [_ y] content viewport]
  (clamp (+ (:from-scroll drag) (- (:from-y drag) (double y))) content viewport))

(defn tap-point
  "Where a tap happened: the gesture's start, not its end. See `begin-drag`."
  [drag]
  (:from drag))

(defn tap?
  "Whether a finished gesture should count as a tap.

  A list that scrolls cannot open a card on press, because at press time there
  is no way to know whether a drag is starting. So the decision moves to the
  release, and only a finger that stayed within the slop counts."
  [drag shorter-side]
  (and (some? drag)
       (<= (:travel drag) (* tap-slop shorter-side))))

(defn thumb
  "The scrollbar thumb as [y height], or nil when there is nothing to scroll.

  Proportional to how much of the content is showing, with a floor so a very
  long list still leaves something visible to grab."
  [scroll content viewport]
  (when (pos? (max-scroll content viewport))
    (let [h (max (* 0.06 viewport) (* viewport (/ (double viewport) content)))
          travel (- viewport h)
          y (* travel (/ (double scroll) (max-scroll content viewport)))]
      [y h])))
