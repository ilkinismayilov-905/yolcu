import { ApiError, type BusDetail, type BusReview, type FleetBus, type FleetBusStatus, type OperatorStats, type PageResponse, type SearchResult } from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import operators from '../../../mocks/operators.json';
import buses from '../../../mocks/buses.json';
import { reportExists, reportsOfBort } from './reports';

// Fleet for API_MODE=mock: seed buses + mockup operator stats (OPSTAT); occupancy and ratings are stable per bort.

const STAT: Record<string, Pick<OperatorStats, 'onLine' | 'avgRating' | 'complaintsPer10k'>> = {
  bb: { onLine: 584, avgRating: 4.6, complaintsPer10k: 3.1 },
  xf: { onLine: 262, avgRating: 3.9, complaintsPer10k: 7.4 },
  ct: { onLine: 121, avgRating: 4.2, complaintsPer10k: 4.8 },
  '6d': { onLine: 49, avgRating: 3.5, complaintsPer10k: 9.2 },
};
const SIM_ROUTES = ['88', '4', '65', '33', 'M1']; // like the backend: occupancy only on simulated routes
const opName = (code: string) => operators.items.find((o) => o.code === code)?.name ?? code;
const hash = (s: string) => [...s].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7);

const fleet: FleetBus[] = buses.map((b) => {
  const h = hash(b.bort);
  return {
    bort: b.bort,
    operatorCode: b.operator,
    operator: opName(b.operator),
    route: b.route,
    type: b.type as FleetBus['type'],
    model: b.model,
    status: b.status as FleetBusStatus,
    occupancy: b.status === 'LINE' && SIM_ROUTES.includes(b.route) ? 20 + (h % 76) : null,
    driverRating: Math.round(32 + ((h >> 8) % 18)) / 10,
    capacity: b.capacity,
  };
});

const TAGS = ['Nəzakətli sürücü', 'Təmiz salon', 'Kəskin əyləc', 'Gecikdi', 'Kondisioner işləmir'];
const COMMENTS = ['Sürücü çox nəzakətli idi.', null, 'Salon isti idi, kondisioner işləmirdi.', null, 'Dayanacaqda tez yola düşdü.'];
const CROWD = ['EMPTY', 'NORMAL', 'FULL', 'OVERFULL'] as const;
const norm = (s: string) => s.toLowerCase().replace(/[-\s]/g, '');

function reviews(b: FleetBus): BusReview[] {
  const h = hash(b.bort);
  return Array.from({ length: 1 + (h % 5) }, (_, i) => ({
    createdAt: new Date(Date.now() - (i * 7 + (h % 5) + 1) * 3_600_000).toISOString(),
    driverRating: 2 + ((h >> i) % 4),
    roadRating: 2 + ((h >> (i + 3)) % 4),
    busRating: 2 + ((h >> (i + 6)) % 4),
    crowdLevel: CROWD[(h + i) % 4],
    tags: [TAGS[(h + i) % TAGS.length]],
    comment: COMMENTS[(h + i) % COMMENTS.length],
  }));
}

export const fleetMocks: Record<string, MockHandler> = {
  'GET /admin/operators': (): OperatorStats[] =>
    operators.items.map((o, i) => ({ id: i + 1, code: o.code, name: o.name, fleetTotal: o.fleetTotal, punctuality: o.punctuality, routes: o.routes, ...STAT[o.code] })),
  'GET /admin/buses': (_b, { query }) => {
    const page = Number(query.get('page') ?? 0);
    const size = Number(query.get('size') ?? 12);
    const [op, st, type] = ['operator', 'status', 'type'].map((k) => query.get(k) ?? '');
    const q = (query.get('q') ?? '').trim().toLowerCase();
    const all = fleet.filter(
      (b) => (!op || b.operatorCode === op) && (!st || b.status === st) && (!type || b.type === type) && (!q || `${b.bort} ${b.route}`.toLowerCase().includes(q)),
    );
    return { items: all.slice(page * size, page * size + size), page, size, total: all.length } satisfies PageResponse<FleetBus>;
  },
  'GET /admin/buses/:bort': (_b, { params }): BusDetail => {
    const b = fleet.find((x) => norm(x.bort) === norm(params.bort));
    if (!b) throw new ApiError(404, 'NOT_FOUND', `Avtobus tapılmadı: ${params.bort}`);
    const rv = reviews(b);
    return { ...b, reviewCount: rv.length, reports: reportsOfBort(b.bort), reviews: rv };
  },
  'GET /admin/search': (_b, { query }): SearchResult => {
    const q = (query.get('q') ?? '').trim().replace(/^#/, '');
    const bus = fleet.find((b) => norm(b.bort) === norm(q));
    if (bus) return { type: 'BUS', ref: bus.bort };
    if (reportExists(q)) return { type: 'REPORT', ref: q.toUpperCase() };
    return { type: 'NONE' };
  },
};
