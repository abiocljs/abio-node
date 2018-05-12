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
  ;; TODO: `cb` has to be 2-arity, first param is errors and second is data
  (-read [_ cb]
    ;; TODO if this is a fd it won't get closed automatically; eventually that needs to be checked for.
    (.readFile fs path (clj->js opts) cb))

  abio.io/IClosable
  ;; Even though there's nothing to close here, we keep it to preserve the use of `with-open`
  (-close [_] nil))

(defrecord BufferedWriter [path opts fs]
  abio.io/IAbioWriter
  (-write [_ output]
    (.writeFileSync fs path output (clj->js opts)))
  ;; XXX `cb` takes one arg, `err`, but otherwise execution signals completion of the write.`
  ;; TODO: is there a good way to signal that, or do we have to just assume the
  ;; consumer of the node library know it?
  (-write [_ output cb]
    (.writeFile fs path output (clj->js opts) cb))

  abio.io/IClosable
  (-close [_]
    nil))

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

  ;; Default to non-destructive write
  (-file-writer-open [this path opts]
    (->BufferedWriter path (merge {:flag "a"} opts) fs)))

(defn bindings
  []
  (->Bindings (js/require "fs")
              (.-sep (js/require "path"))))
