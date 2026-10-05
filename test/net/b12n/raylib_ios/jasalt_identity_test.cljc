(ns net.b12n.raylib-ios.jasalt-identity-test
  "Three namespaces here come from jasalt/jolt-android-experiment at 6d2b291 and
  differ from upstream only in their names (and in layout, since the tree
  went through clojure-lsp format). This puts the upstream names back,
  normalises layout (jasalt-identity/normalise) and compares the sha256 with the one measured from the
  upstream bytes, so a stray edit to any of them fails here.

  The table is tools/jasalt-identity.edn, shared with
  tools/extract-from-notebooks. JVM only: jolt has no sha256, so under jolt
  this namespace defines no tests and the JVM job is the one that checks."
  #?@(:jolt []
      :clj [(:require [clojure.edn :as edn]
                      [clojure.test :as t])]))

#?(:jolt (println "jasalt-identity-test: skipped under jolt, which has no sha256. The JVM run checks it.")
   :clj
   (do
     ;; the comparison itself lives in tools/, shared with extract-from-notebooks
     (load-file "tools/jasalt_identity.clj")
     (t/deftest files-are-upstream-apart-from-names
       (let [identity-sha (resolve 'jasalt-identity/identity-sha)
             table        (edn/read-string (slurp "tools/jasalt-identity.edn"))]
         (t/is (= 3 (count table)))
         (doseq [{:keys [file sha ns]} table]
           (t/testing file
             (t/is (= sha (identity-sha (slurp file) ns)))))))))
