(ns abio.node.io
  (:require
   [abio.io :as io]
   [clojure.string :as string]))

;; (require '[abio.io :as io] '[clojure.string :as string] '[cljs.core.async :as async])
;; (require-macros '[cljs.core.async.macros :refer [go go-loop]])

(defrecord BufferedReader [path opts fs] ; `opts` should contain :encoding and optionally :flag
  abio.io/IReader
  (-read [_]
    (.readFileSync fs path (clj->js opts)))
  (-read [_ _] (throw (ex-info "No double arity -read for a synchronous Node BufferedReader" {})))

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

(defrecord BufferedWriter [path opts fs]
  abio.io/IAbioWriter
  (-write [_ output]
    (.writeFileSync fs path output (clj->js opts)))
  (-write [_ output _]
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
  (-list-files [this d cb]
    (.. fs (readdir d cb)))
  (-delete-file [this f])


  (-file-reader-open [this path opts]
    (->BufferedReader path opts fs))
  (-async-file-reader-open [this path opts]
    (->AsyncBufferedReader path opts fs))

  ;; Default to non-destructive write
  (-file-writer-open [this path opts]
    (->BufferedWriter path (merge {:flag "a"} opts) fs))
  (-async-file-writer-open [this path opts]
    (->AsyncBufferedWriter path (merge {:flag "a"} opts) fs)))

(defn bindings
  []
  (->Bindings (js/require "fs")
              (.-sep (js/require "path"))))
