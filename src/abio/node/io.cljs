(ns abio.node.io
  (:require
    [abio.io :as io]
    [clojure.string :as string]
    [cljs.core.async :as async])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

;; (require '[abio.io :as io] '[clojure.string :as string] '[cljs.core.async :as async])
;; (require-macros '[cljs.core.async.macros :refer [go go-loop]])

(defrecord BufferedReader [raw-read raw-close buffer pos]
  abio.io/IReader
  (-read [_]
    (if-some [buffered @buffer]
      (do
        (reset! buffer nil)
        (subs buffered @pos))
      (raw-read)))
  abio.io/IBufferedReader
  (-read-line [this]
    (if-some [buffered @buffer]
      (if-some [n (string/index-of buffered "\n" @pos)]
        (let [rv (subs buffered @pos n)]
          (reset! pos (inc n))
          rv)
        (if-some [new-chars (raw-read)]
          (do
            (reset! buffer (str (subs buffered @pos) new-chars))
            (reset! pos 0)
            (recur this))
          (do
            (reset! buffer nil)
            (let [rv (subs buffered @pos)]
              (if (= rv "")
                nil
                rv)))))
      (if-some [new-chars (raw-read)]
        (do
          (reset! buffer new-chars)
          (reset! pos 0)
          (recur this))
        nil)))
  abio.io/IClosable
  (-close [_]
    (raw-close)))

;; How would this be used?
;; You call -read and it gives you a channel? How do you read multiple items? I guess I should just write
;; I might be wrong in just trying to wrap a BufferedReader. I think also I need to wrap it all inside a go loop
(defrecord AsyncBufferedReader [buffered-reader]
  abio.io/IAsyncReader
  (-read [_]
    (let [chan (async/chan)]
      (go
        (loop [data (-read buffered-reader)]
          (if data
            (do
              (async/>! chan data)
              (recur (-read buffered-reader)))
            (async/close! chan ))))
      chan))
  abio.io/IAsyncBufferedReader
  (-read-line [_] ;; Will this create a channel every time? I think it will....
    (let [chan (async/chan)]
      (go
        (loop [line (-read-line buffered-reader)]
          (if line
            (do
              (async/>! chan line)
              (recur (-read-line buffered-reader)))
            (async/close! chan))))
      chan))
  abio.io/IClosable
  (-close [_]
    (go (-close buffered-reader))))

;; TODO: add some js->clj to this? More broadly, how/should we offer up cljs data?
(defrecord Bindings [fs sep]
  abio.io/IBindings
  (-path-sep [this] sep)
  (-directory? [this f]
    (.. fs (lstatSync f) (isDirectory)))
  (-list-files [this d]
    (.. fs (readdirSync d)))
  (-async-list-files [this d]
    (let [chan (async/chan)
          cb (fn [err contents]
               (if err
                 (async/>! chan err)
                 (async/>! chan contents))
               (async/close! chan))]
      (go
        (.. fs (readdir d cb))))) ;; XXX I have no idea if this works yet
  (-delete-file [this f])
  (-file-reader-open [this path encoding]
    (let [fd (.openSync fs path "r")
          read-stream (.createReadStream fs nil #js {:fd fd :encoding encoding})]
      (.pause read-stream)
      (.read read-stream)                                   ; Hack to get buffer primed
      (->BufferedReader #(.read read-stream) #(.close fs fd) (atom nil) (atom 0))))
  (-async-file-reader-open [this path encoding]
    (->AsyncBufferedReader (-file-reader-open this path encoding))
    ;; (let [chan (async/promise-chan)
    ;;       cb (fn [& args] (async/put! chan (vec args)))]
    ;;   (.readFile fs path encoding cb)
    ;;   chan)
    )
  ;; (-async-file-writer-write [this path data & opts]
  ;;   (let [chan (async/promise-chan)
  ;;         cb #(async/put! chan (vec %))]
  ;;     (.writeFile fs path data (clj->js (apply hash-map opts) cb))))
  (-file-reader-read [this reader])
  (-file-reader-close [this reader])

  )

(defn bindings
  []
  (->Bindings (js/require "fs")
              (.-sep (js/require "path"))
              ))
