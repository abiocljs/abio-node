# abio-node

## Usage

```
$ lumo -c /Users/me/Projects/abio/target/abio-0.1.0.jar:/Users/me/Projects/abio-node/target/abio-node-0.1.0.jar
Lumo 1.0.0
ClojureScript 1.9.293
 Docs: (doc function-name-here)
 Exit: Control+D or :cljs/quit or exit

cljs.user=> (require '[abio.io :as io] 'abio.node)
nil
cljs.user=> (abio.io/set-io-ops! (abio.node/io-ops))
nil
cljs.user=> (io/directory? "/Users/me")
true
```

## License

Copyright © 2017 abiocljs and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
