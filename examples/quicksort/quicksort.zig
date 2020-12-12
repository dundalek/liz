const std = @import("std");
const expect = std.testing.expect;
pub fn quicksort(a: []usize) void {
    quicksort_(a, 0, a.len - 1);
}

fn quicksort_(a: []usize, lo: usize, hi: usize) void {
    if (lo < hi) {
        var p = partition(a, lo, hi);
        quicksort_(a, lo, p - 1);
        quicksort_(a, p + 1, hi);
    }
}

fn partition(a: []usize, lo: usize, hi: usize) usize {
    var pivot = a[hi];
    var i = lo;
    var j = lo;
    while (j <= hi) : (j += 1) {
        if (a[j] < pivot) {
            swap(a, i, j);
            i += 1;
        }
    }
    swap(a, i, hi);
    return i;
}

fn swap(a: []usize, i: usize, j: usize) void {
    var t = a[i];
    a[i] = a[j];
    a[j] = t;
}

test "quicksort" {
    var items = [_]usize{ 3, 1, 5, 4, 2 };
    var expected = [_]usize{ 1, 2, 3, 4, 5 };
    quicksort(&items);
    expect(std.mem.eql(usize, &expected, &items));
}
