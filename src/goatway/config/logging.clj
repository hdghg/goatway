(ns goatway.config.logging
  (:import (java.util.logging Level Logger LogManager)
           (org.slf4j.bridge SLF4JBridgeHandler)))

(defn replace-jul
  "Inject slf4j bribge to capture java.util.logging.* calls"
  []
  (.reset (LogManager/getLogManager))
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (.setLevel (Logger/getLogger "global") Level/FINEST))
