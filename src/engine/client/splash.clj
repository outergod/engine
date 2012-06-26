(ns engine.client.splash
  (:use engine.client.util
        [cssgen :only (css rule)]))

(defcss "/client/engine/splash.css"
  (rule ".splash.ui-widget" css-display-box (css-box "align" "center") (css-box "pack" "center") (css-box "orient" "vertical")
        :position "absolute" :top 0 :bottom 0 :left 0 :right 0 :z-index 5 :border 0)
  (rule ".splash-frame" :width "400px" :height "140px")
  (rule ".splash-title" :font-family "Quantico" :font-size "22px" :padding-bottom "50px")
  (rule ".splash-status" :font-family "Inconsolata")
  (rule ".splash .ui-progressbar" :border 0 :background "none" :border-bottom "1px solid gray" :border-radius 0 :height "1px")
  (rule ".splash .ui-progressbar .ui-progressbar-value" :background "white" :border-radius 0 :height "1px"))

(def html
  [:div {:id "splash" :class "splash ui-widget ui-widget-content"}
   [:div {:class "splash-frame"}
    [:p {:class "splash-title"} "engine"]
    [:p {:class "splash-status"} "Initializing"]
    [:div {:class "splash-progressbar"}]]])

(def cssfiles ["client/Quantico.css" "client/engine/splash.css"])
