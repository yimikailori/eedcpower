(ns eedcpower.menu.sessions
	(:require [clojure.tools.logging :as log]))

(defn- %is-readonly-key? [key]
	(if (#{:session-id :msisdn :start-time :oldest-loan-time} key) true false))
(defn- %remove-readonly-keys [session-data]
	(dissoc session-data :session-id :msisdn :start-time :oldest-loan-time))

(defn session-data-update
	([session-data key value]
	 (if-not (%is-readonly-key? key)
		 (do
			 (log/debugf "session-data key value %s" (assoc session-data key value))
			 (assoc session-data key value))
		 (do
			 (log/debugf "session-data key-value %s" session-data)
			 session-data)))
	([session-data key-value-pairs]
	 (let [key-value-pairs (%remove-readonly-keys key-value-pairs)]
		 (if-not (empty? key-value-pairs)
			 (do
				 (log/debugf "session dataupdate key-value-pairs %s"(conj session-data key-value-pairs))
				 (conj session-data key-value-pairs))
			 (do
				 (log/debugf "session dataupdate %s"session-data)
				 session-data)))))


