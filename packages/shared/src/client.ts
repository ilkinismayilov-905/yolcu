import { ApiError, type ApiErrorBody, type AuthResponse, type LoginRequest, type UserDto } from './api';

export type ApiMode = 'mock' | 'live';

/** Mock handler: return the response body for `METHOD /path`, or throw ApiError. */
export type MockHandler = (body: unknown) => unknown;

export interface ClientConfig {
  mode: ApiMode;
  baseUrl: string; // e.g. http://localhost:8080 (without /api/v1)
  getToken: () => string | null;
  /** Called on 401 for a request that carried a token (expired / invalid). */
  onUnauthorized?: () => void;
  /** Extra mock handlers keyed by "GET /admin/kpis" (path without /api/v1, without query). */
  mocks?: Record<string, MockHandler>;
}

const MOCK_USERS: Record<string, UserDto> = {
  'dispatcher@yolcu.az': { id: 1, fullName: 'Leyla Məmmədova', role: 'DISPATCHER', coinBalance: 0, accessibility: false, trackingConsent: false },
  'admin@yolcu.az': { id: 2, fullName: 'Admin', role: 'ADMIN', coinBalance: 0, accessibility: false, trackingConsent: false },
};
const MOCK_PASSENGER: UserDto = { id: 3, fullName: 'Nigar Əliyeva', role: 'PASSENGER', coinBalance: 245, accessibility: false, trackingConsent: true };

// shortcut: mock token is "mock.<userId>", no expiry; fine for offline demo only.
const mockToken = (u: UserDto) => `mock.${u.id}`;

export function createClient(cfg: ClientConfig) {
  const builtinMocks: Record<string, MockHandler> = {
    'POST /auth/login': (body) => {
      const { email, password } = body as LoginRequest;
      const user = MOCK_USERS[email?.trim().toLowerCase()];
      if (!user || password !== 'demo123') throw new ApiError(401, 'UNAUTHORIZED', 'Email və ya şifrə yanlışdır');
      return { token: mockToken(user), user } satisfies AuthResponse;
    },
    'POST /auth/demo-login': () => ({ token: mockToken(MOCK_PASSENGER), user: MOCK_PASSENGER }) satisfies AuthResponse,
    'GET /me': () => {
      const user = [...Object.values(MOCK_USERS), MOCK_PASSENGER].find((u) => mockToken(u) === cfg.getToken());
      if (!user) throw new ApiError(401, 'UNAUTHORIZED', 'Giriş tələb olunur');
      return user;
    },
  };

  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const token = cfg.getToken();
    try {
      if (cfg.mode === 'mock') {
        const key = `${method} ${path.split('?')[0]}`;
        const handler = cfg.mocks?.[key] ?? builtinMocks[key];
        if (!handler) throw new ApiError(404, 'NOT_FOUND', `Mock yoxdur: ${key}`);
        await new Promise((r) => setTimeout(r, 150)); // let loading states render
        return handler(body) as T;
      }

      let res: Response;
      try {
        res = await fetch(`${cfg.baseUrl}/api/v1${path}`, {
          method,
          headers: {
            ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: body !== undefined ? JSON.stringify(body) : undefined,
        });
      } catch {
        throw new ApiError(0, 'NETWORK', 'Serverə qoşulmaq olmadı. İnterneti və ya serverin işlədiyini yoxlayın.');
      }
      if (res.status === 204) return undefined as T;
      const text = await res.text();
      const json: unknown = text ? JSON.parse(text) : undefined;
      if (!res.ok) {
        const err = (json as ApiErrorBody | undefined)?.error;
        throw new ApiError(res.status, err?.code ?? 'HTTP_' + res.status, err?.message ?? `Xəta baş verdi (${res.status})`);
      }
      return json as T;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401 && token) cfg.onUnauthorized?.();
      throw e;
    }
  }

  return {
    request,
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
    put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
    patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
    login: (req: LoginRequest) => request<AuthResponse>('POST', '/auth/login', req),
    demoLogin: () => request<AuthResponse>('POST', '/auth/demo-login'),
    me: () => request<UserDto>('GET', '/me'),
  };
}

export type ApiClient = ReturnType<typeof createClient>;
