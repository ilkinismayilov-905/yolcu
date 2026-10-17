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
  'report.created': unknown;
  'report.updated': unknown;
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
