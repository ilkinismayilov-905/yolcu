import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tollRecommended, tollScore, tollScenario } from './toll.ts';

const yusif = { n: 'Yusif Səfərov küç.', cars: 2400, jam: 88, pt: 90, thru: 62, res: 30 };

test('toll score and scenario match the mockup', () => {
  assert.equal(tollScore(yusif), 81);
  assert.deepEqual(tollScenario(yusif, 1, 2.5), { reductionPct: 9, shiftToTransit: 390, revenueAzn: 5500, extraBuses: 2 });
});

test('Heydər Əliyev pr. is not recommended (weak transit alternative)', () => {
  const ha = { n: 'Heydər Əliyev pr. (mərkəz hissə)', cars: 3100, jam: 70, pt: 35, thru: 82, res: 20 };
  assert.equal(tollScore(ha), 63);
  assert.equal(tollRecommended(ha), false);
  assert.equal(tollRecommended(yusif), true);
});
