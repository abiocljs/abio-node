# abio-node

Node bindings for the abio cljs library.

## Usage

See [abio usage](https://github.com/abiocljs/abio#usage) and [examples/walkthrough.cljs](examples/walkthrough.cljs)

## Todo
1. Should we switch to `fs.read`/`fs.readSync` instead of `fs.readFile`/`fs.readFileSync`, and
   emulate the buffered reads in `clojure.java.io`/planck/etc? What're the pros/cons?
2. Should we support buffered and unbuffered readers/writers? I think an unbuffered version of
   `fs.{read,write}` is simply a call to those methods with a length of 1.
3. We don't currently leverage File records; should we?
4. What abstractions might a File record assume, over, say a Resource record or some other, more
   general record type?
5. Create Stream implementations.
6. Bind `*in*` with `abio.core/set-bindings!`
7. Get `abio.io/slurp` working with the node bindings.

## License

Copyright © 2017 abiocljs and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
