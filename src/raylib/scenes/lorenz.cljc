(ns raylib.scenes.lorenz
  "The Lorenz attractor, ported from raylib-jlt's `lorenz_attractor`.

  Three coupled equations, integrated forward one small step at a time:

      dx/dt = sigma (y - x)
      dy/dt = x (rho - z) - y
      dz/dt = x y - beta z

  The trajectory never repeats and never escapes. It swaps between two lobes at
  intervals no amount of precision will let you predict, which is the whole
  reason the shape is famous.

  Two things the original does that a phone cannot. It uses raylib's 3D camera,
  and this host binds no camera, so the orbit is done by hand: rotate about Y,
  then divide by depth. It also keeps a 4000-point trail, which is four times
  what docs/guide/performance-on-a-phone.md says fits in a frame here.

  Input-free. The original maps rho to the arrow keys; this holds it at 28.0,
  the value that gives the butterfly, and lets the camera do the moving.")

(def default-seed 90210)

;; The trail is the whole cost: one segment per point, and every point is
;; re-projected every frame because the camera orbits. That is the difference
;; from lsystem, which holds 59 fps at 1488 segments precomputed once.
;;
;; Swept on an iPhone 17 Pro, reading raylib's own GetFPS with the probe seam
;; calling it every frame:
;;
;;   trail   1200  1000  800  600  500  480  460  440  420  400  300  200
;;   fps       25    23   29   32   56   59   59   58   58   59   59   59
;;
;; Pinned at vsync to 480, slipping at 500, and falling away steeply past 600.
;; 450 sits inside the flat region with room for a phone that has warmed up.
;; At dt 0.006 that is 2.7 time units of trajectory. Measured over ten seconds
;; from the default seed, 499 frames of 600 show a window that straddles both
;; lobes, so the butterfly reads most of the time and warm handles the opening.
;;
;; Readings above 600 are noisy, +/- 30% between sweeps of the same value, so
;; treat the steep end as indicative and the flat end as solid.
(def default-trail-length 450)

;; An atom rather than a def, so the ceiling can be found on other hardware from
;; a REPL without a rebuild. flowfield wanted this and settled for a def; one
;; sweep here paid for the difference immediately.
(defonce trail-length (atom default-trail-length))
;; long enough to do both jobs: discard the opening transient (~900 steps, one
;; long swing into a lobe that says nothing about the attractor) AND fill the
;; trail, so the per-frame cost is flat from the first frame rather than ramping
;; up over the first twenty seconds. The original leaves the trail filling,
;; because at 4000 points it would otherwise warm up for a noticeable beat.
(defn warmup [] (+ @trail-length 900))
(def steps-per-frame 6)

;; The original's step, kept. Integrating the same 60 time units at several
;; step sizes and comparing the attractor's own bounds against a near-exact
;; dt 0.001 reference: 0.006 tracks it (z 7.3..45.9 against 4.6..45.9), while
;; 0.009 and 0.012 inflate it (z to 50.0 and 53.2). So a bigger step is not a
;; way to buy more trajectory per point here. The trail length is the only knob.
(def dt 0.006)
(def sigma 10.0)
(def rho 28.0)
(def beta (/ 8.0 3.0))

(defn step
  "One Euler step. The step is small enough that Euler holds up, and the
  attractor's shape is robust to what error remains."
  [[x y z]]
  [(+ x (* dt sigma (- y x)))
   (+ y (* dt (- (* x (- rho z)) y)))
   (+ z (* dt (- (* x y) (* beta z))))])

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn seed-point
  "A starting point near the attractor, from the seeded generator rather than
  raylib's GetRandomValue, so the scene replays identically."
  [seed]
  (let [s1 (next-random seed)
        s2 (next-random s1)]
    [[(- (/ (double (mod s1 400)) 100.0) 2.0) 0.0 (+ 5.0 (/ (double (mod s2 2000)) 100.0))]
     s2]))

(defn spans-both-lobes?
  "Does this window of the trajectory show both wings, or just one? The lobes
  are separated by the sign of x, so the test is that the window straddles it."
  [points]
  (let [xs (map first points)]
    (and (neg? (apply min xs)) (pos? (apply max xs)))))

(defn warm
  "Run forward from a fresh seed before the first frame, then keep going until
  the visible window shows both wings.

  The first part discards the opening transient, one long swing into a lobe
  that says nothing about the attractor. The second exists because a 450-point
  window is short enough to sit inside a single lobe: measured over ten seconds
  from this seed, 499 frames of 600 straddle the divide and the rest show one
  wing. That is fine while it runs, since the trail sweeps through both within
  a second or two, but it means the scene can OPEN on half a butterfly. Running
  on until the window spans both costs nothing at init and nothing per frame.

  The cap is a safety net rather than a real bound. It has never been reached."
  [seed]
  (let [[p0 seed'] (seed-point seed)
        cap (+ (warmup) 4000)]
    (loop [i 0 p p0 out []]
      (let [window (when (>= i (warmup)) (vec (take-last @trail-length out)))]
        (if (or (and window (spans-both-lobes? window)) (> i cap))
          [(or window (vec (take-last @trail-length out))) seed']
          (let [p' (step p)]
            (recur (inc i) p' (conj out p'))))))))

(defn advance
  "Integrate `steps-per-frame` steps, then trim the tail back to the current
  trail length so the per-frame cost stays flat however long the scene runs.

  The trim takes the whole excess in one subvec rather than one point per step.
  Dropping one at a time is the same thing while the length is fixed, but it
  makes a REPL change to trail-length take hundreds of frames to converge, which
  is useless when the point of the atom is to sweep for the ceiling."
  [points]
  (let [grown (loop [i 0 pts points]
                (if (= i steps-per-frame)
                  pts
                  (recur (inc i) (conj pts (step (peek pts))))))
        excess (- (count grown) @trail-length)]
    (if (pos? excess) (subvec grown excess) grown)))

(defn camera
  "The orbiting camera, as the four numbers the projection needs. Computed once
  a frame rather than per point: it is the only trigonometry in the scene."
  [metrics t]
  (let [[width height] (:screen metrics)
        w (double width) h (double height)]
    {:cos (Math/cos t) :sin (Math/sin t)
     :cx (* w 0.5) :cy (* h 0.5)
     ;; focal length in pixels, chosen so the attractor fills most of the
     ;; short side whichever way the phone is held
     :f (* (min w h) 1.35)
     :distance 26.0
     :scale 0.42
     ;; the attractor sits around z in [0,50], so lift it to centre on screen
     :lift 25.0}))

(defn project
  "One Lorenz point to screen coordinates, or nil when it falls behind the
  camera. Rotate about Y, then divide by depth."
  [{:keys [cos sin cx cy f distance scale lift]} [x y z]]
  (let [px (* scale x)
        py (* scale (- z lift))
        pz (* scale y)
        rx (- (* px cos) (* pz sin))
        rz (+ (* px sin) (* pz cos))
        d (- distance rz)]
    (when (> d 0.5)
      (let [k (/ f d)]
        [(+ cx (* rx k)) (- cy (* py k))]))))

(defn trail-colour
  "[r g b] for a point `age` of the way along the trail, 0.0 at the tail. A
  bright cyan-to-pink ramp rather than a fade to black, because a one-pixel
  line at low luminance simply disappears on a dark ground."
  [age]
  [(int (+ 90 (* 165 age)))
   (int (- 200 (* 130 age)))
   (int (- 255 (* 105 age)))])

(defn- init [_input]
  (let [[points seed] (warm default-seed)]
    [{:points points :seed seed :t 0.0} [[:scene/init :lorenz]]]))

(defn- update-scene [state _input]
  [(-> state
       (update :points advance)
       (update :t + 0.004))
   []])

(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :lorenz]]])

(defn scene []
  {:id :lorenz :title "Lorenz"
   :init init :update update-scene :draw draw :dispose dispose})
