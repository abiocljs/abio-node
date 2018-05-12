(ns abio.node.io
  (:require
   [abio.io :as io]
   [clojure.string :as string]))

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

;; TODO I could also potentially just make the 2-arity function in BufferedReader async
;;      This might simplify the code some
(defrecord AsyncBufferedReader [path opts fs] ; `opts` needs :encoding and optionally :flag TODO how do I want to enforce that?
  abio.io/IReader
  (-read [_] (throw (ex-info "No single arity -read for AsyncBufferedReader" {})))
  (-read [_ cb] ; `cb` has to be 2-arity, first param is errors and second is data
    (.readFile fs path (clj->js opts) cb)) ; TODO if this is a fd it won't get closed automatically; eventually that needs to be checked for.

  abio.io/IClosable
  (-close [_]
    ;; TODO: should these `-close` functions return true or nil?
    true))

(defrecord BufferedWriter [path encoding options fs]
  abio.io/IAbioWriter
  (-write [_ output]
    (.writeFileSync fs path output (clj->js (merge {:encoding encoding} options))))
  (-write [_ output channel]
    (throw (ex-info "No double arity -write for BufferedWriter" {})))

  abio.io/IClosable
  (-close [_]
    nil))

(defrecord AsyncBufferedWriter [path opts fs] ; TODO can the `flags` actually be kept as a map `opts`?
  abio.io/IAbioWriter
  (-write [_ output]
    (throw (ex-info "No single arity -write for AsyncBufferedWriter" {})))
  (-write [_ output cb] ; XXX `cb` takes one arg, `err`, but otherwise execution signals completion of the write.`
    (.writeFile fs path output (clj->js opts) cb))

  abio.io/IClosable
  (-close [_] true))

;; TODO: add some js->clj to this? More broadly, how/should we offer up cljs data?
(defrecord Bindings [fs sep]
  abio.io/IBindings
  (-path-sep [this] sep)
  (-directory? [this f]
    (.. fs (lstatSync f) (isDirectory)))
  (-list-files [this d]
    (.. fs (readdirSync d)))
  (-async-list-files [this d cb]
    (.. fs (readdir d cb))) ;; XXX I have no idea if this works yet
  (-delete-file [this f])
  (-file-reader-open [this path encoding]
    (->BufferedReader path encoding fs))
  (-async-file-reader-open [this path opts]
    (->AsyncBufferedReader path opts fs))

  ;; Default to non-destructive write
  (-file-writer-open [this path encoding {flags :flags :or {flags "a"}}]
    ;; XXX default write stream has an internal buffer, but how to interact with it in a cljs idiomatic
    ;; manner is unclear currently (because the js version has you attach callbacks to the streams events)
    ;; In fact, is it possible to have an unbuffered write stream? maybe skip that for the time being?
    (->BufferedWriter path encoding {:flags flags} fs))
  (-async-file-writer-open [this path opts]
    (->AsyncBufferedWriter path (merge {:flag "a"} opts) fs)))

(defn bindings
  []
  (->Bindings (js/require "fs")
              (.-sep (js/require "path"))
              ))
