# lean-map

A ClojreScript implementation of Lean Hash Array Mapped Tries. See (reference)[https://github.com/usethesource/capsule]

## Primary Goals

- See if Lean Hash Array Mapped Tries performs better then the current HAMT implementation in terms of code size, memory usage, or operation speed
- Have a Property Test Suite that tests the current HAMT implementation and can be used to test the Lean HAMT
- Have an benchmark suite testing maps of sizes (100, 10000, and 100000) for `hash`, `assoc`, `dissoc`, `assoc!`, and `dissoc!` operations
- Be a nearly drop in replacement for the current HAMT implementation

## Secondary Goals

- Be a reference implementation of how to implement a Lean HAMT
- Have detailed documentation about how the Lean HAMT differs from the current HAMT implementation

## Thanks

- (Bendyworks)[https://bendyworks.com/] for letting me work on this

- Michael J. Steindorfer and Jurgen J. Vinju for the (Lean HAMT Paper)[http://michael.steindorfer.name/publications/oopsla15.pdf]

- Use The Source for their reference (implementation)[http://usethesource.io/projects/capsule/]

- Zach Tellman for writing (Collection Check)[https://github.com/ztellman/collection-check] which I transplanted to ClojureScript

## License

Copyright © 2015 Peter Schuck

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
