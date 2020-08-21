;; == run hello
(const std (@import "std"))

(fn ^:pub ^!void main []
  (const stdout (.. std -io getStdOut outStream))
  (try (.print stdout "Hello, {}!\n" ["world"])))

;; == run hello_again
(const print (.. (@import "std") -debug -warn))

(fn ^:pub ^void main []
  (print "Hello, world!\n" []))

;; == test comments

(const assert (.. (@import "std") -debug -assert))

(test "comments"
  ;; (assert false)
  (const x true) ; another comment
  (assert x))

;; == run values
;; Top-level declarations are order-independent:
(const print std.debug.warn)
(const std (@import "std"))
(const os std.os)
(const assert std.debug.assert)

(fn ^:pub ^void main []
    ;; integers
    (const ^i32 one_plus_one (+ 1 1))
    (print "1 + 1 = {}\n" [one_plus_one])

    ;; floats
    (const ^f32 seven_div_three (/ 7.0 3.0))
    (print "7.0 / 3.0 = {}\n", [seven_div_three])

    ;; boolean
    (print "{}\n{}\n{}\n"
           [(and true false)
            (or true false,)
            (not true)])

    ;; optional
    (vari ^"?[]const u8" optional_value nil)
    (assert (= optional_value nil))

    (print "\noptional 1\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf optional_value))
       optional_value])

    (set! optional_value "hi")
    (assert (not= optional_value null))

    (print "\noptional 2\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf optional_value))
       optional_value])

    ;; error union
    (vari ^anyerror!i32 number_or_error error.ArgNotFound)

    (print "\nerror union 1\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf number_or_error))
       number_or_error])

    (set! number_or_error 1234)

    (print "\nerror union 2\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf number_or_error))
       number_or_error]))

;; == test string literals
(const assert (.. (@import "std") -debug -assert))
(const mem (.. (@import "std") -mem))

(test "string literals"
  (const bytes "hello")
  (assert (= (@TypeOf bytes) (zig* "*const [5:0]u8")))
  (assert (= bytes.len 5))
  (assert (= (aget bytes 1) \e))
  (assert (= (aget bytes 5) 0))
  (assert (= \e \u0065));
  (assert (= 0x1f4a9 128169))
  ; (assert (= \💯 128175));
  (assert (.eql mem u8 "hello" "h\u0065llo")))

;; == test namespaced_global.zig
(const std (@import "std"))
(const assert std.debug.assert)

(test "namespaced global variable"
  (assert (= (foo) 1235))
  (assert (= (foo) 1236)))

(fn ^i32 foo []
  (const S (struct
             (vari ^i32 x 1234)))
  (+= S.x 1)
  (return S.x))

;; == test Thread Local Variables
(const std (@import "std"))
(const assert std.debug.assert)

(vari ^:threadlocal ^i32 x 1234)

(test "thread local storage"
  (const thread1 (try (std.Thread.spawn {} testTls)))
  (const thread2 (try (std.Thread.spawn {} testTls)))
  (testTls {})
  (.wait thread1)
  (.wait thread2))

(fn ^void testTls [^void context]
  (assert (= x 1234))
  (+= x 1)
  (assert (= x 1235)))

;; == test comptime variables
(const std (@import "std"))
(const assert std.debug.assert)

(test "comptime vars"
  (vari ^i32 x 1)
  (vari ^:comptime ^i32 y 1)

  (+= x 1)
  (+= y 1)

  (assert (= x 2))
  (assert (= y 2))

  (when (not= y 2)
    ;; This compile error never triggers because y is a comptime variable,)
    ;; and so `y != 2` is a comptime value, and this if is statically evaluated.)
    (@compileError "wrong y value")))

;; == test Arrays
(const assert (.. (@import "std") -debug -assert))
(const mem (.. (@import "std") -mem))

;; array literal
(const message ^"[_]u8" [\h \e \l \l \o])

;; get the size of an array
(comptime
  (assert (= message.len 5)))

;; A string literal is a pointer to an array literal
(const same_message "hello")

(comptime
  (assert (.eql mem u8 (& message) same_message)))

(test "iterate over an array"
  (vari ^usize sum 0)
  (for [byte message]
    (+= sum byte))
  (assert (= sum (+ \h \e (* \l 2) \o))))

;; modifiable array
(vari ^"[100]i32" some_integers undefined)

(test "modify an array"
  (for [[*item, i] some_integers]
      (set! item.* (@intCast i32 i)))
  (assert (= (aget some_integers 10) 10))
  (assert (= (aget some_integers 99) 99)))

;; array concatenation works if the values are known
;; at compile time
(const part_one ^"[_]i32" [1, 2, 3, 4])
(const part_two ^"[_]i32" [5, 6, 7, 8])
(const all_of_it (++ part_one part_two))
(comptime
  (assert (.eql mem i32 (& all_of_it) (& ^"[_]i32" [1, 2, 3, 4, 5, 6, 7, 8]))))

;; remember that string literals are arrays
(const hello "hello")
(const world "world")
(const hello_world (++ hello " " world))
(comptime
  (assert (.eql mem u8 hello_world "hello world")))

;; ** does repeating patterns
(const pattern (** "ab" 3))
(comptime
  (assert (.eql mem u8 pattern "ababab")))

;; initialize an array to zero
(const all_zero (** ^"[_]u16" [0] 10))
(comptime
  (assert (= all_zero.len 10))
  (assert (= (aget all_zero 5) 0)))

;; use compile-time code to initialize an array
(vari fancy_array
  (label :init
    (vari ^"[10]Point" initial_value undefined)
    (for [[*pt, i] initial_value]
      (set! pt.* ^Point{:x (@intCast i32 i)
                        :y (* (@intCast i32 i) 2)}))
    (break :init initial_value)))

(const Point
  (struct
    ^i32 x
    ^i32 y))

(test "compile-time array initalization"
  (assert (= (.-x (aget fancy_array 4)) 4))
  (assert (= (.-y (aget fancy_array 4)) 8)))

;; call a function to initialize an array
(vari more_points (-> ^"[_]Point" [(makePoint 3)]
                     (** 10)))
(fn ^Point makePoint [^i32 x]
  (return ^Point {:x x,
                  :y (* x 2)}))

(test "array initialization with function calls"
  (assert (= (.-x (aget more_points 4)) 3))
  (assert (= (.-y (aget more_points 4)) 6))
  (assert (= more_points.len 10)));

;; == test infer_list_literal.zig
(const std (@import "std"))
(const assert std.debug.assert)

(test "fully anonymous list literal"
  (dump [(@as u32 1234), (@as f64 12.34), true, "hi"]))

(fn ^void dump [^var args]
  (assert (= (.-0 args) 1234))
  (assert (= (.-1 args) 12.34))
  (assert (.-2 args))
  (assert (= (aget (.-3 args) 0) \h))
  (assert (= (aget (.-3 args) 1) \i)))

;; == test multidimensional.zig
(const std (@import "std"))
(const assert std.debug.assert)

(const mat4x4 ^"[4][4]f32" [^"[_]f32" [1.0, 0.0, 0.0, 0.0]
                            ^"[_]f32" [0.0, 1.0, 0.0, 1.0]
                            ^"[_]f32" [0.0, 0.0, 1.0, 0.0]
                            ^"[_]f32" [0.0, 0.0, 0.0, 1.0]])

(test "multidimensional arrays"
  ;; Access the 2D array by indexing the outer array, and then the inner array.
  (assert (= (aget mat4x4 1 1) 1.0))

  ;; Here we iterate with for loops.
  (for [[row, row_index] mat4x4]
    (for [[cell, column_index] row]
      (when (= row_index column_index)
        (assert (= cell 1.0))))))

;; == test volatile
(const assert (.. (@import "std") -debug -assert))

(test "volatile"
  (const mmio_ptr (@intToPtr (zig* "*volatile u8") 0x12345678))
  (assert (= (@TypeOf mmio_ptr) (zig* "*volatile u8"))))

;; == test pointer casting
(const std (@import "std"))
(const assert std.debug.assert)

(test "pointer casting"
    (const ^{:align "@alignOf(u32)"} bytes ^"[_]u8" [0x12, 0x12, 0x12, 0x12])
    (const u32_ptr (@ptrCast (zig* "*const u32") (& bytes)))
    (assert (= u32_ptr.* 0x12121212))

    ;; Even this example is contrived - there are better ways to do the above than
    ;; pointer casting. For example, using a slice narrowing cast:
    (const u32_value (-> (.bytesAsSlice std.mem u32 (slice bytes 0))
                         (aget 0)))
    (assert (= u32_value 0x12121212))

    ;; And even another way, the most straightforward way to do it:
    (assert (= (@bitCast u32 bytes) 0x12121212)))

(test "pointer child type"
    ;; pointer types have a `child` field which tells you the type they point to.
    (assert (= (.-Child (zig* "(*u32)")) u32)))

;; == test structs.zig
;; Declare a struct.
;; Zig gives no guarantees about the order of fields and the size of
;; the struct but the fields are guaranteed to be ABI-aligned.
(const Point (struct ^f32 x
                     ^f32 y))

;; Maybe we want to pass it to OpenGL so we want to be particular about
;; how the bytes are arranged.
(const Point2 (^:packed struct ^f32 x
                               ^f32 y))

;; Declare an instance of a struct.
(const p ^Point {:x 0.12
                 :y 0.34})

;; Maybe we're not ready to fill out some of the fields.
(vari p2 ^Point {:x 0.12
                 :y undefined})

;; Structs can have methods
;; Struct methods are not special, they are only namespaced
;; functions that you can call with dot syntax.
(const Vec3
  (struct
    ^f32 x
    ^f32 y
    ^f32 z
    (fn ^:pub ^Vec3 init [^f32 x ^f32 y ^f32 z]
      (return ^Vec3 {:x x
                     :y y
                     :z z}))

    (fn ^:pub ^f32 dot [^Vec3 self ^Vec3 other]
      (return (+ (* self.x other.x)
                 (* self.y other.y)
                 (* self.z other.z))))))

(const assert (.. (@import "std") -debug -assert))
(test "dot product"
    (const v1 (Vec3.init 1.0, 0.0, 0.0))
    (const v2 (Vec3.init 0.0, 1.0, 0.0))
    (assert (= (.dot v1 v2) 0.0))

    ;; Other than being available to call with dot syntax, struct methods are
    ;; not special. You can reference them as any other declaration inside
    ;; the struct:
    (assert (= (Vec3.dot v1 v2) 0.0)))

;; Structs can have global declarations.
;; Structs can have 0 fields.
(const Empty
  (struct
    (const ^:pub PI 3.14)))

(test "struct namespaced variable"
    (assert (= Empty.PI 3.14))
    (assert (= (@sizeOf Empty) 0))

    ;; you can still instantiate an empty struct
    (const does_nothing ^Empty {}))

;; struct field order is determined by the compiler for optimal performance.
;; however, you can still calculate a struct base pointer given a field pointer:
(fn ^void setYBasedOnX [^*f32 x ^f32 y]
  (const point (@fieldParentPtr Point "x" x))
  (set! point.y y))

(test "field parent pointer"
  (vari point ^Point {:x 0.1234
                      :y 0.5678})

  (setYBasedOnX (& point.x) 0.9)
  (assert (= point.y 0.9)))

;; You can return a struct from a function. This is how we do generics
;; in Zig:
(fn ^type LinkedList [^:comptime ^type T]
  (return (struct
            (const ^:pub Node
              (struct
                ^?*Node prev
                ^?*Node next
                ^T data))

            ^?*Node first
            ^?*Node last
            ^usize len)))

(test "linked list"
  ;; Functions called at compile-time are memoized. This means you can
  ;; do this:
  (assert (= (LinkedList i32) (LinkedList i32)))

  (vari list ^"LinkedList(i32)"
    {:first nil
     :last nil
     :len 0})

  (assert (= list.len 0))

  ;; Since types are first class values you can instantiate the type
  ;; by assigning it to a variable:
  (const ListOfInts (LinkedList i32))
  (assert (= ListOfInts (LinkedList i32)))

  (vari node ^ListOfInts.Node
    {:prev nil
     :next nil
     :data 1234})

  (vari list2 ^"LinkedList(i32)"
    {:first (& node)
     :last (& node)
     :len 1})

  (assert (= list2.first.?.data 1234)))

;; == test Default Field Values
(const Foo
  (struct
    ^i32 ^{:default 1234} a
    ^i32 b))

(test "default struct initialization fields"
    (const x ^Foo {:b 5})
    (when (not= (+ x.a x.b) 1239)
      (@compileError "it's even comptime known!")))

;; == run struct_name.zig
(const std (@import "std"))

(fn ^:pub ^void main []
    (const Foo (struct))
    (.warn std.debug "variable: {}\n", [(@typeName Foo)])
    (.warn std.debug "anonymous: {}\n", [(@typeName (struct))])
    (.warn std.debug "function: {}\n", [(@typeName (List i32))]))

(fn ^type List [^:comptime ^type T]
  (return (struct ^T x)))

;; == test enums.zig
(const assert (.. (@import "std") -debug -assert))
(const mem (.. (@import "std") -mem))

;; Declare an enum.
(const Type
  (enum Ok
        NotOk))

;; Declare a specific instance of the enum variant.
(const c Type.Ok)

;; If you want access to the ordinal value of an enum, you
;; can specify the tag type.
(const Value
  ^u2 (enum Zero
            One
            Two))

;; Now you can cast between u2 and Value.
;; The ordinal value starts from 0, counting up for each member.
(test "enum ordinal value"
    (assert (= (@enumToInt Value.Zero) 0))
    (assert (= (@enumToInt Value.One) 1))
    (assert (= (@enumToInt Value.Two) 2)))

;; You can override the ordinal value for an enum.
(const Value2 ^u32
  (enum (set! Hundred 100)
        (set! Thousand 1000)
        (set! Million 1000000)))

(test "set enum ordinal value"
  (assert (= (@enumToInt Value2.Hundred) 100))
  (assert (= (@enumToInt Value2.Thousand) 1000))
  (assert (= (@enumToInt Value2.Million) 1000000)))

;; Enums can have methods, the same as structs and unions.
;; Enum methods are not special, they are only namespaced
;; functions that you can call with dot syntax.
(const Suit
  (enum Clubs
        Spades
        Diamonds
        Hearts

        (fn ^:pub ^bool isClubs [^Suit self]
          (return (= self Suit.Clubs)))))

(test "enum method"
  (const p Suit.Spades)
  (assert (not (.isClubs p))))

;; An enum variant of different types can be switched upon.
(const Foo
  (enum String
        Number
        None))

(test "enum variant switch"
  (const p Foo.Number)
  (const what_is_it
    (case p
      Foo.String "this is a string"
      Foo.Number "this is a number"
      Foo.None "this is a none"))

  (assert (.eql mem u8 what_is_it "this is a number")))

;; @TagType can be used to access the integer tag type of an enum.
(const Small
  (enum One
        Two
        Three
        Four))

(test "@TagType"
  (assert (= (@TagType Small) u2)))

;; @typeInfo tells us the field count and the fields names:
(test "@typeInfo"
  (assert (= (.-Enum.fields.len (@typeInfo Small)) 4))
  (assert (.eql mem u8
                (-> (@typeInfo Small) .-Enum .-fields (aget 1) .-name)
                "Two")))

;; @tagName gives a []const u8 representation of an enum value:
(test "@tagName"
  (assert (.eql mem u8 (@tagName Small.Three) "Three")))

;; == test Enum Literals
(const std (@import "std"))
(const assert std.debug.assert)

(const Color
  (enum Auto
        Off
        On))

(test "enum literals"
  (const ^Color color1 .Auto)
  (const color2 Color.Auto)
  (assert (= color1 color2)))

(test "switch using enum literals"
  (const color Color.On)
  (const result
    (case color
      .Auto false
      .On true
      .Off false))

  (assert result))

;; == test Non-exhaustive enum
(const std (@import "std"))
(const assert std.debug.assert)

(const Number
  ^u8 (enum
        One
        Two
        Three
        _))

(test "switch on non-exhaustive enum"
  (const number Number.One)
  (const result
    (case number
      .One true
      [.Two .Three] false
      _ false))

  (assert result)
  (const is_one
    (case number
      .One true
      false))

  (assert is_one))

;; == test union
(const std (@import "std"))
(const assert std.debug.assert)

(const Payload
  (union ^i64 Int
         ^f64 Float
         ^bool Bool))

(test "simple union"
  (vari payload ^Payload {:Int 1234})
  (assert (= payload.Int 1234))
  (set! payload ^Payload {:Float 12.34})
  (assert (= payload.Float 12.34)))

;; == test Tagged union
(const std (@import "std"))
(const assert std.debug.assert)

(const ComplexTypeTag
  (enum Ok
        NotOk))

(const ComplexType ^ComplexTypeTag
  (union
    ^u8 Ok
    ^void NotOk))

(test "switch on tagged union"
  (const c ^ComplexType {:Ok 42})
  (assert (= (@as ComplexTypeTag c) ComplexTypeTag.Ok))

  (case c
    ComplexTypeTag.Ok (bind value
                        (assert (= value 42)))
    ComplexTypeTag.NotOk unreachable))

(test "@TagType"
  (assert (= (@TagType ComplexType) ComplexTypeTag)))

(test "coerce to enum"
  (const c1 ^ComplexType {:Ok 42})
  (const c2 ComplexType.NotOk)

  (assert (= c1 .Ok))
  (assert (= c2 .NotOk)))

;; == test Union method
(const std (@import "std"))
(const assert std.debug.assert)

(const Variant
  ^enum (union
          ^i32 Int
          ^bool Bool

          ;; void can be omitted when inferring enum tag type.
          None

          (fn ^bool truthy [^Variant self]
            (return (case self
                      Variant.Int (bind x_int (not= x_int 0))
                      Variant.Bool (bind x_bool x_bool)
                      Variant.None false)))))

(test "union method"
  (vari v1 ^Variant {:Int 1})
  (vari v2 ^Variant {:Bool false})

  (assert (.truthy v1))
  (assert (not (.truthy v2))))

;; == test block separate scopes
(test "separate scopes"
  (do
    (const pi 3.14))
  (do
    (vari ^bool pi true)))

;; == test switch
(const std (@import "std"))
(const assert std.debug.assert)

(test "switch simple"
  (const ^u64 a 10)
  (const ^u64 zz 103)

  ;; All branches of a switch expression must be able to be coerced to a
  ;; common type.
  ;;
  ;; Branches cannot fallthrough. If fallthrough behavior is desired combine
  ;; the cases and use an if.
  (const b
    (case a
      ;; Multiple cases can be combined via a ''
      [1 2 3] 0

      ;; Ranges can be specified using the ... syntax. These are inclusive
      ;; both ends.
      (range 5 100) 1

      ;; Branches can be arbitrarily complex.
      101 (label :blk
            (const ^u64 c 5)
            (break :blk (* c (+ 2 1))))

      ;; Switching on arbitrary expressions is allowed as long as the
      ;; expression is known at compile-time.
      zz zz
      (comptime (label :blk
                  (const ^u32 d 5)
                  (const ^u32 e 100)
                  (break :blk (+ d e))))
      107

      ;; The else branch catches everything not already captured.
      ;; Else branches are mandatory unless the entire range of values
      ;; is handled.
      9))

  (assert (= b 1)))

;; Switch expressions can be used outside a function:
(const os_msg
  (case std.Target.current.os.tag
    .linux "we found a linux user"
    "not a linux user"))

;; Inside a function switch statements implicitly are compile-time
;; evaluated if the target expression is compile-time known.
(test "switch inside function"
  (case std.Target.current.os.tag
    .fuchsia (do
               ;; On an OS other than fuchsia block is not even analyzed
               ;; so this compile error is not triggered.
               ;; On fuchsia this compile error would be triggered.
               (@compileError "fuchsia not supported"))
    (do)))

;; == test switch tagged union
(const assert (.. (@import "std") -debug -assert))

(test "switch on tagged union"
  (const Point
    (struct ^u8 x
            ^u8 y))

  (const Item
    ^enum (union
            ^u32 A
            ^Point C
            D
            ^u32 E))

  (vari a ^Item{:C ^Point{:x 1 :y 2}})

  ;; Switching on more complex enums is allowed.
  (const b
    (case a
      ;; A capture group is allowed on a match and will return the enum
      ;; value matched. If the payload types of both cases are the same
      ;; they can be put into the same switch prong.
      [Item.A Item.E] (bind item item)

      ;; A reference to the matched value can be obtained using `*` syntax.
      Item.C (bind *item (label :blk
                           (+= item.*.x  1)
                           (break :blk 6)))
      ;; No else is required if the types cases was exhaustively handled
      Item.D 8))

  (assert (= b 6))
  (assert (= a.C.x 2)))

;; == test while basic
(const assert (.. (@import "std") -debug -assert))

(test "while basic"
  (vari ^usize i 0)
  (while (< i 10)
    (+= i 1))
  (assert (= i 10)))

;; == test while break
(const assert (.. (@import "std") -debug -assert))

(test "while break"
  (vari ^usize i 0)
  (while true
    (if (= i 10)
      (break))
    (+= i 1))
  (assert (= i 10)))

;; == test while continue
(const assert (.. (@import "std") -debug -assert))

(test "while continue"
  (vari ^usize i 0)
  (while true
    (+= i 1)
    (when (< i 10)
      (continue))
    (break))
  (assert (= i 10)))

;; == test while continue expression
(const assert (.. (@import "std") -debug -assert))

(test "while loop continue expression"
  (vari ^usize i 0)
  (while-continue (< i 10) (+= i 1))
  (assert (= i 10)))

(test "while loop continue expression, more complicated"
  (vari ^usize i 1)
  (vari ^usize j 1)
  (while-continue (< (* i j) 2000)
                  (do (*= i 2) (*= j 3))
    (const my_ij (* i j))
    (assert (< my_ij 2000))))

;; == test while else
(const assert (.. (@import "std") -debug -assert))

(test "while else"
  (assert (rangeHasNumber 0 10 5))
  (assert (not (rangeHasNumber 0 10 15))))

(fn ^bool rangeHasNumber [^usize begin ^usize end ^usize number]
  (vari i begin)
  (return
    (else (while-continue
           (< i end)
           (+= i 1)
           (when (= i number)
             (break true)))
          false)))

;; == test Labeled while
(test "nested break"
  (label :outer
    (while true
      (while true
        (break :outer)))))

(test "nested continue"
  (vari ^usize i 0)
  (label :outer
    (while-continue (< i 10) (+= i 1)
      (while true
        (continue :outer)))))

;; == test while with Optionals
(const assert (.. (@import "std") -debug -assert))

(test "while null capture"
  (vari ^u32 sum1 0)
  (set! numbers_left 3)
  (while (eventuallyNullSequence)
    (bind value
      (+= sum1 value)))
  (assert (= sum1 3))

  (vari ^u32 sum2 0)
  (set! numbers_left 3)
  (-> (while (eventuallyNullSequence)
        (bind value
          (+= sum2 value)))
      (else
       (assert (= sum2 3)))))

(vari ^u32 numbers_left undefined)
(fn ^?u32 eventuallyNullSequence []
  (return (if (= numbers_left 0)
            null
            (label :blk
              (-= numbers_left 1)
              (break :blk numbers_left)))))
