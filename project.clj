(defproject engine "0.0.0-SNAPSHOT"
  :description "Engine next generation internet Emacs"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [aleph "0.2.1-alpha2-SNAPSHOT"]
                 [ring/ring-core "1.0.0-RC1"]
                 [net.cgrand/moustache "1.0.0"]
                 [compojure "0.6.4"]
                 [hiccup "0.3.7"]
                 [cssgen "0.2.5"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-logging-config "1.9.6"]]
  :dev-dependencies [[ring/ring-devel "1.0.0-RC1"]
                     [lacij "0.6.0"]
                     [clojurecheck "2.0.2"]
                     [incanter "1.3.0"]]
  :resources-path "resources"
  :repl-init engine.repl-init
  :license {:name "GNU Affero General Public License v3 (or later)"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"
            :distribution :repo})
