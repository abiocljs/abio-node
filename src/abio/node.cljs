(ns abio.node
  (:require
    [abio.node.io :as io]))

(defn bindings
  []
  (merge
    (io/bindings)))
