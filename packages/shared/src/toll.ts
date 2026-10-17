// Toll road candidates (FAKE), formulas ported 1:1 from design/web_dashboard.html (TOLL, calcToll).

export interface TollCandidate {
  n: string; // corridor name
  cars: number; // peak cars/hour
  jam: number; // %
  pt: number; // public transport alternative, %
  thru: number; // through-traffic share, %
  res: number; // resident share, %
}

export const tollScore = (t: TollCandidate) => Math.round(t.jam * 0.35 + t.pt * 0.3 + t.thru * 0.15 + (100 - t.res) * 0.2);

export interface TollScenario {
  reductionPct: number;
  shiftToTransit: number; // passengers/day
  revenueAzn: number; // per day
  extraBuses: number;
}

/** price in AZN (0.5–3), hours = 2.5 (morning peak) or 5 (both peaks). */
export function tollScenario(t: TollCandidate, price: number, hours: number): TollScenario {
  const reductionPct = Math.min(35, Math.round(price * 9 * (t.pt / 90)));
  const reduced = (t.cars * reductionPct) / 100;
  const shiftToTransit = Math.round((reduced * hours * 1.3 * 0.55) / 10) * 10;
  const revenueAzn = Math.round(((t.cars - reduced) * price * hours) / 100) * 100;
  const extraBuses = Math.ceil(shiftToTransit / hours / 90 / 1.6);
  return { reductionPct, shiftToTransit, revenueAzn, extraBuses };
}
