(ns eedcpower.routes.home
    (:require
        [eedcpower.layout :as layout]
        [eedcpower.db.core :as db]
        [eedcpower.menu.ussd :as ussdmenu]
        [clojure.java.io :as io]
        [eedcpower.middleware :as middleware]
        [eedcpower.config :refer [env]]
        [ring.util.response]
        [ring.util.http-response :as response]
        [clojure.string :as str]
        [clojure.tools.logging :as log]
        [eedcpower.utils :as utils]
        [clojure.data.json :as json]
        [clj-time.core :as t])
    (:use [eedcpower.menu.sessions])
    (:import (java.net URLDecoder)))

(defn home-page [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (layout/render request "about.html"))

(defn- get-start-state [session-data is-new?]
    (let [new-user (and is-new?
                       (nil? (:current-pin session-data)))
          new-session-data (atom (if new-user
                                     (assoc session-data :state :create-pin)
                                     session-data))
          _ (log/debugf "getStartState %s" @new-session-data)]
        [(let [m (ussdmenu/get-initial-state new-session-data)
               _ (log/debugf "Menu => %s |%s" m @new-session-data)]
             (if new-user :create-pin
                 m))
         @new-session-data]))

(defn start-new-session
    [session-data]
    "Start a new session. Return the result of the session start
operation."
    (let [max-age (get-in env [:as :ussd :ussd-session-max-age])
          {:keys [session-id subscriber]} session-data
          {:keys [subscriber_no session_data]} (db/get-session-data {:session-id session-id})
          ;;todo check value of allow-resume?
          allow-resume? true]
        ;; Ensure that this is actually a new session.
        (when-not (nil? session_data)
            (log/errorf "duplicateSessionID(%s,msisdn=%s)" session-id subscriber)
            (throw (Exception. "duplicatedSessionID" )))
        (when-not (nil? subscriber_no)
            (when-not (= subscriber subscriber)
                (throw (Exception. (format "UnexpectedMsisdn(expected=%s,found=%s)" subscriber subscriber)))))
        ;; ---
        (let [now (str (t/now))
              session-data (-> (dissoc session-data :oldest-loan-time)
                               (assoc :start-time now))]
            (log/debugf "inserting new session %s" {:session-id session-id :subscriber subscriber :session-data (str session-data), :max-age max-age, :allow-resume? allow-resume?})
            (db/new-session {:session-id session-id :subscriber subscriber :session-data (json/write-str session-data), :max-age max-age, :allow-resume? allow-resume?}))
        true))

(defn getSubscriberInfo [subscriber]
    (into {} (map (fn [[k v]]
                      [k (if (and (nil? v)
                                 (not (= k :current-pin))) 0 v)]))
        (clojure.set/rename-keys (db/get-customer-info {:sub subscriber})
            {:max_qualified :max-qualified
             :var_current_pin :current-pin
             :current_pin :current-pin
             :var_language :language
             :max_permissible :max-permissible
             :loan_balance :loan-balance
             :oldest_loan_time :oldest-loan-time
             :loans_total :loans-total
             :oldest_expected_repay	:oldest-expected-repay})))

(defn do-handler [session-id subscriber is-new? input]
    (let [pre-session-data(if is-new?
                              (let [request-id (let [timestamp (System/currentTimeMillis)
                                                     rand (format "%04d" (rand-int 9999))
                                                     request-id (str timestamp rand)]
                                                   (biginteger request-id))
                                    subscriber-info (getSubscriberInfo subscriber)
                                    _ (log/infof "subscriber-info[%s]=>%s" subscriber subscriber-info)]
                                  (conj {:session-id session-id :subscriber subscriber :ussd-string input
                                         :request-id request-id}
                                      (dissoc subscriber-info :oldest-loan-time)))
                              ;subscriber not a new user
                              (let [{:keys [subscriber_no session_data]} (db/get-session-data {:session-id session-id})
                                    session_data (into {} (map (fn [[k v]]
                                                                   [k (if (#{:state :loan-type} k) (keyword v) v)])
                                                              session_data))
                                    {:keys [get-card-details max-permissible amount-net serviceq card-number]} session_data
                                    is-card? (if (nil? get-card-details) false get-card-details)

                                    _ (when (nil? session_data)
                                          (throw (Exception. (format "sessionDataNotFound [%s|%s]" subscriber session_data))))
                                    _ (log/debugf "sessionState(session-id,%s,input=%s,msisdn=%s) = %s" session-id input subscriber_no session_data)]
                                  (let [state (:state session_data)]
                                   (cond (some #{state} [:create-pin :confirm-pin]) (cond (and (false? is-new?)
                                                                                              (= state :create-pin))
                                                                                        (do
                                                                                            (log/debugf "User attempt to create pin %s" (assoc session_data :pin input))
                                                                                            (assoc session_data :pin input))
                                                                                        (and (false? is-new?)
                                                                                            (= state :confirm-pin))
                                                                                        (do
                                                                                            (log/debugf "User confirm pin %s" (assoc session_data :pin-confirm input))
                                                                                            (assoc session_data :pin-confirm input))
                                                                                        :else (do
                                                                                                  (log/debugf "issue with pin request %s" (assoc session_data :pin-confirm input))
                                                                                                  session_data))
                                      is-card? (do (log/debugf "show enter card-details")
                                                   (assoc session_data :card-number input))
                                      :else session_data))))
          [start-state session-data] (get-start-state pre-session-data is-new?)
          _ (log/infof "get-start-state %s|%s" start-state session-data)
          session-data (atom (if is-new?
                                 (let [stateSession (assoc session-data :state start-state)
                                       _  (db/update-session-data {:session-id session-id :subscriber subscriber
                                                                   :state start-state :session-data (json/write-str stateSession)})
                                       ]
                                     stateSession)
                                 (ussdmenu/state-automaton (atom session-data) input)))
          {:keys [request-id service-class language state max-permissible service-code
                  serviceq profile-id loan-count loan-balance]} @session-data]

        (log/debugf "Processing session data %s" @session-data)
        (log/debugf "final state [%s|%s|state=%s,is-new?=%s]" (ussdmenu/final-state-p state) subscriber state is-new?)
        (condp = (keyword state)

            (some #{state} [:vend-power-amt :vend-credit-amt]) (let [request-id      (let [timestamp  (System/currentTimeMillis)
                                                                                           rand       (format "%02d" (rand-int 99))
                                                                                           request-id (str timestamp rand)]
                                                                                         (biginteger request-id))
                                                                     subinfo         (getSubscriberInfo subscriber)
                                                                     pre-session     (conj {:session-id   session-id :subscriber subscriber
                                                                                            :service-code service-code :ussd-string input :ma-balance 0 :request-id request-id}
                                                                                         (reduce conj {} (dissoc subinfo :oldest-loan-time)))
                                                                     new-pre-session (conj @session-data pre-session)
                                                                     _               (reset! session-data new-pre-session)]
                                                                   (log/infof "new state [%s,%s]" state @session-data)
                                                                   new-pre-session
                                                                   ;(db/update-session-data {:session-id session-id :subscriber subscriber :state state :session-data (json/write-str pre-session)})
                                                                   )
            :credit-power (let [{:keys [request-id loan-type max-permissible amount-requested amount-net serviceq]} @session-data
                                                             _ (log/debugf "processing for vending => %s" @session-data)]

                                                           #_(utils/with-request-params [subscriber session-id request-id amount-requested serviceq max-permissible]
                                                               (when-not (proceedLend {:loan-type        :power
                                                                                       :subscriber-3p     msisdn-3p
                                                                                       :subscriber       subscriber :service-class service-class :session-id session-id :direct? is-new?
                                                                                       :request-id request-id
                                                                                       :amount-requested amount-requested :serviceq serviceq :max-permissible max-permissible
                                                                                       :channel          :ussd :short-code service-code :misc-args {:profile profile-id} :loan_count loan-count})
                                                                   (reset! session-data (session-data-update @session-data :state :credit-power-failed)))))
            :meter-number       (reset! session-data (assoc @session-data :get-card-details true))

            (some #{state} [:card-one :card-two :card-three])  (let [{:keys [card-number amount-net subscriber loan-type]} @session-data]
                                                                   (log/debugf "processing cards => %s" {:state state :card-number card-number :amount-net amount-net
                                                                                                         :subscriber subscriber :loan-type loan-type}))


            true)
        ;; Handle persistence.
        (if (ussdmenu/final-state-p state)
            (db/close-session {:session-id session-id :subscriber subscriber})
            (if is-new?
                (do
                    ;; If we will be tracking this session, check that session
                    ;; does not already exist.
                    (log/debugf "tracking session %s|%s"session-id subscriber )
                    (start-new-session @session-data))
                (do
                    (log/debugf "userSessionNotNew %s|%s|%s" is-new? session-id subscriber)
                    ;(set-session-data session-id subscriber @session-data)

                    (db/update-session-data {:session-id session-id :subscriber subscriber
                                       :session-data (json/write-str @session-data)}))))
        (log/debugf "Returning session for rendering %s" @session-data)
        ;; Return session data for rendering purposes.
        @session-data))



(defn proceed-handle-ussd-request [session-id subscriber is-new? input]
    (try
        (let [session (do-handler session-id subscriber is-new? input)
              _ (log/debugf "proceed-handle-ussd-request[%s|%s]" (ussdmenu/final-state-p (session :state)) session)
              result  (ussdmenu/render-state session)
              _ (log/debugf "resultRender %s|%s [%s]" session-id subscriber result)]
            (middleware/make-response result
                (if (ussdmenu/final-state-p (session :state)) :terminate :continue)))
        (catch Exception e
            (do
                (log/error (format "CannotHandleMenu(%s) => %s"
                               [session-id subscriber is-new? input] (.getMessage e)) e)
                (middleware/make-response (get-in env [:as :msg :as-error-msg])  :terminate)))))


(defn handle-request [request]
    ;localhost:3000/app/ld/power?msisdn=8156545907&sessionid=12324354646565656565&state=0&option=*321#
    (log/infof "Request recieved %s" (:params request))
    (let [{subscriber   :msisdn
          session-id 	:sessionid
           option	    :option
           state	    :state} (:params request)]
        (if
            (and subscriber session-id option state)
            ;; Alright, we have all the stuff that we expect. Proceed.
            (utils/with-func-timed (str "Request | "subscriber) (fn []
                                                                    (middleware/msg-error (get-in env [:as :msg :as-processing-error-msg])))
                (let [option (when option (str/replace (URLDecoder/decode option "UTF-8") #"[\n\t ]" ""))
                      is-new? (condp = state "0" true "1" false :unknown)]
                    (if (= is-new? :unknown)
                        (do
                            (log/errorf "badSubState(%s,sub=%s,sid=%s,msg=%s)"
                                state subscriber session-id option)
                            (middleware/msg-error "Client error."))
                        (do
                            (log/info "proceed-handle-ussd-request" session-id subscriber is-new? option)
                            (proceed-handle-ussd-request session-id (utils/submsisdn subscriber) is-new? option)
                            ;(make-response "successful" :terminate)
                            ))))

            ;; USSD gateway has sent an unintelligible request.
            (do (log/error (format "notProperlyFormedRequest(msisdn=%s,sid=%s,opt=%s)"subscriber session-id option))
                (middleware/msg-error "Server error.")))))


(defn home-routes []
  [ "" 
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/app/ld/power" {:get handle-request}]
   ["/" {:get home-page}]
   ["/graphiql" {:get (fn [request]
                        (layout/render request "graphiql.html"))}]
   ["/about" {:get about-page}]])




