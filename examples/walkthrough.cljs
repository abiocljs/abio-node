(ns abio.node.walkthrough
  (:require [abio.io :as io]
            [abio.node :as node]
            [abio.core :refer [*io-bindings*]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Set up the host specific bindings.
;;
;; This is the first function that's called, setting up the host specific I/O
;; machinery used by the higher level functions.
(abio.core/set-bindings! (node/bindings))

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
  (if (not (io/-directory? *io-bindings* path))
    (println "Path needs to be a directory, but you gave me the following: " path)
    (do (println "Synchronously listing files in " path)
        (io/-list-files *io-bindings* path))))

;; Asynchronous directory listing
(defn async-ls-cb
  [err files]
  (println "\nHere's the files we got back from the asynchronous -list-files call.")
  (pp/pprint files))

(defn async-ls
  [path]
  (if (not (io/-directory? *io-bindings* path))
    (println "Path needs to be a directory, but you gave me the following: " path)
    (do
      (println "Asynchronously listing files in " path)
      (io/-list-files *io-bindings* path async-ls-cb))))

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
  (if (io/-directory? *io-bindings* (:path reader))
    (println "You asked me to read a directory, but I only work on files.")
    (do (println "Synchronously reading the contents of" (:path reader) "\n")
        (abio.io/-read reader))))

;; Here's an async read
(defn async-read-cb
  [err data]
  (if err
    (println "Oh dag, the read failed with the following error:" err)
    (println data)))

(defn async-read
  [reader]
  (if (io/-directory? *io-bindings* (:path reader))
    (println "You asked me to read a directory, but I only work on files.")
    (do
      (println "Asynchronously reading the contents of" (:path reader) "\n")
      (abio.io/-read rdr async-read-cb))))

;;;;;;;;;;;;;;;;
;; Writing Files

;; First define our writer
(def writer (io/writer "test-writer.txt"))

;; Here's a sync write
(defn sync-write
  [writer output]
  (io/-write writer output))

;; Here's an async write.
;;
;; On Node, the only outcome from an async write is any error that was
;; encountered. If no error, then the write succeeded.
(defn async-write-cb
  [err]
  (if err
    (println "Shucks, the write failed. Here's the error:" err)
    (println "The write was successful, huzzah!")))

(defn async-write
  [writer output]
  (io/-write writer output async-write-cb))

(comment
  ;; Put some content in the writer test file
  (sync-write writer "Here's a sync write.\n")
  (async-write writer "Here's an async write.\n")

  ;; Then read out the content we wrote
  (def rdr2 (io/reader "test-writer.txt" :encoding "utf8"))
  (println (sync-reader rdr3)))
