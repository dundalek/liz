
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

Liz tries to mimic syntax of Clojure/EDN and mapping concepts into Zig semantics. Being familiar with either one the languages is helpful. You can always refer to [Zig documentation](https://ziglang.org/documentation/0.6.0/) for more details.

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

#### Strings / Characters

String literals follow Clojure/Java escaping rules.

TODO

```clojure
  (const bytes "hello")

  \newline

  (const bytes "hello")
  (assert (= (@TypeOf bytes) (zig* "*const [5:0]u8")))
  (assert (= bytes.len 5))
  (assert (= (aget bytes 1) \e))
  (assert (= (aget bytes 5) 0))
  (assert (= \e \u0065));
  (assert (= 0x1f4a9 128169))
  ; (assert (= \💯 128175));
  (assert (.eql mem u8 "hello" "h\u0065llo")))
```

#### Arrays / Slices


aget
aset
slice


#### Structs
struct
enum
union
error

## Special forms

#### definitions
var
const
#### assignments


set!

#### arithmetic operators

  +
  /
  +=
  mod

#### comparison operators

=
not=
zero?

#### logical and bitwise operators

and
or
not


bit-not
    Bitwise complement
    bit-not

bit-and
    Bitwise and
    &
bit-or
    Bitwise or
    |
bit-xor
    Bitwise exclusive or
    bit-xor
bit-shift-left
    Bitwise shift left
    <<
bit-shift-right
    Bitwise shift right

bit-flip
    Flip bit at index n


bit-set
    Set bit at index n


bit-test
    Test bit at index n


#### conditionals
if
when

consider if-let, when-let

if-not

cond

case


#### combining expressions  - blocks

block
break

do


#### loops

while

continue
unreachable
else

while-some

while-continue

for / doseq

dotimes


#### functions

fn
defn
return

#### defer

defer
errdeher

## Other special forms

#### method calls
#### attribute access

#### nulability

?

bind

#### error handling
try
orelse
catch

#### modules

@import
usingnamespace


#### modifiers
comptime
inline



#### async coroutines

suspend
resume
await
async


#### testing

test / testing
expect

zig*


special forms by quoting
('when 1) or 'aget


## Macros
