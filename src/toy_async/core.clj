(ns toy-async.core
  (:require [clojure.core.async :refer [go >! <! timeout chan <!! close! >!!]]
            [cheshire.core :refer [generate-string]]
            [ring.adapter.jetty-async :refer [run-jetty-async]]
            [org.httpkit.client :as http]))

(defn get-chan [url]
  (let [c (chan)]
    (http/get
      url
      {:as :text}
      (fn [resp]
        (>!! c resp)
        (close! c)))
    c))

(count (Thread/getAllStackTraces))

(defn threads [r]
  {:body (-> (Thread/getAllStackTraces)
             count
             (str \newline))})

(defn foo [r]
  (let [c (chan)]
    (go
      (<! (timeout 3000))
      (>! c "foo")
      (close! c))
    {:body c}))

(defn bar [r]
  (let [c (chan)]
    (go
      (<! (timeout 5000))
      (>! c "bar")
      (close! c))
    {:body c}))

(defn foobar [r]
  (let [c (chan)]
    (go
      (let [foo-chan (get-chan "http://localhost:8000/foo")
            bar-chan (get-chan "http://localhost:8000/bar")
            res (str (:body (<! foo-chan))
                     (:body (<! bar-chan)))]
        (>! c res)
        (close! c)))
    {:body c}))

(defn handler [request]
  (let [uri (:uri request)
        dispatch (or ({"/foo" foo
                       "/bar" bar
                       "/threads" threads
                       "/foobar" foobar} uri)
                     (constantly {:body "not found"}))]
    (dispatch request)))

(generate-string {})

(defn start []
  (run-jetty-async #'handler {:join? false :port 8000}))

(start)
