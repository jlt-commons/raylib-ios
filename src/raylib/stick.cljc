(ns raylib.stick
  "Whose finger is a thumb-stick. Pure, so every scene that steers with a
  relative stick can share one rule and one set of tests.

  The rule:
  - a stick starts only on a fresh press, a finger that was not down in the
    frame before, inside the scene's start area and not on a button;
  - it follows only that finger;
  - it ends when that finger is gone, or slides where `:free?` says a stick
    cannot be;
  - it never moves to another finger, so a resting finger is never adopted when
    the stick's own lifts.

  Identity comes from the touch ids the host provides at `[:touches :ids]`, in
  the same order as `:touch-points` (`poc.raylib.diagnostics/normalize-input`,
  read the same way by `raylib.scenes.multitouch`). When the ids are missing
  or do not match the points one to one, which is what a synthetic `tap!`
  gives, identity is guessed from nearness: a fresh finger is one further than
  `gesture/slop` from every finger of the frame before, and a held stick
  follows the nearest free finger within `follow-fraction` of the shorter side.

  A stick is `{:centre [x y] :at [x y] :id id-or-nil}`. The scene keeps the
  previous frame in `prev`, `{:pts [...] :ids [...] :n count}`, with `:pts` and
  `:ids` as `frame` had them, and passes this frame as `frame`:

  - `:points`, `:ids` as `ids-of` returns them (nil when unusable);
  - `:metrics`, for the slop and the follow bound;
  - `:press?`, whether the pointer phase is `:press`;
  - `:free?`, a predicate on a point, true where a stick may be;
  - `:start?`, a predicate on a point, true where a stick may begin."
  (:require [raylib.gesture :as gesture]))

(def follow-fraction
  "How far a stick's finger may travel in one frame without ids, as a fraction
  of the shorter side. estimate: 0.3. Basis: a hard 200 px reversal on a 1206 px
  wide phone is 0.17, and two thumbs rest at least 0.4 of the shorter side
  apart (0.4 is splitscreen's half-width restart bound)."
  0.3)

(defn ids-of
  "The touch ids of `input` when there is exactly one per point of `points`,
  else nil."
  [input points]
  (let [ids (vec (get-in input [:touches :ids]))]
    (when (and (seq ids) (= (count ids) (count points)))
      ids)))

(defn- d2 [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (+ (* dx dx) (* dy dy))))

(defn- slop2 [metrics]
  (let [s (gesture/slop metrics)] (* s s)))

(defn- reach2 [metrics]
  (let [r (* follow-fraction (apply min (:screen metrics)))] (* r r)))

(defn follow
  "`owner` (a map with `:at`, and `:id` when it began with one) moved to its own
  finger in `frame`, or nil when that finger is gone or not `:free?`. It never
  returns another finger's position: with ids it reads the point of its id,
  without them it takes the nearest free point within `follow-fraction` of the
  shorter side of where it was."
  [owner {:keys [points ids metrics free?]}]
  (when owner
    (if (and (:id owner) ids)
      (when-let [i (first (keep-indexed (fn [k id] (when (= id (:id owner)) k)) ids))]
        (let [p (nth points i)]
          (when (free? p) (assoc owner :at p))))
      (let [r2 (reach2 metrics)
            near (filterv #(and (free? %) (<= (d2 % (:at owner)) r2)) points)]
        (when (seq near)
          (assoc owner :at (apply min-key #(d2 % (:at owner)) near)))))))

(defn fresh
  "The fingers of `frame` that were not down in `prev`, in order, as
  `{:at point :id id-or-nil}`. Only after a press, or while a finger was
  already down, does anything count: a finger that was down when the scene
  opened, or when a rotation cleared `prev`, is never fresh."
  [{:keys [points ids metrics press?]} prev]
  (let [prev-n (count (:pts prev))
        by-id? (and ids (seq (:ids prev)) (= (count (:ids prev)) prev-n))
        gate? (if by-id?
                (or press? (pos? prev-n))
                (or press? (and (pos? prev-n) (> (count points) prev-n))))
        old-ids (set (:ids prev))
        s2 (slop2 metrics)]
    (when gate?
      (into []
            (keep-indexed
             (fn [k p]
               (when (if by-id?
                       (not (contains? old-ids (nth ids k)))
                       (every? #(> (d2 p %) s2) (:pts prev)))
                 {:at p
                  :id (when ids (nth ids k))})))
            points))))

(defn begin
  "A stick on the first fresh finger of `frame` that passes `:start?`, or nil."
  [frame prev]
  (when-let [{:keys [at id]} (first (filter #(and ((:free? frame) (:at %))
                                                  ((:start? frame) (:at %)))
                                            (fresh frame prev)))]
    {:centre at
     :at at
     :id id}))

(defn begin-owners
  "`owners`, a map that may hold `:look` and `:stick`, with a look or a stick
  begun on each fresh finger (`fresh`, as `fresh` returns it) whose
  `(region point)` is `:look` or `:stick`, for each one `owners` lacks. A finger
  that is already an owner's is skipped, and one where `region` gives nil (under
  Back, outside the field) is nothing. The first fresh finger in a region wins.
  A look is `{:at :id}`, a stick `{:centre :at :id}`."
  [{:keys [look stick]
    :as owners} region fresh]
  (reduce (fn [acc {:keys [at id]}]
            (if (or (= at (:at look)) (= at (:at stick)))
              acc
              (case (region at)
                :look (if (:look acc) acc (assoc acc :look {:at at
                                                            :id id}))
                :stick (if (:stick acc) acc (assoc acc :stick {:centre at
                                                               :at at
                                                               :id id}))
                acc)))
          owners
          fresh))

(defn follow-pair
  "`look` and `stick` (either may be nil) each moved to its own finger by
  `follow`, as `[look' stick']`. Without ids both could claim the one nearest
  point. Then the owner that moved less keeps it and the other follows from what
  is left."
  [look stick frame]
  (let [l (follow look frame)
        s (follow stick frame)]
    (if (and l s (= (:at l) (:at s)))
      (let [without (fn [at] (update frame :points #(filterv (fn [q] (not= q at)) %)))]
        (if (<= (d2 (:at l) (:at look)) (d2 (:at s) (:at stick)))
          [l (follow stick (without (:at l)))]
          [(follow look (without (:at s))) s]))
      [l s])))

(defn next-stick
  "The stick after this frame. A held `stick` follows its own finger and ends
  when that is gone (`follow`). Without one, `begin` may start one. Nothing
  else does."
  [stick prev frame]
  (cond
    (empty? (:points frame)) nil
    stick (follow stick frame)
    :else (begin frame prev)))
