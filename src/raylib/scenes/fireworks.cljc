(ns raylib.scenes.fireworks
  "Rockets and particles under gravity, ported from raylib-jlt's `fireworks`.

  A rocket launches every launch-interval frames, rises, and at the top of its
  arc becomes forty particles that fall and fade. Pure: raylib's
  GetRandomValue becomes the seeded LCG the other scenes use, so a seed
  reproduces a display exactly and the whole thing runs on a build host.")

(def default-seed 8161)
(def gravity-ratio 0.00027)   ; the original's 0.07 of a 450-tall window
(def particles-per-burst 40)
(def launch-interval 35)
(def fade-per-frame 0.012)

(def palette
  [[255 80 80] [80 180 255] [255 220 80] [180 120 255] [120 255 160]])

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)
        h (double height)]
    {:width (double width) :height h
     :gravity (* h gravity-ratio)
     ;; rocket speeds and particle spread scale with the screen, so a tall
     ;; phone does not get a burst the size of a coin
     :rise-min (* h -0.019) :rise-max (* h -0.027)
     :spread (* h 0.0075)
     :rocket-radius (max 2 (int (* h 0.004)))
     :particle-radius (max 1 (int (* h 0.0026)))}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- pick [seed lo hi]
  (let [seed' (next-random seed)]
    [(+ lo (* (- hi lo) (/ (double (mod seed' 100000)) 100000.0))) seed']))

(defn- new-rocket [dims seed]
  (let [{:keys [width rise-min rise-max]} dims
        [x s1] (pick seed (* 0.12 width) (* 0.88 width))
        [vy s2] (pick s1 rise-max rise-min)
        [c s3] (pick s2 0 (count palette))]
    [{:x x :y (:height dims) :vy vy :color (nth palette (min (int c) (dec (count palette))))} s3]))

(defn- explode [dims {:keys [x y color]} seed]
  (loop [i 0 seed seed out []]
    (if (= i particles-per-burst)
      [out seed]
      (let [[a s1] (pick seed 0.0 6.28318)
            [sp s2] (pick s1 (* 0.15 (:spread dims)) (:spread dims))]
        (recur (inc i) s2
               (conj out {:x x :y y
                          :vx (* sp (Math/cos a)) :vy (* sp (Math/sin a))
                          :life 1.0 :color color}))))))

(defn advance [state metrics]
  (let [dims (dimensions metrics)
        g (:gravity dims)
        {:keys [frame rockets parts seed]} state
        [rockets seed] (if (zero? (mod frame launch-interval))
                         (let [[r s] (new-rocket dims seed)] [(conj rockets r) s])
                         [rockets seed])
        rockets (mapv (fn [r] (-> r (update :y + (:vy r)) (update :vy + g))) rockets)
        spent (filterv (fn [r] (>= (:vy r) 0)) rockets)
        rockets (filterv (fn [r] (< (:vy r) 0)) rockets)
        [burst seed] (reduce (fn [[acc s] r]
                               (let [[ps s'] (explode dims r s)] [(into acc ps) s']))
                             [[] seed] spent)
        parts (into (mapv (fn [p] (-> p (update :x + (:vx p)) (update :y + (:vy p))
                                      (update :vy + g) (update :life - fade-per-frame)))
                          parts)
                    burst)]
    (assoc state :frame (inc frame) :seed seed :rockets rockets
           :parts (filterv (fn [p] (pos? (:life p))) parts))))

(defn- init [_input]
  [{:frame 0 :seed default-seed :rockets [] :parts []}
   [[:scene/init :fireworks]]])

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fireworks]]])

(defn scene []
  {:id :fireworks :title "Fireworks"
   :init init :update update-scene :draw draw :dispose dispose})
