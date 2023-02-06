(ns eedcpower.core
    (:require
        [eedcpower.handler :as handler]
        [eedcpower.nrepl :as nrepl]
        [luminus.http-server :as http]
        [luminus-migrations.core :as migrations]
        [eedcpower.config :refer [env]]
        [eedcpower.menu.ussd :as ussdmenu]
        [eedcpower.db.core :as db]
        [clojure.tools.cli :refer [parse-opts]]
        [clojure.tools.logging :as log]
        [mount.core :as mount]
        [clojure.java.io :as io]
        [eedcpower.utils :as utils])
  (:gen-class)
  (:import (java.io PushbackReader StringReader File)
           (java.nio.file FileSystem
                          FileSystems
                          Files
                          Path)))

(declare initialize)
;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what :uncaught-exception
                  :exception ex
                  :where (str "Uncaught exception on" (.getName thread))}))))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop} http-server
  :start
  (http/start
    (-> env
        (update :io-threads #(or % (* 2 (.availableProcessors (Runtime/getRuntime))))) 
        (assoc  :handler (handler/app))
        (update :port #(or (-> env :options :port) %))
        (select-keys [:handler :host :port])))
  :stop
  (http/stop http-server))

(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (when (env :nrepl-port)
    (nrepl/start {:bind (env :nrepl-bind)
                  :port (env :nrepl-port)}))
  :stop
  (when repl-server
    (nrepl/stop repl-server)))



(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))



(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (initialize)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))



(defn initialize []
  (let [ussd-session-cleanup-interval (get-in env [:as :ussd :ussd-session-cleanup-interval] 60)
        _ (db/load-denom-config)
        menu-def (get-in env [:as :ussd :ussd-menu-def])
        _ (if (.exists (io/file menu-def))
            (let [_ (log/infof "ussd menu def %s" (get-in env [:as :ussd :ussd-menu-def]))
                  {:keys [transition-table state-texts state-initializer]}
                  (with-open [s (PushbackReader. (StringReader. (utils/get-file-contents menu-def)))]
                    {:transition-table (read s) :state-texts (read s) :state-initializer (read s)})

                  _ (ussdmenu/define-nfa transition-table)
                  _ (ussdmenu/define-state-renderer state-texts)
                  _ (ussdmenu/define-state-initializer state-initializer)
                  _ (log/infof "Menu loaded successfully")])
            (throw (RuntimeException. (format "!found(menu-definition,path=%s)" (str menu-def)))))]))

(defn -main [& args]
  (-> args
    (parse-opts cli-options)
    (mount/start-with-args #'eedcpower.config/env))

  (cond
    (nil? (:database-url env))
    (do
      (log/error "Database configuration not found, :database-url environment variable must be set before running")
      (System/exit 1))
    (some #{"init"} args)
    (do
      (migrations/init (select-keys env [:database-url :init-script]))
      (System/exit 0))
    (migrations/migration? args)
    (do
      (migrations/migrate args (select-keys env [:database-url]))
      (System/exit 0))
    :else
    (start-app args)))
  
