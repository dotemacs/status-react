(ns status-im.ui.screens.chat.group
  (:require [re-frame.core :as re-frame]
            [quo.core :as quo]
            [status-im.ui.components.react :as react]
            [status-im.utils.universal-links.core :as links]
            [status-im.ui.screens.chat.styles.main :as style]
            [status-im.i18n :as i18n]
            [status-im.ui.components.list-selection :as list-selection]
            [status-im.ui.components.colors :as colors]
            [reagent.core :as reagent]
            [clojure.string :as string]
            [status-im.ui.components.toolbar :as toolbar])
  (:require-macros [status-im.utils.views :refer [defview letsubs]]))

(defn join-chat-button [chat-id]
  [quo/button
   {:type     :secondary
    :on-press #(re-frame/dispatch [:group-chats.ui/join-pressed chat-id])}
   (i18n/label :t/join-group-chat)])

(defn decline-chat [chat-id]
  [react/touchable-highlight
   {:on-press
    #(re-frame/dispatch [:group-chats.ui/leave-chat-confirmed chat-id])}
   [react/text {:style style/decline-chat}
    (i18n/label :t/group-chat-decline-invitation)]])

(defn request-membership [_]
  (let [message (reagent/atom "")
        retry? (reagent/atom false)]
    (fn [{:keys [rejection introduction-message chat-id] :as invitation}]
      [react/view {:margin-horizontal 16 :margin-top 10}
       (cond
         (and invitation (not rejection) (not @retry?))
         [react/view
          [react/text (i18n/label :t/introduce-yourself)]
          [react/text {:style {:margin-top         10 :margin-bottom 16 :height 66
                               :padding-horizontal 16 :padding-vertical 11
                               :border-color       colors/gray-lighter :border-width 1
                               :border-radius      8
                               :color              colors/gray}}
           introduction-message]
          [react/text {:style {:align-self :flex-end :margin-bottom 30
                               :color      colors/gray}}
           (str (count introduction-message) "/100")]
          [toolbar/toolbar {:show-border? true
                            :center
                            [quo/button
                             {:type     :secondary
                              :disabled true}
                             (i18n/label :t/request-pending)]}]]

         (and invitation rejection (not @retry?))
         [react/view
          [react/text {:style {:align-self :center :margin-bottom 30}}
           (i18n/label :t/membership-declined)]
          [toolbar/toolbar {:show-border? true
                            :right
                            [quo/button
                             {:type     :secondary
                              :on-press #(reset! retry? true)}
                             (i18n/label :t/mailserver-retry)]
                            :left
                            [quo/button
                             {:type     :secondary
                              :on-press #(re-frame/dispatch [:group-chats.ui/remove-chat-confirmed chat-id])}
                             (i18n/label :t/remove-group)]}]]
         :else
         [react/view
          [react/text (i18n/label :t/introduce-yourself)]
          [quo/text-input {:placeholder     (i18n/label :t/message)
                           :on-change-text  #(reset! message %)
                           :max-length      100
                           :multiline       true
                           :container-style {:margin-top 10 :margin-bottom 16}}]
          [react/text {:style {:align-self :flex-end :margin-bottom 30}}
           (str (count @message) "/100")]
          [toolbar/toolbar {:show-border? true
                            :center
                            [quo/button
                             {:type     :secondary
                              :disabled (string/blank? @message)
                              :on-press #(do
                                           (reset! retry? false)
                                           (re-frame/dispatch [:send-group-chat-membership-request @message]))}
                             (i18n/label :t/request-membership)]}]])])))

(defview group-chat-footer
  [chat-id invitation-admin]
  (letsubs [{:keys [joined?]} [:group-chat/inviter-info chat-id]
            invitations [:group-chat/invitations-by-chat-id chat-id]]
    (if invitation-admin
      [request-membership (first invitations)]
      (when-not joined?
        [react/view {:style style/group-chat-join-footer}
         [react/view {:style style/group-chat-join-container}
          [join-chat-button chat-id]
          [decline-chat chat-id]]]))))

(def group-chat-description-loading
  [react/view {:style (merge style/intro-header-description-container
                             {:margin-bottom 36
                              :height        44})}
   [react/text {:style style/intro-header-description}
    (i18n/label :t/loading)]
   [react/activity-indicator {:animating true
                              :size      :small
                              :color     colors/gray}]])

(defview no-messages-group-chat-description-container [chat-id]
  (letsubs [{:keys [highest-request-to lowest-request-from]}
            [:mailserver/ranges-by-chat-id chat-id]]
    [react/nested-text {:style (merge style/intro-header-description
                                      {:margin-bottom 36})}
     (let [quiet-hours (quot (- highest-request-to lowest-request-from)
                             (* 60 60))
           quiet-time  (if (<= quiet-hours 24)
                         (i18n/label :t/quiet-hours
                                     {:quiet-hours quiet-hours})
                         (i18n/label :t/quiet-days
                                     {:quiet-days (quot quiet-hours 24)}))]
       (i18n/label :t/empty-chat-description-public
                   {:quiet-hours quiet-time}))
     [{:style    {:color colors/blue}
       :on-press #(list-selection/open-share
                   {:message
                    (i18n/label
                     :t/share-public-chat-text {:link (links/generate-link :public-chat :external chat-id)})})}
      (i18n/label :t/empty-chat-description-public-share-this)]]))

(defview pending-invitation-description
  [inviter-pk chat-name]
  (letsubs [inviter-name [:contacts/contact-name-by-identity inviter-pk]]
    [react/nested-text {:style style/intro-header-description}
     [{:style {:color colors/black}} inviter-name]
     (i18n/label :t/join-group-chat-description
                 {:username   ""
                  :group-name chat-name})]))

(defview joined-group-chat-description
  [inviter-pk chat-name]
  (letsubs [inviter-name [:contacts/contact-name-by-identity inviter-pk]]
    [react/nested-text {:style style/intro-header-description}
     (i18n/label :t/joined-group-chat-description
                 {:username   ""
                  :group-name chat-name})
     [{:style {:color colors/black}} inviter-name]]))

(defn created-group-chat-description [chat-name]
  [react/text {:style style/intro-header-description}
   (i18n/label :t/created-group-chat-description
               {:group-name chat-name})])

(defview group-chat-inviter-description-container [chat-id chat-name]
  (letsubs [{:keys [joined? inviter-pk]}
            [:group-chat/inviter-info chat-id]]
    (cond
      (not joined?)
      [pending-invitation-description inviter-pk chat-name]
      inviter-pk
      [joined-group-chat-description inviter-pk chat-name]
      :else
      [created-group-chat-description chat-name])))

(defn group-chat-membership-description []
  [react/text {:style {:text-align :center :margin-horizontal 30}}
   (i18n/label :t/membership-description)])

(defn group-chat-description-container
  [{:keys [public?
           invitation-admin
           chat-id
           chat-name
           loading-messages?
           no-messages?]}]
  (cond loading-messages?
        group-chat-description-loading

        (and no-messages? public?)
        [no-messages-group-chat-description-container chat-id]

        invitation-admin
        [group-chat-membership-description]

        (not public?)
        [group-chat-inviter-description-container chat-id chat-name]))
