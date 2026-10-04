(ns net.b12n.raylib-ios.jasalt-identity-test
  "Six namespaces here come from jasalt/jolt-android-experiment at 6d2b291 and
  differ from upstream only in their names (and in whitespace, since the tree
  went through clojure-lsp format). This puts the upstream names back, drops
  the whitespace and compares the sha256 with the one measured from the
  upstream bytes, so a stray edit to any of them fails here.

  The table is tools/jasalt-identity.edn, shared with
  tools/extract-from-notebooks. JVM only: jolt has no sha256, so under jolt
  this namespace defines no tests and the JVM job is the one that checks."
  #?@(:jolt []
      :clj [(:require [clojure.edn :as edn]
                      [clojure.string :as str]
                      [clojure.test :as t])]))

#?(:jolt nil
   :clj
   (defn- sha256 [^String s]
     (let [d (java.security.MessageDigest/getInstance "SHA-256")]
       (->> (.digest d (.getBytes s "UTF-8"))
            (map #(format "%02x" %))
            (apply str)))))

#?(:jolt nil
   :clj
   (defn- upstream-text
     "`text` with every namespace name in `ns-map` (ours -> upstream) put back."
     [text ns-map]
     (reduce (fn [acc [ours theirs]]
               (str/replace acc
                            (re-pattern (str "(?<![\\w.-])"
                                             (java.util.regex.Pattern/quote (str ours))
                                             "(?![\\w.-])"))
                            (java.util.regex.Matcher/quoteReplacement (str theirs))))
             text ns-map)))

#?(:jolt nil
   :clj
   (t/deftest files-are-upstream-apart-from-names
     (let [table (edn/read-string (slurp "tools/jasalt-identity.edn"))]
       (t/is (= 6 (count table)))
       (doseq [{:keys [file sha ns]} table]
         (t/testing file
           (let [got (-> (slurp file)
                         (upstream-text ns)
                         (str/replace #"\s+" "")
                         sha256)]
             (t/is (= sha got))))))))
