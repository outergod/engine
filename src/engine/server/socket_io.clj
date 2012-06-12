(ns engine.server.socket-io
  (:use [aleph.http :only [request-cookie request-params wrap-aleph-handler]]
        [aleph.formats :only [encode-json->string decode-json]]
        lamina.core
        net.cgrand.moustache
        [clojure.pprint :only [cl-format]]
        clj-logging-config.log4j)
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace])
  (:import [java.lang Long]
           [java.util Timer TimerTask]
           [clojure.lang Keyword PersistentArrayMap]
           [java.util.concurrent TimeoutException]))

; from contrib.datalog
(defn reverse-map
  "Reverse the keys/values of a map"
  [m]
  (into {} (map (fn [[k v]] [v k]) m)))

(defrecord WebsocketChannel [channel endpoint sequencer session])

(def close-timeout 25)
(def heartbeat-interval 20)
(def socket-out (io/file "socket.log"))

(def reserved-event-names #{"message" "connect" "disconnect" "open" "close" "error" "retry" "reconnect"})
(def reserved-event-types #{"connect" "disconnect" "message" "ack"})

(def message-types
  {0 :disconnect
   1 :connect
   2 :heartbeat
   3 :message
   4 :json-message
   5 :event
   6 :ack
   7 :error
   8 :noop})

(def type-messages
  (reverse-map message-types))

(defmulti message class)
(defmethod message String [data] (identity data))
(defmethod message :default [data] (encode-json->string data))

(defn translate-message-type [type]
  (message-types (Integer/parseInt type)))

(let [message-splitter #"^([^\+]*)(?:\+?(.*))"]
  (defn translate-ack [data]
    (let [[_ id data] (re-matches message-splitter data)]
      [(Integer/parseInt id) data]))
  (defn translate-error [data]
    (rest (re-matches message-splitter data))))

(let [message-scanner #"^(\d):(\d+)?(\+)?:([^:]+)?(?::(.+))?"]
  (defn translate-message [message]
    (when message ; receiving nil upon closing, bug in lamina or aleph?
      (log/debug (format "message is [%s]" message))
      (if-let [[_ type id data-ack endpoint data] (re-matches message-scanner message)]
        (let [id (and (not (empty? id))
                      (Integer/parseInt id))
              data-ack (not (empty? data-ack))]
          [(translate-message-type type) id data-ack endpoint data])
        (log/error (format "Received malformed message %s" message))))))

(defmulti websocket-message (fn [type & rest] (class type)))
(defmethod websocket-message Long
  [type & {:keys [id endpoint data] :or {id "" endpoint ""}}]
  (format "%d:%s:%s%s" type id
          (if (#{:message :json-message :event :error}
               (message-types type))
            endpoint "")
          (if data (str ":" data) "")))
(defmethod websocket-message Keyword [type & args]
  (apply websocket-message (type-messages type) args))

(defn websocket-send [socket type & message-args]
  {:pre [(instance? WebsocketChannel socket)]}
  (let [seq-args ((:sequencer socket) type message-args)
        channel (:channel socket)
        endpoint (:endpoint socket)
        {:keys [id callback]} seq-args]
    (when (and callback id)
      (receive (->> channel
                    (map* translate-message)
                    (filter* #(= :ack (first %1))) ; [type _ _ _ _]
                    (map* #(translate-ack (last %1))) ; [_ _ _ _ data]
                    (filter* #(= id (first %1))) ; [id data]
                    (map* second)) 
               callback))
    (enqueue channel
             (apply websocket-message type
                    (into seq-args [:endpoint endpoint])))))

(defn websocket-sequencer []
  (let [counter (atom 0)]
    (fn [type message]
      (if (#{:message :json-message :event} type)
        (concat message [:id (swap! counter inc)])
        message))))

(defn send-disconnect [socket]
  (websocket-send socket :disconnect))

(defn send-connect [socket]
  (websocket-send socket :connect))

(defn send-heartbeat [socket]
  (websocket-send socket :heartbeat))

(defn send-message
  ([socket data] (websocket-send socket :message :data (message data)))
  ([socket data callback] (websocket-send socket :message :data (message data) :callback callback)))

(defn send-event
  ([socket name args]
     (websocket-send socket :event :data (encode-json->string {:name name :args args})))
  ([socket name args callback]
     (websocket-send socket :event :data (encode-json->string {:name name :args args})
                     :callback callback)))

(defn send-ack
  ([socket id] (websocket-send socket :ack :data (str id)))
  ([socket id data]
     (websocket-send socket :ack :data (format "%d+%s" id (encode-json->string (if (sequential? data) data [data]))))))

(defn send-error
  ([socket reason] (websocket-send socket :error :data reason))
  ([socket reason advice] (send-error socket (str reason "+" advice))))

(defn send-noop [socket]
  (websocket-send socket :noop))


(def heartbeat-channel (permanent-channel))
(receive-all heartbeat-channel (fn [_])) ; all heartbeat messages are volatile

(defn heartbeatfn []
  (with-logging-config [:root {:level :trace :out socket-out}]
    (log/debug "Bubump")
    (enqueue heartbeat-channel (websocket-message :heartbeat))))

(when (and (.hasRoot (def heartbeat))
           (= Timer (class heartbeat)))
  (.cancel heartbeat)) ; sometimes, defonce just doesn't work as-is..
(def heartbeat (new Timer "Engine heartbeat" true))

(let [timer-task (proxy [TimerTask] [] ; in such moments, I love clojure for making java bearable
                   (run [] (heartbeatfn)))]
  (doto heartbeat (.scheduleAtFixedRate timer-task (long 0) (long (* 1000 20)))))


(defn handshake [request]
  (if-let [sid (and (:session request)
                    (-> request :cookies (get "engine") :value))]
    {:status 200 :content-type "text/plain"
     :body (cl-format nil "~a:~d:~d:~{~a~^,~}" sid heartbeat-interval close-timeout ["websocket"])}
    {:status 401}))

(defn deathwatch [channel timeout] 
  (future
    (let [dup (fork channel)]
      (while (not (drained? channel))
        (try (wait-for-message channel (* 1000 timeout))
             (catch TimeoutException _ (log/info "Timeout and closing") (close channel))
             (catch Exception e (log/error "Caught and closing " e) (close channel)))))))

(defn handle-ack [socket id data data-ack?]
  (cond (and data-ack? (not id))
        (log/warn "Data acknowledgement requested without id, ignoring")
        (and data data-ack?) (send-ack socket id data)
        id (send-ack socket id)))

(defn message-handler [socket dispatcher]
  (fn [message]
    (with-logging-config [:root {:level :debug :out socket-out}]
      (try
        (when-let [[type id data-ack endpoint data] (translate-message message)]
          (case type
            :disconnect (close (:channel socket))
            :connect (send-error socket "Additional endpoints not supported" "Just don't try again, bitch!")
            :heartbeat (log/debug "Client sent heartbeat")
            :message (do
                       (log/debug (str "Client sent message " data))
                       (handle-ack socket id (dispatcher "message" data) data-ack))
            :json-message (do
                            (log/debug (str "Client sent JSON message " data))
                            (handle-ack socket id (apply dispatcher "message" (decode-json data)) data-ack))
            :event (do
                     (log/debug (str "Client sent event message " data))
                     (let [{:keys [name args]} (decode-json data)]
                       (if (reserved-event-types name)
                         (send-error socket (format "Event name %s is reserved" name))
                         (handle-ack socket id (apply dispatcher name args) data-ack))))
            :ack  (let [[id data] (translate-ack data)]
                    (log/debug (format "Client acknowledged %d\nData: %s" id data))
                    (apply dispatcher "ack" id (decode-json data)))
            :error (let [[reason advice] (translate-error data)]
                     (log/error (format "Client signalled an error %s\nAdvice: %s" reason advice)))
            :noop (log/debug "Client sent noop (whatever)")
            (log/error (format "Ignoring unknown type %s for message %s" type message))))
        (catch Exception e
          (log/error "Fatal error handling socket.io request:" e)
          (log/trace (with-out-str (stacktrace/print-stack-trace e))))))))

(defn socket [dispatcher]
  (fn [channel request]
    (let [socket (WebsocketChannel. channel "" (websocket-sequencer) (agent {}))
          handler (partial dispatcher socket)]
      (send-connect socket) ; this is obligatory!
      (siphon heartbeat-channel channel)
      (deathwatch channel close-timeout)
      (receive-all (fork channel) (message-handler socket handler))
      (on-closed channel #(handler "disconnect"))
      (handler "connect"))))

(defn socket.io-handler [handler dispatcher]
  (app
   ["socket.io" &] [["1" &] [[""] handshake
                             ["websocket" session-id] (wrap-aleph-handler (socket dispatcher))
                             [&] (fn [_] {:status 401})] ; Unauthorized, here: bad request
                    [&] (fn [_] {:status 401})] ; Unauthorized, here: unsupported version
   [&] handler))

