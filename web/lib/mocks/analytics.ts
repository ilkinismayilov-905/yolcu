import { ApiError, type AlightingPoint, type AnalyticsKpis, type DemoResponse, type DemoScenario, type HubProposal, type OdMatrix } from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import { emitMock } from '../mockBus';

// API_MODE=mock fixtures for S9 (analytics, hub proposals, /demo). Values = contract §9 examples / mockup.

const od: OdMatrix = {
  zones: ['Avtovağzal', 'Koroğlu', '28 May', 'Nizami', 'Gənclik', 'Nərimanov'],
  matrix: [
    [0, 8200, 14600, 6100, 5300, 4400],
    [3100, 0, 9800, 4200, 7700, 12900],
    [5200, 6400, 0, 7900, 8800, 6000],
    [2400, 3300, 8100, 0, 4600, 3900],
    [2000, 6900, 7400, 3800, 0, 5700],
    [1800, 10400, 6600, 3200, 4900, 0],
  ],
};

const alighting: AlightingPoint[] = [
  { stop: 'Nərimanov metro', perDay: 18420, peak: '08:35', note: 'Gənclik → Nərimanov axını, 88 və 4' },
  { stop: '28 May metro', perDay: 16900, peak: '08:20', note: 'Avtovağzal ekspressi üçün əsas hədəf' },
  { stop: 'Koroğlu', perDay: 14310, peak: '08:50', note: 'Metroya keçid, platforma 2 sıxdır' },
  { stop: 'Gənclik', perDay: 9870, peak: '08:45', note: 'Babək pr. tıxacından sonra artıb' },
  { stop: 'Nizami', perDay: 7640, peak: '09:05', note: 'Sakit, alternativ kimi təklif olunur' },
];

const proposals: HubProposal[] = [];
const babek = (id: number): HubProposal => ({
  id, name: 'Babək pr. – Gənclik metrosu', lat: 40.399, lon: 49.856, jamMinutes: 35, routesCount: 3, metroM: 300, waiting: 58, paxPerHour: 2400, confidence: 82,
  items: ['Avtobus cibi, 2 avtobusluq', 'Velo parkinq, 20 yer', 'Metroya işıqlı piyada keçidi', 'Hub kamerası və gəliş ekranı'],
  impact: { transferMin: -6, toMetroPerDay: 900, waitingPct: -40, costAzn: '180–240 min' }, status: 'NEW',
  reason: 'Bu nöqtədə tıxac sərnişinləri dayanacaqda saxlayır, amma 300 m-də metro var. Hub yaradılarsa, avtobusdan metroya və skuterə keçid rahatlaşır və tıxacdakı gözləmə azalır.',
  createdAt: new Date().toISOString(),
});

const setStatus = (status: HubProposal['status']): MockHandler => (_b, { params }) => {
  const p = proposals.find((x) => x.id === Number(params.id));
  if (!p) throw new ApiError(404, 'NOT_FOUND', 'Təklif tapılmadı');
  p.status = status;
  return structuredClone(p);
};

const ok = (scenario: DemoResponse['scenario']): DemoResponse => ({ ok: true, scenario, message: 'Ssenari işə salındı (mock)' });

export const analyticsMocks: Record<string, MockHandler> = {
  'GET /admin/analytics/kpis': (): AnalyticsKpis => ({ trackedTrips30d: 1240000, consentUsers: 31200, avgTripMin: 27, longestTransferWaitMin: 14, suggestionAcceptance: 41 }),
  'GET /admin/analytics/od': () => structuredClone(od),
  'GET /admin/analytics/alighting': (_b, { query }) => alighting.slice(0, Number(query.get('limit') ?? 5)),
  'GET /admin/hub-proposals': (_b, { query }) => structuredClone(proposals.filter((p) => !query.get('status') || p.status === query.get('status'))),
  'POST /admin/hub-proposals/:id/accept': setStatus('ACCEPTED'),
  'POST /admin/hub-proposals/:id/reject': setStatus('REJECTED'),
  // shortcut: mock traffic layer is empty, so the jam only produces the proposal (no red street); live mode has the real one.
  'POST /demo/trigger/:scenario': (_b, { params }) => {
    if (params.scenario === 'artificial-jam')
      setTimeout(() => {
        const p = babek(proposals.length + 1);
        proposals.push(p);
        emitMock('proposal.created', structuredClone(p));
        emitMock('alert.created', { id: Date.now(), category: 'AI', title: `Yeni hub təklifi: ${p.name}`, body: 'Tıxac, axın və keçid datası birləşdirildi', target: 'p-map', createdAt: new Date().toISOString() });
      }, 4000);
    return ok(params.scenario as DemoScenario);
  },
  'POST /demo/reset': () => (proposals.splice(0), ok('reset')),
};
