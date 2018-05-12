# abio-node

TODO: Should we combine synchronous and asynchronous read/write/stream
functions? Currently we have two records for each type, such as `BufferedReader`
and `AsynchronousBufferedReader`, along with `reader`/`async-reader` functions
in abio core.

While this makes it really clear how to get a sync/async thing, it also
duplicates a lot of code. Currently, the `BufferedReader` and
`AsynchronousBufferedReader` both define the single and double arity form of
each of the protocols they're extending, except the `BR` throws on double arity
-- because the second argument is a callback -- and `ABR` throws on single
arity, since it's lacking the callback it needs.

Both records take the same three arguments, and we don't do any branch logic
based on whether it's a `BR` or `ABR` record, so I don't think there's much
point in having both.

## Usage

See [abio](https://github.com/abiocljs/abio#usage).

## License

Copyright © 2017 abiocljs and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
