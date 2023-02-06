(ns eedcpower.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[eedcpower started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[eedcpower has shut down successfully]=-"))
   :middleware identity})
