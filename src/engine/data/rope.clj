(ns engine.data.rope
  "Implementation of Ropes as per \"Ropes: an Alternative to Strings\"
by Boehm, Hans-J; Atkinson, Russ; and Plass, Michael (December 1995), doi:10.1002/spe.4380251203."
  (:refer-clojure :exclude [merge])
  (:use [clojure.string :only [join]])
  (:require [clojure.zip :as zip])
  (:import [clojure.lang Counted Indexed Delay IDeref ILookup]
           [java.lang String IndexOutOfBoundsException]
           [java.io File RandomAccessFile]))

(def fib-seq (map first (iterate (fn [[a b]] [b (+ a b)]) [0 1])))
(def ^:dynamic *leaf-cutoff-length* 64) ; This value has been determined optimal for split operations through benchmarking

(defprotocol Measurable
  "Trivial protocol for things measurable

Default implementations are provided for nil and String; nil measures as 0
characters, 0 lines. Strings get analyzed accordingly."
  (measure [object] "Measure object"))

(defn measurable?
  "Does x satisfy Measurable?"
  [x]
  (satisfies? Measurable x))

(defrecord CharSequenceMeasurement [#^long length #^long lines])

(defmethod print-method CharSequenceMeasurement [weight writer]
  (print-simple (format "#<CharSequenceMeasurement %d/%d>" (:length weight) (:lines weight)) writer))

(defn measurement
  "CharSequenceMeasurement from length and lines

 (measurement) is 0/0"
  ([] (CharSequenceMeasurement. 0 0))
  ([length lines] (CharSequenceMeasurement. length lines)))

(defn measure+
  "Add up weights of measurements"
  [& xs]
  (->> xs (map vals) (reduce #(map + %1 %2)) (apply measurement)))

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
  "Protocol describing 2-3-Trees, such as Ropes

Default implementations for nil and String exist."
  (split [tree pred] "Split tree by pred")
  (conc [tree1 tree2] "Concatenate tree1 and tree2")
  (left [tree] "Left child of tree")
  (right [tree] "Right child of tree")
  (depth [tree] "Depth of tree")
  (leaf? [tree] "Is tree a leaf?"))

(defn- merge
  "Merge two objects from a 2-3-tree perspective"
  [obj1 obj2]
  (let [count1 (count obj1), count2 (count obj2)]
    (cond (zero? (+ count1 count2)) nil
          (zero? count1) obj2
          (zero? count2) obj1
          :default (conc obj1 obj2))))

(defn treeish-2-3?
  "Does x satisfy Treeish-2-3?"
  [x]
  (satisfies? Treeish-2-3 x))

(defn weigh
  "The accumulated measurement of all children to the right

Provided that Treeish-2-3 is implemented for seq.
In other words: What a new Treeish-2-3 node would weigh with seq as its left child."
  [seq]
  {:pre [(measurable? seq)]}
  (if (and (treeish-2-3? seq) (right seq)) ; no child on the right? not required to weigh again
    (->> seq (iterate right) (take-while identity) (map measure) (apply measure+))
    (measure seq)))

(defmulti cat
  "Concatenation of two Ropes, including Strings which are treated as leafes"
  (fn [rope1 rope2] [(type rope1) (type rope2)]))

(extend-type nil
  Treeish-2-3
  (split [_ _] nil)
  (conc [_ obj] obj)
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0)
  (leaf? [_] true))

(extend-type String
  Treeish-2-3
  (split [_ _] nil)
  (conc [this coll] (cat this coll))
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0)
  (leaf? [_] true))


(defprotocol Ropey
  "Rope-specific protocol for things that don't fit into general 2-3-Trees."
  (balanced? [rope] "Is rope balanced?"))

; necessary forward declaration
(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level])

(defn rope?
  "Is x a Rope?"
  [x]
  (instance? Rope x))

(defn rope
  "New Rope that concatenates the given arguments, if any"
  ([] (Rope. nil nil (measurement 0 0) 0))
  ([seq] (Rope. seq nil (weigh seq) (inc (depth seq))))
  ([seq1 seq2] (Rope. seq1 seq2 (weigh seq1) (inc (max (depth seq1) (depth seq2))))))

(defn- rooted
  "Evaluate to properly rooted Rope without a right child, if necessary"
  [root]
  (if (and (rope? root) (nil? (right root)))
    root
    (rope root)))

(defn rope-zip
  "Creates Zipper structures for Ropes, starting from root"
  [root]
  (zip/zipper rope?
              (fn [node] (filter identity [(left node) (right node)]))
              (fn [node children] (apply rope children))
              root))

(defn- rope-nth-node
  "Vector of zip node and rest index of Rope at index"
  [root index]
  (loop [zipper (rope-zip root) l-index index]
    (if zipper
      (let [node (zip/node zipper)]
        (if (leaf? node)
          [zipper l-index]
          (let [weight (-> node measure :length) next (zip/down zipper)]
            (if (<= weight l-index)
              (recur (zip/right next) (- l-index weight))
              (recur next l-index)))))
      (throw (IndexOutOfBoundsException. (str "Rope index out of range: " index))))))

(defn rope-nth
  "Evaluates to character at position index in the Rope at root in O(log n) time"
  [root index]
  (let [[zipper index] (rope-nth-node root index)]
    (nth (zip/node zipper) index)))

(defn rope-seq
  "Lazy sequence of all Rope nodes at root, depth-first order"
  [root]
  (for [node (iterate zip/next (rope-zip root)) :while (not (zip/end? node))]
    (zip/node node)))

(defn rope-reverse-seq
  "Lazy reverse sequence of all Rope nodes from the end of root, depth-first order"
  [root]
  (let [[end _] (rope-nth-node root (dec (count root)))]
    (for [node (iterate zip/prev end) :while (zip/up node)]
      (zip/node node))))

(defn index-of
  "The position of the first matching char in Rope

Or -1, if none or not applicable. Resembles String's indexOf."
  [root char]
  (loop [leafes (filter leaf? (rope-seq root)), pos 0]
    (if (nil? leafes)
      -1
      (let [leaf (first leafes),
            index (.indexOf leaf (str char))]
        (if (= -1 index)
          (recur (next leafes) (+ pos (count leaf)))
          (+ pos index))))))

(defn last-index-of
  "The position of the last matching char in Rope

Or -1, if none or not applicable. Resembles String's lastIndexOf."
  [root char]
  (loop [leafes (filter leaf? (rope-reverse-seq root)), end (count root)]
    (if (nil? leafes)
      -1
      (let [leaf (first leafes),
            index (.lastIndexOf leaf (str char)),
            end (- end (count leaf))]
        (if (= -1 index)
          (recur (next leafes) end)
          (+ end index))))))

(defn rope->string
  "Flat string from Rope at root"
  [root]
  (apply str (->> (rope-seq root) (filter leaf?))))

(defn rebalance
  "Rebalanced copy of the Rope provided

Using the algorithm described in \"Ropes: an Alternative to Strings\".
The implementation does not resemble the one from the paper, but is instead
idiomatic Clojure and optimized for functional languages in general."
  [root]
  (let [fold (fn [coll] (reduce rope (reverse coll)))
        fib-border (fn [limit] (->> fib-seq (take-while #(>= limit %)) last))]
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

(defn string->rope
  "Fresh Rope from flat string

The resulting leafes will carry at most *leaf-cutoff-length* characters of the
input string."
  [string]
  (letfn [(build [acc]
            (if (> (count acc) 1)
              (recur (->> (partition-all 2 acc) (map #(apply rope %))))
              (first acc)))]
    (->> (partition-all (-> (count string) (/ (.. Runtime getRuntime availableProcessors)) long (max 1)) string)
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
     {:pre [(rope? root)]}
     (loop [zipper (rope-zip root)]
       (let [left-child (zip/down zipper) right-child (zip/right left-child)]
         (cond (and right-child (rope? (zip/node right-child))) (recur right-child)
               (and left-child (rope? (zip/node left-child))) (recur left-child)
               :default (-> zipper (zip/edit #(merge %1 %2) x) zip/root)))))
  ([root x & xs]
     (clojure.tools.logging/debug (clojure.pprint/cl-format nil "going to merge [~a] [~a] ~{~^-> [~a]~}" root x xs))
     (let [result (conjoin root (reduce merge x xs))]
       (clojure.tools.logging/debug (format "result [%s]" result))
       result)))

(defn rope-split-at
  "Split Rope at index

The additional remaining string index will be the fourth vector element"
  [root index]
  (if (= (count root) index)
    [root "" (rope) 0]
    (let [[left-rope string right-rope _] (split root (fn [weight _] (>= index (:length weight))))
          position (- index (count left-rope))]
      [left-rope string right-rope position])))

(defn- split-lines
  [s]
  (seq (.split #"\n" s -1)))

(defn rope-split-at-line
  "Split Rope at line

The resulting vector consists of the left rope, lines left of the target line,
then the same for everything right of the line."
  [root index]
  (let [[left-rope string right-rope _] (split root (fn [weight _] (> index (:lines weight)))),
        position (- index (:lines (measure left-rope))),
        [left-lines [line & right-lines]] (split-at position (split-lines string))]
    [left-rope left-lines line right-lines right-rope]))

(defn insert
  "Insert string in Rope at index or line/column"
  ([root index string]
     (rooted
      (if (= index (count root))
        (conc root string)
        (let [[left-rope target right-rope position] (rope-split-at root index)]
          (merge (conjoin left-rope (subs target 0 position) string (subs target position))
                 right-rope)))))
  ([root line column string]
     (let [[left-rope left-lines line right-lines right-rope] (rope-split-at-line root line)]
       (rooted (merge (conjoin left-rope
                               (apply str (interpose \n left-lines))
                               "\n" (subs line 0 column) string (subs line column)
                               (apply str (interpose \n right-lines)))
                      right-rope)))))

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
     (if (> index (count root))
       (throw (IndexOutOfBoundsException. (str "String index out of range: " index)))
       (let [[left-rope leaf _ rest] (rope-split-at root index),
             lines (split-lines (subs leaf 0 rest)),
             rope-weight (-> left-rope measure :lines),
             weight (dec (count lines))]
         (if (zero? weight)
           (let [[_ _ line _ right-rope] (rope-split-at-line left-rope rope-weight)]
             [rope-weight, (+ (count line) (count right-rope) (-> lines first count))])
           [(+ rope-weight weight), (-> lines last count)])))))

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

(defn delete
  "Delete the characters at interval [start..end] in the Rope"
  ([root start end]
     {:pre [(>= (- end start) 0)]}
     (cond
      (= start end) root
      (= end (count root))
      (if (= start 0)
        (rope)
        (let [[left-rope part _ position] (rope-split-at root start),
              snippet (subs part 0 position)]
          (rooted (merge left-rope snippet))))
      :default
      (let [[left-rope part1 _ position1] (rope-split-at root start)
            [_ part2 right-rope position2] (rope-split-at root end),
            new-left (conjoin left-rope (subs part1 0 position1) (subs part2 position2))]
        (rooted (merge new-left right-rope)))))
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
  (leaf? [_] false)

  Ropey
  (balanced? [this] (>= (count this) (nth fib-seq (+ level 2))))

  Counted
  (count [this] (:length (weigh this)))

  Indexed
  (nth [this index] (rope-nth this index))
  (nth [this index not-found]
    (try (rope-nth this index)
         (catch Exception _ not-found)))

  CharSequence
  (charAt [this index] (nth this index))
  (length [this] (count this))
  (subSequence [this start end] (report this start end))
  (toString [this] (rope->string this))

  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key not-found]
    (if (number? key)
      (try (nth this key)
           (catch Exception _ not-found))
      not-found)))

(defmethod print-method Rope [rope writer]
  (print-simple (format "#<Rope %s>" (->> (measure rope) vals (join "/"))) writer))

; Must be defined here, else the dispatch on type/class will fail
(defmethod cat [Rope String] [coll string]
  (let [right-child (right coll)]
    (if (and (= String (type right-child))
             (<= (+ (count right-child) (count string)) *leaf-cutoff-length*))
      (rope (left coll) (str right-child string))
      (cat coll (rope string)))))

(deftype Bud [#^Delay seed #^CharSequenceMeasurement weight]
  Measurable
  (measure [_] (if (realized? seed)
                 (measure @seed)
                 weight))

  IDeref
  (deref [_] (force seed))

  Treeish-2-3
  (split [_ _] nil)
  (conc [this coll] (cat this coll))
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0)
  (leaf? [_] true)

  Counted
  (count [this] (:length (weigh this)))

  Indexed
  (nth [this index] (nth @seed index))
  (nth [this index not-found]
    (try (nth @seed index)
         (catch Exception _ not-found)))

  CharSequence
  (charAt [this index] (nth @seed index))
  (length [this] (count this))
  (subSequence [this start end] (subs @seed start end))
  (toString [this] @seed)

  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key not-found]
    (if (number? key)
      (try (nth this key)
           (catch Exception _ not-found))
      not-found)))

(defmethod print-method Bud [bud writer]
  (print-simple (format "#<Bud %s>" (->> (measure bud) vals (join "/"))) writer))

(defn file-chunk
  "Fresh Bud that reads from file"
  [file pos len]
  (let [readfn (fn []
                 (with-open [file (RandomAccessFile. file "r")]
                   (let [data (byte-array len)]
                     (doto file (.seek pos) (.read data 0 len))
                     (String. data))))]
   (Bud. (delay (readfn))
         (measure (readfn)))))

(defn- span [start end step]
  (let [coll (->> (iterate (fn [[a b]] [(inc b) (+ (inc b) (- b a))]) [start (dec (+ start step))])
                  (take-while #(< (second %) end)))]
    (concat (butlast coll) [[(-> coll last first) (dec end)]])))

(defn file->rope
  "Fresh Rope from file"
  [path]
  (let [file (File. path), size (.length file),
        step (-> size (/ (.. Runtime getRuntime availableProcessors)) Math/ceil long (max 1))]
    (letfn [(build [acc]
              (if (> (count acc) 1)
                (recur (->> (partition-all 2 acc) (map #(apply rope %))))
                (first acc)))]
      (->> (span 0 size step)
           (pmap (bound-fn [[pos limit]]
                   (->> (span pos (inc limit) *leaf-cutoff-length*)
                        (map (fn [[start end]] (file-chunk file start (inc (- end start))))) build)))
           build rope))))

(deftype LeafSeq [cache coll #^long length]
  CharSequence
  (charAt [_ index]
    (if (< index (count @cache))
      (nth @cache index)
      (loop [coll coll, index index]
        (if coll
          (let [leaf (first coll), length (count leaf)]
            (swap! cache str leaf)
            (if (>= index length)
              (recur (next coll) (- index length))
              (nth leaf index)))
          nil))))
  
  (length [_] length)
  (subSequence [_ start end] nil)
  (toString [_] (str cache (apply str coll))))

(defn leaf-seq [root]
  (LeafSeq. (atom "") (->> (rope-seq root) (filter leaf?)) (count root)))
