(ns raylib.test-runner
  "Entry point for `jolt -M:test` and `clojure -M:test`. Runs the pure scene and
  gallery namespaces, which the JVM and jolt both load, plus the jolt-only set
  (`jolt-only` below) when it is running under jolt. Six of the pure ones are
  carried unchanged from jasalt/jolt-android-experiment at 6d2b291.

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

(defn- exit
  "End the run with `code`.

  Called directly rather than resolved. An earlier version tried
  `(resolve 'System/exit)` first and fell through to nil when that returned
  nil, which it always does: Clojure's resolve looks up vars, and a static
  method is not one. So this function did nothing on either runtime, a failing
  test exited 0, and the CI job that runs it was green whatever the tests said.
  Proved by adding a deliberately failing test and reading the exit code.

  System/exit is available under both `clojure -M:test` and `jolt -M:test`, so
  there is nothing to detect."
  [code]
  (System/exit code))

(def ^:private jolt-only
  "Test namespaces that load jolt.ffi, directly or through raylib.gallery, so
  only jolt can require them. The JVM run skips them and says so."
  '#{raylib.gallery-smoke-test})

(def ^:private jolt?
  "True under jolt, which sets the jolt.version system property (documented
  idiom, present since v0.8.2). It is nil on JVM Clojure."
  (some? (System/getProperty "jolt.version")))

(defn -main [& _]
  (let [namespaces '[raylib.scenes.kaleidoscope-test
                     raylib.scenes.angles-test
                     raylib.scenes.automata-test
                     raylib.scenes.balls-test
                     raylib.scenes.bullets-test
                     raylib.scenes.collision-test
                     raylib.scenes.dashed-test
                     raylib.scenes.multitouch-test
                     raylib.scenes.analog-test
                     raylib.scenes.clockgrid-test
                     raylib.scenes.sector-test
                     raylib.scenes.palette-test
                     raylib.scenes.gradient-test
                     raylib.scenes.ring-test
                     raylib.scenes.splines-test
                     raylib.scenes.rounded-test
                     raylib.scenes.vecangle-test
                     raylib.scenes.bars-test
                     raylib.scenes.bezier-test
                     raylib.scenes.fan-test
                     raylib.scenes.clipbox-test
                     raylib.scenes.align-test
                     raylib.scenes.resize-test
                     raylib.scenes.deltatime-test
                     raylib.scenes.randomvalues-test
                     raylib.scenes.formattext-test
                     raylib.scenes.strip-test
                     raylib.scenes.touchball-test
                     raylib.scenes.rlgltriangle-test
                     raylib.scenes.particles-test
                     raylib.scenes.breakout-test
                     raylib.scenes.bounce-test
                     raylib.scenes.snake-test
                     raylib.scenes.game2048-test
                     raylib.scenes.minesweeper-test
                     raylib.scenes.pong-test
                     raylib.scenes.invaders-test
                     raylib.scenes.tetris-test
                     raylib.scenes.asteroids-test
                     raylib.scenes.virtualpad-test
                     raylib.scenes.starfield-test
                     raylib.scenes.easingsbox-test
                     raylib.scenes.easingstestbed-test
                     raylib.scenes.rectbounds-test
                     raylib.scenes.huewheel-test
                     raylib.scenes.logo-test
                     raylib.scenes.fontsizes-test
                     raylib.scenes.inlinestyle-test
                     raylib.scenes.outlines-test
                     raylib.scenes.shapes-test
                     raylib.scenes.ellipses-test
                     raylib.scenes.screens-test
                     raylib.scenes.survivors-test
                     raylib.scenes.pacman-test
                     raylib.scenes.hello-test
                     raylib.scenes.nudge-test
                     raylib.scenes.wheelbox-test
                     raylib.scenes.undoredo-test
                     raylib.scenes.strings-test
                     raylib.scroll-test
                     raylib.gesture-test
                     raylib.camera2d-test
                     raylib.soft3d-test
                     raylib.scenes.camera2d-test
                     raylib.scenes.camerazoom-test
                     raylib.scenes.platformer-test
                     raylib.scenes.splitscreen-test
                     raylib.scenes.gestures-test
                     raylib.scenes.helitorus-test
                     raylib.scenes.rotcube-test
                     raylib.scenes.camera3d-test
                     raylib.scenes.ortho-test
                     raylib.scenes.spincubes-test
                     raylib.scenes.wireframes-test
                     raylib.scenes.freecam-test
                     raylib.scenes.yawpitchroll-test
                     raylib.scenes.boxcollide-test
                     raylib.scenes.fpcamera-test
                     raylib.scenes.fpmaze-test
                     raylib.scenes.split3d-test
                     raylib.scenes.picking-test
                     raylib.scenes.worldscreen-test
                     raylib.scenes.wavecubes-test
                     raylib.scenes.solarsystem-test
                     raylib.scenes.pointcloud-test
                     raylib.scenes.spheres-test
                     raylib.scenes.bunnymark-test
                     raylib.scenes.bgscroll-test
                     raylib.scenes.spritestack-test
                     raylib.stick-test
                     raylib.easings-test
                     raylib.scenes.clock-test
                     raylib.scenes.easings-test
                     raylib.scenes.colorwheel-test
                     raylib.scenes.life-test
                     raylib.scenes.logoanim-test
                     raylib.scenes.lorenz-test
                     raylib.scenes.piechart-test
                     raylib.scenes.sequence-test
                     raylib.scenes.tesseract-test
                     raylib.scenes.unitcircle-test
                     raylib.scenes.writing-test
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
          missing (clojure.set/difference on-disk (clojure.set/union (set namespaces) jolt-only))]
      (when (seq missing)
        (println "ERROR: test files on disk that this runner does not list:")
        (doseq [m (sort missing)] (println "  " m))
        (exit 1)))
    (let [jolt-ns (vec (sort jolt-only))
          ;; A namespace that fails to load used to print and carry on, so the
          ;; run exited 0 with that namespace's tests silently absent.
          load-failures (atom 0)
          load! (fn [nss]
                  (doseq [ns nss]
                    (try (require ns :reload)
                         (catch Exception e
                           (swap! load-failures inc)
                           (println "ERROR requiring" ns ":" (ex-message e))))))]
      (if jolt?
        (println "running jolt-only:" (clojure.string/join " " jolt-ns))
        (println "skipped (jolt.version not set, so not running under jolt;"
                 "needs jolt.ffi):" (clojure.string/join " " jolt-ns)))
      (load! namespaces)
      (when jolt? (load! jolt-ns))
      (let [counters [:test :pass :fail :error]
            pure     (apply t/run-tests namespaces)
            ;; Its own run so that "it ran" can be checked. Were the jolt-only
            ;; set dropped or emptied, the combined count would still look fine.
            only     (if jolt? (apply t/run-tests jolt-ns) {})
            missing? (and jolt? (zero? (:test only 0)))
            _        (when missing?
                       (println "ERROR: jolt-only namespaces ran zero tests:"
                                (clojure.string/join " " jolt-ns)))
            results  (merge-with + (select-keys pure counters)
                                 (select-keys only counters))
            failed   (+ (:fail results 0) (:error results 0)
                        @load-failures (if missing? 1 0))]
        (println "----")
        (println "tests:" (:test results 0)
                 "assertions:" (:pass results 0) "passed /"
                 failed "failed")
        (when (pos? failed) (exit 1))))))
