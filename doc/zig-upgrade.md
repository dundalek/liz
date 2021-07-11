
# Upgrading Zig

Checklist for upgrading after new zig version is released.

- Run the test suite on the new version and fix issues.
- Go through release notes and add tests+implementation for new language features.
- Compare documentation and update changed/new code samples in `docs-samples.liz`.  
  Comparison can be shown like `git difftool 0.7.0 0.8.0 -- doc/langref.html.in` inside zig repo.
- Run tests from [advent of code](https://github.com/dundalek/adventofcode/tree/master/2020) to check for any regressions.
