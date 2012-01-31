(ns engine.repl-init
  (:use clojure.tools.logging
        clj-logging-config.log4j))

(reset-logging!)
(set-loggers! :root
              {:pattern "%d %-5p [%t]: %m%n" :level :debug :out *out*})

(require 'engine.core 'engine.client 'engine.client.commands 'engine.server.rope 'engine.devel.rope)
