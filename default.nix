with import <nixpkgs> {};
stdenv.mkDerivation {
  name = "liz-env";
  buildInputs = [
    bash
    clojure
    git
    graalvm11-ce
    which
    zig
  ];
}
