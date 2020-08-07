(ns status-im.acquisition.core
  (:require [re-frame.core :as re-frame]
            [clojure.string :as string]
            [status-im.utils.fx :as fx]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.ens :as ens]
            [status-im.ethereum.contracts :as contracts]
            [status-im.acquisition.chat :as chat]
            [status-im.acquisition.advertiser :as advertiser]
            [status-im.acquisition.persistance :as persistence]
            [status-im.acquisition.gateway :as gateway]
            [status-im.acquisition.install-referrer :as install-referrer]))

(def not-found-code "notfound.click_id")
(def advertiser-type "advertiser")
(def chat-type "chat")

(fx/defn handle-registration
  [cofx {:keys [message on-success]}]
  (gateway/handle-acquisition cofx
                              {:message    message
                               :on-success on-success
                               :method     "POST"
                               :url        (gateway/get-url :registrations nil)}))

(re-frame/reg-fx
 ::get-referrer
 (fn []
   (persistence/get-referrer-flow-state
    (fn [^js data]
      (install-referrer/get-referrer
       (fn [install-referrer]
         (persistence/set-referrer install-referrer)
         (when (not= install-referrer "unknown")
           (when-let [referrer (install-referrer/parse-referrer install-referrer)]
             (re-frame/dispatch [::has-referrer data referrer])))))))))

(re-frame/reg-fx
 ::check-referrer
 (fn [external-referrer]
   (persistence/get-referrer-flow-state
    (fn [^js data]
      (if external-referrer
        (re-frame/dispatch [::has-referrer data external-referrer])
        (persistence/get-referrer
         (fn [install-referrer]
           (when (not= install-referrer "unknown")
             (when-let [referrer (install-referrer/parse-referrer install-referrer)]
               (re-frame/dispatch [::has-referrer data referrer]))))))))))

(fx/defn referrer-registered
  {:events [::referrer-registered]}
  [{:keys [db] :as cofx} referrer {:keys [type attributed] :as referrer-meta}]
  (when-not attributed
    (fx/merge cofx
              {:db (assoc-in db [:acquisition :metadata] referrer-meta)}
              (cond
                (= type advertiser-type)
                (advertiser/start-acquisition referrer-meta)

                (= type chat-type)
                (chat/start-acquisition referrer-meta)))))

(fx/defn outdated-referrer
  {:events [::outdated-referrer]}
  [_ _]
  {::persistence/set-referrer-state :outdated})

(fx/defn has-referrer
  {:events [::has-referrer]}
  [{:keys [db] :as cofx} flow-state referrer]
  (when (and referrer
             (get-in db [:acquisition :contract]))
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:acquisition :referrer] referrer)
                       (assoc-in [:acquisition :flow-state] flow-state))}
              (cond
                (nil? flow-state)
                (gateway/get-referrer
                 referrer
                 (fn [resp] [::referrer-registered referrer resp])
                 (fn [{:keys [code]}] (= code not-found-code))
                 (fn [resp] [::outdated-referrer resp]))

                (= flow-state (:accepted persistence/referrer-state))
                (fn [_]
                  {::persistence/check-tx-state (fn [tx]
                                                  (when-not (nil? tx)
                                                    (re-frame/dispatch [::add-tx-watcher tx])))})))))

(re-frame/reg-fx
 ::resolve-contract
 (fn [{:keys [chain contract on-success]}]
   (let [register (get ens/ens-registries chain)]
     (if (string/starts-with? contract "0x")
       (on-success contract)
       (ens/resolver register contract on-success)))))

(fx/defn create [{:keys [db]}]
  {::resolve-contract {:chain      (ethereum/chain-keyword db)
                       :contract   (contracts/get-address db :status/acquisition)
                       :on-success #(re-frame/dispatch [:set-in [:acquisition :contract] %])}
   ::get-referrer     nil})

(fx/defn login [{:keys [db]}]
  {::resolve-contract {:chain      (ethereum/chain-keyword db)
                       :contract   (contracts/get-address db :status/acquisition)
                       :on-success #(re-frame/dispatch [:set-in [:acquisition :contract] %])}
   ::check-referrer   nil})
