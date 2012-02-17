(ns engine.devel.benchmark-data
  (:use engine.data.rope
        [incanter core io datasets]))

(defonce *data* (slurp "/usr/share/dict/linux.words"))
(defonce *rope* (string->rope *data*))
(def *x* (range 0 (count *data*) 10000))

#_(def *string-insert-y*
    (doall
     (for [length *x*]
       (let [target (subs *data* 0 length)]
         (println "step" length)
         (trivial-benchmark
          #(doseq [index (range 0 length (-> length (/ 100) Math/ceil int))]
             (str (subs target 0 index) "hello world!" (subs target index))))))))

#_(def *rope-insert-y*
    (doall
     (for [length *x*]
       (let [rope (string->rope (subs *data* 0 length))]
         (println "step" length)
         (trivial-benchmark
          #(doseq [index (range 0 length (-> length (/ 100) Math/ceil int))]
             (insert rope index "hello world!")))))))

(def *string-insert-y*
  [0.160253 15.48646 36.073431 77.679091 80.168163 99.861025 134.268341
   160.223507 186.510242 244.631871 285.341968 290.379827 322.184602 358.179604
   394.778592 434.769124 472.929808 531.096004 517.410618 550.359544 606.432728
   624.010738 644.796173 668.763184 745.628053 749.68519 769.377433 833.054253
   858.80551 851.052036 901.333624 922.871869 941.495218 979.292345 1008.193844
   1043.527509 1096.882727 1109.669314 1147.668764 1258.902072 1230.489952
   1221.093326 1253.530514 1285.024066 1305.980453 1348.645927 1382.128445
   1426.691329 1429.405436 1459.022822])

(def *rope-insert-y*
  [0.302833 137.619735 146.053564 158.864655 157.472143 163.244358 183.966368
   189.337575 176.705796 213.576086 170.135123 177.658389 212.077855 210.70526
   205.849998 207.825015 194.601511 214.643124 227.699218 246.821333 202.362264
   186.250143 204.033151 208.136948 217.505285 217.273196 240.569096 204.523029
   233.14762 182.01235 247.145981 266.203584 254.612173 226.245118 228.165559
   242.886874 252.322114 235.940062 277.423244 208.056512 213.854219 242.248856
   217.314858 217.145382 230.743301 216.776049 216.690284 255.249923 228.882249
   221.043069])

#_(def *string-concatenate-y*
    (doall
     (for [length *x*]
       (let [target (subs *data* 0 length)]
         (println "step" length)
         (trivial-benchmark
          #(str target "hello world!"))))))

#_(def *rope-concatenate-y*
  (doall
   (for [length *x*]
     (let [target (string->rope (subs *data* 0 length))]
       [(trivial-benchmark (cat target "hello world!"))
        (trivial-benchmark (conjoin target "hello world!"))]))))

(def *string-concatenate-y*
  [0.100662 0.125744 0.976599 0.480629 0.735787 0.997457 1.252008 1.422528
   1.732315 2.122159 2.565919 3.000306 2.734474 3.250319 3.436446 4.628305
   4.283404 4.243877 4.729931 5.823881 5.423131 5.747468 5.608285 5.951176
   6.21726 5.801651 6.449868 7.808984 7.429677 21.858138 7.273794 7.62501
   7.466191 7.650027 8.828216 9.500287 8.838033 9.921307 10.225489 9.998864
   10.910585 9.896103 10.782164 12.974705 12.520404 12.154981 11.334255 12.97311
   12.726435 12.241527])

(def *rope-concatenate-y*
  [[0.128056 0.117714] [0.082605 0.24129] [0.087402 0.164808]
   [0.084876 0.139223] [0.131056 0.278588] [0.085004 0.17345]
   [0.084478 0.194856] [0.083304 0.219376] [0.081837 0.154586]
   [0.085123 0.197841] [0.116788 0.298203] [0.084022 0.131436]
   [0.084842 0.175144] [0.088832 0.21484] [0.118101 0.376738]
   [0.112424 0.219648] [0.092005 0.190692] [0.084503 0.334967]
   [0.089963 0.162573] [0.130786 0.179819] [0.090767 0.25176]
   [0.113139 0.232279] [0.085897 0.198876] [0.08431 0.278109]
   [0.115245 0.194365] [0.095472 0.246594] [0.08991 0.235115]
   [0.113027 0.171328] [0.116058 0.241711] [0.112292 0.578811]
   [0.086507 0.228998] [0.115982 0.278244] [0.084362 0.21754]
   [0.092805 0.252767] [0.084277 0.172617] [0.083458 0.128575]
   [0.083648 0.208729] [0.087173 0.161492] [0.083426 0.193042]
   [0.086172 0.232165] [0.108886 0.14641] [0.09724 0.207974]
   [0.091169 0.198262] [0.083731 0.132136] [12.558462 0.210884]
   [0.09532 0.198078] [0.085343 0.409782] [0.083671 0.171291]
   [0.086554 0.179027] [0.108409 0.284768]])

(def *rope-cat-y* (map first *rope-concatenate-y*))
(def *rope-conjoin-y* (map second *rope-concatenate-y*))

#_(def *string-split-y*
    (doall
     (for [length *x*]
       (let [target (subs *data* 0 length)]
         (println "step" length)
         (trivial-benchmark
          #(doseq [index (range 0 length (-> length (/ 100) Math/ceil int))]
             (split-at index target)))))))

#_(def *rope-split-y*
    (doall
     (for [length *x*]
       (let [rope (string->rope (subs *data* 0 length))]
         (println "step" length)
         (trivial-benchmark
          #(doseq [index (range 0 length (-> length (/ 100) Math/ceil int))]
             (split rope (fn [weight _] (>= index weight)))))))))

(def *rope-split-y*
  [0.156201 148.551091 138.736069 151.369548 146.35905 151.453159 213.715471
   181.583467 160.604711 204.885801 157.656843 156.697332 187.541354 197.915087
   197.679775 193.908307 165.963136 191.183715 213.146733 214.650724 166.433979
   171.499096 184.080231 176.901643 200.205886 194.9796 205.591525 181.669107
   205.611644 154.288757 215.160897 214.432814 176.0866 219.759319 209.604941
   230.189959 235.913218 209.234538 249.080262 197.606646 186.308343 235.641774
   173.167206 193.134369 201.343039 206.400799 213.944348 241.823362 209.035896
   205.239211])

(def *string-split-y*
  [0.142756 0.135567 0.081981 0.093151 0.082321 0.097078 0.080524 0.081522
   0.081725 0.081463 0.081777 0.089279 0.081248 0.081257 0.081462 0.080859
   0.080824 0.081103 0.0807 0.081487 0.081554 0.114681 0.122442 0.120119 0.127338
   0.143982 0.128128 0.129495 0.130498 0.129546 0.143233 0.129286 0.194195
   0.141995 0.152468 0.137954 0.136956 0.138067 0.136574 0.147214 0.135858
   0.135734 0.138555 0.137098 0.147677 0.135976 0.135324 0.153858 0.136084
   0.146774])

(comment
 (def *rope-split-benchmark-raw*
   (doall
    (for [length (range 0 (count *data*) 10000),
          leaf-size (drop 2 (take 18 (iterate #(* 2 %) 2))) ]
      (binding [*leaf-cutoff-length* leaf-size]
        (let [rope (string->rope (subs *data* 0 length))]
          (println "step" length leaf-size)
          (trivial-benchmark
           #(doseq [index (range 0 length (-> length (/ 100) Math/ceil int))]
              (split rope (fn [weight _] (>= index weight))))))))))

 (def *rope-split-benchmark-dataset*
   {:x (range 0 (count *data*) 10000)
    :y (->> (apply interleave (partition 16 *rope-split-benchmark-raw*))
            (partition (Math/ceil (/ (count *data*) 10000)))
            (zipmap (->> (drop 2 (take 18 (iterate #(* 2 %) 2))) (map #(str "leaf-" %)) (map keyword))))})

(-> (partition 16 *rope-split-benchmark-raw*) to-dataset (save "rope-split-benchmark.csv" :header
                                                               (->> (drop 2 (take 18 (iterate #(* 2 %) 2))) (map #(str "leaf-" %))))))

(def *rope-split-benchmark-dataset*
  {:x (range 0 (count *data*) 10000)
   :y (read-dataset (.. (clojure.java.io/resource "benchmark/rope-split-benchmark.csv") getFile) :header true)})
