(ns balthasar.app.core
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.dom :as dom]
   [balthasar.app.config :as config]
   [balthasar.app.video :as video]
   [re-frame.core :as re-frame]))

(defui app []
  #_($  video/video-upload {:on-upload-complete #(println "video uploaded" %)})
  ($ video/video-player-page))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (dom/create-root (.getElementById js/document "app"))]
    (dom/render-root ($ app) root-el)))

(defn ^:export init []
  (dev-setup)
  (mount-root))
