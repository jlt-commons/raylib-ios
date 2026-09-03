(ns raylib.objc
  "The three Objective-C runtime calls the raylib host needs, and nothing else.

  The host is otherwise pure C: SDL, raylib, OpenGL ES. It reaches for UIKit
  exactly twice -- [UIScreen mainScreen].scale, to size the drawable in pixels,
  and the key window's safeAreaInsets, to keep the gallery's Back target clear
  of the Dynamic Island -- and both are plain message sends. The demo this port
  follows took these from glimmer-ios.ffi; lifting the twelve lines instead
  keeps this project free of glimmer, objc-jlt and their natives.

  On Apple arm64 objc_msgSend forwards the caller's registers to the method
  implementation, so a FIXED-arity binding matching the method's calling
  convention is correct even though the C prototype is variadic. Never bind it
  :varargs -- the variadic convention passes floating-point arguments on the
  stack, and the method will read registers.

  NOTHING HERE RUNS AT LOAD. defcfn expands to a Chez foreign-procedure, which
  resolves its symbol at first CALL, so requiring this namespace on the macOS
  build host (which jolt build does, for every namespace) costs nothing. There
  is also no dlopen: unlike glimmer-ios, this app links UIKit at build time
  through the framework list in tools/ios/build.sh, because SDL2 needs it
  anyway."
  (:require [jolt.ffi :as ffi]))

(ffi/defcfn objc-get-class    "objc_getClass"    [:string] :pointer)
(ffi/defcfn sel-register-name "sel_registerName" [:string] :pointer)

(ffi/defcfn objc-msg-send-0 "objc_msgSend" [:pointer :pointer] :pointer)

(def ^:private sel-cache (atom {}))

(defn sel
  "Register (once) and return the selector for a method name."
  [name]
  (or (get @sel-cache name)
      (let [s (sel-register-name name)]
        (swap! sel-cache assoc name s)
        s)))

(def ^:private class-cache (atom {}))

(defn cls
  "Look up (once) and return the Objective-C class for a name."
  [name]
  (or (get @class-cache name)
      (let [c (objc-get-class name)]
        (swap! class-cache assoc name c)
        c)))
