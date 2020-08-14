(ns status-im.ui.components.invite.advertiser
  (:require [status-im.ui.components.invite.modal :as modal]
            [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.acquisition.core :as acquisition]
            [status-im.acquisition.advertiser :as advertiser]))

(defn accept-popover []
  (let [{:keys [has-reward]} @(re-frame/subscribe [::acquisition/metadata])]
    [modal/popover {:on-accept    #(re-frame/dispatch [::advertiser/decision :accept])
                    :on-decline   #(re-frame/dispatch [::advertiser/decision :decline])
                    :has-reward   has-reward
                    :accept-label (i18n/label :t/advertiser-starter-pack-accept)
                    :title        (when has-reward (i18n/label :t/advertiser-starter-pack-title))
                    :description  (if has-reward
                                    (i18n/label :t/advertiser-starter-pack-description)
                                    (i18n/label :t/advertiser-description))}]))
