import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tollScore, tollScenario } from './toll.ts';

const yusif = { n: 'Yusif Səfərov küç.', cars: 2400, jam: 88, pt: 90, thru: 62, res: 30 };

test('toll score and scenario match the mockup', () => {
  assert.equal(tollScore(yusif), 81);
  assert.deepEqual(tollScenario(yusif, 1, 2.5), { reductionPct: 9, shiftToTransit: 390, revenueAzn: 5500, extraBuses: 2 });
});
