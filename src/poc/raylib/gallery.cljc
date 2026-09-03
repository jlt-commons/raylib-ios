(ns poc.raylib.gallery
  "Pure, explicit scene lifecycle for one persistent Jolt/Raylib host loop.")

(def contract-version 1)
(def required-scene-keys [:id :title :init :update :draw :dispose])

(def initial-gallery-state
  {:mode :gallery
   :active-scene-id nil
   :scene-state nil
   :scene-events []
   :close-requested? false})

(defn valid-scene? [scene]
  (and (map? scene)
       (every? #(contains? scene %) required-scene-keys)
       (keyword? (:id scene))
       (string? (:title scene))
       (every? fn? ((juxt :init :update :draw :dispose) scene))))

(defn make-registry
  "Create a deterministic ID map from a static scene vector. Duplicate IDs and
  malformed scene descriptors fail before the frame loop starts."
  [scenes]
  (when-not (every? valid-scene? scenes)
    (throw (ex-info "Invalid gallery scene descriptor" {:scenes scenes})))
  (let [registry (into {} (map (juxt :id identity) scenes))]
    (when-not (= (count scenes) (count registry))
      (throw (ex-info "Duplicate gallery scene ID" {:scenes scenes})))
    registry))

(defn scene-by-id [registry scene-id]
  (get registry scene-id))

(defn append-events [gallery-state events]
  (update gallery-state :scene-events into events))

(defn open-scene [registry gallery-state scene-id input]
  (if-let [scene (scene-by-id registry scene-id)]
    (let [[scene-state events] ((:init scene) input)]
      (-> gallery-state
          (assoc :mode :scene
                 :active-scene-id scene-id
                 :scene-state scene-state
                 :close-requested? false)
          (append-events events)))
    gallery-state))

(defn update-active [registry gallery-state input]
  (if-let [scene (scene-by-id registry (:active-scene-id gallery-state))]
    (let [[scene-state events]
          ((:update scene) (:scene-state gallery-state) input)]
      (-> gallery-state
          (assoc :scene-state scene-state)
          (append-events events)))
    gallery-state))

(defn draw-active [registry gallery-state input]
  (if-let [scene (scene-by-id registry (:active-scene-id gallery-state))]
    (let [[scene-state events]
          ((:draw scene) (:scene-state gallery-state) input)]
      (-> gallery-state
          (assoc :scene-state scene-state)
          (append-events events)))
    gallery-state))

(defn dispose-active [registry gallery-state]
  (if-let [scene (scene-by-id registry (:active-scene-id gallery-state))]
    (let [[scene-state events] ((:dispose scene) (:scene-state gallery-state))]
      (-> gallery-state
          (assoc :scene-state scene-state)
          (append-events events)))
    gallery-state))

(defn back
  "Back closes an active scene into the gallery. Back at gallery level requests
  host-loop closure; it never creates or destroys a window/runtime."
  [registry gallery-state]
  (if (= :scene (:mode gallery-state))
    (-> (dispose-active registry gallery-state)
        (assoc :mode :gallery :active-scene-id nil :scene-state nil))
    (assoc gallery-state :close-requested? true)))

(defn reset-active [registry gallery-state input]
  (if-let [scene-id (:active-scene-id gallery-state)]
    (let [disposed (dispose-active registry gallery-state)
          scene (scene-by-id registry scene-id)
          [scene-state events] ((:init scene) input)]
      (-> disposed
          (assoc :scene-state scene-state)
          (append-events events)))
    gallery-state))

(defn run-frame
  "Execute one scene update/draw pair, or process Back as an edge before scene
  work. Polling and rendering remain owned by the single external host loop."
  [registry gallery-state input]
  (if (:back? input)
    (back registry gallery-state)
    (if (= :scene (:mode gallery-state))
      (let [updated (update-active registry gallery-state input)]
        (draw-active registry updated input))
      gallery-state)))
