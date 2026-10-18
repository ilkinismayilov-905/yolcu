import type { AdminHub, Alert, AlertCategory, CrowdLevel, HubCount, Kpis, LiveBus, LiveHub, TrafficSegment } from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import hubsJson from '../../../mocks/hubs.json';
import stopsJson from '../../../mocks/stops_seed.json';
import simRoutes from '../../../mocks/sim_routes.json';
import { emitMock } from '../mockBus';

// API_MODE=mock fixtures for the live map (S2). Coordinates come only from mocks/stops_seed.json;
// shortcut: mock buses hop stop to stop and traffic is empty, real trajectories exist only in live mode.

const stop = (id: string) => stopsJson.items.find((s) => s.id === id);

// Contract BE-8.4: kind and level thresholds; recommendations from mockup HUBT.
const HUB_IDS = ['may28', 'koroglu', 'avtovagzal'];
const RECS: Record<string, string> = {
  koroglu: 'Platforma 2-yə nəzarətçi göndər, 4 sərnişinlərinə tətbiqdə 88 təklif et',
  may28: '65 intervalı 12 → 9 dəq, 88 intervalı 12 → 8 dəq',
  avtovagzal: '08:20 Gəncə reysi üçün M1 ekspressi işə sal',
};
const level = (count: number, capacity: number): CrowdLevel => (count / capacity >= 0.8 ? 'HIGH' : count / capacity >= 0.55 ? 'MEDIUM' : 'LOW');

const hubs: AdminHub[] = hubsJson.flatMap((h) => {
  const s = stop(h.id);
  return s
    ? [{ id: h.id, name: h.name, lat: s.lat, lon: s.lon, count: h.base, capacity: h.capacity, trend10: 0, forecast15: h.base, level: level(h.base, h.capacity), avgWaitMin: Math.max(3, Math.round((h.base / h.capacity) * 14)), camera: h.camera, recommendation: RECS[h.id] ?? null, kind: HUB_IDS.includes(h.id) ? 'HUB' : 'STOP' } satisfies AdminHub]
    : [];
});

const BUSES = [
  { bort: '10-BB-482', route: '88' },
  { bort: '10-BB-117', route: '4' },
];
let tick = 0;
function buses(): LiveBus[] {
  return BUSES.flatMap(({ bort, route }) => {
    const r = simRoutes.routes.find((x) => x.route === route);
    if (!r) return [];
    const pts = r.stops.map(stop).filter((s) => s !== undefined);
    const a = pts[tick % pts.length];
    const b = pts[(tick + 1) % pts.length];
    const bearing = (Math.atan2(b.lon - a.lon, b.lat - a.lat) * 180) / Math.PI;
    return [{ bort, route, type: r.type === 'ELECTRIC' ? 'ELECTRIC' : 'DIESEL', lat: a.lat, lon: a.lon, bearing: (bearing + 360) % 360, occupancy: 70, capacity: 90 }];
  });
}

let started = false;
function startSim() {
  if (started) return;
  started = true;
  setInterval(() => {
    tick++;
    emitMock('bus.positions', buses());
  }, 2000);
  setInterval(() => {
    const counts: HubCount[] = hubs.map((h) => {
      const delta = Math.round((Math.random() - 0.5) * 20);
      h.count = Math.max(0, h.count + delta);
      h.trend10 = delta;
      h.forecast15 = h.count + delta;
      h.level = level(h.count, h.capacity);
      return { hubId: h.id, count: h.count, trend: delta, forecast15: h.count + delta };
    });
    emitMock('hub.counts', counts);
  }, 5000);
}

const MIN = 60_000;
const FEED: [AlertCategory, string, string, string, number][] = [
  ['HUB', 'Koroğlu, platforma 2: 63 nəfər gözləyir', 'Orta gözləmə 14 dəq. 15 dəqiqəyə 70-ə çata bilər', 'p-hubs', 1],
  ['OCC', '88: avtobuslar 92% doludur', 'Tövsiyə: interval 12 → 8 dəq', 'p-occ', 2],
  ['OCC', '88: iki avtobus topa yığılıb', '10-BB-482 və 10-BB-511 arası 2 dəq', 'p-occ', 4],
  ['AI', '33-dən 2 avtobusu 88-ə keçirin', '88-də doluluq 92%, 33-də 31%', 'p-split', 5],
  ['BILETIM', 'Gəncə reysi 08:20-də gəlir, 41 bilet satılıb', 'M1 ekspress tövsiyə olunur', 'p-express', 6],
  ['EVENT', 'Bu axşam stadionda oyun var, 19:00', '65 və 88 üçün 17:30–20:30 arası +6 avtobus', 'p-occ', 17],
  ['WEATHER', '17:00-dan yağış gözlənilir', 'Avtobus tələbatı təxminən 15% artacaq', 'p-occ', 22],
];
const now = Date.now();
const feed: Alert[] = FEED.map(([category, title, body, target, ago], i) => ({ id: FEED.length - i, category, title, body, target, createdAt: new Date(now - ago * MIN).toISOString() }));

const toLive = ({ id, name, lat, lon, count, capacity, trend10, forecast15 }: AdminHub): LiveHub => ({ id, name, lat, lon, count, capacity, trend10, forecast15 });

export const liveMocks: Record<string, MockHandler> = {
  'GET /live/buses': () => (startSim(), buses()),
  'GET /live/hubs': () => (startSim(), hubs.map(toLive)),
  'GET /live/traffic': (): TrafficSegment[] => [],
  'GET /admin/hubs': () => (startSim(), structuredClone(hubs)),
  'POST /admin/hubs/:id/apply': (_b, { params }) => {
    const h = hubs.find((x) => x.id === params.id);
    if (h?.recommendation)
      emitMock('alert.created', { id: Date.now(), category: 'AI', title: `${h.name}: tövsiyə tətbiq olundu`, body: h.recommendation, target: 'p-hubs', createdAt: new Date().toISOString() });
    return { ok: true };
  },
  'GET /admin/kpis': (): Kpis => ({ avgSpeedKmh: 18, avgSpeedYesterday: 22, waitingAtHubs: 12480, avgOccupancy: 74, overfullBuses: 63, satisfaction: 4.2, satisfactionWeekDelta: 0.2 }),
  'GET /admin/feed': () => structuredClone(feed),
  'GET /geo/layers/:name': () => ({ type: 'FeatureCollection', features: [] }),
};
