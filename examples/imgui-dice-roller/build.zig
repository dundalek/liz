const Builder = @import("std").build.Builder;
const builtin = @import("builtin");

pub fn build(b: *Builder) void {
  const mode = b.standardReleaseOptions();
  // const windows = b.option(bool, "windows", "create windows build") orelse false;

  const cimgui = b.addStaticLibrary("cimgui", null);
  cimgui.addIncludeDir("cimgui");
  cimgui.addIncludeDir("cimgui/imgui");
  cimgui.addCSourceFile("cimgui/cimgui.cpp", &[_][]const u8{});
  cimgui.addCSourceFile("cimgui/imgui/imgui.cpp", &[_][]const u8{});
  cimgui.addCSourceFile("cimgui/imgui/imgui_draw.cpp", &[_][]const u8{});
  cimgui.addCSourceFile("cimgui/imgui/imgui_demo.cpp", &[_][]const u8{});
  cimgui.addCSourceFile("cimgui/imgui/imgui_widgets.cpp", &[_][]const u8{});
  cimgui.addIncludeDir("src/c");
  cimgui.addCSourceFile("src/c/cimgui_custom.cpp", &[_][]const u8{});
  cimgui.linkSystemLibrary("c");

  const cimgui_step = b.step("cimgui", "Build cimgui lib");
  cimgui_step.dependOn(&cimgui.step);

  const exe = b.addExecutable("main", "src/main.zig");
  exe.setOutputDir("target");
  exe.setBuildMode(mode);

  // if (windows) {
  //   exe.setTarget(builtin.Arch.x86_64, builtin.Os.windows, builtin.Abi.gnu);
  // }

  // exe.linkSystemLibrary("c");
  // exe.linkSystemLibrary("c++");
  // exe.linkSystemLibrary("glfw");
  // exe.linkSystemLibrary("epoxy");
  // exe.addIncludeDir("cimgui");
  // exe.linkSystemLibraryName("cimgui/cimgui.so");

  exe.linkSystemLibrary("c");
  exe.linkSystemLibrary("c++");
  exe.linkSystemLibrary("glfw");
  exe.linkSystemLibrary("epoxy");
  exe.addIncludeDir("cimgui");
  exe.addIncludeDir("src/c");
  exe.linkLibraryOrObject(cimgui);

  exe.install();

  const play = b.step("play", "Play the game");
  const run = exe.run();
  run.step.dependOn(b.getInstallStep());
  play.dependOn(&run.step);
}
