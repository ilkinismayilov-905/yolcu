import { createClient, type ApiMode } from '@yolcu/shared/client';
import type { Role } from '@yolcu/shared/api';
import { liveMocks } from './mocks/live';
import { occupancyMocks } from './mocks/occupancy';
import { reportMocks } from './mocks/reports';

export const API_MODE: ApiMode = process.env.NEXT_PUBLIC_API_MODE === 'live' ? 'live' : 'mock';
export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? '';

const TOKEN_KEY = 'yolcu.token';

export const getToken = () => (typeof window === 'undefined' ? null : localStorage.getItem(TOKEN_KEY));
export const setToken = (t: string) => localStorage.setItem(TOKEN_KEY, t);

export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

export const ROLE_LABEL: Record<Role, string> = { DISPATCHER: 'Dispetçer', ADMIN: 'Admin', PASSENGER: 'Sərnişin' };

/** 401 fallback: full reload to /login drops the SSE connection and the query cache. The UI "Çıxış" uses useLogout. */
export function logout() {
  clearToken();
  window.location.replace('/login');
}

export const api = createClient({
  mode: API_MODE,
  baseUrl: API_URL,
  getToken,
  onUnauthorized: logout,
  mocks: API_MODE === 'mock' ? { ...reportMocks, ...liveMocks, ...occupancyMocks } : undefined,
});
