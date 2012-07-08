(ns engine.data.mode
  (:use [engine.server input command])
  (:require [engine.data.cursor :as cursor]))

(defn fundamental-mode-keymap [syncfn]
  (keymap {#{:backspace} #(syncfn cursor/backward-delete command-delete-backward),
           #{:return} #(syncfn (insertfn "\n") command-insert),
           #{:space} #(syncfn (insertfn " ") command-insert),
           #{:shift :space} (aliasfn #{:space}),
           #{:cursor-left} #(syncfn cursor/backward-char),
           #{:ctrl "b"} #(syncfn cursor/backward-char),
           #{:cursor-up} #(syncfn cursor/previous-line),
           #{:ctrl "p"} #(syncfn cursor/previous-line),
           #{:cursor-right} #(syncfn cursor/forward-char),
           #{:ctrl "f"} #(syncfn cursor/forward-char),
           #{:cursor-down} #(syncfn cursor/next-line),
           #{:ctrl "n"} #(syncfn cursor/next-line),
           #{:ctrl "d"} #(syncfn cursor/forward-delete command-delete-forward),
           #{:delete} (aliasfn #{:ctrl "d"}),
           #{:home} #(syncfn cursor/move-beginning-of-line),
           #{:ctrl "a"} #(syncfn cursor/move-beginning-of-line),
           #{:end} #(syncfn cursor/move-end-of-line),
           #{:ctrl "e"} #(syncfn cursor/move-end-of-line),
           #{:ctrl :cursor-right} #(syncfn cursor/forward-word),
           #{:alt "f"} #(syncfn cursor/forward-word),
           #{:ctrl :cursor-left} #(syncfn cursor/backward-word),
           #{:alt "b"} #(syncfn cursor/backward-word),
           #{:alt "d"} #(syncfn cursor/forward-kill-word command-delete-forward),
           #{:alt :backspace} #(syncfn cursor/backward-kill-word command-delete-backward),
           #{:alt :shift ","} #(syncfn cursor/beginning-of-buffer),
           #{:alt :shift "."} #(syncfn cursor/end-of-buffer),
           #{:ctrl "k"} #(syncfn cursor/kill-line command-delete-forward),
           #{:alt "x"} (fn [& _] {:commands [(command "execute-extended-command" :prompt "> " :args "")]}),
           #{:ctrl "x"} (fn [& _]  {:state {:keymap (keymap)}})}))

(defn minibuffer-mode-keymap [syncfn]
  (assoc (fundamental-mode-keymap syncfn)
    #{:return} #(syncfn cursor/purge command-exit),
    #{:ctrl "g"} #(syncfn cursor/purge command-exit)))
