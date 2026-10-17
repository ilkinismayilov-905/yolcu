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
  role: Role;
  coinBalance: number;
  accessibility: boolean;
  trackingConsent: boolean;
}

export interface AuthResponse {
  token: string;
  user: UserDto;
}

// ---- 2. Geo: /geo/layers/{name}, /stops, /places ----

/** Layer names for GET /geo/layers/{name} (raw GeoJSON FeatureCollection). */
export type GeoLayerName =
  | 'routes'
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
  'bus.positions': unknown;
  'hub.counts': unknown;
  'alert.created': unknown;
  'report.created': ReportListItem;
  'report.updated': ReportListItem;
  'notification.created': unknown;
  'coin.updated': CoinUpdatedPayload;
  'route.updated': unknown;
  'proposal.created': unknown;
  'traffic.updated': unknown;
  ping: PingPayload;
  hello: HelloPayload;
}

export type StreamEvent = {
  [K in StreamEventName]: { type: K; data: StreamEventMap[K]; receivedAt: number };
}[StreamEventName];
