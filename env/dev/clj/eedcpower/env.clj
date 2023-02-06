(ns eedcpower.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [eedcpower.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[eedcpower started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[eedcpower has shut down successfully]=-"))
   :middleware wrap-dev})
