(ns engine.repl-init
  (:use clojure.tools.logging
        clj-logging-config.log4j))

(set-logger! :pattern "%d %-5p [%t]: %m%n" :level :debug :out *out*)

(require 'engine.core 'engine.client 'engine.data.rope 'engine.devel.rope 'engine.devel.benchmark)
