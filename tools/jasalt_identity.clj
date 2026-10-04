(ns jasalt-identity
  "How a file in this tree is compared with its jasalt/jolt-android-experiment
  original. One definition, loaded by the identity test and by
  tools/extract-from-notebooks, so the two cannot drift apart."
  (:require [clojure.string :as str]))

(defn upstream-text
  "`text` with every namespace name in `ns-map` (ours -> upstream) put back."
  [text ns-map]
  (reduce (fn [acc [ours theirs]]
            (str/replace acc
                         (re-pattern (str "(?<![\\w.-])"
                                          (java.util.regex.Pattern/quote (str ours))
                                          "(?![\\w.-])"))
                         (java.util.regex.Matcher/quoteReplacement (str theirs))))
          text ns-map))

(defn normalise
  "Layout only: runs of whitespace become one space, and the spaces beside a
  bracket go. Anything that changes where a token starts or ends, or what a
  string holds between non-bracket characters, still shows. Not covered: runs
  of spaces inside a string, and a space next to a bracket inside one."
  [s]
  (-> s
      (str/replace #"\s+" " ")
      str/trim
      (str/replace #" ?([()\[\]{}]) ?" "$1")))

(defn sha256 [^String s]
  (let [d (java.security.MessageDigest/getInstance "SHA-256")]
    (->> (.digest d (.getBytes s "UTF-8"))
         (map #(format "%02x" %))
         (apply str))))

(defn identity-sha
  "The sha256 a table row's :sha is compared with, for `text` read from the
  file and `ns-map` from the row."
  [text ns-map]
  (sha256 (normalise (upstream-text text ns-map))))
