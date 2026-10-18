import {
  ApiError,
  type CreateSurveyRequest,
  type PageResponse,
  type SimulateAnswersResult,
  type SurveyAudience,
  type SurveyListItem,
  type SurveyResults,
  type SurveyStatus,
} from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import seed from '../../../mocks/surveys.json';
import { emitMock } from '../mockBus';
import { AUDIENCES } from '../surveys';

// In-memory admin surveys for API_MODE=mock. Every change emits survey.updated like the backend.

interface MockSurvey extends SurveyListItem {
  questions: { text: string; options: { label: string; count: number }[] }[];
}

const DAY = 864e5;
const now = Date.now();
const DAYS_AGO = [1, 3, 16, 24, 36, 0]; // mockup dates: 17 okt, 15 okt, 2 okt, 24 sen, 12 sen, draft

const db: MockSurvey[] = seed.map((s, i) => ({
  id: i + 1,
  title: s.title,
  status: s.status as SurveyStatus,
  audience: s.audience as SurveyAudience,
  audienceLabel: AUDIENCES[s.audience as SurveyAudience][0],
  responses: s.responses,
  coinReward: s.coinReward,
  createdAt: new Date(now - DAYS_AGO[i] * DAY).toISOString(),
  publishedAt: s.status === 'DRAFT' ? null : new Date(now - DAYS_AGO[i] * DAY).toISOString(),
  closedAt: s.status === 'CLOSED' ? new Date(now - (DAYS_AGO[i] - 2) * DAY).toISOString() : null,
  questions: s.questions.map((q) => ({
    text: q.text,
    options: Object.entries(q.options).map(([label, pct]) => ({ label, count: Math.round((s.responses * pct) / 100) })),
  })),
}));

const toItem = ({ questions: _q, ...item }: MockSurvey): SurveyListItem => item;

function find(id: string) {
  const s = db.find((x) => x.id === Number(id));
  if (!s) throw new ApiError(404, 'NOT_FOUND', `Sorğu tapılmadı: ${id}`);
  return s;
}

function invalid(msg: string): never {
  throw new ApiError(400, 'VALIDATION', msg);
}

/** Integer percents summing to 100 (largest remainder), like the backend. */
function percents(counts: number[]) {
  const total = counts.reduce((a, b) => a + b, 0);
  if (!total) return counts.map(() => 0);
  const raw = counts.map((c) => (c * 100) / total);
  const out = raw.map(Math.floor);
  const order = raw.map((r, i) => [r - Math.floor(r), i]).sort((a, b) => b[0] - a[0]);
  const left = 100 - out.reduce((a, b) => a + b, 0);
  for (let k = 0; k < left; k++) out[order[k][1]]++;
  return out;
}

function setStatus(s: MockSurvey, from: SurveyStatus, to: SurveyStatus, msg: string) {
  if (s.status !== from) invalid(msg);
  s.status = to;
  if (to === 'ACTIVE') s.publishedAt = new Date().toISOString();
  if (to === 'CLOSED') s.closedAt = new Date().toISOString();
  emitMock('survey.updated', toItem(s));
  return toItem(s);
}

export const surveyMocks: Record<string, MockHandler> = {
  'GET /admin/surveys': (_b, { query }) => {
    const page = Number(query.get('page') ?? 0);
    const size = Number(query.get('size') ?? 20);
    const status = query.get('status');
    const all = db.filter((s) => !status || s.status === status).map(toItem);
    return { items: all.slice(page * size, page * size + size), page, size, total: all.length } satisfies PageResponse<SurveyListItem>;
  },
  'GET /admin/surveys/:id/results': (_b, { params }): SurveyResults => {
    const s = find(params.id);
    return {
      id: s.id,
      responses: s.responses,
      questions: s.questions.map((q, i) => {
        const pct = percents(q.options.map((o) => o.count));
        return { id: s.id * 100 + i, text: q.text, total: q.options.reduce((a, o) => a + o.count, 0), options: q.options.map((o, j) => ({ ...o, percent: pct[j] })) };
      }),
    };
  },
  'POST /admin/surveys': (body) => {
    const r = body as CreateSurveyRequest;
    if (!r.title?.trim()) invalid('Başlıq boş ola bilməz');
    if (![5, 10, 20].includes(r.coinReward)) invalid('Mükafat 5, 10 və ya 20 Yolçu Coin olmalıdır');
    if (!r.questions?.length || r.questions.some((q) => !q.text.trim() || q.options.length < 2)) invalid('Hər sualda ən azı 2 variant olmalıdır');
    const s: MockSurvey = {
      id: Math.max(...db.map((x) => x.id)) + 1,
      title: r.title.trim(),
      status: r.publish ? 'ACTIVE' : 'DRAFT',
      audience: r.audience,
      audienceLabel: AUDIENCES[r.audience][0],
      responses: 0,
      coinReward: r.coinReward,
      createdAt: new Date().toISOString(),
      publishedAt: r.publish ? new Date().toISOString() : null,
      closedAt: null,
      questions: r.questions.map((q) => ({ text: q.text, options: q.options.map((label) => ({ label, count: 0 })) })),
    };
    db.unshift(s);
    emitMock('survey.updated', toItem(s));
    return toItem(s);
  },
  'POST /admin/surveys/:id/publish': (_b, { params }) => setStatus(find(params.id), 'DRAFT', 'ACTIVE', 'Yalnız qaralama göndərilə bilər'),
  'POST /admin/surveys/:id/close': (_b, { params }) => setStatus(find(params.id), 'ACTIVE', 'CLOSED', 'Yalnız aktiv sorğu bitirilə bilər'),
  'POST /demo/surveys/:id/simulate-answers': (_b, { params, query }): SimulateAnswersResult => {
    const s = find(params.id);
    if (s.status !== 'ACTIVE') invalid('Yalnız aktiv sorğuya cavab əlavə etmək olar');
    const count = Number(query.get('count') ?? 50);
    for (const q of s.questions) for (let i = 0; i < count; i++) q.options[Math.floor(Math.random() * q.options.length)].count++;
    s.responses += count;
    emitMock('survey.updated', toItem(s));
    return { created: count, survey: toItem(s) };
  },
};
