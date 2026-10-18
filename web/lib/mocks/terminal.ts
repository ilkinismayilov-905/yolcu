import type { BiletimArrival } from '@yolcu/shared/api';
import type { MockHandler } from '@yolcu/shared/client';
import arrivalsJson from '../../../mocks/biletim_arrivals.json';

// API_MODE=mock: mocks/biletim_arrivals.json shifted to "now" like the backend MockBiletimAdapter.
const hhmm = (d: Date) => d.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Baku' });
let ganjaAt: number | null = null;

const row = (minutesFromNow: number, a: { from: string; plate: string; ticketsSold: number; status: string }): BiletimArrival => {
  const [nextCityBusMin, level] = a.ticketsSold >= 35 ? [18, 'HIGH' as const] : a.ticketsSold >= 20 ? [11, 'MEDIUM' as const] : [5, 'LOW' as const];
  return { time: hhmm(new Date(Date.now() + minutesFromNow * 60_000)), ...a, nextCityBusMin, level };
};

/** Demo `ganja-arrival`: a Gəncə trip 5 min from now with 45 tickets goes to the top of the list. */
export const mockGanjaArrival = () => (ganjaAt = Date.now() + 5 * 60_000);

export const terminalMocks: Record<string, MockHandler> = {
  'GET /admin/biletim/arrivals': () => {
    const list = arrivalsJson.items.map(({ minutesFromNow, ...a }) => row(minutesFromNow, a));
    if (ganjaAt && ganjaAt > Date.now())
      list.unshift(row((ganjaAt - Date.now()) / 60_000, { from: 'Gəncə', plate: '90-AB-205', ticketsSold: 45, status: 'Yoldadır, 6 km' }));
    return list;
  },
};
