import type { CoinConversion, CoinRule, CoinSummary, DemoResponse, PageResponse } from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import { emitMock } from '../mockBus';

// API_MODE=mock fixtures for S10 coins: contract §9 summary + mockup #p-coins rules and conversions.

const summary: CoinSummary = { earnedToday: 48210, earnedTodayUsers: 9120, inCirculation: 3100000, convertedThisMonthAzn: '12400.00', rate: 500 };

const rules: CoinRule[] = [
  { code: 'REVIEW', action: 'Səfərdən sonra rəy', amount: '+5', limit: 'Hər səfər üçün 1 dəfə' },
  { code: 'SURVEY', action: 'Sorğuya cavab', amount: '+5–20', limit: 'Sorğu başına 1 dəfə' },
  { code: 'FARE', action: 'Gediş haqqını Yolçu Coin ilə ödəmək', amount: '−300', limit: '0,60 ₼ gediş' },
];

const ago = (min: number) => new Date(Date.now() - min * 60_000).toISOString();
const conversions: CoinConversion[] = [
  { id: 5, userName: 'Lalə S.', phoneMask: '+994 77 ••• 14', coins: 2000, azn: '4.00', cardMask: '•• 6620', status: 'COMPLETED', createdAt: ago(40), completedAt: ago(39) },
  { id: 4, userName: 'Rauf H.', phoneMask: '+994 51 ••• 92', coins: 800, azn: '1.60', cardMask: '•• 1205', status: 'PENDING', createdAt: ago(55), completedAt: null },
  { id: 3, userName: 'Aysel Q.', phoneMask: '+994 70 ••• 55', coins: 300, azn: '0.60', cardMask: '•• 7781', status: 'COMPLETED', createdAt: ago(90), completedAt: ago(89) },
  { id: 2, userName: 'Elvin M.', phoneMask: '+994 55 ••• 08', coins: 1200, azn: '2.40', cardMask: '•• 4410', status: 'PENDING', createdAt: ago(120), completedAt: null },
  { id: 1, userName: 'Nigar Ə.', phoneMask: '+994 50 ••• 21', coins: 500, azn: '1.00', cardMask: '•• 0193', status: 'COMPLETED', createdAt: ago(180), completedAt: ago(179) },
];

export const coinMocks: Record<string, MockHandler> = {
  'GET /admin/coins/summary': () => ({ ...summary }),
  'GET /admin/coins/rules': () => structuredClone(rules),
  'GET /admin/coins/conversions': (_b, { query }): PageResponse<CoinConversion> => {
    const page = Number(query.get('page') ?? 0);
    const size = Number(query.get('size') ?? 10);
    return { items: structuredClone(conversions.slice(page * size, page * size + size)), page, size, total: conversions.length };
  },
  // Same as the backend demo: a 1000-coin request that the "bank" completes after 60 s.
  'POST /demo/trigger/coin-conversion': (): DemoResponse => {
    const c: CoinConversion = { id: conversions.length + 1, userName: 'Demo S.', phoneMask: '+994 50 ••• 00', coins: 1000, azn: '2.00', cardMask: '•• 0193', status: 'PENDING', createdAt: new Date().toISOString(), completedAt: null };
    conversions.unshift(c);
    emitMock('conversion.updated', { ...c });
    setTimeout(() => {
      c.status = 'COMPLETED';
      c.completedAt = new Date().toISOString();
      emitMock('conversion.updated', { ...c });
    }, 60_000);
    return { ok: true, scenario: 'coin-conversion', message: 'Çevirmə sorğusu yaradıldı (mock)' };
  },
};
