(ns abio.node.shell
  (:require
    [abio.shell :as shell]))

(defrecord Bindings []
  abio.shell/IBindings
  (-sh [this args]
    {:exit 0
     :out "hi"
     :err ""}))

(defn bindings
  []
  (->Bindings))
