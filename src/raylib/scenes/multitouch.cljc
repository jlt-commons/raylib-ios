(ns raylib.scenes.multitouch
  "Every finger on the glass, drawn where it is. Ported from raylib-jlt's
  `input_multitouch`.

  The original says plainly what it could not do: raylib's per-index
  GetTouchPosition returns a Vector2 by value, the desktop binding set had no
  path for that, so it read point zero through the scalar GetTouchX and GetTouchY
  pair. Its own docstring calls that the honest limit, the ids of every point
  visible and the coordinates of only the first.

  Here it is the whole scene. A phone is the machine that actually has the
  hardware the example was written about, `raylib.host/touch-position` returns
  the by-value Vector2, and the gallery hands every active point down under
  `:touch-points`. So this draws all of them, each with its raylib id and its own
  colour, and a trail behind each one.

  Trails are keyed by touch id rather than by position in the list. raylib does
  not promise ids are 0..n-1, and when a middle finger lifts the remaining points
  shift down an index. Keying by index would make two fingers swap trails on the
  lift, which looks like a glitch and is really a bookkeeping mistake."
  (:require [clojure.string]))

(def trail-length 26)

;; Distinct hues so two fingers are never the same colour. raylib's own palette,
;; in the order the example's own text and rings use it.
(def palette
  [[0 121 241] [230 41 55] [0 158 47] [255 161 0]
   [200 122 255] [255 109 194] [0 178 191] [255 203 0]])

(defn assign-colours
  "Give every live touch a palette slot, keeping what an existing one already had.

  Colour comes from a slot rather than from the id, and the id is the obvious
  thing to reach for. Two findings killed that.

  First, iOS touch ids are derived from object pointers, so they are 8-byte
  aligned. Four real fingers on an iPhone 17 Pro reported 809313472, 809313920,
  809314368 and 809317952: every one divisible by 8, so `(mod id 8)` sent all
  four to the same entry and every finger drew in the same blue. Shifting the
  alignment away does not save it either, because those ids step by 448, which
  is still a multiple of 8.

  Second, and the reason no hash fixes this: with eight colours and four
  fingers, a perfectly uniform hash still collides. The chance all four land in
  different slots is 8/8 x 7/8 x 6/8 x 5/8, which is 41%. A murmur3 finalizer
  measured 41% here, so it was already ideal and still wrong more often than
  not.

  Assigning the lowest free slot instead makes distinctness exact for up to
  `(count palette)` simultaneous touches, which is more than any phone reports.
  A finger keeps its slot for as long as it is down, and slots are recycled only
  when it lifts."
  [previous live-ids]
  (let [kept (select-keys previous live-ids)
        taken (set (vals kept))]
    (first (reduce (fn [[acc used] id]
                     (if (contains? acc id)
                       [acc used]
                       (let [slot (first (remove used (range (count palette))))]
                         [(assoc acc id slot) (conj used slot)])))
                   [kept taken]
                   live-ids))))

(defn colour-for
  "The palette entry for a slot."
  [slot]
  (nth palette (mod (or slot 0) (count palette))))

(defn dimensions
  "Sizes for this screen.

  The radii are a fraction of the width rather than a pixel count. The first
  version used a flat 46, which is under 8% of a 1206-pixel screen and read as a
  small dot in a lot of empty space. These are about double that."
  [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     ;; About twice the first version, which used a flat 46 px. Bigger than
     ;; this and four fingers held naturally start to overlap into one blob.
     :touch-radius (* 0.075 w)
     :dot-radius (* 0.011 w)
     :centre-radius (* 0.009 w)
     :label-lift (* 0.10 w)
     :label-size (max 18 (int (* 0.030 (min w h))))
     :count-size (max 24 (int (* 0.046 (min w h))))}))

(defn- push-trail [trail point]
  (vec (take-last trail-length (conj (or trail []) point))))

(defn advance
  "Fold this frame's touches into the per-id trails.

  Points arrive as `[[x y] ...]` with `:touch-ids` alongside in the same order.
  A point with no id falls back to its index, which is what the synthetic
  tap path produces."
  [state input]
  (let [pts (vec (:touch-points input))
        ids (vec (get-in input [:touches :ids]))
        live (into {} (map-indexed (fn [i p] [(nth ids i i) p]) pts))
        trails (reduce-kv (fn [acc id p] (update acc id push-trail p))
                          (select-keys (:trails state) (keys live))
                          live)]
    (assoc state
           :trails trails
           :live live
           :colours (assign-colours (:colours state) (keys live))
           :peak (max (:peak state 0) (count live))
           :t (inc (:t state 0)))))

(defn- init [_] [{:trails {} :live {} :colours {} :peak 0 :t 0} [[:scene/init :multitouch]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :multitouch]]])

(defn scene []
  {:id :multitouch :title "Multitouch"
   :init init :update update-scene :draw draw :dispose dispose})
