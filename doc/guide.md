
# Liz language guide

Liz tries to mimic syntax of Clojure/EDN and map concepts to Zig semantics. Being familiar with either one the languages is helpful. You can always refer to [Zig documentation](https://ziglang.org/documentation/0.7.1/) for more details.

<!-- vim-markdown-toc GFM -->

* [Literals](#literals)
    * [Comments](#comments)
    * [nil](#nil)
    * [undefined](#undefined)
    * [Booleans](#booleans)
    * [Numbers](#numbers)
    * [Characters and Strings](#characters-and-strings)
    * [Symbols](#symbols)
* [Basic forms](#basic-forms)
    * [Functions](#functions)
    * [Declarations and Assignment](#declarations-and-assignment)
    * [Arithmetic operators](#arithmetic-operators)
    * [Comparison operators](#comparison-operators)
    * [Logical operators](#logical-operators)
    * [Bitwise operators](#bitwise-operators)
    * [Conditionals](#conditionals)
    * [Combining expressions - blocks](#combining-expressions---blocks)
    * [loops](#loops)
* [Data structures](#data-structures)
    * [Arrays and Slices](#arrays-and-slices)
    * [Structs](#structs)
      * [method calls](#method-calls)
      * [attribute access](#attribute-access)
    * [Pointers](#pointers)
* [Other forms](#other-forms)
    * [modules](#modules)
    * [defer](#defer)
    * [Optionals](#optionals)
    * [error handling](#error-handling)
    * [unreachable](#unreachable)
    * [async coroutines](#async-coroutines)
    * [other](#other)
* [Std library](#std-library)
    * [I/O and printing](#io-and-printing)
    * [testing](#testing)
* [Macros](#macros)

<!-- vim-markdown-toc -->


## Literals

#### Comments

Comments start with semicolon `;`. By convention line comments use two semicolons and comments on end of line use one. `comment` macro can be also used, it discards all forms nested inside of it.

```clojure
;; Line comment
;; Another line

(+ 3 4) ; End of line comment

(comment
  (+ 1 2))
```


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

#### Characters and Strings

String literals follow Clojure/Java escaping rules. String literals are represented as sentinel-terminated arrays with zero byte as terminating sentinel. Use backslash to escape single characters and `\newline`, `\space`, `\tab`, `\formfeed`, `\backspace`, `\return` for special characters. 

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


## Basic forms

#### Functions

Define private functions with `defn-`.  
For functions to be used by other modules use `defn`.  
Anonymous functions are defined with `fn`.  
Note that unlike Clojure there is not implicit return in Liz, use `return` to return values from a function.

```clj
(defn- ^int32 add [^int32 a ^int32 b]
  (return (+ a b)))

(defn ^void main []
  (print "{}" [(add 1 2)]))
```

Functions can have additional metadata keywords:
- `^:export` - makes a function externally visible for use with C ABI
- `^:extern` - when linking statically resolve at link time, or when linking dunamically resolve at runtime

```clj
(defn ^{:extern "c"} ^f64 atan2 [^f64 a ^f64 b])
```

For more complicated types that are not valid symbols, use string tags.  
Example specifying a different call convention:

```clj
;; Force a function to be inlined by specifying a calling convention.
(defn ^"callconv(.Inline) u32" shiftLeftOne [^u32 a]
  (return (<< a 1)))
```

Parameters can have additional metadata keywords:
- `^:comptime` - parameter values is known at the compile time
- `^:noalias` - TODO

```clj
(defn ^usize typeNameLength [^:comptime ^type T]
  (return (.-len (@typeName T))))
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
(+ a b)    ; add numbers  (+ 1 2)  => 3
(- a b)    ; subract      (- 2 5)  => -3
(- a)      ; negate       (- 2)    => -2
(* a b)    ; multiply     (* 2 5)  => 10
(/ a b)    ; divide       (/ 10 5) => 2

(mod a b)  ; division modulus    (mod 5 3)   => 1
(rem a b)  ; division remainder  (rem -10 3) => -1
(inc a)    ; increment by one    (inc 2)     => 3
(dec a)    ; decrement by one    (dec 2)     => 1

;; Append = to operators to modify variables
(+= a b)   ; same as  (set! a (+ a b))
(-= a b)   ; same as  (set! a (- a b))
(*= a b)   ; same as  (set! a (* a b))
(/= a b)   ; same as  (set! a (/ a b))

;; Mutating increment/decrement
(inc! a)   ; increment, same as  (+= a 1)
(dec! a)   ; decrement, same as  (-= a 1)
```

#### Comparison operators

```clj
(= a b)    ; equal
(not= a b) ; not equal
(zero? a)  ; same as (= a 0)
(pos? a)   ; positive number, same as (> a 0)
(neg? a)   ; negative number, same as (< a 0)
(even? a)  ; even number
(odd? a)   ; odd number
```

Unlinke in Clojure `=` is a binary operator for now. Instead of `(= a b c)` you need to write `(and (= a b) (= b c))`.

#### Logical operators

```clj
(and a b)  ; logical and  (and true true) => true
(or a b)   ; logical or   (and true false) => true
(not x)    ; negation     (not true) => false
```
#### Bitwise operators

```clj
(bit-not x)           ; Bitwise complement
(bit-and a b)         ; Bitwise and, alias for `&`
(bit-or a b)          ; Bitwise or, alias for `|`
(bit-xor a b)         ; Bitwise exclusive or
(bit-shift-left x n)  ; Bitwise shift left, alias for `<<`
(bit-shift-right x n) ; Bitwise shift right, alias for `>>`
(bit-flip x n)        ; Flip bit at index n
(bit-set x n)         ; Set bit at index n
(bit-test x n)        ; Test bit at index n
```

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


`case` also known as switch in other languages. 

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


## Data structures


#### Arrays and Slices

Arrays store elements of the same type with length known at compile time.  
Slices have a pointer and length known at run time. Slices usually act as a view into array or can be returned when allocating memory of dynamic size.  
The difference to arrays in C represented by pointers is that Arrays and Slices have always associated length andprotect against out of bounds access and overflows.

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


## Other forms


#### modules

@import
usingnamespace

https://ziglang.org/documentation/0.7.1/#import
https://ziglang.org/documentation/0.7.1/#usingnamespace


#### defer

`defer` will execute an expression at the end of the current scope.
`errdeher` is similar to defer, but will only execute if the scope returns with an error.

[defer](https://ziglang.org/documentation/0.7.1/#defer)

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

#### unreachable

#### async coroutines

suspend
resume
await
async

https://ziglang.org/documentation/0.7.1/#Async-Functions

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

## Std library


#### I/O and printing

and io - print

#### testing

test / testing
expect

https://ziglang.org/documentation/0.7.1/#Zig-Test



## Macros

Macros are used internally to implement some special forms, but user-defined macros are not implemented. Zig's comptime is less powerful, but I am interested to explore its limitations first before introducing macros.

Questions
- like ClojureScript - macros written in Clojure
- self-hosted macros - would need to implement higher-level datasctructures convenient for manipulating code as data


