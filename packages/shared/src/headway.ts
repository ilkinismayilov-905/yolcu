// Interval engine, same formula as backend occupancy/IntervalEngine (docs/02_ARCHITECTURE.md §7).

export const TARGET_OCCUPANCY = 65;
export const MIN_INTERVAL = 6;
export const MAX_INTERVAL = 16;

const clamp = (lo: number, hi: number, x: number) => Math.min(hi, Math.max(lo, x));
const round1 = (x: number) => Math.round(x * 10) / 10;

export const occNew = (occ: number, currentIv: number, iv: number) => Math.min(130, (occ * iv) / currentIv);
export const waitMin = (iv: number) => iv / 2;
export const busesNeeded = (busCount: number, currentIv: number, iv: number) => Math.ceil((busCount * currentIv) / iv);
export const sat = (occ: number, wait: number) =>
  clamp(2.5, 4.8, 4.6 - Math.max(0, occ - 65) * 0.022 - Math.max(0, wait - 3) * 0.08);

export function recommendedInterval(occ: number, currentIv: number): number {
  const raw = clamp(MIN_INTERVAL, MAX_INTERVAL, Math.floor((currentIv * TARGET_OCCUPANCY) / occ));
  return Math.abs(raw - currentIv) <= 1 ? currentIv : raw;
}

export interface HeadwayResult {
  intervalMin: number;
  occupancy: number; // rounded %
  waitMin: number;
  busesNeeded: number;
  satNow: number; // 1 decimal
  satAfter: number; // 1 decimal
  satDelta: number; // satAfter - satNow, 1 decimal
}

/** Simulate `iv` (defaults to the recommended interval). sat values are rounded to 1 decimal before the delta, as the UI shows them. */
export function simulate(occ: number, currentIv: number, busCount: number, iv = recommendedInterval(occ, currentIv)): HeadwayResult {
  const o = occNew(occ, currentIv, iv);
  const satNow = round1(sat(occ, waitMin(currentIv)));
  const satAfter = round1(sat(o, waitMin(iv)));
  return {
    intervalMin: iv,
    occupancy: Math.round(o),
    waitMin: waitMin(iv),
    busesNeeded: busesNeeded(busCount, currentIv, iv),
    satNow,
    satAfter,
    satDelta: round1(satAfter - satNow),
  };
}
