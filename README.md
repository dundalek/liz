
# Liz: Lisp-flavored general-purpose programming language (based on Zig)

Borrowing [Zig](https://github.com/ziglang/zig)'s tagline:
> General-purpose programming language and toolchain for maintaining robust, optimal, and reusable software.

- Written as Clojure-looking S-expressions ([EDN](https://github.com/edn-format/edn)) and translated to Zig code.
- [Type-A](https://github.com/dundalek/awesome-lisp-languages#classification) Lisp-flavored language. I call it "Lisp-flavored" because Liz is missing many fundamental features to be called a Lisp or even a Clojure dialect (no closures, no persistent data structures).
- When you need a language closer to the metal and Clojure with GraalVM's native image is too much overhead.
- Supports many targets including x86, ARM, RISC-V, WASM and [more](https://ziglang.org/#Wide-range-of-targets-supported)

Why is Zig an interesting choice as a lower-level language for Clojure programmers? (compared to Rust, Go or other languages):

- Focus on simplicity
- Seamless interop with C without the need to write bindings.  
  Similar quality like Clojure seamlessly interoperating with Java.
- Incremental compilation with the Zig self-hosted compiler.  
  To accomplish this Zig uses a Global Offset Table for all function calls which is similar to Clojure Vars. Therefore it will be likely possible to implement a true REPL.
- Decomplecting principles  
  Most higher-level languages have bundled memory management, which disqualifies them from certain use cases. Zig decomplects memory management by introducing explicit Allocator interface, programmer can choose fitting memory management mechanism with regard to performance/convenience trade-offs.

**Status:** *Experimental, but proving itself on a few projects.*

## Examples

Hello World:

```clojure
;; hello.liz
(const print (.. (@import "std") -debug -print))

(defn ^void main []
  (print "Hello, world!\n" []))
```

It will get translated into:

```zig
const print = @import("std").debug.print;
pub fn main() void {
    print("Hello, world!\n", .{});
}
```

Run with:

```
$ liz hello.liz && zig run hello.zig
Hello, world!
```

FizzBuzz example:

```clojure
(const print (.. (@import "std") -debug -print))

(defn ^void main []
  (var ^usize i 1)
  (while-step (<= i 100) (inc! i)
    (cond
      (zero? (mod i 15)) (print "FizzBuzz\n" [])
      (zero? (mod i 3)) (print "Fizz\n" [])
      (zero? (mod i 5)) (print "Buzz\n" [])
      :else (print "{}\n" [i]))))
```

See also:
- more [examples](./examples)
- Advent of Code [solutions](https://github.com/dundalek/adventofcode/tree/master/2020/src/)
- Rendering [TUI with Notcurses](https://github.com/dundalek/notcurses-zig-example)

## Documentation

Read the work in progress [language guide](./doc/guide.md).  
To see how a form is used you can also take a look at [samples](./test/resources/docs-samples.liz) adapted from Zig docs.

## Usage



Download [Liz](https://github.com/dundalek/liz/releases/latest) and [Zig](https://ziglang.org/download/#release-0.7.1). To compile files from Liz to Zig pass them as parameters:
```sh
liz file1.liz file2.liz
# file1.zig and file2.zig will be created
```

Then use `zig run` or `zig build-exe` on the generated `.zig` files.

Alternatively you can use the JAR:
```
java -jar liz.jar file1.liz file2.liz
```

### Extension and Syntax highlighting

Use `.liz` file extension. It works pretty well to use Clojure syntax highlighting, add `-*- clojure -*-` metadata to the source file for Github and text editors to apply highlighting.

```
;; -*- clojure -*-
```

## Design principles

- Create 1:1 mapping, everything expressible in Zig should be expressible in Liz, or can be considered a bug.
- If a Zig feature maps cleanly to Clojure then use the Clojure variant and name.
- If the mapping conflicts then use the Zig vocabulary.

## License

MIT

## Development

Get the source:

```sh
git clone https://github.com/dundalek/liz.git
cd liz
```

Build platform independent uberjar:
```sh
scripts/build-jar
```

Build the native binary (requires [GraalVM](https://www.graalvm.org/downloads/)):
```sh
# If you don't have native-image on PATH then you need to specify GRAALVM_HOME
export GRAALVM_HOME=/your/path/to/graal
scripts/build-native
```

Use Clojure CLI:

```sh
clj -M -m liz.main file1.liz file2.liz
```

Run tests:
```sh
clj -Mtest
```

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
