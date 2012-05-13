(ns engine.test.data.rope
  (:use clojure.test clojure.test.tap)
  (:require [clojurecheck.core :as cc]
            [engine.data.rope :as rope]))

(defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
  `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
     ~@tests))

(deftest fib-seq-test
  (testing "First 8 Fibonacci numbers are matched in fib-seq"
    (is (= [0 1 1 2 3 5 8 13] (take 8 rope/fib-seq)))))

(deftest measurable?-test
  (testing "All objects are considered measurable which are supposed to be"
    (are [x y] (= x (rope/measurable? y))
         true (rope/rope)
         true nil
         true "foo"
         false 4
         false [:foo]
         false '(:foo))))

(deftest rope?-test
  (testing "All objects are considered ropes which are supposed to be"
    (are [x y] (= x (rope/rope? y))
         true (rope/rope)
         false nil
         false "foo"
         false 4
         false [:foo]
         false '(:foo))))

(deftest adding-up-measurements
  (testing "Measurements add up as expected"
    (are [x y] (= (apply rope/measurement x)
                  (apply rope/measure+ (map rope/measure y)))
         [0 0] [nil]
         [0 0] [""]
         [0 0] ["" nil "" nil]
         [10 3] ["foo\nbar" "1\n\n"]
         [10 3] [(rope/string->rope "foo\nbar") (rope/string->rope "1\n\n")]
         [10 3] ["foo\nbar" (rope/string->rope "1\n\n")]
         [10 3] [(rope/string->rope "foo\nbar") "1\n\n"])))

(deftest splitting
  (testing "Rope splitting leaves behind expected artifacts"
    (let [r (rope/rope (rope/rope (rope/rope (rope/rope "12" "34")
                                             "56")
                                  "78"))]
      (doseq [x (range (inc (count r)))]
        (let [[left-rope target right-rope _] (rope/rope-split-at r x)]
            (is (= (str r) (str left-rope target right-rope))))))))

(deftest nil-conc
  (testing "conc on nil will always evaluate to the latter"
    (doseq [x [nil :foo "a" 1 (rope/rope) []]]
      (is (= x (rope/conc nil x))))))

(deftest merging
  (with-private-fns [engine.data.rope [merge]]
    (testing "Merging objects works as expected"
      (are [x y] (= (str x) (str (apply merge y)))
           "" ["" ""]
           "" [(rope/rope) (rope/rope)]
           "" [(rope/rope) ""]
           "" ["" (rope/rope)]
           "" [(rope/rope "") ""]
           "" ["" (rope/rope "")]
           "abc" ["abc" ""]
           "abc" ["" "abc"]
           "abc" [(rope/rope) "abc"]
           "abc" ["abc" (rope/rope)]
           "abc" ["" (rope/rope "abc")]
           "abc" ["" (rope/rope "" "abc")]
           "" [nil nil]
           "" [(rope/rope) nil]
           "" [nil (rope/rope)]
           "" [(rope/rope "") nil]
           "" [nil (rope/rope "")]
           "abcdef" ["abc" "def"]
           "abcdef" ["abc" (rope/rope "def")]
           "abcdef" [(rope/rope "abc") "def"]))))

(deftest conjoining
  (testing "conjoin does its job at the right node"
    (is (= (rope/rope (rope/rope (rope/rope (rope/rope "12" "34")
                                            "56")
                                 "X78"))
           (rope/insert (rope/rope (rope/rope (rope/rope (rope/rope "12" "34")
                                            "56")
                                 "78"))
                        6 "X")))))

(deftest insertion
  (testing "Rope insertion works as determined"
    (let [s "12345678"
          r (rope/rope (rope/rope (rope/rope (rope/rope "12" "34")
                                             "56")
                                  "78"))]
      (doseq [x (range (inc (count r)))]
        (is (= (str (subs s 0 x) "X" (subs s x))
               (str (rope/insert r x "X"))))))))

#_(deftest rope-editing-sequence
  (testing "Editing sequences behaves as determined"
    (is (= "This is the scratcfh buffer. Yes :)"
           (-> (rope/string->rope "This is the scratch buffer.")
               (rope/insert 8 "1") (rope/insert 9 "2") (rope/insert 10 "3")
               (rope/delete 10 11) (rope/delete 9 10) (rope/delete 8 9)
               (rope/insert 27 " ") (rope/insert 28 "Y") (rope/insert 29 "e")
               (rope/insert 30 "s") (rope/insert 31 " ") (rope/insert 32 ":")
               (rope/insert 33 ")")
               (rope/insert 19 "f")
               str)))))
