(ns net.b12n.raylib-ios.scenes.kaleidoscope.draw
  "The draw-scene! method for the `:kaleidoscope` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.kaleidoscope :as kal]))

(defmethod draw-scene! :kaleidoscope [_ {:keys [trail]} {:keys [m]}]
  ;; Everything on the per-line path is inlined here on purpose, and the
  ;; measurements are in docs/guide/performance-on-a-phone.md. Briefly: this
  ;; scene draws twelve lines per trail segment, and calling a helper that
  ;; returns [x y] allocated two vectors per line. Removing that took 1068
  ;; lines from 22 fps to 47. kaleidoscope/place is the readable statement of
  ;; the same transform, kept for the tests.
  (rl/clear-background (rl/rgba 12 12 20 255))
  (let [{:keys [cx cy]} (kal/dimensions m)
        rots (kal/rotations)
        rn (count rots)
        n (count trail)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth trail (dec i))
              b (nth trail i)
              ax (double (nth a 0)) ay (double (nth a 1))
              bx (double (nth b 0)) by (double (nth b 1))
              age (/ (double i) n)
              packed (rl/rgba (int (* 255 age)) 120 (int (* 255 (- 1.0 age))) 255)]
          (loop [r 0]
            (when (< r rn)
              (let [rot (nth rots r)
                    ca (double (nth rot 0))
                    sa (double (nth rot 1))
                    ax (if (nth rot 2) (- ax) ax)
                    bx (if (nth rot 2) (- bx) bx)]
                (rl/draw-line (int (+ cx (- (* ax ca) (* ay sa)))) (int (+ cy (+ (* ax sa) (* ay ca))))
                              (int (+ cx (- (* bx ca) (* by sa)))) (int (+ cy (+ (* bx sa) (* by ca))))
                              packed))
              (recur (inc r)))))
        (recur (inc i))))))
