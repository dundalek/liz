
# Liz: Lisp-flavored general-purpose programming language (based on Zig)

Borrowing Zig's tagline:
> General-purpose programming language and toolchain for maintaining robust, optimal, and reusable software.

- Written as Clojure-looking S-expressions (EDN) and translated to Zig code.
- [Type-A](https://github.com/dundalek/awesome-lisp-languages#classification) Lisp-flavored language. I call it "Lisp-flavored" because Liz is missing many fundamental features to be called a Lisp or even a Clojure dialect.
- When you need a language closer to the metal and Clojure with GraalVM's native image is still too much overhead.

Why is Zig an interesting choice as a lower-level language for Clojure programmers? (compared to Rust or other languages):

- Focus on simplicity
- Seamless interop with C without the need to write bindings.
Similar quality like Clojure seamlessly interoperating with Java.
- Incremental compilation with the Zig self-hosted compiler.
  To accomplish this Zig uses a Global Offset Table for all function calls which is similar to Clojure Vars. Therefore it will be likely possible to implement a true REPL.
- Decomplecting principles
  Most higher-level languages have bundled memory management, which disqualifies them from certain use cases. Zig decomplects memory management by introducing explicit Allocator interface, programmer can choose fitting memory management mechanism with regard to performance/convenience trade-offs.

**Status:** *Highly experimental.*

## Examples

Hellow World:

```clojure
;; hello.liz
(const print (.. (@import "std") -debug -print))

(defn ^:pub ^void main []
  (print "Hello, world!\n" []))
```

Will be translated into:

```zig
const print = @import("std").debug.warn;
pub fn main() void {
    print("Hello, world!\n", .{});
}
```

See [guess number program](./examples/guess_number/main.liz) for a longer example and the [examples](./examples) directory for more.


## Documentation

Read the work in progress [language guide](./doc/guide.md).
To see how a form is used you can also take a look at [samples](./test/resources/docs-samples.liz) adapted from Zig docs.

## Usage

Download [Liz binary](https://github.com/dundalek/liz/releases) and [Zig 0.7.0](https://ziglang.org/download/#release-0.7.0). To compile files from Liz to Zig pass them as parameters:
```sh
liz file1.liz file2.liz
# file1.zig and file2.zig will be created
```

Alternatively you can use use the JAR:
```
java -jar liz.jar file1.liz file2.liz
```

Or use Clojure CLI:

```sh
clojure -m liz.main file1.liz file2.liz
```

### Extension and Syntax highlighting

Use `.liz` file extension. It works pretty well to use Clojure syntax highlighting, add `-*- clojure -*-` metadata to the source file for Github and text editors to apply highlighting.

```
;; -*- clojure -*-
```

## Design principles

- Create 1:1 mapping, everything expressible in Zig should be expressible in Liz, otherwise it can be considered a bug.
- If a Zig feature maps cleanly to Clojure then use the Clojure variant and name.
- If the mapping conflicts then use the Zig vocabulary.

## License

MIT

## TODOs

- [ ] analyzer improvements
  - [ ] override `catch` to not require exception type?
  - [ ] clean up and move the custom logic from emitter to analyzer - perhaps as an analyzer pass
  - [ ] specification of AST - spec or malli
- [ ] write documentation - tutorial/reference
- [ ] assembly notation

### Nice to haves

- [ ] preserve comments, perhaps read it with [rewrite-clj](https://github.com/xsc/rewrite-clj)
- [ ] doc comments - in Zig with `///`
- [ ] preserve line and column info, possibilities:
  - emit Zig AST directly
  - try to hack it and emit annotated tokens
  - emit code as is but have a mapping to translate back locations reported by Zig CLI

## Future explorations

Trying to bring in more Clojure semantics.

- [ ] Try to support macros in Liz (defined in Clojure, interpreted via SCI)
- [ ] Could we implement destructuring?
- [ ] Would having implicit block return work?
- [ ] Try to write library implementing Clojure-like Sequences protocol
- [ ] Explore possibility of implementation of Protocols
- [ ] If we have sequences protocol we could write immutable data structures library
