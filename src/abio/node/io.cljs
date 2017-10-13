(ns abio.node.io
  (:require
    [abio.io :as io]
    [clojure.string :as string]
    [cljs.core.async :as async])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

;; (require '[abio.io :as io] '[clojure.string :as string] '[cljs.core.async :as async])
;; (require-macros '[cljs.core.async.macros :refer [go go-loop]])

(defrecord BufferedReader [path encoding fs]
  abio.io/IReader
  (-read [_]
    (.readFileSync fs path #js {:encoding encoding}))
  (-read [_ _] (throw (ex-info "No double arity -read for a Node BufferedReader" {})))

  abio.io/IClosable
  ;; Even though there's nothing to close here, we keep it to preserve the use of `with-open`
  (-close [_] nil))

;; I might be wrong in just trying to wrap a BufferedReader. I think also I need to wrap it all inside a go loop
;; TODO I could also potentially just make the 2-arity function in BufferedReader async
;;      This might simplify the code some
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

  abio.io/IClosable
  (-close [_]
    (go (io/-close buffered-reader))))

(defrecord BufferedWriter [path encoding options fs]
  abio.io/IAbioWriter
  (-write [_ output]
    (.writeFileSync fs path output (clj->js (merge {:encoding encoding} options))))
  (-write [_ output channel]
    (throw (ex-info "No double arity -write for BufferedWriter" {})))

  abio.io/IClosable
  (-close [_]
    nil))

(defrecord AsyncBufferedWriter [buffered-writer]
  abio.io/IAbioWriter
  (-write [_ output]
    (throw (ex-info "No single arity -write for AsyncBufferedWriter" {})))
  (-write [_ output channel]
    (go (async/>! channel (io/-write buffered-writer output))))

  abio.io/IClosable
  (-close [_]
    (io/-close buffered-writer)))

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
    (->BufferedReader path encoding fs))
  (-async-file-reader-open [this path encoding]
    (->AsyncBufferedReader (io/-file-reader-open this path encoding)))

  ;; Default to non-destructive write
  (-file-writer-open [this path encoding {flags :flags :or {flags "a"}}]
    ;; XXX default write stream has an internal buffer, but how to interact with it in a cljs idiomatic
    ;; manner is unclear currently (because the js version has you attach callbacks to the streams events)
    ;; In fact, is it possible to have an unbuffered write stream? maybe skip that for the time being?
    (->BufferedWriter path encoding {:flags flags} fs))
  (-async-file-writer-open [this path encoding options]
    (->AsyncBufferedWriter (io/-file-writer-open this path encoding options))))

(defn bindings
  []
  (->Bindings (js/require "fs")
              (.-sep (js/require "path"))
              ))
