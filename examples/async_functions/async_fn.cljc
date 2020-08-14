;; Based on: https://ziglang.org/download/0.5.0/release-notes.html#Async-Functions

(const std (@import "std"))

(vari ^anyframe frame undefined)

(fn ^:pub ^void main []
  (.warn std.debug "begin main\n" [])
  (set! _ (async (func)))
  (.warn std.debug "resume func\n" [])
  (resume frame)
  (.warn std.debug "end main\n" []))

(fn ^void func []
  (.warn std.debug "begin func\n" [])
  (set! frame (@frame))
  (suspend)
  (.warn std.debug "end func\n" []))
