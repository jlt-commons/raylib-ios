(ns raylib.link
  "Are raylib and SDL2 actually in this binary?

  The first thing worth answering on a new toolchain, and the cheapest. No
  window, no GL context, no device interaction: one call into each library and
  a report. If this runs, the static archives linked, the frameworks resolved,
  and jolt.ffi found both symbols in the process image at first call.

  If it does not run, the failure names which half broke. A missing SDL symbol
  is a link-order problem, since SDL2 must come after the raylib that references
  it. A missing raylib symbol usually means -force_load was dropped, because the
  linker discards an archive member nothing references yet and every raylib
  symbol is reached through the FFI at run time rather than at link time.

  Run it with: NS=raylib.link TARGET=device sh tools/ios/build.sh"
  (:require [jolt.ffi :as ffi]))

;; SDL_GetVersion fills an SDL_version, three bytes: major, minor, patch.
(ffi/defcfn sdl-get-version  "SDL_GetVersion"  [:pointer] :void)
(ffi/defcfn sdl-get-platform "SDL_GetPlatform" [] :string)
(ffi/defcfn sdl-get-revision "SDL_GetRevision" [] :string)

;; raylib's RNG is the cheapest raylib call with an observable result: it needs
;; no window, and seeding it makes the answer deterministic, so a wrong number
;; means a broken call rather than bad luck.
(ffi/defcfn set-random-seed  "SetRandomSeed"   [:uint] :void)
(ffi/defcfn get-random-value "GetRandomValue"  [:int :int] :int)

(def ^:private version-layout
  (ffi/layout [:struct [[:major :uint8] [:minor :uint8] [:patch :uint8]]]))

(defn sdl-version
  "SDL's compiled-in version as [major minor patch]."
  []
  (ffi/with-layout [v version-layout]
    (sdl-get-version v)
    (mapv #(ffi/read-field v version-layout %) [:major :minor :patch])))

(defn raylib-responds?
  "Does raylib's RNG answer in range, from a fixed seed? Ten draws from a
  six-sided die, which is enough that a stuck or garbage return shows up."
  []
  (set-random-seed 20260904)
  (let [draws (repeatedly 10 #(get-random-value 1 6))]
    {:draws (vec draws)
     :ok? (every? #(<= 1 % 6) draws)}))

(defn -main [& _]
  (let [[maj min patch] (sdl-version)
        {:keys [draws ok?]} (raylib-responds?)]
    (println (format "link: SDL %d.%d.%d on %s (%s)" maj min patch (sdl-get-platform) (sdl-get-revision)))
    (println (format "link: raylib RNG %s -> %s" (pr-str draws) (if ok? "in range" "OUT OF RANGE")))
    (println (format "link: host %s, both libraries answered"
                     (System/getProperty "os.arch")))))
