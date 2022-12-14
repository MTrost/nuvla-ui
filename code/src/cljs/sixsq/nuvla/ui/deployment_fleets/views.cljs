(ns sixsq.nuvla.ui.deployment-fleets.views
  (:require
    [clojure.string :as str]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [sixsq.nuvla.ui.deployment-fleets-detail.views :as detail]
    [sixsq.nuvla.ui.deployment-fleets.events :as events]
    [sixsq.nuvla.ui.deployment-fleets.spec :as spec]
    [sixsq.nuvla.ui.deployment-fleets.subs :as subs]
    [sixsq.nuvla.ui.history.events :as history-events]
    [sixsq.nuvla.ui.i18n.subs :as i18n-subs]
    [sixsq.nuvla.ui.main.components :as components]
    [sixsq.nuvla.ui.panel :as panel]
    [sixsq.nuvla.ui.plugins.full-text-search :as full-text-search-plugin]
    [sixsq.nuvla.ui.plugins.pagination :as pagination-plugin]
    [sixsq.nuvla.ui.utils.general :as general-utils]
    [sixsq.nuvla.ui.utils.semantic-ui :as ui]
    [sixsq.nuvla.ui.utils.semantic-ui-extensions :as uix]
    [sixsq.nuvla.ui.utils.style :as style]
    [sixsq.nuvla.ui.utils.time :as time]
    [sixsq.nuvla.ui.utils.values :as values]))

(def view-type (r/atom :cards))

(def ^:const STARTED "STARTED")
(def ^:const CREATED "CREATED")
(def ^:const STOPPED "STOPPED")
(def ^:const PENDING "PENDING")

(defn state->icon
  [state]
  (if (str/ends-with? state "ING")
    "sync"
    (get {STARTED "play"
          STOPPED "stop"
          CREATED "circle outline"} state)))

(defn StatisticStates
  [clickable?]
  (let [summary  (subscribe [::subs/deployment-fleets-summary])
        terms    (general-utils/aggregate-to-map
                   (get-in @summary [:aggregations :terms:state :buckets]))
        started  (:STARTED terms 0)
        starting (:STARTING terms 0)
        creating (:CREATING terms 0)
        created  (:CREATED terms 0)
        stopping (:STOPPING terms 0)
        stopped  (:STOPPED terms 0)
        pending  (+ starting creating stopping)
        total    (:count @summary)]
    [ui/GridColumn {:width 8}
     [ui/StatisticGroup {:size  "tiny"
                         :style {:justify-content "center"
                                 :padding-top     "20px"
                                 :padding-bottom  "20px"}}
      [components/StatisticState total ["fas fa-bullseye"] "TOTAL" clickable?
       ::events/set-state-selector ::subs/state-selector]
      [components/StatisticState created [(state->icon CREATED)] CREATED
       clickable? "blue"
       ::events/set-state-selector ::subs/state-selector]
      [components/StatisticState started [(state->icon STARTED)] STARTED
       clickable? "green"
       ::events/set-state-selector ::subs/state-selector]
      [components/StatisticState stopped [(state->icon STOPPED)] STOPPED
       clickable? "red"
       ::events/set-state-selector ::subs/state-selector]
      [components/StatisticState pending [(state->icon PENDING)] PENDING
       clickable? "brown"
       ::events/set-state-selector ::subs/state-selector]]]))

(defn AddButton
  []
  (let [tr (subscribe [::i18n-subs/tr])]
    [uix/MenuItem
     {:name     (@tr [:add])
      :icon     "add"
      :on-click #(dispatch
                   [::history-events/navigate "deployment-fleets/New"])}]))

(defn MenuBar []
  (let [loading? (subscribe [::subs/loading?])]
    (fn []
      [components/StickyBar
       [ui/Menu {:borderless true, :stackable true}
        [AddButton]
        [ui/MenuItem {:icon     "grid layout"
                      :active   (= @view-type :cards)
                      :on-click #(reset! view-type :cards)}]
        [ui/MenuItem {:icon     "table"
                      :active   (= @view-type :table)
                      :on-click #(reset! view-type :table)}]
        [components/RefreshMenu
         {:action-id  events/refresh-id
          :loading?   @loading?
          :on-refresh #(dispatch [::events/refresh])}]]])))

(defn DeploymentFleetRow
  [{:keys [id name description created state tags] :as _deployment-fleet}]
  (let [uuid (general-utils/id->uuid id)]
    [ui/TableRow {:on-click #(dispatch [::history-events/navigate (str "deployment-fleets/" uuid)])
                  :style    {:cursor "pointer"}}
     [ui/TableCell (or name uuid)]
     [ui/TableCell description]
     [ui/TableCell state]
     [ui/TableCell (values/format-created created)]
     [ui/TableCell [uix/Tags tags]]]))

(defn Pagination
  []
  (let [deployment-fleets @(subscribe [::subs/deployment-fleets])]
    [pagination-plugin/Pagination {:db-path [::spec/pagination]
                            :total-items    (get deployment-fleets :count 0)
                            :change-event   [::events/refresh]}]))

(defn DeploymentFleetTable
  []
  (let [deployment-fleets (subscribe [::subs/deployment-fleets])]
    [:div style/center-items
     [ui/Table {:compact "very", :selectable true}
      [ui/TableHeader
       [ui/TableRow
        [ui/TableHeaderCell "name"]
        [ui/TableHeaderCell "description"]
        [ui/TableHeaderCell "state"]
        [ui/TableHeaderCell "created"]
        [ui/TableHeaderCell "tags"]]]

      [ui/TableBody
       (for [{:keys [id] :as deployment-fleet} (:resources @deployment-fleets)]
         (when id
           ^{:key id}
           [DeploymentFleetRow deployment-fleet]))]]]))

(defn DeploymentFleetCard
  [{:keys [id created name state description tags] :as _deployment-fleet}]
  (let [tr   (subscribe [::i18n-subs/tr])
        href (str "deployment-fleets/" (general-utils/id->uuid id))]
    ^{:key id}
    [uix/Card
     {:on-click    #(dispatch [::history-events/navigate href])
      :href        href
      :header      [:<>
                    [ui/Icon {:name (state->icon state)}]
                    (or name id)]
      :meta        (str (@tr [:created]) " " (-> created time/parse-iso8601 time/ago))
      :state       state
      :description (when-not (str/blank? description) description)
      :tags        tags}]))

(defn DeploymentFleetCards
  []
  (let [deployment-fleets (subscribe [::subs/deployment-fleets])]
    [:div style/center-items
     [ui/CardGroup {:centered    true
                    :itemsPerRow 4}
      (for [{:keys [id] :as deployment-fleet} (:resources @deployment-fleets)]
        (when id
          ^{:key id}
          [DeploymentFleetCard deployment-fleet]))]]))

(defn ControlBar []
  [ui/GridColumn {:width 4}
   [full-text-search-plugin/FullTextSearch
    {:db-path      [::spec/search]
     :change-event [::pagination-plugin/change-page [::spec/pagination] 1]}]])

(defn Main
  []
  (dispatch [::events/refresh])
  (let [tr (subscribe [::i18n-subs/tr])]
    [components/LoadingPage {}
     [:<>
      [uix/PageHeader "bullseye"
       (@tr [:deployment-fleets])]
      [MenuBar]
      [ui/Grid {:columns   3
                :stackable true
                :reversed  "mobile"}
       [ControlBar]
       [StatisticStates true]]
      (case @view-type
        :cards [DeploymentFleetCards]
        :table [DeploymentFleetTable])
      [Pagination]]]))


(defmethod panel/render :deployment-fleets
  [path]
  (let [[_ path1] path
        n        (count path)
        children (case n
                   2 [detail/Details path1]
                   [Main])]
    [:<>
     [ui/Segment style/basic children]]))
