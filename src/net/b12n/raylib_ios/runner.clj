(ns net.b12n.raylib-ios.runner
  "One scene, full screen, on the iOS host loop: no menu and no Back.

  `(run! (hello/scene))` is the whole of an app that shows a single scene.
  It drives the scene through the same frame machinery as the gallery shell,
  because both call net.b12n.raylib-ios.frame: the touch sampling, the safe
  area, the scissored and translated draw region, and the pure lifecycle in
  net.b12n.raylib-ios.gallery.core. The per-scene texture lifecycle is the
  gallery's too, so the scene's textures are freed when it is left.

  The scene's `draw-scene!` method is not loaded here. The app namespace
  requires the scene's own draw namespace, which is what keeps a single-scene
  binary from carrying all the others.

  A scene that throws, in :init, :update or its draw method, is not retried.
  Its message is printed and drawn on screen, and it stays there. There is no
  menu to return to, and a loop that re-ran a failing scene every frame would
  bury the message. Textures are freed on the next frame, as in the gallery.

  Dev builds that want an nREPL use net.b12n.raylib-ios.runner.live, which is
  a separate namespace so a release binary never loads jolt.nrepl."
  (:refer-clojure :exclude [run!])
  (:require [clojure.string :as str]
            [net.b12n.raylib-ios.frame :as frame]
            [net.b12n.raylib-ios.gallery.core :as core]
            [net.b12n.raylib-ios.gallery.draw-util :as du :refer [color]]
            [net.b12n.raylib-ios.gallery.ui :as ui]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.texture :as texture]))

(defn wrap-lines
  "`text` as lines of at most `n` characters, broken at whitespace. A word
  longer than a line is cut, since an exception message can be one long path."
  [text n]
  (let [n (max 1 n)
        words (mapcat (fn [w] (map #(apply str %) (partition-all n w)))
                      (remove str/blank? (str/split (str text) #"\s+")))]
    (->> words
         (reduce (fn [lines w]
                   (let [current (peek lines)]
                     (if (and current (<= (+ (count current) 1 (count w)) n))
                       (conj (pop lines) (str current " " w))
                       (conj lines w))))
                 [])
         (#(if (seq %) % [""])))))

(defn- init [{:keys [scale inset-top]}]
  {:k scale
   :insets {:top inset-top}
   :touches 0
   :failure nil
   :gstate core/initial-gallery-state})

(defn- fail
  "The state after the scene threw: the message kept for the screen, and said on
  the console. The pure state is dropped without calling :dispose, which is
  scene code that has just failed."
  [s id e]
  (let [message (or (ex-message e) (str e))]
    (println (str "runner: " (pr-str id) " failed: " message))
    (assoc s :failure message :gstate core/initial-gallery-state)))

(defn- step
  "Open the scene on the first frame and run it after that. A scene asking to
  close means nothing here, as in the gallery. Returns the state, failed if
  the scene threw."
  [{:keys [gstate]
    :as s} registry id scene-input]
  (frame/guarded
   (fn [e] (fail s id e))
   (fn []
     (assoc s :gstate
            (-> (if (= :scene (:mode gstate))
                  (core/run-frame registry gstate scene-input)
                  (core/open-scene registry gstate id scene-input))
                frame/drain-events!
                frame/ignore-close)))))

(defn- step-unless-failed
  "`step`, except that a failed scene stays failed. `fail` resets the pure
  state, and stepping that would open the scene again, so the scene would be
  retried every frame."
  [s registry id scene-input]
  (if (:failure s)
    s
    (step s registry id scene-input)))

(defn- draw-failure!
  "The scene's id and message, wrapped to the safe region, on the presentation's
  background. The scene's textures go with it."
  [{:keys [k failure]} id safe]
  (texture/enter! nil)
  (let [p (ui/live-presentation)
        size (max 16 (int (* 9 k)))
        margin size
        per-line (int (/ (- (:width safe) (* 2 margin)) (* 0.55 size)))]
    (rl/clear-background (color (:background p)))
    (rl/draw-text (str (pr-str id) " failed") (+ (:x safe) margin) (+ (:y safe) margin)
                  size (color (:accent p)))
    (doseq [[i line] (map-indexed vector (wrap-lines failure per-line))]
      (rl/draw-text line (+ (:x safe) margin)
                    (+ (:y safe) margin (* (+ i 2) (+ size (quot size 3))))
                    size rl/DARKGRAY))))

(defn- frame
  "One frame: sample, step the pure lifecycle, then draw the scene in the safe
  region, or the failure if there is one."
  [registry id {:keys [k insets touches]
                :as s}]
  (let [insets (frame/resolve-insets k insets)
        input (frame/poll-input touches)
        m (:metrics input)
        safe (frame/safe-region (:screen m) insets)
        scene-m (frame/scene-metrics m safe)
        s (-> s
              (assoc :insets insets
                     :touches (get-in input [:touches :count]))
              (step-unless-failed registry id
                                  (frame/scene-input input safe scene-m false)))]
    (if (:failure s)
      (do (draw-failure! s id safe) s)
      ;; every frame, so the scene's textures are freed the frame after it fails
      (do (texture/enter! id)
          (frame/draw-in-safe-region!
           (color (:background (ui/live-presentation))) safe
           (fn []
             (frame/guarded
              (fn [e] (fail s id e))
              (fn []
                (du/draw-scene! id (:scene-state (:gstate s))
                                {:k k
                                 :m scene-m
                                 :safe safe})
                s))))))))

(defn app
  "The map net.b12n.raylib-ios.host/run! takes for `scene-map`: :title, :init
  and :frame. Exposed so a test can drive the frames without UIKit."
  [scene-map]
  (let [id (:id scene-map)
        registry (core/make-registry [scene-map])]
    {:title (:title scene-map)
     :init init
     :frame (fn [s] (frame registry id s))}))

(defn run!
  "Hand `scene-map`, what a scene's `(scene)` fn returns, to UIKit and show it
  full screen. Does not return. The scene's draw namespace must already be
  loaded, since `draw-scene!` is a multimethod on its id."
  [scene-map]
  (rl/run! (app scene-map)))
