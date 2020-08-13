
git submodule update --init --recursive

clojure -m zil.main > examples/imgui-dice-roller/src/main.zig
zig fmt examples/imgui-dice-roller/src/main.zig
