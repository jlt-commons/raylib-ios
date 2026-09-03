(ns poc.raylib.touch-trail-test
  (:require [clojure.test :refer [deftest is]] [poc.raylib.touch-trail :as trail]))
(defn input [phase point] {:pointer {:phase phase :position point}})
(deftest trail-is-bounded-and-idle-stable
  (let [started (trail/step {:points []} (input :press [0 0]))
        drawn (reduce (fn [s n] (trail/step s (input :down [n n]))) started (range 100))]
    (is (= trail/max-points (count (:points drawn))))
    (is (= [99 99] (last (:points drawn))))
    (is (= drawn (trail/step drawn (input :idle nil))))
    (is (= {:points [[7 8]]} (trail/step drawn (input :press [7 8]))))))
