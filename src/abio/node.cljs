(ns abio.node
  (:require
    [abio.io]))

(defrecord IOOps [fs]
  abio.io/IIOOps
  (-directory? [this f]
    (.. fs (lstatSync f) (isDirectory)))
  (-list-files [this d])
  (-delete-file [this f]))

(defn bindings
  []
  {:abio/io-ops (->IOOps (js/require "fs"))})
