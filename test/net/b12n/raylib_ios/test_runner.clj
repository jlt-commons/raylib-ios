(ns net.b12n.raylib-ios.test-runner
  "Entry point for `jolt -M:test` and `clojure -M:test`. Runs the pure scene and
  gallery namespaces, which the JVM and jolt both load, plus the jolt-only set
  (`jolt-only` below) when it is running under jolt. Six of the pure ones are
  carried from jasalt/jolt-android-experiment at 6d2b291, apart from their names.

  Nothing here touches raylib, SDL, UIKit or a device: the whole point of the
  scene contract is that the simulation is pure, so its tests run on the build
  host. The iOS half -- net.b12n.raylib-ios.host and the owner loops over it -- has no
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
  "Test namespaces that load jolt.ffi, directly or through net.b12n.raylib-ios.gallery, so
  only jolt can require them. The JVM run skips them and says so."
  '#{net.b12n.raylib-ios.gallery-smoke-test
     net.b12n.raylib-ios.runner-smoke-test
     net.b12n.raylib-ios.texture-test})

(def ^:private jolt?
  "True under jolt, which sets the jolt.version system property (documented
  idiom, present since v0.8.2). It is nil on JVM Clojure."
  (some? (System/getProperty "jolt.version")))

(defn -main [& _]
  (let [namespaces '[net.b12n.raylib-ios.scenes.kaleidoscope-test
                     net.b12n.raylib-ios.scenes.angles-test
                     net.b12n.raylib-ios.scenes.automata-test
                     net.b12n.raylib-ios.scenes.balls-test
                     net.b12n.raylib-ios.scenes.bullets-test
                     net.b12n.raylib-ios.scenes.collision-test
                     net.b12n.raylib-ios.scenes.dashed-test
                     net.b12n.raylib-ios.scenes.multitouch-test
                     net.b12n.raylib-ios.scenes.analog-test
                     net.b12n.raylib-ios.scenes.clockgrid-test
                     net.b12n.raylib-ios.scenes.sector-test
                     net.b12n.raylib-ios.scenes.palette-test
                     net.b12n.raylib-ios.scenes.gradient-test
                     net.b12n.raylib-ios.scenes.ring-test
                     net.b12n.raylib-ios.scenes.splines-test
                     net.b12n.raylib-ios.scenes.rounded-test
                     net.b12n.raylib-ios.scenes.vecangle-test
                     net.b12n.raylib-ios.scenes.bars-test
                     net.b12n.raylib-ios.scenes.bezier-test
                     net.b12n.raylib-ios.scenes.fan-test
                     net.b12n.raylib-ios.scenes.clipbox-test
                     net.b12n.raylib-ios.scenes.align-test
                     net.b12n.raylib-ios.scenes.resize-test
                     net.b12n.raylib-ios.scenes.deltatime-test
                     net.b12n.raylib-ios.scenes.randomvalues-test
                     net.b12n.raylib-ios.scenes.formattext-test
                     net.b12n.raylib-ios.scenes.strip-test
                     net.b12n.raylib-ios.scenes.touchball-test
                     net.b12n.raylib-ios.scenes.rlgltriangle-test
                     net.b12n.raylib-ios.scenes.particles-test
                     net.b12n.raylib-ios.scenes.breakout-test
                     net.b12n.raylib-ios.scenes.bounce-test
                     net.b12n.raylib-ios.scenes.snake-test
                     net.b12n.raylib-ios.scenes.game2048-test
                     net.b12n.raylib-ios.scenes.minesweeper-test
                     net.b12n.raylib-ios.scenes.pong-test
                     net.b12n.raylib-ios.scenes.invaders-test
                     net.b12n.raylib-ios.scenes.tetris-test
                     net.b12n.raylib-ios.scenes.asteroids-test
                     net.b12n.raylib-ios.scenes.virtualpad-test
                     net.b12n.raylib-ios.scenes.starfield-test
                     net.b12n.raylib-ios.scenes.easingsbox-test
                     net.b12n.raylib-ios.scenes.easingstestbed-test
                     net.b12n.raylib-ios.scenes.rectbounds-test
                     net.b12n.raylib-ios.scenes.huewheel-test
                     net.b12n.raylib-ios.scenes.logo-test
                     net.b12n.raylib-ios.scenes.fontsizes-test
                     net.b12n.raylib-ios.scenes.inlinestyle-test
                     net.b12n.raylib-ios.scenes.outlines-test
                     net.b12n.raylib-ios.scenes.shapes-test
                     net.b12n.raylib-ios.scenes.ellipses-test
                     net.b12n.raylib-ios.scenes.screens-test
                     net.b12n.raylib-ios.scenes.survivors-test
                     net.b12n.raylib-ios.scenes.pacman-test
                     net.b12n.raylib-ios.scenes.hello-test
                     net.b12n.raylib-ios.scenes.nudge-test
                     net.b12n.raylib-ios.scenes.wheelbox-test
                     net.b12n.raylib-ios.scenes.undoredo-test
                     net.b12n.raylib-ios.scenes.strings-test
                     net.b12n.raylib-ios.scroll-test
                     net.b12n.raylib-ios.gesture-test
                     net.b12n.raylib-ios.camera2d-test
                     net.b12n.raylib-ios.soft3d-test
                     net.b12n.raylib-ios.scenes.camera2d-test
                     net.b12n.raylib-ios.scenes.camerazoom-test
                     net.b12n.raylib-ios.scenes.platformer-test
                     net.b12n.raylib-ios.scenes.splitscreen-test
                     net.b12n.raylib-ios.scenes.gestures-test
                     net.b12n.raylib-ios.scenes.helitorus-test
                     net.b12n.raylib-ios.scenes.rotcube-test
                     net.b12n.raylib-ios.scenes.camera3d-test
                     net.b12n.raylib-ios.scenes.ortho-test
                     net.b12n.raylib-ios.scenes.spincubes-test
                     net.b12n.raylib-ios.scenes.wireframes-test
                     net.b12n.raylib-ios.scenes.freecam-test
                     net.b12n.raylib-ios.scenes.yawpitchroll-test
                     net.b12n.raylib-ios.scenes.boxcollide-test
                     net.b12n.raylib-ios.scenes.fpcamera-test
                     net.b12n.raylib-ios.scenes.voxel-test
                     net.b12n.raylib-ios.scenes.doom-test
                     net.b12n.raylib-ios.scenes.fpmaze-test
                     net.b12n.raylib-ios.scenes.split3d-test
                     net.b12n.raylib-ios.scenes.picking-test
                     net.b12n.raylib-ios.scenes.worldscreen-test
                     net.b12n.raylib-ios.scenes.wavecubes-test
                     net.b12n.raylib-ios.scenes.solarsystem-test
                     net.b12n.raylib-ios.scenes.pointcloud-test
                     net.b12n.raylib-ios.scenes.spheres-test
                     net.b12n.raylib-ios.scenes.bunnymark-test
                     net.b12n.raylib-ios.scenes.bgscroll-test
                     net.b12n.raylib-ios.scenes.spritestack-test
                     net.b12n.raylib-ios.scenes.pixelperfect-test
                     net.b12n.raylib-ios.scenes.vpscaling-test
                     net.b12n.raylib-ios.scenes.letterbox-test
                     net.b12n.raylib-ios.scenes.fogofwar-test
                     net.b12n.raylib-ios.scenes.blendmodes-test
                     net.b12n.raylib-ios.scenes.blendparticles-test
                     net.b12n.raylib-ios.scenes.billboard-test
                     net.b12n.raylib-ios.scenes.dirbillboard-test
                     net.b12n.raylib-ios.scenes.texcube-test
                     net.b12n.raylib-ios.scenes.geoshapes-test
                     net.b12n.raylib-ios.scenes.textiling-test
                     net.b12n.raylib-ios.scenes.srcrec-test
                     net.b12n.raylib-ios.scenes.spritebutton-test
                     net.b12n.raylib-ios.scenes.npatch-test
                     net.b12n.raylib-ios.scenes.texpoly-test
                     net.b12n.raylib-ios.scenes.texproc-test
                     net.b12n.raylib-ios.scenes.rawdata-test
                     net.b12n.raylib-ios.scenes.screenbuf-test
                     net.b12n.raylib-ios.scenes.spriteanim-test
                     net.b12n.raylib-ios.scenes.texcurve-test
                     net.b12n.raylib-ios.scenes.rendertex-test
                     net.b12n.raylib-ios.scenes.fbrender-test
                     net.b12n.raylib-ios.scenes.mousepaint-test
                     net.b12n.raylib-ios.scenes.magnify-test
                     net.b12n.raylib-ios.scenes.toplights-test
                     net.b12n.raylib-ios.stick-test
                     net.b12n.raylib-ios.easings-test
                     net.b12n.raylib-ios.texel-test
                     net.b12n.raylib-ios.perlin-test
                     net.b12n.raylib-ios.scenes.clock-test
                     net.b12n.raylib-ios.scenes.easings-test
                     net.b12n.raylib-ios.scenes.colorwheel-test
                     net.b12n.raylib-ios.scenes.life-test
                     net.b12n.raylib-ios.scenes.logoanim-test
                     net.b12n.raylib-ios.scenes.lorenz-test
                     net.b12n.raylib-ios.scenes.piechart-test
                     net.b12n.raylib-ios.scenes.sequence-test
                     net.b12n.raylib-ios.scenes.tesseract-test
                     net.b12n.raylib-ios.scenes.unitcircle-test
                     net.b12n.raylib-ios.scenes.writing-test
                     net.b12n.raylib-ios.jasalt-identity-test
                     net.b12n.raylib-ios.scenes.flappy-bird-test
                     net.b12n.raylib-ios.gallery.core-test
                     net.b12n.raylib-ios.gallery.ui-test
                     net.b12n.raylib-ios.gallery.diagnostics-test
                     net.b12n.raylib-ios.scenes.following-eyes-test
                     net.b12n.raylib-ios.scenes.touch-trail-test]]
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
