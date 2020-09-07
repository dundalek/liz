(const c (@import "c.zig"))
(const std (@import "std"))
(const panic (.. std -debug -panic))
(const debug_gl (@import "debug_gl.zig"))
(const glfw_impl (@import "glfw_impl.zig"))
(const gl3_impl (@import "gl3_impl.zig"))

(fn ^:export ^void errorCallback [^c_int err ^"[*c]const u8" description]
  (panic "Error: {}\n" [description]))

;; TODO: rename vari to var when I start to tweak analyzer passes
(var ^std.rand.DefaultPrng rand undefined)

(fn ^!void initRandom []
  (var ^"[8]u8" buf undefined)
  (try (std.crypto.randomBytes (slice buf 0)))
  (const seed (std.mem.readIntLittle u64 (slice buf 0 8)))
  (set! rand (std.rand.DefaultPrng.init seed)))

;; no implicit returns for now, but will see how it fits with zig semantics,
;; because it would be super nice to have
(fn ^u32 rgbColor [^u32 col]
  (return (rgbaColor (| (<< col 8) 0xFF))))

(fn ^u32 rgbaColor [^u32 col]
  (const a (<< (& col 0xFF) 24))
  (const b (<< (& col 0xFF00) 8))
  (const g (>> (& col 0xFF0000) 8))
  (const r (>> (& col 0xFF000000) 24))
  (return (| r g b a)))

(const Dice
  (struct ^u32 sides
          ^u32 color
          ^u32 color_hovered))

(const Roll
  (struct ^u32 roll
          ^usize dice))

(const dice ^"[_]Dice"
  [(makeDice 2 0xC9D127)
   (makeDice 4 0x24A146)
   (makeDice 6 0x27BCD1)
   (makeDice 8 0x9334E6)
   (makeDice 10 0xE13294)
   (makeDice 12 0xD93025)
   (makeDice 20 0xF36D00)
   (makeDice 100 0x878787)])

(fn ^Dice makeDice [^u32 sides ^u32 color]
  (return {:sides sides
           :color (rgbaColor (| (<< color 8) 0xCC))
           :color_hovered (rgbColor color)}))

(const ^usize max_rolls 20)
(var ^"[max_rolls]Roll" rolls undefined)
(var ^usize rolls_len 0)

(fn ^void doRoll []
  (var ^usize i 0)
  ;; TODO while with step expression, for now put it at the end
  (while (< i rolls_len)
    (var roll (& (aget rolls i)))
    ;; alternatively shortcut roll.dice since struct is same as namespace in zig
    (const d (aget dice (.-dice roll)))
    (set! roll.roll (+ (rand.random.uintLessThan u32 d.sides) 1))
    (+= i 1)))

(fn ^!void render []
  (c.igSetNextWindowSizeXY 500 300 c.ImGuiCond_Once)

  (var p_open false)
  (set! _ (c.igBegin "Dice Roller" (& p_open) c.ImGuiWindowFlags_None))

  (c.igText "Rolls")
  (c.igPushIDStr "Rolls buttons")

  (do
    (var ^usize i 0)
    (while (< i rolls_len)
      (const roll (aget rolls i))
      (const d (aget dice roll.dice))
      (c.igPushIDInt (@intCast c_int i))
      (c.igPushStyleColorU32 c.ImGuiCol_Button d.color)
      (c.igPushStyleColorU32 c.ImGuiCol_ButtonHovered d.color_hovered)
      (c.igPushStyleColorU32 c.ImGuiCol_ButtonActive, d.color)

      (var ^"[32]u8" buf undefined)
      (set! _ (try (std.fmt.bufPrint (& buf) "{}" [roll.roll])))
      (when (and (c.igButtonXY (& buf) 50 50)
                 (> rolls_len 1))
        (var ^usize j i)
        (while (< j (- rolls_len 1))
          (aset rolls j (aget rolls (+ j 1)))
          (+= j 1))
        (-= rolls_len 1))
      (c.igPopStyleColor 3)
      (when (< (+ i 1) rolls_len)
        (c.igSameLine 0 -1))
      (c.igPopID)
      (+= i 1)))
  (c.igPopID)

  (c.igText "Add dice")
  (c.igPushIDStr "Dice buttons")
  ;; incompatibility with clojure: for does not have implicit do but is nicer than doseq
  (for [[d i] dice]
    (c.igPushIDInt (@intCast c_int i))
    (c.igPushStyleColorU32 c.ImGuiCol_Button d.color)
    (c.igPushStyleColorU32 c.ImGuiCol_ButtonHovered d.color_hovered)
    (c.igPushStyleColorU32 c.ImGuiCol_ButtonActive, d.color)

    (var ^"[32]u8" buf undefined)
    (set! _ (try (std.fmt.bufPrint (& buf) "{}" [d.sides])))
    (when (c.igButtonXY (& buf) 50 50)
      (std.debug.warn "Adding {}: #{}, {}\n", [rolls_len, i, d])
      (when (< rolls_len max_rolls)
        (aset rolls rolls_len
              {:roll (+ (rand.random.uintLessThan u32 d.sides) 1)
               :dice i})
        (+= rolls_len 1)))
    (c.igPopStyleColor 3)
    (when (< i (- dice.len 1))
      (c.igSameLine 0 -1))
    (c.igPopID))
  (c.igPopID)

  (do
    (var ^u32 total 0)
    (var ^usize i 0)
    (while (< i rolls_len)
      (+= total (.-roll (aget rolls i)))
      (+= i 1))
    (var ^"[32]u8" buf undefined)
    (set! _ (try (std.fmt.bufPrint (& buf) "{}" [total])))
    (c.igText (slice buf 0)))

  (if (c.igButtonXY "Roll" 0 0)
    (doRoll))

  (c.igEnd))

(fn ^:pub ^!void main []
  (try (initRandom))

  (aset rolls 0
        {:roll 0
         :dice 2})
  (set! rolls_len 1)
  (doRoll)

  (set! _ (c.glfwSetErrorCallback errorCallback))

  (if (= (c.glfwInit) c.GL_FALSE)
    (panic "GLFW init failure\n" []))
  (defer (c.glfwTerminate))

  (c.glfwWindowHint c.GLFW_CONTEXT_VERSION_MAJOR, 3)
  (c.glfwWindowHint c.GLFW_CONTEXT_VERSION_MINOR, 2)
  (c.glfwWindowHint c.GLFW_OPENGL_FORWARD_COMPAT, c.GL_TRUE)
  (c.glfwWindowHint c.GLFW_OPENGL_DEBUG_CONTEXT, debug_gl.is_on)
  (c.glfwWindowHint c.GLFW_OPENGL_PROFILE, c.GLFW_OPENGL_CORE_PROFILE)
  (c.glfwWindowHint c.GLFW_RESIZABLE, c.GL_TRUE)

  (const window_width 640)
  (const window_height 480)
  (const window (orelse
                  (c.glfwCreateWindow window_width window_height "ImGUI Test" nil nil)
                  (panic "unable to create window \n" [])))
  (defer (c.glfwDestroyWindow window))

  (c.glfwMakeContextCurrent window)
  (c.glfwSwapInterval 1)

  (const context (c.igCreateContext nil))
  (defer (c.igDestroyContext context))

  (const io (c.igGetIO))
  (|= io.*.ConfigFlags c.ImGuiConfigFlags_NavEnableKeyboard)

  (const style (c.igGetStyle))
  (c.igStyleColorsDark style)

  ; (when (and (false (not= (& io.*.ConfigFlags c.ImGuiConfigFlags_ViewportsEnable) 0)))
  ;   (set! style.*.WindowRounding 0.0)
  ;   (set! style.*.Colors[c.ImGuiCol_WindowBg].w 1.0))

  (glfw_impl.Init window, true, glfw_impl.ClientApi.OpenGL)
  (defer (glfw_impl.Shutdown))

  (gl3_impl.Init) ; #version 150
  (defer (gl3_impl.Shutdown))

  (const start_time (c.glfwGetTime))
  (var prev_time start_time)

  (while (= (c.glfwWindowShouldClose window) c.GL_FALSE)
    (c.glfwPollEvents)

    (try (gl3_impl.NewFrame))
    (glfw_impl.NewFrame)
    (c.igNewFrame)

    (try (render))

    ;; main part
    ;; c.igShowDemoWindow(null)

    (c.igRender)
    (var ^c_int w undefined)
    (var ^c_int h undefined)
    (c.glfwGetFramebufferSize window, (& w) (& h))
    (c.glViewport 0, 0, w, h)
    (c.glClearColor 0.0, 0.0, 0.0, 0.0)
    (c.glClear c.GL_COLOR_BUFFER_BIT)
    (gl3_impl.RenderDrawData (c.igGetDrawData))

    ;(const now_time (c.glfwGetTime))
    ;(const elapsed (- now_time prev_time))
    ;(set! prev_time now_time)
    ;(nextFrame t, elapsed)
    ;(draw t, (@This))

    (c.glfwSwapBuffers window)))
