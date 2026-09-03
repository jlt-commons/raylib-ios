(ns raylib.link
  "examples/flappy milestone 1: are SDL2 and raylib linked in? One call each,
  no window: this proves the archives, the frameworks and the export trie."
  (:require [jolt.ffi :as ffi]))

(ffi/defcfn sdl-get-platform "SDL_GetPlatform" [] :string)
(ffi/defcfn sdl-get-version  "SDL_GetVersion"  [:pointer] :void)      ; SDL_version {u8 major, minor, patch}
(ffi/defcfn get-random-value "GetRandomValue"  [:int :int] :int)
(ffi/defcfn set-random-seed  "SetRandomSeed"   [:uint] :void)

(defn -main [& _]
  (ffi/with-alloc [v 4]
    (sdl-get-version v)
    (println "link: SDL" (str (ffi/read v :uint8 0) "." (ffi/read v :uint8 1) "." (ffi/read v :uint8 2))
             "on" (sdl-get-platform)))
  (set-random-seed 7)
  (println "link: raylib GetRandomValue(1, 6) =" (get-random-value 1 6)
           "— os.arch" (System/getProperty "os.arch")))
