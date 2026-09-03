(ns poc.raylib.gallery-test
  (:require [clojure.test :refer [deftest is testing]]
            [poc.raylib.gallery :as gallery]))

(defn fake-scene [scene-id title]
  {:id scene-id
   :title title
   :init (fn [input]
           [{:ticks 0 :last-input input} [[:init scene-id]]])
   :update (fn [state input]
             [(-> state (update :ticks inc) (assoc :last-input input))
              [[:update scene-id]]])
   :draw (fn [state _]
           [state [[:draw scene-id (:ticks state)]]])
   :dispose (fn [state]
              [state [[:dispose scene-id]]])})

(def fake-scenes
  [(fake-scene :eyes "Following Eyes")
   (fake-scene :trail "Touch Trail")])

(def fake-registry (gallery/make-registry fake-scenes))
(def input {:back? false :pointer {:phase :idle} :metrics {:screen [720 1280]}})

(deftest deterministic-static-registry-test
  (is (= [:eyes :trail] (mapv :id fake-scenes)))
  (is (= "Following Eyes" (:title (gallery/scene-by-id fake-registry :eyes))))
  (is (nil? (gallery/scene-by-id fake-registry :missing)))
  (is (every? gallery/valid-scene? fake-scenes)))

(deftest scene-lifecycle-order-test
  (let [opened (gallery/open-scene fake-registry gallery/initial-gallery-state
                                   :eyes input)
        framed (gallery/run-frame fake-registry opened input)
        reset (gallery/reset-active fake-registry framed input)
        closed (gallery/back fake-registry reset)]
    (is (= :scene (:mode opened)))
    (is (= 1 (get-in framed [:scene-state :ticks])))
    (is (= [[:init :eyes] [:update :eyes] [:draw :eyes 1]]
           (:scene-events framed)))
    (is (= [[:init :eyes] [:update :eyes] [:draw :eyes 1]
            [:dispose :eyes] [:init :eyes]]
           (:scene-events reset)))
    (is (= :gallery (:mode closed)))
    (is (nil? (:active-scene-id closed)))
    (is (= :dispose (first (last (:scene-events closed)))))))

(deftest gallery-and-scene-back-test
  (testing "scene Back disposes, gallery Back requests host close"
    (let [opened (gallery/open-scene fake-registry gallery/initial-gallery-state
                                     :trail input)
          scene-back (gallery/run-frame fake-registry opened (assoc input :back? true))
          gallery-back (gallery/run-frame fake-registry scene-back
                                          (assoc input :back? true))]
      (is (= :gallery (:mode scene-back)))
      (is (= [[:init :trail] [:dispose :trail]] (:scene-events scene-back)))
      (is (false? (:close-requested? scene-back)))
      (is (:close-requested? gallery-back)))))

(deftest no-window-or-runtime-ownership-in-contract-test
  (is (= #{:id :title :init :update :draw :dispose}
         (set gallery/required-scene-keys)))
  (is (not-any? #(contains? (first fake-scenes) %)
                [:window :runtime :native-pointer :gamepad])))
