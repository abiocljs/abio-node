(ns abio.node
  (:require
    [abio.node.io :as io]
    [abio.node.shell :as shell]))

(defn bindings
  []
  {:abio.io/bindings (io/bindings)
   :abio.shell/bindings (shell/bindings)})
