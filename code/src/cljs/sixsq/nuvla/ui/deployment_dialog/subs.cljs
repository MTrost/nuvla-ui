(ns sixsq.nuvla.ui.deployment-dialog.subs
  (:require
    [clojure.string :as str]
    [re-frame.core :refer [reg-sub subscribe]]
    [sixsq.nuvla.ui.deployment-dialog.spec :as spec]
    [sixsq.nuvla.ui.deployment-dialog.utils :as utils]))


(reg-sub
  ::deploy-modal-visible?
  ::spec/deploy-modal-visible?)


(reg-sub
  ::loading-deployment?
  ::spec/loading-deployment?)


(reg-sub
  ::deployment
  ::spec/deployment)


(reg-sub
  ::loading-credentials?
  ::spec/loading-credentials?)


(reg-sub
  ::selected-credential
  (fn [db]
    (::spec/selected-credential db)))


(reg-sub
  ::credentials
  (fn [db]
    (::spec/credentials db)))


(reg-sub
  ::active-step
  (fn [db]
    (::spec/active-step db)))


(reg-sub
  ::data-step-active?
  (fn [db]
    (::spec/data-step-active? db)))


(reg-sub
  ::step-states
  (fn [db]
    (::spec/step-states db)))


(reg-sub
  ::data-clouds
  (fn [db]
    (::spec/data-infra-services db)))


(reg-sub
  ::selected-cloud
  (fn [db]
    (::spec/selected-infra-service db)))


(reg-sub
  ::connectors
  (fn [db]
    (::spec/infra-services db)))


;;
;; dynamic subscriptions to manage flow of derived data
;;


(reg-sub
  ::credentials-completed?
  :<- [::selected-credential]
  (fn [selected-credential _]
    (boolean selected-credential)))


(reg-sub
  ::input-parameters
  (fn [_ _]
    [(subscribe [::deployment])
     (subscribe [::selected-credential])])
  (fn [[deployment selected-credential] _]
    (-> deployment :module :content :inputParameters)))


(reg-sub
  ::parameters-completed?
  :<- [::input-parameters]
  (fn [input-params _]
    (every? #(not (str/blank? (:value %))) input-params)))


(reg-sub
  ::data-completed?
  :<- [::selected-cloud]
  (fn [selected-cloud _]
    (boolean selected-cloud)))


