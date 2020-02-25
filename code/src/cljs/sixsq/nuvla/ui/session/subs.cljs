(ns sixsq.nuvla.ui.session.subs
  (:require
    [clojure.string :as str]
    [re-frame.core :refer [reg-sub subscribe]]
    [sixsq.nuvla.ui.cimi.subs :as cimi-subs]
    [sixsq.nuvla.ui.session.spec :as spec]))


(reg-sub
  ::session-loading?
  (fn [db]
    (::spec/session-loading? db)))


(reg-sub
  ::open-modal
  (fn [db]
    (::spec/open-modal db)))


(reg-sub
  ::session
  (fn [db]
    (::spec/session db)))


(reg-sub
  ::loading?
  (fn [db]
    (::spec/loading? db)))


(reg-sub
  ::roles
  :<- [::session]
  (fn [session]
    (set (some-> session :roles (str/split #"\s+")))))


(reg-sub
  ::has-role?
  :<- [::roles]
  (fn [roles [_ role]]
    (contains? roles role)))


(reg-sub
  ::is-admin?
  :<- [::has-role? "group/nuvla-admin"]
  (fn [is-admin?]
    is-admin?))


(reg-sub
  ::is-user?
  :<- [::has-role? "group/nuvla-user"]
  (fn [is-user?]
    is-user?))


(reg-sub
  ::user
  :<- [::session]
  (fn [session]
    (:identifier session)))


(reg-sub
  ::user-id
  :<- [::session]
  (fn [session]
    (:user session)))


(reg-sub
  ::error-message
  ::spec/error-message)


(reg-sub
  ::success-message
  ::spec/success-message)


(reg-sub
  ::server-redirect-uri
  (fn [db]
    (::spec/server-redirect-uri db)))


(reg-sub
  ::user-templates
  :<- [::cimi-subs/collection-templates :user-template]
  (fn [user-templates]
    user-templates))


(reg-sub
  ::user-template-exist?
  :<- [::user-templates]
  (fn [user-templates [_ template-id]]
    (contains? user-templates template-id)))


(reg-sub
  ::session-templates
  :<- [::cimi-subs/collection-templates :session-template]
  (fn [session-templates _]
    session-templates))


(reg-sub
  ::session-template-exist?
  :<- [::session-templates]
  (fn [session-templates [_ template-id]]
    (contains? session-templates template-id)))