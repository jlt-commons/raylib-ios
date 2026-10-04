(ns net.b12n.raylib-ios.scenes.survivors.draw
  "The draw-scene! method for the `:survivors` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.survivors :as surv]))

(defmethod draw-scene! :survivors [_ {:keys [hero enemies bullets gems time kills over? stick]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack surv/background-colour))
        dims (surv/dimensions m)
        measure host-measure
        {:keys [enemy-r bullet-r hero-r gem-side hp-bar xp-bar lv hint]
         kills-row :kills
         time-row :time} dims
        text (fn [{:keys [x y size]} s colour] (rl/draw-text s (int x) (int y) size (pack colour)))
        bar (fn [[x y w h] frac colour]
              (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack surv/bar-back-colour))
              (rl/draw-rectangle (int x) (int y) (int (* w (max 0.0 (min 1.0 frac)))) (int h) (pack colour)))]
    (doseq [g gems]
      (rl/draw-rectangle (int (- (:x g) (/ gem-side 2))) (int (- (:y g) (/ gem-side 2)))
                         (int gem-side) (int gem-side) (pack surv/gem-colour)))
    (doseq [e enemies]
      (rl/draw-circle (int (:x e)) (int (:y e)) (float enemy-r) (pack surv/enemy-colour)))
    (doseq [b bullets]
      (rl/draw-circle (int (:x b)) (int (:y b)) (float bullet-r) (pack surv/bullet-colour)))
    (rl/draw-circle (int (:x hero)) (int (:y hero)) (float hero-r)
                    (pack (if (pos? (:hurt-cd hero)) surv/hurt-colour surv/hero-colour)))
    (when stick
      (let [[cx cy] (:centre stick)
            [kx ky] (surv/knob dims stick)
            r (:stick-r dims)]
        (rl/draw-ring (int cx) (int cy) (- r (max 2.0 (* r 0.06))) r 0 360 48 (pack surv/stick-colour))
        (rl/draw-circle (int kx) (int ky) (float (:knob-r dims)) (pack surv/knob-colour))))
    (bar hp-bar (/ (max 0 (:hp hero)) (double surv/hero-hp)) surv/hp-colour)
    (bar xp-bar (/ (:xp hero) (double (surv/xp-needed (:level hero)))) surv/xp-colour)
    (text lv (surv/lv-line (:level hero)) surv/text-colour)
    (text kills-row (surv/kills-line kills) surv/text-colour)
    (text time-row (surv/time-line time) surv/hint-colour)
    (text hint surv/hint-line surv/hint-colour)
    (when over?
      (let [{:keys [msg msg2 msg3]} dims
            centred (fn [{:keys [y size]} s colour]
                      (rl/draw-text s (surv/centred-x dims size s measure) (int y) size (pack colour)))]
        (centred msg surv/over-line surv/enemy-colour)
        (centred msg2 (surv/summary-line time kills) surv/text-colour)
        (centred msg3 surv/restart-line surv/hint-colour)))))
