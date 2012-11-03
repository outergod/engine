(ns engine.data.mode
  (:use [engine.data command util])
  (:require [engine.data.cursor :as cursor]
            [clojure.tools.logging :as log]
            [clojure.stacktrace :as stacktrace]))

(def ^:dynamic *keymap*)
(defn aliasfn [key]
  #(apply (*keymap* key) %&))

(defn keymap
  ([map]
     (into map
           {#{:ctrl "g"} {:state {:keymap nil}}}))
  ([] (keymap {})))

(defn fundamental-mode-keymap [syncfn]
  (keymap {#{:backspace} #(syncfn cursor/backward-delete),
           #{:return} #(syncfn (insertfn "\n")),
           #{:space} #(syncfn (insertfn " ")),
           #{:shift :space} (aliasfn #{:space}),
           #{:cursor-left} #(syncfn cursor/backward-char),
           #{:ctrl "b"} #(syncfn cursor/backward-char),
           #{:cursor-up} #(syncfn cursor/previous-line),
           #{:ctrl "p"} #(syncfn cursor/previous-line),
           #{:cursor-right} #(syncfn cursor/forward-char),
           #{:ctrl "f"} #(syncfn cursor/forward-char),
           #{:cursor-down} #(syncfn cursor/next-line),
           #{:ctrl "n"} #(syncfn cursor/next-line),
           #{:ctrl "d"} #(syncfn cursor/forward-delete),
           #{:delete} (aliasfn #{:ctrl "d"}),
           #{:home} #(syncfn cursor/move-beginning-of-line),
           #{:ctrl "a"} #(syncfn cursor/move-beginning-of-line),
           #{:end} #(syncfn cursor/move-end-of-line),
           #{:ctrl "e"} #(syncfn cursor/move-end-of-line),
           #{:ctrl :cursor-right} #(syncfn cursor/forward-word),
           #{:alt "f"} #(syncfn cursor/forward-word),
           #{:ctrl :cursor-left} #(syncfn cursor/backward-word),
           #{:alt "b"} #(syncfn cursor/backward-word),
           #{:alt "d"} #(syncfn cursor/forward-kill-word),
           #{:alt :backspace} #(syncfn cursor/backward-kill-word),
           #{:alt :shift ","} #(syncfn cursor/beginning-of-buffer),
           #{:alt :shift "."} #(syncfn cursor/end-of-buffer),
           #{:ctrl "k"} #(syncfn cursor/kill-line),
           #{:alt "x"} (voidfn (commands (broadcasted ["execute-extended-command" (:name (syncfn)) {:prompt "> " :args ""}]))),
           #{:ctrl "x"} (voidfn {:state {:keymap (keymap)}})}))

(defn minibuffer-execute [syncfn]
  (let [[name & args] (clojure.string/split (deref (syncfn)) #" +")]
    (if (zero? (count name))
      (merge-with concat
             (syncfn cursor/purge trans-exit)
             (commands (broadcasted ["error-message" "Minibuffer" "No command name given"])))
      (do
        (if-let [fun (ns-resolve 'engine.server (symbol name))]
          (try (let [result (fun args nil)]
                 (merge-with concat (syncfn cursor/purge trans-exit) result))
               (catch Exception e
                 (log/trace (with-out-str (stacktrace/print-stack-trace e)))
                 (commands (broadcasted ["error-message" "Minibuffer" (format "Error executing [%s]: %s" name e)]))))
          (commands (broadcasted ["error-message" "Minibuffer" (format "No such command [%s]" name)])))))))

(defn minibuffer-mode-keymap [syncfn]
  (assoc (fundamental-mode-keymap syncfn)
    #{:return} #(minibuffer-execute syncfn),
    #{:ctrl "g"} #(syncfn cursor/purge trans-exit)))
