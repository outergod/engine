(ns engine.data.rope
  "Implementation of Ropes as per \"Ropes: an Alternative to Strings\"
by Boehm, Hans-J; Atkinson, Russ; and Plass, Michael (December 1995), doi:10.1002/spe.4380251203."
  (:use [clojure.string :only [join]])
  (:require [clojure.zip :as zip])
  (:import [clojure.lang Counted Indexed]
           [java.lang String IndexOutOfBoundsException]))

(def *fib-seq* (map first (iterate (fn [[a b]] [b (+ a b)]) [0 1])))
(def *leaf-cutoff-length* 64) ; This value has been determined optimal for split operations through benchmarking. TODO make constant in clojure 1.3

(defprotocol Measurable
  "Trivial protocol for things measurable.
Default implementations are provided for nil and String; nil measures as 0
characters, 0 lines. Strings get analyzed accordingly."
  (measure [object] "Measure object"))

(defn measurable?
  "Does x satisfy Measurable?"
  [x]
  (satisfies? Measurable x))

(defn measure+
  "Add up weights of measurements"
  [& xs]
  (->> xs (map vals) (reduce #(map + %1 %2)) (apply measurement)))

(defrecord CharSequenceMeasurement [#^int length #^int lines])

(defmethod print-method CharSequenceMeasurement [weight writer]
  (print-simple (format "#<CharSequenceMeasurement %d/%d>" (:length weight) (:lines weight)) writer))

(defn measurement
  "CharSequenceMeasurement from length and lines; (measurement) is 0/0"
  ([] (CharSequenceMeasurement. 0 0))
  ([length lines] (CharSequenceMeasurement. length lines)))

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
  (split [tree pred] "Split tree by pred")
  (conc [tree1 tree2] "Concatenate tree1 and tree2")
  (left [tree] "Left child of tree")
  (right [tree] "Right child of tree")
  (depth [tree] "Depth of tree"))

(defn treeish-2-3?
  "Does x satisfy Treeish-2-3?"
  [x]
  (satisfies? Treeish-2-3 x))

(defmulti cat
  "Concatenation of two Ropes, including Strings which are treated as leafes"
  (fn [rope1 rope2] [(type rope1) (type rope2)]))


(extend-type nil
  Treeish-2-3
  (split [_ _] nil)
  (conc [_ _] nil)
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0))

(extend-type String
  Treeish-2-3
  (split [_ _] nil)
  (conc [this coll] (cat this coll))
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0))

(defprotocol Ropey
  "Rope-specific protocol for things that don't fit into general 2-3-Trees."
  (balanced? [rope] "Is rope balanced?"))


; necessary forward declaration
(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level])

(defn rope?
  "Is x a Rope?"
  [x]
  (instance? Rope x))

(defn weigh
  "The accumulated measurement of all children to the right
Provided that Treeish-2-3 is implemented for seq.
In other words: What a new Treeish-2-3 node would weigh with seq as its left child."
  [seq]
  {:pre [(measurable? seq)]}
  (if (and (treeish-2-3? seq) (right seq)) ; no child on the right? not required to weigh again
    (->> seq (iterate right) (take-while identity) (map measure) (apply measure+))
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
            :default (throw (IndexOutOfBoundsException. (str "Rope index out of range: " index)))))))

(defn rope-seq
  "Lazy sequence of all Rope nodes at root, depth-first order"
  [root]
  (for [node (iterate zip/next (rope-zip root)) :while (not (zip/end? node))]
    (zip/node node)))

(defn rope->string
  "Flat string from Rope at root"
  [root]
  (apply str (->> (rope-seq root) (filter string?))))

(defn rebalance
  "Create rebalanced copy of the Rope, using the algorithm described in \"Ropes: an Alternative to Strings\".
The implementation does not resemble the one from the paper, but is instead
idiomatic Clojure and optimized for functional languages in general."  [root]
  (let [fold (fn [coll] (reduce rope (reverse coll)))
        fib-border (fn [limit] (->> *fib-seq* (take-while #(>= limit %)) last))]
    (loop [acc (), coll (->> (rope-seq root) (filter string?)), max-length nil]
      (let [current (first coll), length (count current)]
        (if current
          (let [rest (next coll)]
           (if (and (seq acc) (>= length max-length))
             (let [[low high] (split-with #(>= length (fib-border (count %))) acc)]
               (recur high (conj rest (rope (fold low) current)) (fib-border (count (first high)))))
             (recur (cons current acc) rest (fib-border length))))
          (fold acc))))))

(defmethod cat :default [coll1 coll2]
  (let [new-rope (rope coll1 coll2)]
    (if (balanced? new-rope)
      new-rope
      (rebalance new-rope))))

(defn string->rope [string]
  "Create a fresh Rope from flat string.
The resulting leafes will carry at most *leaf-cutoff-length* characters of the
input string."
  (letfn [(build [acc]
            (if (> (count acc) 1)
              (recur (->> (partition-all 2 acc) (map #(apply rope %))))
              (first acc)))]
    (->> (partition-all (-> (count string) (/ (.. Runtime getRuntime availableProcessors)) int) string)
         (pmap (bound-fn [chunk]
                 (->> (partition-all *leaf-cutoff-length* chunk) (map #(apply str %)) build)))
         build rope)))

(defmethod cat [String String] [string1 string2]
  (let [string (str string1 string2)]
    (if (> (count string) *leaf-cutoff-length*)
     (string->rope string)
     string)))

(defn rope-split
  "Split Rope at root by calling pred against the accumulated weights and nodes traversed so far

Pred should evaluate to a truthy value whenever the weight or node should be
traversed to the right, i.e. towards higher weights.
The vector returned is composed of the re-evaluated tree to the left of the
split, the actual split leaf, a reassembled tree of the nodes cut off to the
right of the traversal and the measurement traversed, i.e. the weight of the
left tree plus the split leaf."
  [root pred]
  (let [reassemble (fn [coll]
                     (if (empty? coll)
                       (rope)
                       (->> coll (reduce conc) rope)))]
    (loop [acc (), zipper (rope-zip (if (zero? (count root)) (rope "") root)),
           total-weight (measurement)]
      (let [node (zip/node zipper), node-weight (measure node), new-weight (measure+ node-weight total-weight)]
        (if (string? node)
          (if (pred new-weight node)
            (throw (IndexOutOfBoundsException. (str "Node index out of range: " (pr-str new-weight))))
            [(-> zipper zip/remove zip/root), node, (reassemble acc), new-weight])
          (let [left (zip/down zipper), right (zip/right left)]
            (cond (nil? right) (recur acc left total-weight)
                  (pred new-weight node) (recur acc right new-weight)
                  :default
                  (recur (cons (zip/node right) acc)
                         (-> right (zip/replace nil) zip/left)
                         total-weight))))))))

;; Presumably faster than iterating till zip/end?
(defn conjoin
  "conj for Ropes. x is concatenated to the depth-first end, i.e. rightmost node of the Rope

Additional xs will be concatenated first."
  ([root x]
     (loop [zipper (rope-zip root)]
       (let [left-child (zip/down zipper) right-child (zip/right left-child)]
         (cond (and right-child (rope? (zip/node right-child))) (recur right-child)
               (and left-child (rope? (zip/node left-child))) (recur left-child)
               :default (-> zipper (zip/edit #(conc %1 %2) x) zip/root)))))
  ([root x & xs]
     (conjoin root (reduce conc x xs))))

(defn- rope-split-at
  "Split Rope at index

The additional remaining string index will be the fourth vector element"
  [root index]
  (if (= (count root) index)
    [root "" (rope) 0]
    (let [[left-rope string right-rope _] (rope-split root (fn [weight _] (>= index (:length weight))))
          position (- index (count left-rope))]
      [left-rope string right-rope position])))

(defn- split-lines
  [s]
  (seq (.split #"\n" s -1)))

(defn- rope-split-at-line
  "Split Rope at line

The resulting vector consists of the left rope, lines left of the target line,
then the same for everything right of the line."
  [root index]
  (let [[left-rope string right-rope _] (rope-split root (fn [weight _] (> index (:lines weight)))),
        position (- index (:lines (measure left-rope))),
        [left-lines [line & right-lines]] (split-at position (split-lines string))]
    [left-rope left-lines line right-lines right-rope]))

(defn insert
  "Insert string in Rope at index or line/column"
  ([root index string]
     (if (= index (count root))
       (conjoin root string)
       (let [[left-rope target right-rope position] (rope-split-at root index)]
         (conc (conjoin left-rope (subs target 0 position) string (subs target position))
               right-rope))))
  ([root line column string]
     (let [[left-rope left-lines line right-lines right-rope] (rope-split-at-line root line)]
       (conc (conjoin left-rope
                      (apply str (interpose \n left-lines))
                      "\n" (subs line 0 column) string (subs line column)
                      (apply str (interpose \n right-lines)))
             right-rope))))

(defn report
  "Report in O(log n) time for Ropes, the equivalent of string subs"
  ([root start]
     (if (= start (count root))
       "" ; For consistency with "normal" subs
       (let [[_ string right-rope position] (rope-split-at root start)]
         (conc (subs string position) right-rope))))
  ([root start end]
     {:pre [(>= (- end start) 0)]}
     (let [length (count root)]
      (cond (= start length) ""
            (> end length) (throw (IndexOutOfBoundsException. (str "Rope index out of range: " end)))
            :default
            (let [[_ part1 right-rope position1] (rope-split-at root start)
                  length (- end start)
                  rest (- (count part1) position1)]
              (cond (= length (+ rest (count right-rope))) (conc (subs part1 position1) right-rope)
                    (> length rest)
                    (let [[middle-rope part2 _ position2] (rope-split-at right-rope (- length rest))]
                      (conc (subs part1 position1) (conjoin middle-rope (subs part2 0 position2))))
                    :default (subs part1 position1 (+ position1 length)))))))
  ([root start-line start-column end-line end-column]
     {:pre [(>= (- end-line start-line) 0)]}
     (if (and (= start-line end-line)
              (= start-column end-column))
       ""
       (report root (translate root start-line start-column) (translate root end-line end-column)))))

(defn translate
  "Translate between Rope measurements"
  ([root row column]
     {:pre [(>= row 0) (>= column 0)]}
     (if (> row (-> root measure :lines))
       (throw (IndexOutOfBoundsException. (str "Line index out of range: " row)))
       (let [[left-rope left-lines line right-lines right-rope] (rope-split-at-line root row),
             offset (+ (count left-rope) (count (apply concat left-lines)) (count left-lines) column),
             delta (- column (count line))]
         (if (and (pos? delta) (empty? right-lines)
                  (not (and (>= (count right-rope) delta)
                            (zero? (first (translate right-rope delta))))))
           (throw (IndexOutOfBoundsException. (str "String index out of range: " column)))
           offset))))
  ([root index]
     {:pre [(>= index 0)]}
     (let [[left-rope leaf _ rest] (rope-split-at root index),
           lines (split-lines (subs leaf 0 rest)),
           rope-weight (-> left-rope measure :lines),
           weight (dec (count lines))]
       (if (zero? weight)
         (let [[_ _ line _ right-rope] (rope-split-at-line left-rope rope-weight)]
           [rope-weight, (+ (count line) (count right-rope) (-> lines first count))])
         [(+ rope-weight weight), (-> lines last count)]))))

(defn delete
  "Delete the characters at interval [start..end] in the Rope"
  ([root start end]
     {:pre [(>= (- end start) 0)]}
     (cond
      (= start end) root
      (= end (count root))
      (let [[left-rope part _ position] (rope-split-at root start)]
        (conjoin left-rope (subs part 0 position)))
      :default
      (let [[left-rope part1 _ position1] (rope-split-at root start)
            [_ part2 right-rope position2] (rope-split-at root end)]
        (conc (conjoin left-rope (subs part1 0 position1) (subs part2 position2))
              right-rope))))
  ([root start-line start-column end-line end-column]
     {:pre [(>= (- end-line start-line) 0)]}
     (if (and (= start-line end-line)
              (= start-column end-column))
       root
       (delete root (translate root start-line start-column) (translate root end-line end-column)))))

(defn delete-line
  "Delete the line at index in the Rope"
  [root index]
  (delete root index 0 (inc index) 0))

(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level]
  Measurable
  (measure [_] weight)

  Treeish-2-3
  (split [this pred] (rope-split this pred))
  (conc [this tree] (cat this tree))
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
  (subSequence [this start end] (report this start end))
  (toString [this] (rope->string this)))  

(defmethod print-method Rope [rope writer]
  (print-simple (format "#<Rope %s>" (->> (measure rope) vals (join "/"))) writer))

; Must be defined here, else the dispatch on type/class will fail
(defmethod cat [Rope String] [coll string]
  (let [right-child (right coll)]
    (if (and (= String (type right-child))
             (<= (+ (count right-child) (count string)) *leaf-cutoff-length*))
      (rope (left coll) (str right-child string))
      (cat coll (rope string)))))
