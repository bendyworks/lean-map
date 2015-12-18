# Lean Hash Array Mapped Trie (Lean Map)

A ClojureScript implementation of Lean Hash Array Mapped Tries. See [reference](http://michael.steindorfer.name/publications/oopsla15.pdf).

### Leiningen

```clojure
[lean-map "0.3.1"]
```

### Technical Terms

- HAMT is Hash Array Mapped Trie

### Primary Goals

- See if Lean Hash Array Mapped Tries performs better then the current HAMT implementation in terms of code size, memory usage, or operation speed
- Have a Property Test Suite that tests the current HAMT implementation and can be used to test the Lean HAMT
- Have an benchmark suite testing maps of sizes (100, 10000, and 100000) for `hash`, `assoc`, `dissoc`, `assoc!`, and `dissoc!` operations
- Be a nearly drop in replacement for the current HAMT implementation

### Secondary Goals

- Be a reference implementation of how to implement a Lean HAMT
- Have detailed documentation about how the Lean HAMT differs from the current HAMT implementation

### Current Status

All standard ClojureScript map functionality (`assoc`, `dissoc`, `get`, `seq`, `reduce-kv`, `persistent!`, and `transient`) have been implemented and passed Collection Check tests. The improved iteration has been implemented, the improved equality checking is the last feature that needs to be completed.

### Performance

Here are the performance gains over the reference ClojureScript HAMT implementation

* 2x for scanning over sequences (except in Firefox which has a 2x slowdown)
* 2 - 4x for hashing maps
* One order of magnitude for equality checking in the worst case (no structural sharing) and two orders of magnitude in the best case (structural sharing)

The other operations are comparable to ~25% slower then the reference implementation.

Use `script/bench.sh` the performance benchmarks JavaScript (creates benchmarks in`resources/bench/app.js`). The benchmarks are meant to run how ClojureScript runs [tests](https://github.com/clojure/clojurescript/wiki/Running-the-tests)

### Main Ideas of the Paper

#### Consolidation of key values pairs and HAMT nodes

The primary idea behind the paper is changing the current HAMT node implementation from this

`[key1, value1, null, <HAMT Node 1>, key2, value2, key3, value3, null, <HAMTNode 2>] `

to this

`[key1, value1, key2, value2, key3, value3, <HAMT Node 1>, <HAMT Node 2>]`

consolidating the key value pairs and HAMT nodes instead of having them interspersed.

This leads to memory savings from not having to have a marker `null` in front of HAMT node.

#### Compaction on delete

The secondary idea is to have delete always return the most compact HAMT. For example deleting D from this HAMT

`[A, <HAMT Node 1>] -> [<HAMT Node 2>, D] -> [B, C]`

returns this HAMT

`[A, <HAMT Node 1>] -> [<HAMT Node 2>] -> [B, C]`

that has an unnecessary HAMT node (HAMT Node 2). Ideally the delete operation should return

`[A, <HAMT Node 1>] -> [B, C]`

removing the superfluous HAMT node

According to the [paper](http://michael.steindorfer.name/publications/oopsla15.pdf) this leads to an 80 to 100 percent speedup for iteration and equality checks and comparable to better performance on insertion, deletion, and lookup.

### Usage

Lean HAMT's are implemented in the `cljs.lean-map.core` namespace. User functions live in the `cljs.lean-map.util` namespace. Here is documentation of their usage

* `set-maps-to-lean-map!`

    Modifies the literal maps `{}` so that they become lean-maps instead of standard ClojureScript maps. Note this does __not__ change the `hash-map` function to output lean-maps.

    Usage: `(set-maps-to-lean-map!)`

* `set-maps-to-cljs-map!`

    Modifies the literal maps `{}` back to the default ClojureScript maps.

    Usage: `(set-maps-to-cljs-map!)`

* `using-lean-maps?`

    Usage: `(using-lean-maps?)`

* `lean-map?`

    Check if a map is a lean-map

    Usage: `(lean-map? {0 0 1 1 2 2 3 3 4 4 5 5})`

* `lean-map-seq?`

    Check if a seq is a seq of a lean-map

    Usage: `(lean-map? {0 0 1 1 2 2 3 3 4 4 5 5})`

* `hash-map`

    Copy of the ClojureScript `hash-map` function but returns a lean-map instead of a ClojureScript map.

    Usage: `(hash-map 0 0 1 1 2 2 3 3 4 4 5 5)`

* `empty`

    An empty lean-map for use in place of `{}`

    Usage: `(assoc empty :foo :bar)`

### Testing

Currently testing is done via a collection check port to ClojureScript that will take some time to be officially released. In the meantime here's how you setup the dependencies

  1. git clone https://github.com/martinklepsch/collection-check-1.git
  2. cd collection-check-1; boot build-jar
  3. You should now be able to use [collection-check "0.1.7-SNAPSHOT”]

To test on node use `script/node-test.sh`. For phantomjs use `script/phantom-test`. If you want to run tests with a custom engine use:

`lein with-profile test doo <js environment> <name of test build e.g. "test">`

For a custom test build add it to `:cljsbuild` in `project.clj`

### Thanks

* [Bendyworks](https://bendyworks.com/) for letting me work on this

* Michael J. Steindorfer and Jurgen J. Vinju for the [Lean HAMT Paper](http://michael.steindorfer.name/publications/oopsla15.pdf)

* Use The Source for their reference [implementation](https://github.com/usethesource/capsule)

* Zach Tellman for writing [Collection Check](https://github.com/ztellman/collection-check)

* Martin Klepsch for porting Collection Check to ClojureScript and Nicolás Berger for helping me get it setup

* David Nolen for perf and profiling suggestions

### License

Copyright © 2015 Peter Schuck

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
