(ns sixsq.nuvla.ui.edge.views
  (:require
    [clojure.string :as str]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [sixsq.nuvla.ui.cimi-api.effects :as cimi-fx]
    [sixsq.nuvla.ui.edge-detail.views :as edge-detail]
    [sixsq.nuvla.ui.edge.events :as events]
    [sixsq.nuvla.ui.edge.subs :as subs]
    [sixsq.nuvla.ui.edge.utils :as utils]
    [sixsq.nuvla.ui.history.events :as history-events]
    [sixsq.nuvla.ui.i18n.subs :as i18n-subs]
    [sixsq.nuvla.ui.main.components :as main-components]
    [sixsq.nuvla.ui.main.events :as main-events]
    [sixsq.nuvla.ui.panel :as panel]
    [sixsq.nuvla.ui.utils.general :as general-utils]
    [sixsq.nuvla.ui.utils.map :as map]
    [sixsq.nuvla.ui.utils.semantic-ui :as ui]
    [sixsq.nuvla.ui.utils.semantic-ui-extensions :as uix]
    [sixsq.nuvla.ui.utils.style :as style]
    [sixsq.nuvla.ui.utils.ui-callback :as ui-callback]
    [sixsq.nuvla.ui.utils.zip :as zip]))


(def view-type (r/atom :cards))

(defn StatisticState
  [value icon label]
  (let [state-selector (subscribe [::subs/state-selector])
        selected?      (or
                         (= label @state-selector)
                         (and (= label "TOTAL")
                              (= @state-selector nil)))
        color          (if selected? "black" "grey")]
    [ui/Statistic {:style    {:cursor "pointer"}
                   :color    color
                   :on-click #(dispatch [::events/set-state-selector
                                         (if (= label "TOTAL") nil label)])}
     [ui/StatisticValue (or value "-")
      "\u2002"
      [ui/Icon {:size (when selected? "large") :name icon}]]
     [ui/StatisticLabel label]]))


(defn StatisticStates
  []
  (let [{:keys [total new activated commissioned
                decommissioning decommissioned error]} @(subscribe [::subs/state-nuvlaboxes])]
    [ui/StatisticGroup (merge {:size "tiny"} style/center-block)
     [StatisticState total "box" "TOTAL"]
     [StatisticState new (utils/state->icon utils/state-new) "NEW"]
     [StatisticState activated (utils/state->icon utils/state-activated) "ACTIVATED"]
     [StatisticState commissioned (utils/state->icon utils/state-commissioned) "COMMISSIONED"]
     [StatisticState decommissioning
      (utils/state->icon utils/state-decommissioning) "DECOMMISSIONING"]
     [StatisticState decommissioned (utils/state->icon utils/state-decommissioned) "DECOMMISSIONED"]
     [StatisticState error (utils/state->icon utils/state-error) "ERROR"]]))


(defn AddButton
  []
  (let [tr (subscribe [::i18n-subs/tr])]
    [uix/MenuItemWithIcon
     {:name      (@tr [:add])
      :icon-name "add"
      :on-click  #(dispatch
                    [::main-events/subscription-required-dispatch
                     [::events/open-modal :add]])}]))


(defn MenuBar []
  (let [loading?  (subscribe [::subs/loading?])
        full-text (subscribe [::subs/full-text-search])
        tr (subscribe [::i18n-subs/tr])]
    (dispatch [::events/refresh])
    (fn []
      [:<>
       [uix/PageHeader
        "box" (general-utils/capitalize-first-letter (@tr [:edge]))]
       [ui/Menu {:borderless true, :stackable true}
        [AddButton]
        [ui/MenuItem {:icon     "grid layout"
                      :active   (= @view-type :cards)
                      :on-click #(reset! view-type :cards)}]
        [ui/MenuItem {:icon     "table"
                      :active   (= @view-type :table)
                      :on-click #(reset! view-type :table)}]
        [ui/MenuItem {:icon     "map"
                      :active   (= @view-type :map)
                      :on-click #(reset! view-type :map)}]
        [main-components/RefreshMenu
         {:action-id  events/refresh-id
          :loading?   @loading?
          :on-refresh #(dispatch [::events/refresh])}]]
       [main-components/SearchInput
        {:default-value @full-text
         :on-change     (ui-callback/input-callback
                          #(dispatch [::events/set-full-text-search %]))}]])))


(defn NuvlaDocs
  [tr]
  [ui/Container {:text-align :center
                 :style      {:margin "0.5em"}}
   [:span (@tr [:nuvlabox-documentation])
    [:a {:href   "https://docs.nuvla.io/docs/nuvlabox/nuvlabox-engine/quickstart.html"
         :target "_blank"} "Nuvla Docs"]]])


(defn CreatedNuvlaBox
  [nuvlabox-id creation-data nuvlabox-release-data nuvlabox-ssh-keys new-private-ssh-key on-close-fn tr]
  (let [nuvlabox-release     (:nb-selected nuvlabox-release-data)
        nuvlabox-peripherals (:nb-assets nuvlabox-release-data)
        private-ssh-key-file (str (general-utils/id->short-uuid nuvlabox-id) ".ssh.private")
        public-keys          (if @nuvlabox-ssh-keys
                               (str (str/join "\\n" (:public-keys @nuvlabox-ssh-keys)) "\\n")
                               nil)
        zip-url              (r/atom nil)
        envsubst             (if public-keys
                               [#"\$\{NUVLABOX_UUID\}" nuvlabox-id
                                #"\$\{NUVLABOX_SSH_PUB_KEY\}" public-keys]
                               [#"\$\{NUVLABOX_UUID\}" nuvlabox-id])
        download-files       (utils/prepare-compose-files nuvlabox-release nuvlabox-peripherals envsubst)]
    (zip/create download-files #(reset! zip-url %))
    (when @nuvlabox-ssh-keys
      (dispatch [::events/assign-ssh-keys @nuvlabox-ssh-keys nuvlabox-id]))
    (fn []
      (let [nuvlabox-name-or-id (str "NuvlaBox " (or (:name creation-data)
                                                     (general-utils/id->short-uuid nuvlabox-id)))
            execute-command     (str "docker-compose -p nuvlabox -f "
                                     (str/join " -f " (map :name download-files)) " up -d")]
        [:<>
         [ui/ModalHeader
          [ui/Icon {:name "box"}] (str nuvlabox-name-or-id " created")]

         (when @new-private-ssh-key
           [ui/Message
            {:negative true
             :content  (@tr [:nuvlabox-modal-private-ssh-key-info])
             :header   (r/as-element
                         [:a {:href     (str "data:text/plain;charset=utf-8,"
                                             (js/encodeURIComponent @new-private-ssh-key))
                              :target   "_blank"
                              :download private-ssh-key-file
                              :key      private-ssh-key-file}
                          [ui/Icon {:name "privacy"}] private-ssh-key-file])}])

         [ui/ModalContent
          [ui/CardGroup {:centered true}
           [ui/Card
            [ui/CardContent {:text-align :center}
             [ui/Header [:span {:style {:overflow-wrap "break-word"}} nuvlabox-name-or-id]]
             [ui/Icon {:name  "box"
                       :color "green"
                       :size  :massive}]]
            [ui/CopyToClipboard {:text nuvlabox-id}
             [ui/Button {:positive true
                         :icon     "clipboard"
                         :content  (@tr [:copy-nuvlabox-id])}]]]]]

         [ui/Divider {:horizontal true}
          [ui/Header (@tr [:nuvlabox-quick-install])]]

         [ui/Segment {:loading    (nil? @zip-url)
                      :text-align :center
                      :raised     true}
          [ui/Label {:circular true
                     :color    "green"} "1"]
          [:h5 {:style {:margin "0.5em 0 1em 0"}}
           (str/capitalize (@tr [:download])) " compose file(s)"]
          [:a {:href     @zip-url
               :target   "_blank"
               :style    {:margin "1em"}
               :download "nuvlabox-engine.zip"} "nuvlabox-engine.zip"]]

         [ui/Segment {:text-align :center
                      :raised     true}
          [ui/Label {:circular true
                     :color    "green"} "2"]
          [:h5 {:style {:margin "0.5em 0 1em 0"}}
           (@tr [:nuvlabox-unzip-execute])
           [ui/CopyToClipboard {:text execute-command}
            [:a {:href  "#"
                 :style {:font-size   "0.9em"
                         :color       "grey"
                         :font-style  "italic"
                         :font-weight "lighter"}} "(click to copy)"]]]
          [:span {:style {:font "1em Inconsolata, monospace"}} execute-command]]

         [NuvlaDocs tr]

         [ui/ModalActions
          [ui/Button {:on-click on-close-fn} (@tr [:close])]]]))))


(defn CreatedNuvlaBoxUSBTrigger
  [creation-data nuvlabox-release-data nuvlabox-ssh-keys new-private-ssh-key on-close-fn tr]
  (let [nuvlabox-release     (:nb-selected nuvlabox-release-data)
        nuvlabox-peripherals (:nb-assets nuvlabox-release-data)
        new-api-key          (subscribe [::subs/nuvlabox-usb-api-key])
        private-ssh-key-file "nuvlabox.ssh.private"
        download-files       (utils/prepare-compose-files nuvlabox-release nuvlabox-peripherals
                                                          [#"placeholder" "placeholder"])
        download-files-names (map :name download-files)]

    (fn []
      (let [apikey                (:resource-id @new-api-key)
            apisecret             (:secret-key @new-api-key)
            nb-trigger-file-base  {:assets      download-files-names
                                   :version     (:release nuvlabox-release)
                                   :name        (:name creation-data)
                                   :description (:description creation-data)
                                   :script      (str @cimi-fx/NUVLA_URL
                                                     "/ui/downloads/nuvlabox-self-registration.py")
                                   :endpoint    @cimi-fx/NUVLA_URL
                                   :vpn         (:vpn-server-id creation-data)
                                   :apikey      apikey
                                   :apisecret   apisecret}
            nuvlabox-trigger-file (if @nuvlabox-ssh-keys
                                    (assoc nb-trigger-file-base :ssh @nuvlabox-ssh-keys)
                                    nb-trigger-file-base)]
        [:<>
         [ui/ModalHeader
          [ui/Icon {:name "usb"}] (@tr [:nuvlabox-modal-usb-header])]
         [ui/Message {:attached true
                      :icon     true
                      :floating true}
          [ui/Icon {:name (if apikey "check circle outline" "spinner")}]
          [ui/MessageContent
           [ui/MessageHeader
            (@tr [:nuvlabox-usb-key])]
           (if apikey
             [:span (str (@tr [:nuvlabox-usb-key-ready]) " ")
              [:a {:href   (str "api/" apikey)
                   :target "_blank"}
               apikey] " "
              [ui/Popup {:content (@tr [:nuvlabox-modal-usb-apikey-warning])
                         :trigger (r/as-element [ui/Icon {:name  "exclamation triangle"
                                                          :color "orange"}])}]]
             (@tr [:nuvlabox-usb-key-wait]))]]

         (when @new-private-ssh-key
           [ui/Message
            {:negative true
             :content  (@tr [:nuvlabox-modal-private-ssh-key-info])
             :header   (r/as-element
                         [:a {:href     (str "data:text/plain;charset=utf-8,"
                                             (js/encodeURIComponent @new-private-ssh-key))
                              :target   "_blank"
                              :download private-ssh-key-file
                              :key      private-ssh-key-file}
                          [ui/Icon {:name "privacy"}] private-ssh-key-file])}])

         [ui/ModalContent
          [ui/CardGroup {:centered true}
           [ui/Card
            [ui/CardContent {:text-align :center}
             [ui/Header [:span {:style {:overflow-wrap "break-word"}}
                         (@tr [:nuvlabox-modal-usb-trigger-file])]]
             [ui/Icon {:name    (if apikey "file code" "spinner")
                       :loading (nil? apikey)
                       :color   "green"
                       :size    :massive}]]
            [:a {:href     (str "data:text/plain;charset=utf-8,"
                                (js/encodeURIComponent
                                  (general-utils/edn->json nuvlabox-trigger-file)))
                 :target   "_blank"
                 :download "nuvlabox-installation-trigger-usb.nuvla"}
             [ui/Button {:positive       true
                         :fluid          true
                         :loading        (nil? apikey)
                         :icon           "download"
                         :label-position "left"
                         :as             "div"
                         :content        (@tr [:download])}]]]]]

         [ui/Divider {:horizontal true}
          [ui/Header (@tr [:instructions])]]

         [ui/Segment {:loading    (nil? nuvlabox-trigger-file)
                      :text-align :center
                      :raised     true}
          [ui/Label {:circular true
                     :color    "green"} "1"]
          [:h5 {:style {:margin "0.5em 0 1em 0"}}
           (@tr [:nuvlabox-modal-usb-copy])
           [ui/Popup {:content (@tr [:nuvlabox-modal-usb-copy-warning])
                      :trigger (r/as-element [ui/Icon {:name "info circle"}])}]]]

         [ui/Segment {:text-align :center
                      :raised     true}
          [ui/Label {:circular true
                     :color    "green"} "2"]
          [:h5 {:style {:margin "0.5em 0 1em 0"}}
           (@tr [:nuvlabox-modal-usb-plug])]
          [:span (@tr [:nuvlabox-modal-usb-plug-info])]]

         [ui/Segment {:text-align :center
                      :raised     true}
          [ui/Label {:circular true
                     :color    "green"} "3"]
          [:h5 {:style {:margin "0.5em 0 1em 0"}}
           (@tr [:repeat])]
          [:span (@tr [:repeat-info])]]

         [NuvlaDocs tr]

         [ui/ModalActions
          [ui/Button {:on-click on-close-fn} (@tr [:close])]]]))))


(defn AddModal
  []
  (let [modal-id                   :add
        tr                         (subscribe [::i18n-subs/tr])
        visible?                   (subscribe [::subs/modal-visible? modal-id])
        nuvlabox-id                (subscribe [::subs/nuvlabox-created-id])
        vpn-infra-opts             (subscribe [::subs/vpn-infra-options])
        nb-releases                (subscribe [::subs/nuvlabox-releases])
        ssh-credentials            (subscribe [::subs/ssh-keys-available])
        nb-releases-options        (map
                                     (fn [{:keys [release]}]
                                       {:key release, :text release, :value release})
                                     @nb-releases)
        nb-releases-by-rel         (group-by :release @nb-releases)
        default-data               {:refresh-interval 30}
        first-nb-release           (first @nb-releases)
        creation-data              (r/atom default-data)
        default-release-data       {:nb-rel      (:release first-nb-release)
                                    :nb-selected first-nb-release
                                    :nb-assets   (->> first-nb-release
                                                      :compose-files
                                                      (map :scope)
                                                      set)}
        nuvlabox-release-data      (r/atom default-release-data)
        advanced?                  (r/atom false)
        install-strategy-default   nil
        install-strategy           (r/atom install-strategy-default)
        install-strategy-error     (r/atom install-strategy-default)
        create-usb-trigger-default false
        create-usb-trigger         (r/atom create-usb-trigger-default)
        ; default ttl for API key is 30 days
        default-ttl                30
        usb-trigger-key-ttl        (r/atom default-ttl)
        new-api-key-data           {:description "Auto-generated for NuvlaBox self-registration USB trigger"
                                    :name        "NuvlaBox self-registration USB trigger"
                                    :template    {
                                                  :method "generate-api-key"
                                                  :ttl    (* @usb-trigger-key-ttl 24 60 60)
                                                  :href   "credential-template/generate-api-key"}}
        ssh-toggle                 (r/atom false)
        ssh-existing-key           (r/atom false)
        ssh-chosen-keys            (r/atom [])
        nuvlabox-ssh-keys          (subscribe [::subs/nuvlabox-ssh-key])
        new-private-ssh-key        (subscribe [::subs/nuvlabox-private-ssh-key])
        on-close-fn                #(do
                                      (dispatch [::events/set-created-nuvlabox-id nil])
                                      (dispatch [::events/set-nuvlabox-usb-api-key nil])
                                      (dispatch [::events/set-nuvlabox-ssh-keys nil])
                                      (dispatch [::events/set-nuvlabox-created-private-ssh-key nil])
                                      (dispatch [::events/open-modal nil])
                                      (reset! advanced? false)
                                      (reset! ssh-toggle false)
                                      (reset! ssh-existing-key false)
                                      (reset! ssh-chosen-keys [])
                                      (reset! creation-data default-data)
                                      (reset! install-strategy install-strategy-default)
                                      (reset! usb-trigger-key-ttl default-ttl)
                                      (reset! install-strategy-error install-strategy-default)
                                      (reset! create-usb-trigger create-usb-trigger-default)
                                      (reset! nuvlabox-release-data default-release-data))
        on-add-fn                  #(cond
                                      (nil? @install-strategy) (reset! install-strategy-error true)
                                      :else (do
                                              (when @ssh-toggle
                                                (if @ssh-existing-key
                                                  (when (not-empty @ssh-chosen-keys)
                                                    (dispatch [::events/find-nuvlabox-ssh-keys
                                                               @ssh-chosen-keys]))
                                                  ; else, create new one
                                                  (let [ssh-desc "SSH credential generated for NuvlaBox: "
                                                        ssh-tpl  {:name        (str "SSH key for " (:name @creation-data))
                                                                  :description (str ssh-desc (:name @creation-data))
                                                                  :template    {:href "credential-template/generate-ssh-key"}}]
                                                    (dispatch [::events/create-ssh-key ssh-tpl]))))
                                              (if (= @install-strategy "usb")
                                                (do
                                                  (dispatch [::events/create-nuvlabox-usb-api-key
                                                             (->> new-api-key-data
                                                                  (remove (fn [[_ v]]
                                                                            (str/blank? v)))
                                                                  (into {}))])
                                                  (reset! create-usb-trigger true))
                                                (do
                                                  (dispatch [::events/create-nuvlabox
                                                             (->> @creation-data
                                                                  (remove (fn [[_ v]]
                                                                            (str/blank? v)))
                                                                  (into {}))])
                                                  (reset! creation-data default-data)))))]
    (dispatch [::events/get-ssh-keys-available ["ssh-key"] nil])
    (fn []
      (when (= (count @vpn-infra-opts) 1)
        (swap! creation-data assoc :vpn-server-id (-> @vpn-infra-opts first :value)))
      [ui/Modal {:open       @visible?
                 :close-icon true
                 :on-close   on-close-fn}
       (cond
         @nuvlabox-id [CreatedNuvlaBox @nuvlabox-id @creation-data @nuvlabox-release-data
                       nuvlabox-ssh-keys new-private-ssh-key on-close-fn tr]
         @create-usb-trigger [CreatedNuvlaBoxUSBTrigger @creation-data @nuvlabox-release-data
                              nuvlabox-ssh-keys new-private-ssh-key on-close-fn tr]
         :else [:<>
                [ui/ModalHeader
                 [ui/Icon {:name "add"}] (str (@tr [:nuvlabox-modal-new-nuvlabox]) (:name @creation-data))]

                [ui/ModalContent
                 [ui/Divider {:horizontal true :as "h3"}
                  (@tr [:nuvlabox-modal-general])]

                 [ui/Table style/definition
                  [ui/TableBody
                   [uix/TableRowField (@tr [:name]), :on-change #(swap! creation-data assoc :name %),
                    :default-value (:name @creation-data)]
                   [uix/TableRowField (@tr [:description]), :type :textarea,
                    :on-change #(swap! creation-data assoc :description %)
                    :default-value (:name @creation-data)]
                   [ui/TableRow
                    [ui/TableCell {:collapsing true} "vpn"]
                    ^{:key (or key name)}
                    [ui/TableCell
                     [ui/Dropdown {:clearable   (> (count @vpn-infra-opts) 1)
                                   :selection   true
                                   :fluid       true
                                   :placeholder (@tr [:none])
                                   :value       (:vpn-server-id @creation-data)
                                   :on-change   (ui-callback/callback
                                                  :value #(swap! creation-data assoc
                                                                 :vpn-server-id %))
                                   :options     @vpn-infra-opts}]]]]]

                 [ui/Checkbox {:slider    true
                               :label     (@tr [:nuvlabox-modal-add-ssh-key])
                               :checked   @ssh-toggle
                               :on-change #(do
                                             (swap! ssh-toggle not)
                                             (reset! ssh-chosen-keys [])
                                             (reset! ssh-existing-key false))}]

                 [ui/Segment {:style {:display (if @ssh-toggle "block" "none")}}
                  [ui/Form
                   [ui/FormGroup {:inline true}

                    [ui/FormCheckbox {:label     (@tr [:nuvlabox-modal-add-new-ssh-key])
                                      :radio     true
                                      :checked   (not @ssh-existing-key)
                                      :on-change #(do
                                                    (swap! ssh-existing-key not))}]

                    [ui/FormCheckbox {:label     (@tr [:nuvlabox-modal-add-existing-ssh-key])
                                      :radio     true
                                      :checked   @ssh-existing-key
                                      :on-change #(do
                                                    (swap! ssh-existing-key not))}]]]

                  (when @ssh-existing-key
                    (if (pos-int? (count @ssh-credentials))
                      [ui/Dropdown {:search      true
                                    :multiple    true
                                    :selection   true
                                    :fluid       true
                                    :placeholder (@tr [:nuvlabox-modal-select-existing-ssh-key])

                                    :on-change   (ui-callback/callback
                                                   :value #(do
                                                             (reset! ssh-chosen-keys %)))
                                    :options     (map (fn [{id :id, name :name}]
                                                        {:key id, :value id, :text name})
                                                      @ssh-credentials)}]

                      [ui/Message {:content (str/capitalize
                                              (@tr [:nuvlabox-modal-no-ssh-keys-avail]))}]
                      ))

                  ]

                 (let [{nb-rel                                  :nb-rel
                        nb-assets                               :nb-assets
                        {:keys [compose-files url pre-release]} :nb-selected}
                       @nuvlabox-release-data]
                   [ui/Container
                    [ui/Divider {:horizontal true :as "h3"}
                     (@tr [:nuvlabox-modal-version])]
                    [ui/Dropdown {:selection   true
                                  :placeholder nb-rel
                                  :value       nb-rel
                                  :options     nb-releases-options
                                  :on-change   (ui-callback/value
                                                 (fn [value]
                                                   (swap! nuvlabox-release-data
                                                          assoc :nb-rel value)
                                                   (swap! creation-data assoc
                                                          :version (-> value
                                                                       utils/get-major-version
                                                                       general-utils/str->int))
                                                   (swap! nuvlabox-release-data assoc
                                                          :nb-selected
                                                          (->> value
                                                               (get nb-releases-by-rel)
                                                               (into (sorted-map))))
                                                   (swap! nuvlabox-release-data assoc :nb-assets
                                                          (set (map :scope
                                                                    (:compose-files
                                                                      (:nb-selected
                                                                        @nuvlabox-release-data)))))
                                                   ))}]
                    [:a {:href   url
                         :target "_blank"
                         :style  {:margin "1em"}}
                     (@tr [:nuvlabox-release-notes])]
                    (when pre-release
                      [ui/Popup
                       {:trigger        (r/as-element [ui/Icon {:name "exclamation triangle"}])
                        :content        (@tr [:nuvlabox-pre-release])
                        :on             "hover"
                        :hide-on-scroll true}])
                    [ui/Container
                     (when (> (count compose-files) 1)
                       [ui/Popup
                        {:trigger        (r/as-element [:span (@tr [:additional-modules])])
                         :content        (str (@tr [:additional-modules-popup]))
                         :on             "hover"
                         :hide-on-scroll true}])
                     (doall
                       (for [{:keys [scope]} compose-files]
                         (when-not (#{"core" ""} scope)
                           [ui/Checkbox {:key             scope
                                         :label           scope
                                         :default-checked (contains?
                                                            (:nb-assets @nuvlabox-release-data)
                                                            scope)
                                         :style           {:margin "1em"}
                                         :on-change       (ui-callback/checked
                                                            (fn [checked]
                                                              (if checked
                                                                (swap! nuvlabox-release-data assoc
                                                                       :nb-assets
                                                                       (conj nb-assets scope))
                                                                (swap! nuvlabox-release-data assoc
                                                                       :nb-assets
                                                                       (-> @nuvlabox-release-data
                                                                           :nb-assets
                                                                           (disj scope))))))}])))]

                    [ui/Divider {:horizontal true :as "h3"}
                     (@tr [:nuvlabox-modal-install-method])]

                    [ui/Form
                     [ui/FormCheckbox {:label     "Compose file bundle"
                                       :radio     true
                                       :error     (not (nil? @install-strategy-error))
                                       :checked   (= @install-strategy "compose")
                                       :on-change #(do
                                                     (reset! install-strategy "compose")
                                                     (reset! install-strategy-error nil))}]

                     [:div {:style {:color "grey" :font-style "oblique"}}
                      (@tr [:create-nuvlabox-compose])]

                     [ui/Divider {:hidden true}]

                     [ui/FormCheckbox {:label     "USB stick"
                                       :radio     true
                                       :error     (not (nil? @install-strategy-error))
                                       :checked   (= @install-strategy "usb")
                                       :on-change #(do
                                                     (reset! install-strategy "usb")
                                                     (reset! install-strategy-error nil))}]

                     [:div {:style {:color "grey" :font-style "oblique"}}
                      (@tr [:create-nuvlabox-usb])]
                     [:a {:href   "https://docs.nuvla.io"
                          :target "_blank"}
                      (@tr [:nuvlabox-modal-more-info])]
                     ]

                    [ui/Container {:style {:margin  "5px"
                                           :display (if (= @install-strategy "usb")
                                                      "inline-block" "none")}}
                     [ui/Input {:label       (@tr [:nuvlabox-modal-usb-expires])
                                :placeholder default-ttl
                                :value       @usb-trigger-key-ttl
                                :size        "mini"
                                :type        "number"
                                :on-change   (ui-callback/input-callback
                                               #(cond
                                                  (number? (general-utils/str->int %))
                                                  (reset! usb-trigger-key-ttl
                                                          (general-utils/str->int %))
                                                  (empty? %) (reset! usb-trigger-key-ttl 0)))
                                :step        1
                                :min         0}]
                     [ui/Popup {:content  (@tr [:nuvlabox-modal-usb-expires-popup] [default-ttl])
                                :position "right center"
                                :wide     true
                                :trigger  (r/as-element [ui/Icon {:name  "question"
                                                                  :color "grey"}])}]]])]

                [ui/ModalActions
                 [:span {:style {:color "#9f3a38" :display (if (not (nil? @install-strategy-error))
                                                             "inline-block" "none")}}
                  (@tr [:nuvlabox-modal-missing-fields])]
                 [ui/Button {:positive true
                             :on-click on-add-fn}
                  (@tr [:create])]]])])))


(defn AddModalWrapper
  []
  (let [nb-release (subscribe [::subs/nuvlabox-releases])]
    ^{:key (count @nb-release)}
    [AddModal]))

(defn NuvlaboxRow
  [{:keys [id state name] :as nuvlabox}]
  (let [status   (subscribe [::subs/status-nuvlabox id])
        uuid     (general-utils/id->uuid id)
        on-click #(dispatch [::history-events/navigate (str "edge/" uuid)])]
    [ui/TableRow {:on-click on-click
                  :style    {:cursor "pointer"}}
     [ui/TableCell {:collapsing true}
      [edge-detail/StatusIcon @status]]
     [ui/TableCell {:collapsing true}
      [ui/Icon {:name (utils/state->icon state)}]]
     [ui/TableCell (or name uuid)]]))


(defn Pagination
  []
  (let [nuvlaboxes        (subscribe [::subs/nuvlaboxes])
        elements-per-page (subscribe [::subs/elements-per-page])
        page              (subscribe [::subs/page])
        total-elements    (get @nuvlaboxes :count 0)
        total-pages       (general-utils/total-pages total-elements @elements-per-page)]

    [uix/Pagination {:totalPages   total-pages
                     :activePage   @page
                     :onPageChange (ui-callback/callback
                                     :activePage #(dispatch [::events/set-page %]))}]))


(defn NuvlaboxTable
  []
  (let [nuvlaboxes (subscribe [::subs/nuvlaboxes])]
    [ui/Table {:compact "very", :selectable true}
     [ui/TableHeader
      [ui/TableRow
       [ui/TableHeaderCell [ui/Icon {:name "heartbeat"}]]
       [ui/TableHeaderCell "state"]
       [ui/TableHeaderCell "name"]]]

     [ui/TableBody
      (doall
        (for [{:keys [id] :as nuvlabox} (:resources @nuvlaboxes)]
          ^{:key id}
          [NuvlaboxRow nuvlabox]))]]))


(defn NuvlaboxMapPoint
  [{:keys [id name location] :as nuvlabox}]
  (let [status   (subscribe [::subs/status-nuvlabox id])
        uuid     (general-utils/id->uuid id)
        on-click #(dispatch [::history-events/navigate (str "edge/" uuid)])]
    [map/CircleMarker {:on-click on-click
                       :center   (map/longlat->latlong location)
                       :color    (utils/map-status->color @status)
                       :opacity  0.5
                       :weight   2}
     [map/Tooltip (or name id)]]))


(defn NuvlaboxCards
  []
  (let [nuvlaboxes (subscribe [::subs/nuvlaboxes])]
    [ui/CardGroup {:centered true}
     (doall
       (for [{:keys [id] :as nuvlabox} (:resources @nuvlaboxes)]
         (let [status      (subscribe [::subs/status-nuvlabox id])
               uuid        (general-utils/id->uuid id)
               on-click-fn #(dispatch [::history-events/navigate (str "edge/" uuid)])]
           ^{:key id}
           [edge-detail/NuvlaboxCard nuvlabox @status :on-click on-click-fn])))]))


(defn NuvlaboxMap
  []
  (let [nuvlaboxes (subscribe [::subs/nuvlaboxes])]
    [map/MapBox
     {:style  {:height 500}
      :center map/sixsq-latlng
      :zoom   3}
     (doall
       (for [{:keys [id] :as nuvlabox} (->> @nuvlaboxes
                                            :resources
                                            (filter #(:location %)))]
         ^{:key id}
         [NuvlaboxMapPoint nuvlabox]))]))


(defmethod panel/render :edge
  [path]
  (let [[_ uuid] path
        n        (count path)
        root     [:<>
                  [MenuBar]
                  [StatisticStates]
                  (case @view-type
                    :cards [NuvlaboxCards]
                    :table [NuvlaboxTable]
                    :map [NuvlaboxMap])
                  (when-not (= @view-type :map)
                    [Pagination])
                  [AddModalWrapper]]
        children (case n
                   1 root
                   2 [edge-detail/EdgeDetails uuid]
                   root)]
    (dispatch [::events/get-vpn-infra])
    (dispatch [::events/get-nuvlabox-releases])
    [ui/Segment style/basic
     children]))
