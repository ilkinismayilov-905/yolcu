// Shapes from docs/03_API_CONTRACT.md. Backend DTOs (backend/.../dto, common/) must match.

// ---- Common ----

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type ErrorCode =
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'VALIDATION'
  | 'COINS_INSUFFICIENT'
  | 'ALREADY_DONE'
  | 'RATE_LIMIT';

/** Error body for every 4xx/5xx: { "error": { "code", "message" } } */
export interface ApiErrorBody {
  error: { code: ErrorCode | (string & {}); message: string };
}

/** Thrown by the client. `status` is the HTTP status (0 = network error). */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

// ---- 1. Auth ----

export type Role = 'PASSENGER' | 'DISPATCHER' | 'ADMIN';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserDto {
  id: number;
  fullName: string;
  email: string | null;
  role: Role;
  coinBalance: number;
  accessibility: boolean;
  trackingConsent: boolean;
}

/** POST /me/password → OkResponse; wrong current password → 400 VALIDATION. */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface OkResponse {
  ok: true;
}

export interface AuthResponse {
  token: string;
  user: UserDto;
}

// ---- 2. Geo: /geo/layers/{name}, /stops, /places ----

/** Layer names for GET /geo/layers/{name} (raw GeoJSON FeatureCollection). */
export type GeoLayerName =
  | 'routes'
  | 'routes-lite' // simplified `routes` (same properties) for the web live map
  | 'terminals'
  | 'bike-parkings'
  | 'bike-lanes'
  | 'metro-exits'
  | 'metro-ridership';

export interface Stop {
  id: string;
  name: string;
  lat: number;
  lon: number;
  routes: string[];
  kind: string; // only "BUS" in seed data so far
  accessible: boolean;
}

/** GET /stops?lat&lon&radius item, sorted by distance. */
export interface NearbyStop extends Stop {
  distanceMeters: number;
}

export interface Place {
  id: string;
  name: string;
  lat: number;
  lon: number;
}

// ---- 6 + 9. Reports (mobile /me/reports, admin /admin/reports) ----

/** Fixed category list (contract §6). */
export const REPORT_CATEGORIES = [
  'Dayanacaqda saxlamadı',
  'Kobud davranış',
  'Təhlükəli sürüş',
  'Telefonla danışırdı',
  'Kondisioner işləmir',
  'Çirkli salon',
  'Çox uzun gözləmə',
  'Əlçatanlıq problemi',
  'Ödəniş problemi',
  'Doluluq',
] as const;
export type ReportCategory = (typeof REPORT_CATEGORIES)[number];

export type ReportStatus = 'NEW' | 'REVIEW' | 'FORWARDED' | 'ANSWERED' | 'RESOLVED';
/** `status` filter of GET /admin/reports; CLOSED = ANSWERED + RESOLVED. */
export type ReportStatusFilter = 'NEW' | 'REVIEW' | 'FORWARDED' | 'CLOSED';

export interface ReportListItem {
  code: string; // "A-2042"
  createdAt: string;
  route: string;
  bort: string | null;
  operator: string | null; // operator display name, e.g. "BakuBus MMC"
  category: ReportCategory | (string & {});
  textPreview: string;
  status: ReportStatus;
  /** FORWARDED and forwardedAt older than 24 h. */
  late: boolean;
  place: string | null;
}

export type ReportEventType = 'CREATED' | 'STATUS' | 'FORWARDED' | 'SHARED' | 'ANSWERED';

export interface ReportEvent {
  type: ReportEventType;
  note: string | null;
  actor: string | null;
  createdAt: string;
}

export interface ReportDetail extends ReportListItem {
  text: string;
  hasPhoto: boolean;
  /** Total coins the passenger got for this report: submit (+5) and RESOLVED (+15). */
  coinsGiven: number;
  answerText: string | null;
  events: ReportEvent[];
  /** Admin endpoint only: "Gizli". */
  passenger?: string;
}

/** POST /me/reports */
export interface CreateReportRequest {
  route: string;
  bort?: string;
  category: ReportCategory;
  text: string;
  hasPhoto: boolean;
  lat?: number;
  lon?: number;
}

export interface CreateReportResponse extends ReportDetail {
  coinsAwarded: number;
}

/** GET /admin/reports/stats. `newCount` drives the sidebar badge. */
export interface ReportStats {
  today: number;
  open: number; // NEW + REVIEW
  forwarded: number;
  late: number;
  newCount: number;
}

/** PATCH /admin/reports/{code}/status */
export interface ReportStatusRequest {
  status: ReportStatus;
  note?: string;
}

/** POST /admin/reports/{code}/resolve */
export interface ReportResolveRequest {
  answerText: string;
}

/** OPERATOR = carrier, YPX = Dövlət Yol Polisi, CITY = Bakı Şəhər İcra Hakimiyyəti. */
export type ReportShareTarget = 'OPERATOR' | 'YPX' | 'CITY';

/** POST /admin/reports/{code}/share */
export interface ReportShareRequest {
  targets: ReportShareTarget[];
  hidePersonal: boolean;
}

export interface OkResponse {
  ok: true;
}

// ---- 8. Live data (SIM): /live/buses, /live/traffic, /live/hubs ----

export type BusType = 'ELECTRIC' | 'DIESEL';

/** GET /live/buses?route= item; also the `bus.positions` SSE item. */
export interface LiveBus {
  bort: string;
  route: string;
  type: BusType;
  lat: number;
  lon: number;
  bearing: number; // degrees, 0 = north
  occupancy: number; // % full, 0–100
  capacity: number; // places
}

export type TrafficLevel = 'FREE' | 'SLOW' | 'JAM';

/** GET /live/traffic item; `traffic.updated` SSE sends the full list. */
export interface TrafficSegment {
  id: string;
  name: string;
  level: TrafficLevel;
  speedKmh: number;
  /** [lat, lon] pairs (not GeoJSON order). */
  line: [number, number][];
}

/** GET /live/hubs item. */
export interface LiveHub {
  id: string;
  name: string;
  lat: number;
  lon: number;
  count: number;
  capacity: number;
  trend10: number;
  forecast15: number;
}

/** `hub.counts` SSE item (every 5 s). `trend` = LiveHub.trend10. */
export interface HubCount {
  hubId: string;
  count: number;
  trend: number;
  forecast15: number;
}

// ---- 9. Admin dashboard: /admin/kpis, /admin/feed, /admin/hubs ----

/** GET /admin/kpis */
export interface Kpis {
  avgSpeedKmh: number;
  avgSpeedYesterday: number;
  waitingAtHubs: number;
  avgOccupancy: number; // %
  overfullBuses: number; // buses over 90 %
  satisfaction: number; // 0–5
  satisfactionWeekDelta: number;
}

export type AlertCategory = 'HUB' | 'OCC' | 'AI' | 'BILETIM' | 'REPORT' | 'EVENT' | 'WEATHER';

/** GET /admin/feed?limit=50 item (newest first); `alert.created` SSE payload. */
export interface Alert {
  id: number;
  category: AlertCategory | (string & {});
  title: string;
  body: string | null;
  /** Web page id from the mockup, e.g. "p-occ", "p-reports". */
  target: string | null;
  createdAt: string;
}

/** GET /admin/hubs item: /live/hubs + camera data. */
export interface AdminHub extends LiveHub {
  level: string; // e.g. "HIGH"; full value list not fixed in the contract yet
  avgWaitMin: number;
  camera: string; // "K-201"
  recommendation: string | null;
}

// ---- 9. Admin: Doluluq və interval (/admin/occupancy/...) ----

/** GET /admin/occupancy/routes item; POST .../routes/{route}/interval response; `route.updated` SSE payload. */
export interface RouteOccupancy {
  route: string;
  type: BusType;
  operator: string; // display name, e.g. "BakuBus MMC"
  busCount: number;
  avgOccupancy: number; // %
  intervalMin: number;
  recommendedMin: number;
  satNow: number; // 1 decimal
  satAfter: number; // at recommendedMin, 1 decimal
  autoMode: boolean;
}

/** GET /admin/occupancy/routes/{route}/buses item. `occupancy` = passengers on board (shown as "84 / 90"). */
export interface OccupancyBus {
  bort: string;
  type: BusType;
  near: string; // stop name
  occupancy: number;
  capacity: number;
}

/** POST /admin/occupancy/routes/{route}/interval body. */
export interface IntervalRequest {
  intervalMin: number;
}

/** GET /admin/occupancy/simulate?route=&intervalMin= */
export interface SimulateResult {
  occupancy: number; // %
  waitMin: number;
  busesNeeded: number;
  sat: number;
  note: string;
}

/** PUT /admin/occupancy/auto body. */
export interface AutoModeRequest {
  enabled: boolean;
}

export interface BunchingBus {
  bort: string;
  occupancy: number; // passengers
}

/** GET /admin/occupancy/bunching item. */
export interface BunchingCase {
  route: string;
  front: BunchingBus;
  back: BunchingBus;
  gapMin: number;
  near: string;
}

// ---- 9. Admin surveys: /admin/surveys ----

export type SurveyStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED';
export type SurveyAudience = 'ALL' | 'AVTOVAGZAL_2KM' | 'ROUTE_88_4';

/** GET /admin/surveys?status=&page=&size= item; also the `survey.updated` SSE payload. */
export interface SurveyListItem {
  id: number;
  title: string;
  status: SurveyStatus;
  audience: SurveyAudience;
  audienceLabel: string; // "Bütün istifadəçilər"
  responses: number;
  coinReward: number;
  createdAt: string;
  publishedAt: string | null;
  closedAt: string | null;
}

export interface SurveyResultOption {
  label: string;
  count: number;
  percent: number; // 0–100 integer; per question sums to 100
}

/** GET /admin/surveys/{id}/results */
export interface SurveyResults {
  id: number;
  responses: number;
  /** `total` = answers to this question; may differ from `responses` by a few (seed rounding). */
  questions: { id: number; text: string; total: number; options: SurveyResultOption[] }[];
}

/** POST /demo/surveys/{id}/simulate-answers?count=50 (ACTIVE only) */
export interface SimulateAnswersResult {
  created: number;
  survey: SurveyListItem;
}

/** POST /admin/surveys. publish=true → ACTIVE + notifications, false → DRAFT. */
export interface CreateSurveyRequest {
  title: string;
  audience: SurveyAudience;
  coinReward: number; // 5 | 10 | 20
  questions: { text: string; options: string[] }[];
  publish: boolean;
}

// ---- 11. SSE (GET /api/v1/stream?token=...), list in 02_ARCHITECTURE.md §4 ----

export const STREAM_EVENTS = [
  'bus.positions',
  'hub.counts',
  'alert.created',
  'report.created',
  'report.updated',
  'notification.created',
  'coin.updated',
  'route.updated',
  'proposal.created',
  'traffic.updated',
  'survey.updated',
  'ping',
  'hello',
] as const;

export type StreamEventName = (typeof STREAM_EVENTS)[number];

/** `ping` every 20 s; `ts` is server UTC ISO instant. */
export interface PingPayload {
  ts: string;
}

/** Sent once right after the stream connects. */
export interface HelloPayload {
  userId: number;
  role: Role;
  ts: string;
}

export interface CoinUpdatedPayload {
  balance: number;
}

/** Payload types get narrowed per event as their slices land; until then `unknown`. */
export interface StreamEventMap {
  'bus.positions': LiveBus[];
  'hub.counts': HubCount[];
  'alert.created': Alert;
  'report.created': ReportListItem;
  'report.updated': ReportListItem;
  'notification.created': unknown;
  'coin.updated': CoinUpdatedPayload;
  'route.updated': RouteOccupancy;
  'proposal.created': unknown;
  'traffic.updated': TrafficSegment[];
  'survey.updated': SurveyListItem;
  ping: PingPayload;
  hello: HelloPayload;
}

export type StreamEvent = {
  [K in StreamEventName]: { type: K; data: StreamEventMap[K]; receivedAt: number };
}[StreamEventName];
