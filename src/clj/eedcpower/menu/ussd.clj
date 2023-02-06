(ns eedcpower.menu.ussd
	(:require [clojure.tools.logging :as log]
			  [eedcpower.config :refer [env]]
			  [eedcpower.menu.sessions :as sessions]
			  [eedcpower.utils :as utils]
			  [eedcpower.db.core :as db]
			  [clojure.data.zip.xml :as zip-xml :only [xml1]]
			  [clojure.zip :as zip :only [xml-zip]])
	(:import (clojure.lang PersistentList Keyword)))

(declare define-nfa define-state-renderer define-state-initializer)

;; -------
;;  Definers.

(declare %nfa-automaton-def %ussd-renderer-def %state-initializer-def directstring-automaton-def)

(defn define-nfa
	"Define a non-deterministic finite automaton and related
	functions. The generated automaton accepts 2 parameters:
	a. the current session data (used to determine current state); and
	b. input from user
	and uses these to determine the next state. The function updates and
	returns the session data to the caller.
	---
	Transition table definition:
	state table ::= <state> transitions
	transitions ::= {transitions}*
	transition ::= (<input> <predicate> <state-if-true> <state-if-false> &optional <actions>)"
	[transition-table]
	[(def state-automaton (eval (%nfa-automaton-def 'session-data transition-table)))
	 (def ^:dynamic *valid-states* (let [result (atom #{})]
									   (doseq [[state & transitions] transition-table]
										   (when state (swap! result conj state))
										   ;(log/debugf "transitions=%s"transitions)
										   (doseq [[_ _ state-t state-f] transitions]
											   ;(log/debugf "state-t=%s,state-f=%s"state-t state-f)
											   (when state-t (swap! result conj state-t))
											   (when state-f (swap! result conj state-f))))
									   (log/debugf "*valid-states* definition=%s"@result)
									   @result))
	 (def ^:dynamic *final-states* (let [transitions (reduce conj
														 (map (fn [[state & transitions]]
																  {state (let [result (atom #{})]
																			 (doseq [[_ _ state-t state-f] transitions]
																				 (when state-t (swap! result conj state-t))
																				 (when state-f (swap! result conj state-f)))
																			 @result)})
															 transition-table))
										 outfinal (into #{} (doall (remove #(not (empty? (transitions %))) *valid-states*)))
										 _ (log/debugf "*final-states* definition=%s"outfinal)]
									   outfinal))
	 (defn valid-state-p [state] (if (*valid-states* state) true false))
	 (defn final-state-p [state] (if (*final-states* state) true false))])

(defn define-state-renderer [state-texts]
	(def render-state (eval (%ussd-renderer-def 'session-data state-texts))))


(defn define-state-initializer [initialization-table]
	(def get-initial-state (eval (%state-initializer-def 'session-data initialization-table))))

;; -------
;;  SEXP generation functions.

(defn %nfa-automaton-def [session-data-var transition-table]
	(let [input-var (gensym "input")]
		`(fn [~session-data-var ~input-var]
			 (let [~'input ~input-var
				   ~'session (fn [field#]
								 ((deref ~session-data-var) field#))
				   ~'can-lend? (fn [amount#]
								   (and (>= (or (~'session :max-permissible) 0) amount#)
									   true))
				   ~'session+ (fn [& args#]
								  (reset! ~session-data-var (sessions/session-data-update @~session-data-var (utils/options->map args#))))
				   ~'get-gross (fn [amount#] amount#)
				   ~'get-net (fn [loan-type# amount#] (utils/get-net-amount amount#))
				   ~'gross ~'get-gross
				   ~'net ~'get-net
				   ~'fee (fn [loan-type# amount#] (utils/get-fee-amount amount#))
				   ~'check-pin (fn []
								   (if (and (= (count (~'session :pin)) 4)
										   (= (~'session :state) :create-pin))
									   true))
				   ~'confirm-pin (fn []
									 (if (and (= (~'session :pin) (~'session :pin-confirm))
											 (= (~'session :state) :confirm-pin))
										 true))
				   ~'check-meter (fn []
									 (if (and (not (nil? (~'session :card-number)))
											 (= (count (~'session :card-number)) 11))
										 true false))
				   ~'check-card-one  (fn []
									 (if (= (count (~'session :card-number)) 16)
										 true))
				   ~'check-card-two  (fn []
										 (if (= (count (~'session :card-number)) 3)
											 true))
				   ~'check-card-three  (fn []
										 (if (= (count (~'session :card-number)) 4)
											 true))
				   ~'is-with-cash? (fn []
										 (if (= (~'session :type) :power-cash)
											 true))
				   ;; ---
				   {:keys [~'subscriber ~'state ~'language
						   ~'max-permissible ~'max-qualified ~'ma-balance ~'current-pin
						   ~'amount-requested ~'amount-gross ~'amount-net ~'serviceq ~'ussd-string ]}
				   @~session-data-var]
				 (let [current-state# (keyword (~'session :state))]
					 (condp = current-state#
						 ~@(mapcat (fn [[state & transitions]]
									   (list state
										   `(condp = ~input-var
												~@(mapcat (fn [[%input predicate state-t state-f & actions]]
															  ; (log/debug (format "showingTransition(state=%s,input=%s,
															  ; predicate=%s,next={%s,%s},actions=%s)" state %input predicate state-t state-f actions))
															  ;; Validate transition definition.
															  (when-not (or (= %input :any) (string? %input))
																  (throw (RuntimeException. (format "State input improper for `%s'. Expected :any|string got `%s'" state %input))))
															  ;(when (= state-t state) (log/warn (format "Potential cycle detected in [%s]%s/%s." %input state state-t)))
															  ;(when (= state-f state) (log/warn (format "Potential cycle detected in [%s]%s/%s." %input state state-f)))
															  ;; ---
															  (let [body `(do ~(if predicate
																				   `(if ~predicate
																						(do ~@actions
																							(swap! ~session-data-var assoc :state ~state-t))
																						(swap! ~session-data-var assoc :state ~state-f))
																				   `(do ~@actions
																						(swap! ~session-data-var assoc :state ~state-t)))
																			  @~session-data-var)]
																  (if (string? %input)
																	  `(~%input ~body)
																	  (list body))))
													  transitions))))
							   transition-table)
						 (throw (Exception.  (format "unknownState(%s)" current-state#)))))))))


(defn- %ussd-renderer-def
	"Return the definition of the function to be used in rendering a
session on the USSD menu.
---
State text definition:
 state-texts ::= {state-text}+
 state-text ::= (<state> &rest {line}+)
 line ::= {<show-condition> {message}+}
 message ::= (<lang> <text>)
 text ::= (or string list)"
	[session-data-var state-texts]
	(let [known-languages (let [result (atom #{})]
							  (doseq [[_ & texts] state-texts]
								  ;(log/debugf "state-texts %s"state-texts)
								  ;(log/debugf "texts %s"texts)
								  (doseq [[_ & messages] texts]
									  ;(log/debugf "messages %s"messages)
									  (doseq [[lang] messages]
										  ;(log/debugf "lang %s"lang)
										  (swap! result conj lang))))
							  @result)
		  %generate-printer (fn [language]
								`(condp = (keyword ~'state)
									 ~@(mapcat (fn [[state# & lines#]]
												   (let [line-count# (count lines#)]
													   (when (zero? line-count#)
														   (throw (Exception. (format "!stateMessages(`%s')" state#))))
													   (let [need-stream?# (atom (> line-count# 1))
															 body# (doall
																	   (map (fn [[condition# & messages#] line-number#]
																				(let [[_ text#] (first (filter (fn [[lang#]] (= lang# language)) messages#))]
																					(when-not text#
																						(log/warn (format "!translation(state=%s,lang=%s,line=%s)" state# language line-number#)))
																					(let [more?#  (< line-number# line-count#)
																						  result# (condp = (type text#)
																									  nil `(do)
																									  String (if (= line-count# 1) text#
																												 (if more?# `(println ~text#) `(print ~text#)))
																									  PersistentList
																									  (do (reset! need-stream?# true)
																										  `(do ~@(let [result#
																													   (map (fn [elt#]
																																(condp = (type elt#)
																																	String `(print ~elt#)
																																	Keyword `(print (~'%param ~elt#))
																																	(throw (Exception. (format "unexpectedCompositeType(%s,text=%s)"
																																						   (type elt#) elt#)))))
																														   text#)]
																													 (if more?#
																														 `(~@result# (println))
																														 result#)))))]
																						(if condition# `(when ~condition# ~result#) result#))))
																		   lines# (range 1 (+ 1 line-count#))))
															 body# (if @need-stream?# `((with-out-str ~@body#)) body#)]
														   (list* state# body#))))
										   state-texts)))]
		`(fn [~session-data-var]
			 (let [~'session  (fn [field#]
								  ;(log/debugf "session-data params" ~(~session-data-var field#))
								  (~session-data-var field#))
				   ~'%param (fn [param#]
								(let [value# (param# ~session-data-var)]
									(when (nil? value#)
										(log/errorf "unexpectedNullParam(%s, session=%s)" param# ~session-data-var))
									value#))
				   {:keys [~'ma-balance ~'max-permissible]} ~session-data-var

				   ~'is-airtime? (fn [] (if (= (~session-data-var :loan-type) :airtime) true false))
				   ~'can-lend? (fn [amount#]
								   (and (>= (or ~'max-permissible 0) amount#)
									   (or (empty? @db/max-allowed-balances)
										   (let [min# (@db/max-allowed-balances amount#)]
											   (if min#
												   (if ~'ma-balance
													   (<= ~'ma-balance min#)
													   (throw (RuntimeException. "Main account balance unknown.")))
												   true)))))
				   ~'check-pin (fn []
								   (if (= (count (~'session :pin)) 4)
									   true))
				   ~'confirm-pin (fn []
									 (if (= (~'session :pin)
											 (~'session :pin-confirm))
										 true))
				   ~'get-gross (fn [amount#] amount#)
				   ~'get-net   (fn [loan-type# amount#] (utils/get-net-amount amount#))
				   ~'gross     ~'get-gross
				   ~'net       ~'get-net
				   ~'fee       (fn [loan-type# amount#] (utils/get-fee-amount  amount#))
				   ;; ---
				   {:keys [~'subscriber ~'state ~'language ~'current-pin
						   ~'queue-total ~'loan-balance ~'max-qualified ~'max-permissible
						   ~'amount-requested ~'amount-gross ~'amount-net ~'serviceq ~'ussd-string ]}
				   ~session-data-var]
				 ~(if (= (count known-languages) 1)
					  (do
						  ;(log/debugf "known-languages %s" (%generate-printer (first known-languages)))
						  (%generate-printer (first known-languages)))
					  `(condp = ~'language
						   ~@(mapcat (fn [lang#]
										 `(~lang# ~(%generate-printer lang#)))
								 known-languages)
						   (throw (Exception. (format "noTranslations(lang=%s)" ~'language)))))))))


(defn- %state-initializer-def
	"Return the definition of the function to be used in determining the
	starting state of a USSD session.
	---
	State initialization table definition:
	initialization-table ::= {initialization-entry}+
	initialization-entry ::= predicate actions state"
	[session-data-var initialization-table]
	(if (empty? initialization-table)
		(constantly nil)
		`(fn [~session-data-var]
			 (let [~'session  (fn [field#]
								  ((deref ~session-data-var) field#))
				   ~'session+ (fn [& args#]
								  (reset! ~session-data-var (sessions/session-data-update @~session-data-var (utils/options->map args#))))
				   ~'get-gross (fn [amount#] amount#)
				   ~'get-net   (fn [loan-type# amount#] (utils/get-net-amount amount#))
				   ~'gross     ~'get-gross
				   ~'net       ~'get-net
				   ~'fee       (fn [loan-type# amount#] (utils/get-fee-amount amount#))
				   ;; ---
				   {:keys [~'subscriber ~'state ~'language ~'current-pin
						   ~'max-permissible ~'max-qualified ~'ma-balance
						   ~'amount-requested ~'amount-gross ~'amount-net ~'serviceq ~'ussd-string
						    ~'loan-type]} @~session-data-var
				   ;; ---
				   ~'can-lend? (fn [amount#]
								   (and (>= (or ~'max-permissible 0) amount#)
									   (or (empty? @db/max-allowed-balances)
										   (let [min# (@db/max-allowed-balances amount#)]
											   (if min#
												   (if (when-not (= ~'ma-balance :error) ~'ma-balance)
													   (<= ~'ma-balance min#)
													   (throw (Exception. (format "Main account balance unknown."))))
												   true)))))]
				 (let [state# (cond ~@(mapcat (fn [[predicate# actions# state#]]
												  (log/debug (format "showingIntializing(state=%s,predicate=%s,actions=%s)"
																 state# predicate# actions#))
												  [predicate# `(do ~@actions# ~state#)])
										  initialization-table))]
					 state#)))))
