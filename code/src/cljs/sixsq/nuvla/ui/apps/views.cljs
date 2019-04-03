(ns sixsq.nuvla.ui.apps.views
  (:require
    [cemerick.url :as url]
    [re-frame.core :refer [dispatch subscribe]]
    [sixsq.nuvla.ui.apps-component.views :as apps-component-views]
    [sixsq.nuvla.ui.apps-project.views :as apps-project-views]
    [sixsq.nuvla.ui.apps-store.views :as apps-store-views]
    [sixsq.nuvla.ui.apps.events :as events]
    [sixsq.nuvla.ui.apps.subs :as subs]
    [sixsq.nuvla.ui.apps.utils :as utils]
    [sixsq.nuvla.ui.apps.views-detail :as views-detail]
    [sixsq.nuvla.ui.main.subs :as main-subs]
    [sixsq.nuvla.ui.panel :as panel]
    [sixsq.nuvla.ui.utils.semantic-ui :as ui]
    [taoensso.timbre :as timbre]))


(defn module-details
  [new-type]
  (let [module   (subscribe [::subs/module])
        nav-path (subscribe [::main-subs/nav-path])]
    (fn [new-type]
      (let [type       (:type @module)
            new-parent (utils/nav-path->parent-path @nav-path)
            new-name   (utils/nav-path->module-name @nav-path)]
        (when (empty? @module)
          (do
            (dispatch [::events/name new-name])
            (dispatch [::events/parent new-parent])
            (dispatch [::events/type new-type])
            ))
        (if (or (= "component" new-type) (= "COMPONENT" type))
          [apps-component-views/view-edit]
          [apps-project-views/view-edit])
        ))))


(defn module-view
  [new-type version]
  (dispatch [::events/is-new? (not (empty? new-type))])
  (if (empty? new-type)
    (dispatch [::events/get-module version])
    (dispatch [::events/clear-module])
    )
  [module-details new-type])


(defn apps
  []
  (let [query       (clojure.walk/keywordize-keys (:query (url/url (-> js/window .-location .-href))))
        type        (:type query)
        version     (:version query nil)
        module-name (utils/nav-path->module-name @(subscribe [::main-subs/nav-path]))]
    (if module-name
      (module-view type version)
      [apps-store-views/root-view])))


(defmethod panel/render :apps
  [path]
  (timbre/set-level! :info)
  [:div
   [views-detail/version-warning]
   [views-detail/add-modal]
   [views-detail/save-modal]
   [views-detail/validation-error-message]
   [views-detail/logo-url-modal]
   [apps]])
