import { test } from 'node:test';
import assert from 'node:assert/strict';
import { simulate } from './headway.ts';

// docs/02_ARCHITECTURE.md §7 test vectors: route, occ, iv, n -> rec, occNew, buses, satDelta
const VECTORS: [string, number, number, number, number, number, number, number][] = [
  ['88', 92, 12, 6, 8, 61, 9, 0.7],
  ['4', 81, 10, 5, 8, 65, 7, 0.4],
  ['M1', 76, 10, 3, 8, 61, 4, 0.3],
  ['65', 58, 12, 4, 12, 58, 4, 0],
  ['33', 31, 12, 5, 16, 41, 4, -0.2],
];

for (const [route, occ, iv, n, rec, occ2, buses, delta] of VECTORS) {
  test(`headway vector ${route}`, () => {
    const r = simulate(occ, iv, n);
    assert.equal(r.intervalMin, rec);
    assert.equal(r.occupancy, occ2);
    assert.equal(r.busesNeeded, buses);
    assert.equal(r.satDelta, delta);
  });
}
