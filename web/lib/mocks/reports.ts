import {
  ApiError,
  type OkResponse,
  type PageResponse,
  type ReportDetail,
  type ReportEvent,
  type ReportListItem,
  type ReportResolveRequest,
  type ReportShareRequest,
  type ReportStats,
  type ReportStatus,
  type ReportStatusFilter,
  type ReportStatusRequest,
} from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import seed from '../../../mocks/reports_seed.json';
import { emitMock } from '../mockBus';
import { OPERATORS, matchesFilters } from '../reports';

// In-memory admin reports for API_MODE=mock. Same transitions as the contract; changes emit report.updated like the backend.

const MIN = 60_000;
const iso = (ms: number) => new Date(ms).toISOString();
const ANSWER = 'Sürücü ilə izahat işi aparıldı';
const opName = (code: string) => OPERATORS.find((o) => o.code === code)?.name ?? code;

function buildEvents(created: number, minutesAgo: number, status: ReportStatus, operator: string): ReportEvent[] {
  const step = (minutesAgo * MIN) / 4;
  const ev: ReportEvent[] = [{ type: 'CREATED', note: null, actor: 'Sərnişin', createdAt: iso(created) }];
  if (status !== 'NEW') ev.push({ type: 'STATUS', note: 'REVIEW', actor: 'Dispetçer', createdAt: iso(created + step) });
  if (status === 'FORWARDED' || status === 'ANSWERED' || status === 'RESOLVED')
    ev.push({ type: 'FORWARDED', note: operator, actor: 'Dispetçer', createdAt: iso(created + 2 * step) });
  if (status === 'ANSWERED' || status === 'RESOLVED') ev.push({ type: 'ANSWERED', note: ANSWER, actor: operator, createdAt: iso(created + 3 * step) });
  return ev;
}

const now = Date.now();
const db: ReportDetail[] = seed.map((r) => {
  const created = now - r.minutesAgo * MIN;
  const status = r.status as ReportStatus;
  const operator = opName(r.operator);
  return {
    code: r.code,
    createdAt: iso(created),
    route: r.route,
    bort: r.bort,
    operator,
    category: r.category,
    textPreview: r.text,
    status,
    late: r.late,
    place: r.place,
    text: r.text,
    hasPhoto: r.hasPhoto,
    coinsGiven: status === 'RESOLVED' ? 20 : 5,
    answerText: status === 'ANSWERED' || status === 'RESOLVED' ? ANSWER : null,
    events: buildEvents(created, r.minutesAgo, status, operator),
    passenger: 'Gizli',
  };
});

const toItem = ({ code, createdAt, route, bort, operator, category, textPreview, status, late, place }: ReportDetail): ReportListItem => ({
  code, createdAt, route, bort, operator, category, textPreview, status, late, place,
});

/** For the fleet mock: last 5 reports of a bus, and whether a report code exists. */
export const reportsOfBort = (bort: string) => db.filter((r) => r.bort === bort).slice(0, 5).map(toItem);
export const reportExists = (code: string) => db.some((r) => r.code.toLowerCase() === code.toLowerCase());

function find(code: string) {
  const r = db.find((x) => x.code === code);
  if (!r) throw new ApiError(404, 'NOT_FOUND', `Şikayət tapılmadı: ${code}`);
  return r;
}

function change(r: ReportDetail, status: ReportStatus, event: Omit<ReportEvent, 'createdAt'>) {
  r.status = status;
  r.late = false;
  r.events.push({ ...event, createdAt: iso(Date.now()) });
  emitMock('report.updated', toItem(r));
  return structuredClone(r);
}

function invalid(msg: string): never {
  throw new ApiError(400, 'VALIDATION', msg);
}

// Same moment as the mockup: a new report from the demo passenger ~11 s after the dashboard opens.
let demoScheduled = false;
function scheduleDemoReport() {
  if (demoScheduled) return;
  demoScheduled = true;
  setTimeout(() => {
    const t = Date.now();
    const r: ReportDetail = {
      code: 'A-2042', createdAt: iso(t), route: '65', bort: '10-BB-559', operator: opName('bb'), category: 'Çox uzun gözləmə',
      textPreview: 'Avtobusu 25 dəqiqə gözlədim.', status: 'NEW', late: false, place: '28 May', text: 'Avtobusu 25 dəqiqə gözlədim.',
      hasPhoto: false, coinsGiven: 5, answerText: null, events: buildEvents(t, 0, 'NEW', ''), passenger: 'Gizli',
    };
    db.unshift(r);
    emitMock('report.created', toItem(r));
  }, 11_000);
}

export const reportMocks: Record<string, MockHandler> = {
  'GET /admin/reports': (_b, { query }) => {
    scheduleDemoReport();
    const page = Number(query.get('page') ?? 0);
    const size = Number(query.get('size') ?? 20);
    const f = {
      q: query.get('q') ?? '',
      status: (query.get('status') ?? '') as ReportStatusFilter | '',
      category: query.get('category') ?? '',
      operator: query.get('operator') ?? '',
      page,
    };
    const all = db.map(toItem).filter((r) => matchesFilters(r, f));
    return { items: all.slice(page * size, page * size + size), page, size, total: all.length } satisfies PageResponse<ReportListItem>;
  },
  'GET /admin/reports/stats': (): ReportStats => ({
    today: 174 + db.length, // seed has 40 rows; mockup shows 214 for the day
    open: db.filter((r) => r.status === 'NEW' || r.status === 'REVIEW').length,
    forwarded: db.filter((r) => r.status === 'FORWARDED').length,
    late: db.filter((r) => r.late).length,
    newCount: db.filter((r) => r.status === 'NEW').length,
  }),
  'GET /admin/reports/:code': (_b, { params }) => structuredClone(find(params.code)),
  'PATCH /admin/reports/:code/status': (body, { params }) => {
    const r = find(params.code);
    const { status, note } = body as ReportStatusRequest;
    if (!(r.status === 'NEW' && status === 'REVIEW')) invalid(`Status keçidi mümkün deyil: ${r.status} → ${status}`);
    return change(r, status, { type: 'STATUS', note: note || status, actor: 'Dispetçer' });
  },
  'POST /admin/reports/:code/forward': (_b, { params }) => {
    const r = find(params.code);
    if (r.status !== 'NEW' && r.status !== 'REVIEW') invalid(`Status keçidi mümkün deyil: ${r.status} → FORWARDED`);
    return change(r, 'FORWARDED', { type: 'FORWARDED', note: r.operator, actor: 'Dispetçer' });
  },
  'POST /admin/reports/:code/resolve': (body, { params }) => {
    const r = find(params.code);
    const { answerText } = body as ReportResolveRequest;
    if (r.status !== 'FORWARDED' && r.status !== 'ANSWERED') invalid(`Status keçidi mümkün deyil: ${r.status} → RESOLVED`);
    if (!answerText?.trim()) invalid('answerText boş ola bilməz');
    r.answerText = answerText;
    r.coinsGiven += 15;
    return change(r, 'RESOLVED', { type: 'ANSWERED', note: answerText, actor: 'Dispetçer' });
  },
  'POST /admin/reports/:code/share': (body, { params }) => {
    const r = find(params.code);
    const { targets } = body as ReportShareRequest;
    if (!targets?.length) invalid('Ən azı bir ünvan seçin');
    r.events.push({ type: 'SHARED', note: targets.join(','), actor: 'Dispetçer', createdAt: iso(Date.now()) });
    return { ok: true } satisfies OkResponse;
  },
};
