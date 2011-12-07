(ns engine.client.commands
  (:use [engine.client :only (defjshandler unchanged-handler)]))

(defjshandler default-commands
  (define)
  ;; (define (list "pilot/canon" "pilot/lang" "parenscript")
  ;;     (lambda (canon lang ps)
  ;;       (flet ((bind-key (key)
  ;;                (create win key mac key sender "editor")))
  ;;         (macrolet ((add-command-args (name &body body)
  ;;                      `(create name ,name bind-key (bind-key nil)
  ;;                               exec (lambda (env args request)
  ;;                                      ,@body))))
  ;;           (chain ps (map (chain canon add-command)
  ;;                          (list (add-command-args "self-insert-command"
  ;;                                                  (chain env editor (insert (@ args text))))
  ;;                                (add-command-args "backward-char"
  ;;                                                  (chain env editor (navigate-left 1)))
  ;;                                (add-command-args "forward-char"
  ;;                                                  (chain env editor (navigate-right 1)))
  ;;                                (add-command-args "move-to-position"
  ;;                                                  (chain env editor (move-cursor-to (@ args row) (@ args column))))
  ;;                                (add-command-args "backward-delete-char"
  ;;                                                  (chain env editor (remove-left)))
  ;;                                (add-command-args "delete-char"
  ;;                                                  (chain env editor (remove-right))))))))))
  )
