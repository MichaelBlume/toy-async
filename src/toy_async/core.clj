(ns toy-async.core
  (:gen-class)
  (:require [clojure.core.async :refer [go >! <! timeout chan <!! close! >!!]]
            [org.httpkit.client :as http]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            [org.httpkit.server :refer [send! with-channel run-server]]))

(defn get-chan [url]
  (let [c (chan)]
    (http/get
      url
      {:as :text}
      (fn [resp]
        (>!! c resp)
        (close! c)))
    c))

(defmacro go-channel [r c & body]
  `(with-channel ~r ~c
     (go ~@body)))

(defn threads [r]
  {:body (-> (Thread/getAllStackTraces)
             count
             (str \newline))})

(defn foo [r]
  (go-channel r c
    (<! (timeout 3000))
    (send! c {:body "foo"})))

(defn bar [r]
  (go-channel r c
    (<! (timeout 5000))
    (send! c {:body "bar"})))

(defn foobar [r]
  (go-channel r c
    (let [foo-chan (get-chan "http://localhost:8000/foo")
          bar-chan (get-chan "http://localhost:8000/bar")
          res (str (:body (<! foo-chan))
                   (:body (<! bar-chan)))]
      (send! c {:body res}))))

(defroutes handler
  (GET "/foo" [] foo)
  (GET "/bar" [] bar)
  (GET "/foobar" [] foobar)
  (GET "/threads" [] threads)
  (route/not-found "not found"))

(defn -main [] (run-server (site #'handler) {:port 8000}))
