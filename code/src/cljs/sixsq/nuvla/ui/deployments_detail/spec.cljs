(ns sixsq.nuvla.ui.deployments-detail.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def ::deployment any?)

(s/def ::module-versions any?)

(s/def ::deployment-parameters any?)

(s/def ::events any?)

(s/def ::node-parameters any?)

(s/def ::upcoming-invoice any?)

(s/def ::active-tab keyword?)

(s/def ::not-found? boolean?)


(s/def ::db (s/keys :req [::not-found?
                          ::deployment
                          ::deployment-parameters
                          ::events
                          ::node-parameters
                          ::upcoming-invoice
                          ::active-tab]))


(def defaults {::not-found?                false
               ::deployment                nil
               ::deployment-parameters     nil
               ::module-versions           nil
               ::events                    nil
               ::node-parameters           nil
               ::deployment-log-controller nil
               ::upcoming-invoice          nil
               ::active-tab                :overview})