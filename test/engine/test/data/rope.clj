(ns engine.test.data.rope
  (:use clojure.test clojure.test.tap)
  (:require [clojurecheck.core :as cc]
            [engine.data.rope :as rope]))

(def ascii (cc/int :lower 0 :upper 127))
(def string-int (cc/int :lower 0 :upper 1048576))

(defn rand-string
  ([] (cc/string (fn [size] (char (ascii size)))))
  ([length] (cc/string (fn [size] (char (ascii size))) :length (fn [_] length))))

(defmacro with-rope-test-bindings [& body]
  `(do
     (binding [cc/*size-scale* ~(fn [n] (Math/pow n 3.5)),
               cc/*trials* 50]
       ~@body)
     (binding [cc/*size-scale* ~(fn [n] (* n 2)),
               cc/*trials* 1000]
       ~@body)))

(defmacro defropetest [name & body]
  `(deftest ~name
     (with-rope-test-bindings
       ~@body)))

(deftest fib-seq-test
  (is (= [0 1 1 2 3 5 8 13] (take 8 rope/fib-seq))))

(deftest count-matches-test
  (cc/property "count matches evaluates to the number of characters in a string"
               [haystack (rand-string)
                needle ascii]
               (let [needle (char needle)]
                 (is (== (->> haystack (re-seq (re-pattern (str "\\Q" needle "\\E"))) count)
                         (rope/count-matches needle haystack))))))

(deftest rope?-test
  (are [x y] (= x (rope/rope? y))
       true (rope/rope)
       false nil
       false "foo"
       false 4
       false [:foo]
       false '(:foo)))

; deactivated until bug in clojurecheck fixed
#_(deftest conc-test
  (cc/property "conc produces flat strings until *leaf-cutoff-length* is reached"
               [string (cc/string (fn [size] (char (ascii size))) :length (cc/int :lower 0 :upper rope/*leaf-cutoff-length*))]
               (is (string? (rope/conc string ""))))
  (cc/property "conc produces actual Ropes, if beyond *leaf-cutoff-length*"
               [length (cc/int :lower (inc rope/*leaf-cutoff-length*)),
                string (cc/string (fn [size] (char (ascii size))) :length (cc/int :lower (inc rope/*leaf-cutoff-length*)))]
               #_(println length)
               (is (rope/rope? (rope/conc string "")))))

(defropetest weigh-test
  (cc/property "weighing a text container works independently from the underlying data structure"
               [length string-int, string (rand-string length)]
               (is (= length (count string))) ; cross-check
               (is (= length (count (rope/string->rope string))))))

(defropetest report-test
  (cc/property "all substrings requested match those of equivalent flat strings"
               [length string-int, string (rand-string length),
                start (cc/int :lower 0 :upper length)
                end (cc/int :lower start :upper length)]
               (is (= (subs string start end)
                      (str (rope/report (rope/rope string) start end))))))

(defropetest insert-test
  (cc/property "inserting strings works as expected"
               [length string-int, string (rand-string length),
                position (cc/int :lower 0 :upper length)
                string2 (rand-string)]
               (is (= (str (subs string 0 position) string2 (subs string position))
                      (str (rope/insert (rope/rope string) position string2))))))

(defropetest delete-test
  (cc/property "deleting parts of a Rope works as expected"
               [length string-int, string (rand-string length),
                start (cc/int :lower 0 :upper length)
                end (cc/int :lower start :upper length)]
               (is (= (str (subs string 0 start) (subs string end))
                      (str (rope/delete (rope/rope string) start end))))))
