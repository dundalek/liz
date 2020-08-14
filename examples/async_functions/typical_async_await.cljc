(const std (@import "std"))
(const expect std.testing.expect)

;; Try toggling these)
(const simulate_fail_download false)
(const simulate_fail_file false)
(const suspend_download true)
(const suspend_file true)

(fn ^:pub ^void main []
  (set! _ (async (amainWrap)))
  ;; This simulates an event loop)
  (when suspend_file
    (resume global_file_frame))
  (when suspend_download
    (resume global_download_frame)))

(fn ^void amainWrap []
  (if (amain)
    (bind _
      (expect (not simulate_fail_download))
      (expect (not simulate_fail_file)))
    (bind e
      (case e
        error.NoResponse (expect simulate_fail_download)
        error.FileNotFound (expect simulate_fail_file)
        (@panic "test failure")))))

(fn ^!void amain []
  (const allocator std.heap.page_allocator)
  (vari download_frame (async (fetchUrl allocator "https://example.com/")))
  (vari download_awaited false)
  (errdefer (when (not download_awaited)
              (if (await download_frame)
                (bind x (.free allocator x))
                (bind _))))

  (vari file_frame (async (readFile allocator "something.txt")))
  (vari file_awaited false)
  (errdefer (when (not file_awaited)
              (if (await file_frame)
                (bind x (.free allocator x))
                (bind _))))

  (set! download_awaited true)
  (const download_text (try (await download_frame)))
  (defer (.free allocator download_text))

  (set! file_awaited true)
  (const file_text (try (await file_frame)))
  (defer (.free allocator file_text))

  (expect (.eql std.mem u8 "expected download text" download_text))
  (expect (.eql std.mem u8 "expected file text" file_text))
  (.warn std.debug "OK!\n" []))

(vari ^anyframe global_download_frame undefined)
(fn ^"anyerror![]u8" fetchUrl [^"*std.mem.Allocator" allocator ^"[]const u8" url]
  (const result (try (.dupe std.mem allocator u8 "expected download text")))
  (errdefer (.free allocator result))
  (when suspend_download
    (suspend (set! global_download_frame (@frame))))
  (when simulate_fail_download
    (return error.NoResponse))
  (.warn std.debug "fetchUrl returning\n" [])
  (return result))

(vari ^anyframe global_file_frame undefined)
(fn ^"anyerror![]u8" readFile [^"*std.mem.Allocator" allocator ^"[]const u8" filename]
  (const result (try (.dupe std.mem allocator u8 "expected file text")))
  (errdefer (.free allocator result))
  (when suspend_file
    (suspend (set! global_file_frame (@frame))))
  (when simulate_fail_file
    (return error.FileNotFound))
  (.warn std.debug "readFile returning\n" [])
  (return result))
