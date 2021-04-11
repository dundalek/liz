
# Liz language guide

<!-- @import "[TOC]" {cmd="toc" depthFrom=2 depthTo=6 orderedList=false} -->
<!-- code_chunk_output -->

- [Intro](#intro)
  - [Comments](#comments)
- [Data structures](#data-structures)
    - [nil](#nil)
    - [undefined](#undefined)
    - [Booleans](#booleans)
    - [Numbers](#numbers)
    - [Strings / Characters](#strings-characters)
    - [Arrays / Slices](#arrays-slices)
    - [Structs](#structs)
- [Special forms](#special-forms)
    - [definitions](#definitions)
    - [assignments](#assignments)
    - [arithmetic operators](#arithmetic-operators)
    - [comparison operators](#comparison-operators)
    - [logical and bitwise operators](#logical-and-bitwise-operators)
    - [conditionals](#conditionals)
    - [combining expressions  - blocks](#combining-expressions-blocks)
    - [loops](#loops)
    - [functions](#functions)
    - [defer](#defer)
- [Other special forms](#other-special-forms)
    - [method calls](#method-calls)
    - [attribute access](#attribute-access)
    - [nulability](#nulability)
    - [error handling](#error-handling)
    - [modules](#modules)
    - [modifiers](#modifiers)
    - [async coroutines](#async-coroutines)
    - [testing](#testing)
- [Macros](#macros)

<!-- /code_chunk_output -->

## Intro

Liz tries to mimic syntax of Clojure/EDN and map concepts to Zig semantics. Being familiar with either one the languages is helpful. You can always refer to [Zig documentation](https://ziglang.org/documentation/0.7.1/) for more details.

### Comments

Comments start with semicolon `;`. By convention line comments use two semicolons and comments on end of line use one. `comment` macro can be also used, it discards all forms nested inside of it.

```clojure
;; Line comment
;; Another line

(+ 3 4) ; End of line comment

(comment
  (+ 1 2))
```

## Data structures

#### nil

`nil` maps to `null` and is used for optionals:

```clojure
nil ; => null
```
#### undefined

[undefined](https://ziglang.org/documentation/0.6.0/#undefined) is used for uninitialized variables:

```clojure
undefined ; => undefined
```

#### Booleans

Booleans are directly equivalent to Zig booleans:

```clojure
true ; => true
false ; => false
```

#### Numbers

Number literals can be written in following ways:
- decimal `123` `1.23`
- exponent `12e3` `1.2e3` `1.2e-3`
- hexadecimal `0x123`
- octal `0123`
- binary `2r0110`
- arbitrary base `NrXXX` where (<= 2 N 36) and X is in [0-9, A-Z]

Note that in Zig each [number has a type](https://ziglang.org/documentation/0.6.0/#Primitive-Types). For example `u8` for bytes/chars, `i32` as signed integer, `f64` for floating doubles, `c_int` for compatibility with C types, etc.

#### Characters / Strings

String literals follow Clojure/Java escaping rules. Strings literals are represented as sentinel-terminated arrays with zero byte as terminating sentinel. Use backslash to escape single characters and `\newline`, `\space`, `\tab`, `\formfeed`, `\backspace`, `\return` for special characters. 

```clojure
  (const bytes "hello")
  (assert (= bytes.len 5))
  (assert (= (aget bytes 1) \e))
  (assert (= (aget bytes 5) 0))
  (assert (= \e \u0065))
  (assert (= 0x1f4a9 128169))
  (assert (.eql mem u8 "hello" "h\u0065llo")))
```

#### Symbols

Valid symbol characters are limited to make it simple to interop with Zig. Unlike Clojure, dash `-` is not a valid character for identifier. It is recommended to follow Zig's naming conventions:
- `snake_case` for variables and constants
- `camelCase` for functions
- `TitleCase` for types

#### Arrays / Slices

Arrays store elements of the same type with length known at compile time.  
Slices have a pointer and length known at run time. Slices usually act as a view into array or can be returned when allocating memory of dynamic size.  
The difference to arrays in C represented by pointers is that Arrays and Slices have always associated length nad protect against out of bounds access and overflows.

```clj
;; array uses vector notation and a type tag
(const message ^"[_]u8" [\h \e \l \l \o])
;; same as above
(const message "hello")

;; coerce string literal into slice
(const ^"[]const u8" message_slice "hello")

;; create slice from array specifying beginning and end
(const arr_slice (slice arr 0 5))

;; omitting end slices to the end
(const message_slice (slice message 0))

;; get size of array/slice with `len`
(.-len message) ; => 5
message.len ; also works

;; aget to get element
(aget message 0) ; => \h

;; aset for setting element at a given index
(aset arr 0 value)

;; aget/aset can take multiple indices for nested arrays
(aget pixels 5 10)
(aset pixels 5 10 value)

;; concatenate arrays with ++, works if the values are known at compile time
(const all_of_it (++ part_one part_two))
```

#### Structs

struct
enum
union
error

Expressions that define structs can be parametrized by comptime parameters which acts like generics known from other languages.

##### method calls

```clj
(.method x arg) ; => x.method(arg)
```

##### attribute access

```clj
(.-attr x) ; => x.attr
(set! (.-attr x) value) ; => x.attr = value
```

#### Pointers

Memory address is done with `&` and dereference with `*`.

```clj
(& arr)

;; can be used also as part of the symbol
&arr

(.-* ptr)

;; can be used also as part of the symbol
ptr.*

```

## Special forms

#### Functions

Define private function with `defn-`.  
For functions to be used by other modules use `defn`.  
Anonymous functions are defined with `fn`.  
Note that unlike Clojure there is not implicit return in Liz, use the `return` form to return values from a function.

For parameter keywords:
  ^:noalias
  ^:comptime



```clj
(defn- ^int32 add [^int32 a ^int32 b]
  (return (+ a b)))

(defn ^void main []
  (print "{}" [(add 1 2)]))

(defn ^{:extern "c"} ^f64 atan2 [^f64 a ^f64 b])
```

#### Declarations and Assignment

Use `const` and `var` for constants of variable declarations.  
Usually the type can be inferred, but for literals you can explicitly specify it.  
Use `set!` to modify value of a variable or struct field.

```clj
(const ^i32 a 123)

(var b a)
(set! b 456)

;; Use :pub modifier to make top-level declarations public
(const ^:pub ^f64 pi 3.14)
```


Zig does not allow name shadowing, so to keep the semantics similar Liz does not implement `let` binding.

#### Arithmetic operators

```clj
(+ )
(/ )
(mod )
(rem )
(inc )
(dec )

(+= )
(inc! )
(dec! )
```

#### Comparison operators

```clj
=
not=
zero?
pos?
neg?
even?
odd?
```

#### Logical and Bitwise operators

- and
- or
- not


- `(bit-not n)` Bitwise complement
- `bit-and`  Bitwise and `&`
- `bit-or` Bitwise or `|`
- `bit-xor` Bitwise exclusive or
- `bit-shift-left` Bitwise shift left `<<`
- `bit-shift-right` Bitwise shift right `>>`
- `bit-flip` Flip bit at index n
- `bit-set` Set bit at index n
- `bit-test` Test bit at index n

#### Conditionals

Use `if` for if-then-else conditions. Multiple forms in a branch can be grouped with `do`.  
Use `when` for conditions without else which allows you to write multiple then forms.  

```clj
(if (zero? x)
  (print "zero")
  (print "something else"))

(if (zero? x)
  (do (print "zero")
      (print "still zero")))

(when (zero? x)
  (print "zero") 
  (print "still zero")))
```

Use `if-not` and `when-not` as a shortcut for negated conditions to save on parentheses.
```clj
(if (not x) ...)

(if-not x ...)
```

Use `if-some` or `when-some` to test optionals and unwrapping values.
```clj
(const ^?u32 optional_a 0)
(when-some [value optional_a]
  (expect (= value 0)))
```

`cond` is for multiple if else statements

```clj
(cond
  (pos? x) (print "greater than zero")
  (neg? x) (print "less than zero")
  :else (print "zero"))
```


`case` also know as switch in other languages. 

```clj
(case a
  ;; Multiple cases can be combined together.
  [1 2 3] 0

  ;; Ranges can be specified using (range). These are inclusive both ends.
  (range 5 100) 1

  ;; Branches can be arbitrarily complex.
  101 (block :blk
        (const ^u64 c 5)
        (break :blk (* c (+ 2 1))))

  ;; The else branch catches everything not already captured.
  ;; Else branches are mandatory unless the entire range of values
  ;; is handled.
  9)
```
More details: [if](https://ziglang.org/documentation/0.7.1/#if), [switch](https://ziglang.org/documentation/0.7.1/#switch) in Zig docs.

#### Combining expressions - blocks

Group multiple forms into a scoped block with `do`. Mostly useful for multiple forms in conditionals.
```clj
(do
  (var ^i32 a 1)
  (inc! a))

;; a is out of scope here
```

Use `block` for labeled blocks. `break` with label can be used to return a value from the block.
block
break

[blocks](https://ziglang.org/documentation/0.7.1/#blocks)

#### loops


`while`

basic loop that iterates while the condition holds true

`continue` 
`break`
else

while-step
step evaluated on every iteration including when using `continue`

while-some
iterates while the expression is optional with value and binds the value

iterates over elements of array or slice. like Clojure `doseq`
`for`

`dotimes` macro is provided for convenience
note that parameter can only be a constant or variable

#### defer

`defer` will execute an expression at the end of the current scope.
`errdeher` is similar to defer, but will only execute if the scope returns with an error.

[defer](https://ziglang.org/documentation/0.7.1/#defer)

## Other special forms


unreachable

#### Optionals

?

if-some, when-some, while-some
bind

for errors in else branch

https://ziglang.org/documentation/0.7.1/#Optionals

#### error handling

error values

syntax mimics Clojure

try
orelse
catch

https://ziglang.org/documentation/0.7.1/#Errors

#### modules

@import
usingnamespace

https://ziglang.org/documentation/0.7.1/#import
https://ziglang.org/documentation/0.7.1/#usingnamespace

#### async coroutines

suspend
resume
await
async

https://ziglang.org/documentation/0.7.1/#Async-Functions

#### testing

test / testing
expect

https://ziglang.org/documentation/0.7.1/#Zig-Test

#### other

Use `zig*` as an escape hatch to emit Zig code directly. Useful when manipulating types or for writing down assembly.

```clj
(const bytes "hello")
(assert (= (@TypeOf bytes) (zig* "*const [5:0]u8")))
```

In case when Liz special forms would clash with Zig names, you can use quote.
```clj
('when 1) ; => when(1);
```

## Macros

Macros are used internally to implement some special forms, but user-defined macros are not implemented. Zig's comptime is less powerful, but I am interested to explore its limitations first before introducing macros.

Questions
- like ClojureScript - macros written in Clojure
- self-hosted macros - would need to implement higher-level datasctructures convenient for manipulating code as data


