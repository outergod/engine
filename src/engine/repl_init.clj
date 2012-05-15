(ns engine.repl-init
  (:use clojure.tools.logging
        clj-logging-config.log4j))

(set-logger! :pattern "%d %-5p [%t]: %m%n" :level :trace :out *out*)

(require 'engine.core 'engine.client #_engine.devel.rope #_engine.devel.benchmark)
