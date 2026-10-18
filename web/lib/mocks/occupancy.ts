import { recommendedInterval, simulate } from '@yolcu/shared/headway';
import { ApiError, type AutoModeRequest, type BunchingCase, type IntervalRequest, type OccupancyBus, type OkResponse, type RouteOccupancy, type SimulateResult } from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import routeConfig from '../../../mocks/route_config.json';
import simRoutes from '../../../mocks/sim_routes.json';
import busesJson from '../../../mocks/buses.json';
import { emitMock } from '../mockBus';

// API_MODE=mock fixtures for S3 occupancy. Occupancy values are the mockup's (OCCR); formulas from packages/shared headway.ts.
const OCC: Record<string, number> = { '88': 92, '4': 81, M1: 76, '65': 58, '33': 31, H1: 64 };
const STOPS = ['Avtovağzal', 'Elmlər', 'Füzuli küç.', '28 May', 'Gənclik', 'Xoyski pr.', 'Nərimanov'];

let autoMode = false;
const state = routeConfig.map((c) => ({ ...c, occ: OCC[c.route] ?? 60 }));

function row(c: (typeof state)[number]): RouteOccupancy {
  const r = simulate(c.occ, c.intervalMin, c.busCount);
  const type = simRoutes.routes.find((x) => x.route === c.route)?.type === 'DIESEL' ? 'DIESEL' : 'ELECTRIC';
  return { route: c.route, type, operator: 'BakuBus MMC', busCount: c.busCount, avgOccupancy: c.occ, intervalMin: c.intervalMin, recommendedMin: recommendedInterval(c.occ, c.intervalMin), satNow: r.satNow, satAfter: r.satAfter, autoMode };
}

const find = (route: string) => {
  const c = state.find((x) => x.route === route);
  if (!c) throw new ApiError(404, 'NOT_FOUND', 'Tapılmadı');
  return c;
};

function buses(route: string): OccupancyBus[] {
  const c = find(route);
  const list = busesJson.filter((b) => b.route === route).slice(0, c.busCount);
  return list.map((b, i) => ({
    bort: b.bort,
    type: b.type === 'DIESEL' ? 'DIESEL' : 'ELECTRIC',
    near: STOPS[(i * 2 + 1) % STOPS.length],
    occupancy: Math.max(8, Math.min(b.capacity, Math.round((c.occ / 100) * b.capacity + ((i * 29) % 30) - 12 + Math.round(Math.random() * 4 - 2)))),
    capacity: b.capacity,
  }));
}

const BUNCHING: BunchingCase[] = [{ route: '88', front: { bort: '10-BB-482', occupancy: 84 }, back: { bort: '10-BB-511', occupancy: 41 }, gapMin: 2, near: '28 May' }];

export const occupancyMocks: Record<string, MockHandler> = {
  'GET /admin/occupancy/routes': () => state.map(row),
  'GET /admin/occupancy/routes/:route/buses': (_b, { params }) => buses(params.route),
  'POST /admin/occupancy/routes/:route/interval': (body, { params }) => {
    const { intervalMin } = body as IntervalRequest;
    if (!(intervalMin >= 6 && intervalMin <= 16)) throw new ApiError(400, 'VALIDATION', 'İnterval 6–16 dəqiqə olmalıdır');
    const c = find(params.route);
    c.occ = simulate(c.occ, c.intervalMin, c.busCount, intervalMin).occupancy;
    c.intervalMin = intervalMin;
    const r = row(c);
    emitMock('route.updated', r);
    return r;
  },
  'GET /admin/occupancy/simulate': (_b, { query }): SimulateResult => {
    const c = find(query.get('route') ?? '');
    const r = simulate(c.occ, c.intervalMin, c.busCount, Number(query.get('intervalMin')));
    return { occupancy: r.occupancy, waitMin: r.waitMin, busesNeeded: r.busesNeeded, sat: r.satAfter, note: '' };
  },
  'PUT /admin/occupancy/auto': (body): OkResponse => {
    autoMode = (body as AutoModeRequest).enabled;
    return { ok: true };
  },
  'GET /admin/occupancy/bunching': () => structuredClone(BUNCHING),
  'POST /admin/occupancy/bunching/:route/message': (): OkResponse => ({ ok: true }),
};
