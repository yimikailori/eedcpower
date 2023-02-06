(ns eedcpower.utils
	(:import (java.nio.file FileSystem
							FileSystems
							Files
							Path)
			 java.nio.charset.Charset))


(def ^:dynamic *file-system* (FileSystems/getDefault))

(defmulti make-path
	(fn [path] (type path)))

(defmethod make-path String [path]
	(.getPath #^FileSystem *file-system* path (make-array String 0)))

(defmethod make-path java.io.File [path]
	(.toPath #^java.io.File path))

(defmethod make-path Path [path]
	path)

(defmethod make-path clojure.lang.PersistentVector [path]
	(condp = (count path)
		0 (assert (> (count path) 0))
		1 (make-path (get path 0))
		(.getPath #^FileSystem *file-system*
			(str (first path)) (into-array String (map str (next path))))))

(defn toInt [s]
	(if (number? s) s (read-string s)))

(defn get-fee-amount [amt]
	(int (* (toInt amt)
			 0.1)))

(defn get-net-amount [amt]
	(- (toInt amt)
		(get-fee-amount amt)))

(defn options->map [options]
	(if (and options (not (empty? options)))
		(apply array-map options)
		{}))

(defn submsisdn
	([sub]
	 (submsisdn sub :default))
	([sub error]
	 (let [msisdn (apply str (filter #(Character/isDigit ^char %) (str sub)))
		   res (fn [val]
				   (biginteger val))]
		 (cond (= (count msisdn) 13) (res (subs msisdn 3))
			 (= (count msisdn) 11) (res (subs msisdn 1))
			 (= (count msisdn) 10) (res msisdn)
			 :else (if (= error :default)
					   (throw (Exception. "cannot parse msisdn"))
					   nil)))))

(defn validate-int [digits]
	(try
		(number? (read-string digits))
		(catch Exception e
			nil)))

(defmacro with-func-timed
	"macro to execute blocks that need to be timed and may throw exceptions"
	[tag fn-on-failure & body]
	`(let
		 [fn-name# (str "do" (.toUpperCase (subs ~tag 0 1)) (subs ~tag 1))
		  start-time# (System/currentTimeMillis)
		  e-handler# (fn [err# fname#] (log/errorf err# "!%s -> %s" fname# (.getMessage err#)) :error)
		  return-val# (try
						  ~@body
						  (catch Exception se#
							  (if (fn? ~fn-on-failure)
								  (~fn-on-failure se#)
								  (e-handler# se# fn-name#))))]
		 (log/infof "callProf|%s|%s -> %s" (- (System/currentTimeMillis) start-time#) fn-name# return-val#)
		 return-val#))


(defmacro with-request-params [parameters & body]
	(let [session-data-var (gensym "session-data")
		  bindings (mapcat (fn [param]
							   (let [kwd (keyword (str param))]
								   `(~param (let [ret# (~session-data-var ~kwd)]
												(when (nil? ret#)
													(throw (Exception. (format ~(str "UnableTotransition() -> unexpectedNullParam(" kwd ")")))))
												ret#))))
					   parameters)]
		`(let [~session-data-var (deref ~'session-data)
			   ~@bindings]
			 ~@body)))


(defn get-file-contents
	([file style #^String charset]
	 (let [file (if (instance? Path file) file (make-path file))]
		 (condp = style
			 :lines  (Files/readAllLines file (Charset/forName charset))
			 :string (String. #^"[B" (get-file-contents file :bytes) charset)
			 (Files/readAllBytes file))))
	([file style]
	 (get-file-contents file style "UTF-8"))
	([file]
	 (get-file-contents file :string "UTF-8")))
