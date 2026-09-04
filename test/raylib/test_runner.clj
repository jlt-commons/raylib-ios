(ns raylib.test-runner
  "Entry point for `jolt -M:test`. Runs the six pure namespaces this project
  carries unchanged from jasalt/jolt-android-experiment at 6d2b291.

  Nothing here touches raylib, SDL, UIKit or a device: the whole point of the
  scene contract is that the simulation is pure, so its tests run on the build
  host. The iOS half -- raylib.host and the owner loops over it -- has no
  headless test and is proven by running on the phone."
  (:require [clojure.set]
            [clojure.string]
            [clojure.test :as t]))

(defmethod t/report :error [m]
  (t/with-test-out
    (t/inc-report-counter :error)
    (println "\nERROR in" (t/testing-vars-str m))
    (when (seq t/*testing-contexts*) (println (t/testing-contexts-str)))
    (when-let [message (:message m)] (println message))
    (when-let [e (:actual m)]
      (if (instance? Throwable e)
        (do (println "  ->" (.getName (class e)) ":" (ex-message e))
            (when-let [d (ex-data e)] (prn d)))
        (prn e)))))

(defn- exit [code]
  (cond
    (resolve 'jolt.host/exit) ((resolve 'jolt.host/exit) code)
    (resolve 'System/exit)    ((resolve 'System/exit) code)
    :else nil))

(defn -main [& _]
  (let [namespaces '[raylib.scenes.kaleidoscope-test
                     raylib.scenes.automata-test
                     raylib.easings-test
                     raylib.scenes.clock-test
                     raylib.scenes.easings-test
                     raylib.scenes.colorwheel-test
                     raylib.scenes.life-test
                     raylib.scenes.logoanim-test
                     raylib.scenes.lorenz-test
                     raylib.scenes.piechart-test
                     raylib.scenes.tesseract-test
                     raylib.scenes.unitcircle-test
                     poc.raylib.flappy-bird-test
                     poc.raylib.gallery-test
                     poc.raylib.gallery-ui-test
                     poc.raylib.diagnostics-test
                     poc.raylib.following-eyes-test
                     poc.raylib.touch-trail-test]]
    ;; A hardcoded list silently skips any test file not on it, and "Ran 23
    ;; tests" reads exactly like success when the new namespace never loaded.
    ;; Cost one round today. Compare the list against what is on disk instead.
    (let [on-disk (->> (file-seq (java.io.File. "test"))
                       (filter (fn [f] (re-find #"_test\.cljc?$" (.getName f))))
                       (map (fn [f] (-> (.getPath f)
                                        (clojure.string/replace #"^test/" "")
                                        (clojure.string/replace #"\.cljc?$" "")
                                        (clojure.string/replace "_" "-")
                                        (clojure.string/replace "/" ".")
                                        symbol)))
                       set)
          missing (clojure.set/difference on-disk (set namespaces))]
      (when (seq missing)
        (println "ERROR: test files on disk that this runner does not list:")
        (doseq [m (sort missing)] (println "  " m))
        (exit 1)))
    (doseq [ns namespaces]
      (try (require ns :reload)
           (catch Exception e
             (println "ERROR requiring" ns ":" (ex-message e)))))
    (let [results (apply t/run-tests namespaces)
          failed  (+ (:fail results 0) (:error results 0))]
      (println "----")
      (println "tests:" (:test results 0)
               "assertions:" (:pass results 0) "passed /"
               failed "failed")
      (when (pos? failed) (exit 1)))))
