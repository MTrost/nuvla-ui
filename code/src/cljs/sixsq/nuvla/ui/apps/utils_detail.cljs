(ns sixsq.nuvla.ui.apps.utils-detail
  (:require
    [sixsq.nuvla.ui.apps-application.utils :as apps-application-utils]
    [sixsq.nuvla.ui.apps-component.utils :as apps-component-utils]
    [sixsq.nuvla.ui.apps.spec :as spec]
    [sixsq.nuvla.ui.apps.utils :as utils]))

(defn db->module
  [module commit db]
  (let [subtype (-> db ::spec/module-common ::spec/subtype)
        module  (utils/db->module module commit db)]
    (case subtype
      "component" (apps-component-utils/db->module module commit db)
      "application" (apps-application-utils/db->module module commit db)
      "application_kubernetes" (apps-application-utils/db->module module commit db)
      "project" module
      module)))


(def data-type-options
  (atom [{:key "application/x-hdr", :value "application/x-hdr", :text "application/x-hdr"}
         {:key "application/x-clk", :value "application/x-clk", :text "application/x-clk"}
         {:key "text/plain", :value "text/plain", :text "text/plain"}]))
