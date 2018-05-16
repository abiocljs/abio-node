(ns abio.node.walkthrough
  (:require [abio.io :as io]
            abio.node
            abio.core
            [clojure.pprint :as pp]
            [clojure.string :as string]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Set up the host specific bindings.
;;
;; This is the first function that's called, setting up the host specific I/O
;; machinery used by the higher level functions.
(abio.core/set-bindings! (abio.node/bindings))

;;;;;;;;;;;;;;;;;;;;;;
;; General FS bindings
;;
;; These are things that don't, necessarily, warrant opening up some Reader or
;; Writer, currently limited to listing files, returning the host path
;; separator, and checking whether some path is a directory

;; In both of these examples we're calling the methods defined in `abio.io`
;; against the `abio.core` dynamic var `*io-bindings*`, which we'd defined in
;; our call to `set-bindings!`. Whatever record is stored in `*io-bindings*`
;; needs to extend the `abio.io/IBindings` protocol.

;; Synchronous directory listing
(defn sync-ls
  [path]
  (if (not (io/-directory? abio.core/*io-bindings* path))
    (println "Path needs to be a directory, but you gave me the following: " path)
    (do (println "Synchronously listing files in " path)
        (io/-list-files abio.core/*io-bindings* path))))

;; Asynchronous directory listing
(defn async-ls-cb
  [err files]
  (println "Here's the files we got back from the asynchronous -list-files call.")
  (pp/pprint files))

(defn async-ls
  [path]
  (if (not (io/-directory? abio.core/*io-bindings* path))
    (println "Path needs to be a directory, but you gave me the following: " path)
    (do
      (println "Asynchronously listing files in " path)
      (io/-list-files abio.core/*io-bindings* path async-ls-cb))))

;;;;;;;;;;;;;;;;
;; Reading Files
;;
;; We currently split reading and writing into two different methods on the
;; `IBindings` protocol -- `-file-reader-open` and `-file-writer-open` -- though
;; that may change in the future. Realistically, you could have a single record,
;; say `BufferedFile`, and implement `IReader` and `IAbioWriter` for it, which
;; would allow both reading and writing to the same path via a single var.

;; Let's read some data; we start by defining our reader, which we'll pass to
;; both of the following functions.
(def rdr (abio.io/reader "project.clj" :encoding "utf8"))

;; Here's a sync read
(defn sync-read
  [reader]
  (if (io/-directory? abio.core/*io-bindings* (:path reader))
    (println "You asked me to read a directory, but I only work on files.")
    (do (println "Synchronously reading the contents of" (:path reader) "\n")
        (abio.io/-read reader))))

;; Here's an async read
(defn async-read
  [reader]
  (if (io/-directory? abio.core/*io-bindings* (:path reader))
    (println "You asked me to read a directory, but I only work on files.")
    (abio.io/-read rdr (fn [err data]
                         (println "Asynchronously read the contents of" (:path reader) "\n")
                         (println data)))))
