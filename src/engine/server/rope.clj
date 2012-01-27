(ns engine.server.rope
  (:use [clojure.string :only [join]])
  (:require [clojure.zip :as zip])
  (:import [clojure.lang IPersistentCollection ITransientCollection Seqable Counted Indexed ISeq]
           [java.lang String IndexOutOfBoundsException]))

(def *fib-seq* (map first (iterate (fn [[a b]] [b (+ a b)]) [0 1])))
(def *leaf-cutoff-length* 128)

(defprotocol Measurable
  (measure [object]))

(defrecord CharSequenceMeasurement [#^int length #^int lines])

(defn measurement [length lines]
  (CharSequenceMeasurement. length lines))

(defn count-matches [char string]
  (->> string (filter #(= char %)) count))

(defn measure-string [string]
  (measurement (count string) (count-matches \newline string)))

(extend-type nil
  Measurable
  (measure [object] (measurement 0 0)))

(extend-type String
  Measurable
  (measure [string] (measure-string string)))


(defprotocol Treeish-2-3
  (split [tree pred])
  (conc [tree1 tree2])
  (left [tree])
  (right [tree])
  (depth [tree]))

(defprotocol Ropey
  (balanced? [rope]))

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


; necessary forward declaration
(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level])
        
(defn rope? [x]
  (instance? Rope x))

(defn weigh [seq]
  (if (and (rope? seq) (right seq)) ; no child on the right? not required to weigh again
    (->> seq (iterate right) (take-while identity) (map measure) (map vals) (reduce #(map + %1 %2)) (apply measurement))
    (measure seq)))

(defn rope
  ([] (Rope. nil nil (measurement 0 0) 0))
  ([seq] (Rope. seq nil (weigh seq) (inc (depth seq)))) 
  ([seq1 seq2] (Rope. seq1 seq2 (weigh seq1) (inc (max (depth seq1) (depth seq2))))))

(defn rope-zip [root]
  (zip/zipper rope?
              (fn [node] (filter identity [(left node) (right node)]))
              (fn [node children] (apply rope children))
              root))

(defn rope-nth [rope index]
  (loop [zipper (rope-zip rope) l-index index]
    (let [node (zip/node zipper)]
      (cond (string? node) (nth node l-index)
            node
            (let [weight (-> node measure :length) next (zip/down zipper)]
              (if (<= weight l-index)
                (recur (zip/right next) (- l-index weight))
                (recur next l-index)))
            :default (throw (IndexOutOfBoundsException. (str "String index out of range: " index)))))))

(defn rope-seq [rope]
  (for [node (iterate zip/next (rope-zip rope)) :while (not (zip/end? node))]
    (zip/node node)))

(defn rope-str [rope]
  (->> (rope-seq rope) (filter string?) (reduce str)))

(defn rope-rebalance [coll] 
  (let [fold (fn [coll] (reduce rope (reverse coll)))
        fib-border (fn [limit] (->> *fib-seq* (take-while #(>= limit %)) last))]
    (loop [acc (), coll (->> (rope-seq coll) (filter string?)), max-length nil]
      (let [current (first coll), length (count current)]
        (if current
          (if (and (seq acc) (>= length max-length))
            (let [[low high] (split-with #(>= length (count %)) acc)]
              (recur high (conj (next coll) (rope (fold low) current)) (fib-border (count (first high)))))
            (recur (cons current acc) (next coll) (fib-border length)))
          (fold acc))))))

(defmulti rope-concat (fn [rope1 rope2] [(type rope1) (type rope2)]))
(defmethod rope-concat [String String] [string1 string2] (str string1 string2))
(defmethod rope-concat :default [coll1 coll2]
  (let [new-rope (rope coll1 coll2)]
    (if (balanced? new-rope)
      new-rope
      (rope-rebalance new-rope))))

(defn rope-split
  ([coll pred] (rope-split coll pred :length))
  ([coll pred measure-key]
     (let [reassemble (fn [coll]
                        (if (empty? coll)
                          (rope)
                          (->> coll (reduce rope-concat) rope)))]
       (loop [acc (), zipper (rope-zip coll), total-weight 0]
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
  ([coll x]
     (loop [zipper (rope-zip coll)]
       (let [left-child (zip/down zipper) right-child (zip/right left-child)]
         (cond (and right-child (rope? (zip/node right-child))) (recur right-child)
               (and left-child (rope? (zip/node left-child))) (recur left-child)
               :default (-> zipper (zip/edit #(rope-concat %1 %2) x) zip/root)))))
  ([coll x & xs]
     (rope-conj coll (reduce rope-concat x xs))))

(defn- rope-split-at [coll index]
  (let [[left-rope string right-rope] (rope-split coll (fn [weight _] (>= index weight)))
        position (- index (count left-rope))]
    [left-rope string right-rope position]))

(defn rope-insert [coll index string]
  (if (= index (count coll))
    (rope-conj coll string)
    (let [[left-rope target right-rope position] (rope-split-at coll index)]
      (rope-concat (rope-conj left-rope (subs target 0 position) string (subs target position))
                   right-rope))))

(defn rope-subs
  ([coll start]
     (if (= start (count coll))
       "" ; For consistency with "normal" subs
       (let [[_ string right-rope position] (rope-split-at coll start)]
         (str (subs string position) right-rope))))
  ([coll start end]
     {:pre [(>= (- end start) 0)]}
     (if (= start (count coll))
       ""
       (let [[_ part1 right-rope position1] (rope-split-at coll start)
             length (- end start)
             rest (- (count part1) position1)]
         (if (> length rest)
           (let [[middle-rope part2 _ position2] (rope-split-at right-rope (- length rest))]
             (str (subs part1 position1) middle-rope (subs part2 0 position2)))
           (subs part1 position1 (+ position1 length)))))))

(defn rope-delete [coll start end]
  {:pre [(>= (- end start) 0)]}
  (if (= end (count coll))
    (let [[left-rope part _ position] (rope-split-at coll start)]
      (rope-conj left-rope (subs part 0 position)))
    (let [[left-rope part1 _ position1] (rope-split-at coll start)
          [_ part2 right-rope position2] (rope-split-at coll end)]
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
  (toString [this] (rope-str this))
  
  ;; ISeq
  ;; (first [_] left)
  ;; (more [this] (or (next this) (empty this)))
  ;; (next [this] (seq right))

  ;; Seqable
  ;; (seq [this] (when left this))

  ;; IPersistentCollection
  ;; (cons [this x] (rope x this))
  ;; (empty [_] (rope))
  ;; (equiv [this x] (= (str this) (str x)))

  ;; ITransientCollection
  ;; (conj [this x] (println "called") (rope-conj this x))
  ;; (persistent [this] this)
  )

(defmethod print-method Rope [rope writer]
  (print-simple (format "#<Rope %s>" (->> (measure rope) vals (join "/"))) writer))

; Must be defined here, else the dispatch on type/class will fail
(defmethod rope-concat [Rope String] [coll string]
  (let [right-child (right coll)]
    (if (and (= String (type right-child))
             (<= (+ (count right-child) (count string)) *leaf-cutoff-length*))
      (rope (left coll) (str right-child string))
      (rope-concat coll (rope string)))))

(defn string->rope [string]
  (->> (partition-all *leaf-cutoff-length* string) (map #(apply str %)) (reduce rope) rope-rebalance rope))
