(ns engine.devel.benchmark
  (:use engine.data.rope
        engine.devel.benchmark-data
        [engine.core :only [defdispatcher]]
        [incanter core stats charts optimize])
  (:require [clojure.zip :as zip]))

(defn trivial-benchmark [thunk]
  (-> (let [start (. System nanoTime)]
        (thunk)
        (- (. System nanoTime) start))
      (/ 1000000) double))

(defn show-insert-plot []
  (let [x (range 0 (count *data*) 100000),
        X (reduce bind-columns (for [i (range 1 11)] (pow x i))),
        string-insert *string-insert-y*,
        rope-insert *rope-insert-y*]
      (view (doto (scatter-plot x string-insert
                                :title "Rope / String insert benchmark 100 ops"
                                :legend true
                                :x-label "Length (characters)"
                                :y-label "Time spent (milliseconds)")
              (add-points x rope-insert)
              (add-lines x (-> (linear-model string-insert X) :fitted))
              (add-lines x (-> (linear-model rope-insert X) :fitted))))))

; Wow, conjoin seems to have constant time costs!!
(defn show-concatenate-plot []
  (let [x (range 0 (count *data*) 100000),
        X (reduce bind-columns (for [i (range 1 11)] (pow x i))),
        string-concatenate *string-concatenate-y*,
        rope-cat *rope-cat-y*,
        rope-conjoin *rope-conjoin-y*]
   (view (doto (scatter-plot x string-concatenate
                             :title "Rope / String concatenate benchmark"
                             :legend true
                             :x-label "Length (characters)"
                             :y-label "Time spent (milliseconds)")
           (add-points x rope-cat)
           (add-points x rope-conjoin)
           (add-lines x (-> (linear-model string-concatenate X) :fitted))
           (add-lines x (-> (linear-model rope-cat X) :fitted))
           (add-lines x (-> (linear-model rope-conjoin X) :fitted))))))

(defn show-split-plot []
  (let [x (range 0 (count *data*) 100000),
        X (reduce bind-columns (for [i (range 1 11)] (pow x i))),
        string-split *string-split-y*,
        rope-split *rope-split-y*]
   (view (doto (scatter-plot x string-split
                        :title "Rope / String split benchmark 100 ops"
                        :legend true
                        :x-label "Length (characters)"
                        :y-label "Time spent (milliseconds)")
           (add-points x rope-split)
           (add-lines x (-> (linear-model string-split X) :fitted))
           (add-lines x (-> (linear-model rope-split X) :fitted))))))

#_(defn show-leaflength-plot []
  (let [x (range 0 (count *data*) 10000),
        X (reduce bind-columns (for [i (range 1 11)] (pow x i))),
        [time-2 time-4 time-8 time-16 time-32 time-64 time-128 time-256 time-512] *rope-split-leaflengths-y*]
    (view (doto (xy-plot x (-> (linear-model time-2 X) :fitted)
                          :title "Rope split benchmark 100 ops"
                          :legend true
                          :x-label "Length (characters)"
                          :y-label "Time spent (milliseconds)")
             #_(add-lines x (-> (linear-model time-4 X) :fitted))
             #_(add-lines x (-> (linear-model time-8 X) :fitted))
             #_(add-lines x (-> (linear-model time-16 X) :fitted))
             #_(add-lines x (-> (linear-model time-32 X) :fitted))
             #_(add-lines x (-> (linear-model time-64 X) :fitted))
             #_(add-lines x (-> (linear-model time-128 X) :fitted))
             #_(add-lines x (-> (linear-model time-256 X) :fitted))
             (add-lines x (-> (linear-model time-512 X) :fitted))))))

(defn show-leaflength-plot []
  (with-data (*rope-split-benchmark-dataset* :y)
    (let [x (*rope-split-benchmark-dataset* :x),
          X (reduce bind-columns (for [i (range 1 11)] (pow x i))),
          time-8 ($ :leaf-8),
          plot (xy-plot x (-> (linear-model time-8 X) :fitted)
                       :title "Rope split benchmark 100 ops"
                       :legend true
                       :x-label "Length (characters)"
                       :y-label "Time spent (milliseconds)"
                       :series-label (str "lm leaf-8"))]
      (add-lines plot x (-> (linear-model ($ :leaf-16) X) :fitted) :series-label (str "lm leaf-16"))
      (add-lines plot x (-> (linear-model ($ :leaf-32) X) :fitted) :series-label (str "lm leaf-32"))
      (add-lines plot x (-> (linear-model ($ :leaf-64) X) :fitted) :series-label (str "lm leaf-64"))
      (add-lines plot x (-> (linear-model ($ :leaf-128) X) :fitted) :series-label (str "lm leaf-128"))
     (view plot))))
