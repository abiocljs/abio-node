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
  (-read [_ _] (throw (ex-info "No double arity -read for BufferedReader" {})))

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
  (-read-line [_ _] (throw (ex-info "No double arity -read-line for BufferedReader" {})))

  abio.io/IClosable
  (-close [_]
    (raw-close)))

;; How would this be used?
;; You call -read and it gives you a channel? How do you read multiple items? I guess I should just write
;; I might be wrong in just trying to wrap a BufferedReader. I think also I need to wrap it all inside a go loop
(defrecord AsyncBufferedReader [buffered-reader]
  abio.io/IReader
  (-read [_] (throw (ex-info "No single arity -read for AsyncBufferedReader" {})))
  (-read [_ chan]
    (go
      (loop [data (io/-read buffered-reader)]
        (if data
          (do
            (async/>! chan data)
            (recur (io/-read buffered-reader)))
          (async/close! chan)))))

  abio.io/IBufferedReader
  (-read-line [_] (throw (ex-info "No single arity -read-line for AsyncBufferedReader" {})))
  (-read-line [_ chan]
    (go
      (loop [line (io/-read-line buffered-reader)]
        (if line
          (do
            (async/>! chan line)
            (recur (io/-read-line buffered-reader)))
          (async/close! chan)))))

  abio.io/IClosable
  (-close [_]
    (go (io/-close buffered-reader))))

;; -write should write data immediately
;; -buffered-write should buffer up a bunch of data, flush, buffer, repeat
;; -flush should write whatever buffered data there is currently
(defrecord BufferedWriter [raw-write raw-close]
  ;; abio.io/IWriter
  ;; TODO: Does it make sense to have an unbuffered write? Do we care?
  ;; (-write [this]
  ;;   (if-some [buffered @buffer]
  ;;     (do
  ;;       (reset! buffer nil)
  ;;       (raw-write buffered))))
  ;; (-write [_ output]
  ;;   (raw-write output))
  ;; (-write [_ output channel]
  ;;   (go (async/>! channel (raw-write output))))

  abio.io/IBufferedWriter
  (-buffered-write [_ output]
    (raw-write output))
  (-buffered-write [_ output channel]
    (throw (ex-info "No double arity -buffered-write for BufferedWriter" {})))

  abio.io/IClosable
  (-close [_]
    (raw-close)))

(defrecord AsyncBufferedWriter [buffered-writer]
  abio.io/IBufferedWriter
  (-buffered-write [_ output]
    (throw (ex-info "No single arity -buffered-write for AsyncBufferedWriter" {})))
  (-buffered-write [_ output channel]
    (go (async/>! channel (io/-buffered-write buffered-writer output))))

  abio.io/IClosable
  (-close [_]
    (io/-close buffered-writer))
  )

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
    (->AsyncBufferedReader (io/-file-reader-open this path encoding)))

  ;; Default to non-destructive write
  (-file-writer-open [this path encoding {flags :flags :or {flags "a"}}]
    ;; XXX default write stream has an internal buffer, but how to interact with it in a cljs idiomatic
    ;; manner is unclear currently (because the js version has you attach callbacks to the streams events)
    ;; In fact, is it possible to have an unbuffered write stream? maybe skip that for the time being?
    (let [write-stream (.createWriteStream fs path #js {:encoding encoding :flags flags})]
      (->BufferedWriter #(.write write-stream %1) #(.end write-stream)))

    )
  (-async-file-writer-open [this path encoding options]
    (->AsyncBufferedWriter (io/-file-writer-open this path encoding options)))

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
