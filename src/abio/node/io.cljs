(ns abio.node.io
  (:require
    [abio.io :as io]
    [clojure.string :as string]
    [clojure.core.async :as async]))

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

(defrecord Bindings [fs]
  abio.io/IBindings
  (-directory? [this f]
    (.. fs (lstatSync f) (isDirectory)))
  (-list-files [this d])
  (-delete-file [this f])
  (-file-reader-open [this path encoding]
    (let [fd (.openSync fs path "r")
          read-stream (.createReadStream fs nil #js {:fd fd :encoding encoding})]
      (.pause read-stream)
      (.read read-stream)                                   ; Hack to get buffer primed
      (->BufferedReader #(.read read-stream) #(.close fs fd) (atom nil) (atom 0))))
  (-file-async-reader-open [this path encoding]
    (let [chan (async/promise-chan)
          cb (fn [& args] (async/put! chan (vec args)))]
      (.readFile fs path encoding cb)
      chan))
  (-file-reader-read [this reader])
  (-file-reader-close [this reader]))

(defn bindings
  []
  (->Bindings (js/require "fs")))
