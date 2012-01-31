(ns engine.server.rope
  "Implementation of Ropes as per \"Ropes: an Alternative to Strings\"
by Boehm, Hans-J; Atkinson, Russ; and Plass, Michael (December 1995), doi:10.1002/spe.4380251203."
  (:use [clojure.string :only [join]])
  (:require [clojure.zip :as zip])
  (:import [clojure.lang Counted Indexed]
           [java.lang String IndexOutOfBoundsException]))

(def *fib-seq* (map first (iterate (fn [[a b]] [b (+ a b)]) [0 1])))
(def *leaf-cutoff-length* 128)

(defprotocol Measurable
  "Trivial protocol for things measurable.
Default implementations are provided for nil and String; nil measures as 0
characters, 0 lines. Strings get analyzed accordingly."
  (measure [object] "Measure object"))

(defrecord CharSequenceMeasurement [#^int length #^int lines])

(defn measurement
  "CharSequenceMeasurement from length and lines"
  [length lines]
  (CharSequenceMeasurement. length lines))

(defn count-matches
  "Number of character matches in string"
  [char string]
  (->> string (filter #(= char %)) count))

(defn measure-string
  "CharSequenceMeasurement of number of characters and newlines in string"
  [string]
  (measurement (count string) (count-matches \newline string)))

(extend-type nil
  Measurable
  (measure [object] (measurement 0 0)))

(extend-type String
  Measurable
  (measure [string] (measure-string string)))


(defprotocol Treeish-2-3
  "Protocol describing 2-3-Trees, such as Ropes.
Default implementations for nil and String exist."
  (split [tree pred])
  (conc [tree1 tree2])
  (left [tree])
  (right [tree])
  (depth [tree]))

(extend-type nil
  Treeish-2-3
  (split [_ _] nil)
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0))

(extend-type String
  Treeish-2-3
  (split [_ _] nil)
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0))

(defprotocol Ropey
  "Rope-specific protocol for things that don't fit into general 2-3-Trees."
  (balanced? [rope]))


; necessary forward declaration
(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level])

(defn rope?
  "Is x a Rope?"
  [x]
  (instance? Rope x))

(defn weigh
  "The accumulated measurement of all children to the right, provided that Treeish-2-3 is implemented for seq.
In other words: What a new Treeish-2-3 node would weigh with seq as its left child."
  [seq]
  (if (and (satisfies? Treeish-2-3 seq) (right seq)) ; no child on the right? not required to weigh again
    (->> seq (iterate right) (take-while identity) (map measure) (map vals) (reduce #(map + %1 %2)) (apply measurement))
    (measure seq)))

(defn rope
  "New Rope that concatenates the given arguments, if any"
  ([] (Rope. nil nil (measurement 0 0) 0))
  ([seq] (Rope. seq nil (weigh seq) (inc (depth seq)))) 
  ([seq1 seq2] (Rope. seq1 seq2 (weigh seq1) (inc (max (depth seq1) (depth seq2))))))

(defn rope-zip
  "Creates Zipper structures for Ropes, starting from root"
  [root]
  (zip/zipper rope?
              (fn [node] (filter identity [(left node) (right node)]))
              (fn [node children] (apply rope children))
              root))

(defn rope-nth
  "Evaluates to character at position index in the Rope at root in O(log n) time"
  [root index]
  (loop [zipper (rope-zip root) l-index index]
    (let [node (zip/node zipper)]
      (cond (string? node) (nth node l-index)
            node
            (let [weight (-> node measure :length) next (zip/down zipper)]
              (if (<= weight l-index)
                (recur (zip/right next) (- l-index weight))
                (recur next l-index)))
            :default (throw (IndexOutOfBoundsException. (str "String index out of range: " index)))))))

(defn rope-seq
  "Lazy sequence of all Rope nodes at root, depth-first order"
  [root]
  (for [node (iterate zip/next (rope-zip root)) :while (not (zip/end? node))]
    (zip/node node)))

(defn rope->string
  "Flat string from Rope at root"
  [root]
  (->> (rope-seq root) (filter string?) (reduce str)))

(defn rope-rebalance
  "Create rebalanced copy of the Rope, using the algorithm described in \"Ropes: an Alternative to Strings\".
The implementation does not resemble the one from the paper, but is instead
idiomatic Clojure and optimized for functional languages in general."  [root]
  (let [fold (fn [coll] (reduce rope (reverse coll)))
        fib-border (fn [limit] (->> *fib-seq* (take-while #(>= limit %)) last))]
    (loop [acc (), coll (->> (rope-seq root) (filter string?)), max-length nil]
      (let [current (first coll), length (count current)]
        (if current
          (if (and (seq acc) (>= length max-length))
            (let [[low high] (split-with #(>= length (count %)) acc)]
              (recur high (conj (next coll) (rope (fold low) current)) (fib-border (count (first high)))))
            (recur (cons current acc) (next coll) (fib-border length)))
          (fold acc))))))

(defmulti rope-concat
  "Concatenation of two Ropes, including Strings which are treated as leafes"
  (fn [rope1 rope2] [(type rope1) (type rope2)]))
(defmethod rope-concat [String String] [string1 string2] (str string1 string2))
(defmethod rope-concat :default [coll1 coll2]
  (let [new-rope (rope coll1 coll2)]
    (if (balanced? new-rope)
      new-rope
      (rope-rebalance new-rope))))

(defn rope-split
  "Split Rope at root by calling pred against the accumulated weights and nodes traversed so far.
Pred should evaluate to a truthy value if the weight or node should be traversed
to the right, i.e. towards higher weights.
The vector retuned is composed of the re-evaluated tree to the left of the
split, the actual split leaf and a reassembled tree of the nodes cut off to the
right of the traversal."
  ([root pred] (rope-split root pred :length))
  ([root pred measure-key]
     (let [reassemble (fn [coll]
                        (if (empty? coll)
                          (rope)
                          (->> coll (reduce rope-concat) rope)))]
       (loop [acc (), zipper (rope-zip root), total-weight 0]
         (let [node (zip/node zipper), node-weight (-> node measure measure-key), new-weight (+ node-weight total-weight)]
           (if (string? node)
             (if (pred new-weight node)
               (throw (IndexOutOfBoundsException. (str "Node index out of range")))
               [(-> zipper zip/remove zip/root) node (reassemble acc)])
             (let [left (zip/down zipper), right (zip/right left)]
               (cond (nil? right) (recur acc left total-weight)
                     (pred new-weight node) (recur acc right new-weight)
                     :default
                     (recur (cons (zip/node right) acc)
                            (-> right (zip/replace nil) zip/left)
                            total-weight)))))))))

;; Presumably faster than iterating till zip/end?
(defn rope-conj
  "Conjoin for Ropes. x is concatenated to the depth-first end, i.e. rightmost node of the Rope.
Additional xs will be concatenated first."
  ([root x]
     (loop [zipper (rope-zip root)]
       (let [left-child (zip/down zipper) right-child (zip/right left-child)]
         (cond (and right-child (rope? (zip/node right-child))) (recur right-child)
               (and left-child (rope? (zip/node left-child))) (recur left-child)
               :default (-> zipper (zip/edit #(rope-concat %1 %2) x) zip/root)))))
  ([root x & xs]
     (rope-conj root (reduce rope-concat x xs))))

(defn- rope-split-at
  "Split Rope at index, with the additional remaining string index as fourth vector element"
  [root index]
  (let [[left-rope string right-rope] (rope-split root (fn [weight _] (>= index weight)))
        position (- index (count left-rope))]
    [left-rope string right-rope position]))

(defn rope-insert
  "Insert string in Rope at index"
  [root index string]
  (if (= index (count root))
    (rope-conj root string)
    (let [[left-rope target right-rope position] (rope-split-at root index)]
      (rope-concat (rope-conj left-rope (subs target 0 position) string (subs target position))
                   right-rope))))

(defn rope-subs
  "Report in O(log n) time for Ropes, the equivalent of string subs"
  ([root start]
     (if (= start (count root))
       "" ; For consistency with "normal" subs
       (let [[_ string right-rope position] (rope-split-at root start)]
         (str (subs string position) right-rope))))
  ([root start end]
     {:pre [(>= (- end start) 0)]}
     (if (= start (count root))
       ""
       (let [[_ part1 right-rope position1] (rope-split-at root start)
             length (- end start)
             rest (- (count part1) position1)]
         (if (> length rest)
           (let [[middle-rope part2 _ position2] (rope-split-at right-rope (- length rest))]
             (str (subs part1 position1) middle-rope (subs part2 0 position2)))
           (subs part1 position1 (+ position1 length)))))))

(defn rope-delete
  "Delete the characters at interval [start..end] in the Rope"
  [root start end]
  {:pre [(>= (- end start) 0)]}
  (if (= end (count root))
    (let [[left-rope part _ position] (rope-split-at root start)]
      (rope-conj left-rope (subs part 0 position)))
    (let [[left-rope part1 _ position1] (rope-split-at root start)
          [_ part2 right-rope position2] (rope-split-at root end)]
      (rope-concat (rope-conj left-rope (subs part1 0 position1) (subs part2 position2))
                   right-rope))))

(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level]
  Measurable
  (measure [_] weight)

  Treeish-2-3
  (split [this pred] (rope-split this pred))
  (conc [this tree] (rope-concat this tree))
  (left [_] left)
  (right [_] right)
  (depth [_] level)

  Ropey
  (balanced? [this] (>= (count this) (nth *fib-seq* (+ level 2))))

  Counted
  (count [this] (:length (weigh this)))

  Indexed
  (nth [this index] (rope-nth this index))

  CharSequence
  (charAt [this index] (nth this index))
  (length [this] (count this))
  (subSequence [this start end] (rope-subs this start end))
  (toString [this] (rope->string this)))  

(defmethod print-method Rope [rope writer]
  (print-simple (format "#<Rope %s>" (->> (measure rope) vals (join "/"))) writer))

; Must be defined here, else the dispatch on type/class will fail
(defmethod rope-concat [Rope String] [coll string]
  (let [right-child (right coll)]
    (if (and (= String (type right-child))
             (<= (+ (count right-child) (count string)) *leaf-cutoff-length*))
      (rope (left coll) (str right-child string))
      (rope-concat coll (rope string)))))

(defn string->rope
  "Create a fresh Rope from flat string.
The resulting leafes will carry at most *leaf-cutoff-length* characters of the
input string."
  [string]
  (->> (partition-all *leaf-cutoff-length* string) (map #(apply str %)) (reduce rope) rope-rebalance rope))
