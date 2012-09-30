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
                 [aleph "0.2.1-rc1"]
                 [ring/ring-core "1.1.0"]
                 [net.cgrand/moustache "1.0.0"]
                 [compojure "0.6.4"]
                 [hiccup "1.0.0"]
                 [cssgen "0.2.5"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-logging-config "1.9.6"]]
  :profiles {:dev
             {:dependencies
              [[org.clojure/tools.nrepl "0.2.0-beta9"]
               [ring/ring-devel "1.1.0"]
               [lacij "0.6.0"]
               #_[clojurecheck "2.0.2"] ; broken
               [incanter "1.3.0"]
               [org.clojure/tools.trace "0.7.3"]
               [midje "1.4.0"]]}}
  :plugins [[lein-swank "1.4.4"]
            [lein-midje "1.0.10"]]
  :resources-path "resources"
  :repl-options { :init-ns engine.repl-init }
  :min-lein-version "2.0.0")
