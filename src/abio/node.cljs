(ns abio.node
  (:require
    [abio.io]
    [clojure.string :as string]))

(defrecord IOOps [fs]
  abio.io/IIOOps
  (-directory? [this f]
    (.. fs (lstatSync f) (isDirectory)))
  (-list-files [this d])
  (-delete-file [this f])
  (-file-reader-open [this path encoding]
    (let [fd (.openSync fs path "r")
          read-stream (.createReadStream fs nil #js {:fd fd :encoding encoding})]
      (.pause read-stream)
      (.read read-stream)                                   ; Hack to get buffer primed
      (abio.io/->BufferedReader #(.read read-stream) #(.close fs fd) (atom nil) (atom 0))))
  (-file-reader-read [this reader])
  (-file-reader-close [this reader]))

(defn bindings
  []
  {:abio/io-ops (->IOOps (js/require "fs"))})
