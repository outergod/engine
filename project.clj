;;;; Engine - project.clj
;;;; Copyright (C) 2012  Alexander Kahl <e-user@fsfe.org>
;;;; This file is part of Engine.
;;;; Engine is free software; you can redistribute it and/or modify it
;;;; under the terms of the GNU Affero General Public License as
;;;; published by the Free Software Foundation; either version 3 of the
;;;; License, or (at your option) any later version.
;;;;
;;;; Engine is distributed in the hope that it will be useful,
;;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;; GNU General Public License for more details.
;;;;
;;;; You should have received a copy of the GNU General Public License
;;;; along with this program.  If not, see <http://www.gnu.org/licenses/>.
(defproject engine "0.0.0-SNAPSHOT"
  :description "Engine next generation internet Emacs"
  :license {:name "GNU Affero General Public License v3 (or later)"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [aleph "0.3.0-beta6"]
                 [ring/ring-core "1.1.6"]
                 [net.cgrand/moustache "1.1.0"]
                 [compojure "0.6.4"]
                 [hiccup "1.0.0"]
                 [cssgen "0.2.6"]
                 [org.clojure/tools.logging "0.2.4"]
                 [clj-logging-config "1.9.6"]]
  :profiles {:dev
             {:dependencies
              [[org.clojure/tools.nrepl "0.2.0-beta10"]
               [ring/ring-devel "1.1.6"]
               [lacij "0.6.0"]
               #_[clojurecheck "2.0.2"] ; broken
               #_[incanter "1.3.0"] ; dep missing org.mongodb:mongo-java-driver:pom:2.6.5
               [org.clojure/tools.trace "0.7.3"]
               [midje "1.4.0"]]}}
  :resources-path "resources"
  :repl-options { :init (require 'engine.repl-init) :port 1337 }
  :min-lein-version "2.0.0")
