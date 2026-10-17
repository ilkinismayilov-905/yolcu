import type { QueryClient } from '@tanstack/react-query';
import type { PageResponse, ReportListItem, ReportStatus, ReportStatusFilter } from '@yolcu/shared/api';
import type { StatusKind } from '@/components/ui';
import operatorsJson from '../../mocks/operators.json';

// shortcut: operator select uses the static seed list (names are real, fixed); switch to GET /admin/operators when S-operators lands.
export const OPERATORS: { code: string; name: string }[] = operatorsJson.items.map(({ code, name }) => ({ code, name }));

export const REPORTS_PER_PAGE = 10;

export interface ReportFilters {
  q: string;
  status: ReportStatusFilter | '';
  category: string;
  operator: string; // operator code, e.g. "bb"
  page: number;
}

export const reportKeys = {
  all: ['reports'] as const,
  stats: ['reports', 'stats'] as const,
  lists: ['reports', 'list'] as const,
  list: (f: ReportFilters) => ['reports', 'list', f] as const,
  detail: (code: string) => ['reports', 'detail', code] as const,
};

export function reportsPath(f: ReportFilters) {
  const p = new URLSearchParams();
  if (f.status) p.set('status', f.status);
  if (f.category) p.set('category', f.category);
  if (f.operator) p.set('operator', f.operator);
  if (f.q) p.set('q', f.q);
  p.set('page', String(f.page));
  p.set('size', String(REPORTS_PER_PAGE));
  return `/admin/reports?${p}`;
}

const CLOSED: ReportStatus[] = ['ANSWERED', 'RESOLVED'];

/** Same rule as the backend filter (and the mockup's `filtered()`). */
export function matchesFilters(r: ReportListItem, f: ReportFilters) {
  const opName = OPERATORS.find((o) => o.code === f.operator)?.name;
  const q = f.q.trim().toLowerCase();
  return (
    (!q || [r.code, r.bort, r.route, r.textPreview, r.operator].join(' ').toLowerCase().includes(q)) &&
    (!f.status || (f.status === 'CLOSED' ? CLOSED.includes(r.status) : r.status === f.status)) &&
    (!f.category || r.category === f.category) &&
    (!f.operator || r.operator === opName)
  );
}

export const STATUS_LABEL: Record<ReportStatus, [string, StatusKind]> = {
  NEW: ['Yeni', 'bad'],
  REVIEW: ['Baxılır', 'warn'],
  FORWARDED: ['Operatora göndərilib', 'info'],
  ANSWERED: ['Cavab verilib', 'ok'],
  RESOLVED: ['Həll edilib', 'ok'],
};

export const statusOf = (r: ReportListItem): [string, StatusKind] => (r.late ? ['Cavab gecikir', 'bad'] : STATUS_LABEL[r.status]);

export const isClosed = (s: ReportStatus) => CLOSED.includes(s);

const hm = (d: Date) => d.toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit', hour12: false });

/** "08:12" today, "Dünən" yesterday, otherwise the date. */
export function reportTime(iso: string) {
  const d = new Date(iso);
  const days = Math.round((new Date().setHours(0, 0, 0, 0) - new Date(iso).setHours(0, 0, 0, 0)) / 864e5);
  return days <= 0 ? hm(d) : days === 1 ? 'Dünən' : d.toLocaleDateString('az-AZ');
}
export const clockTime = (iso: string) => hm(new Date(iso));

/** Put a changed report into every cached list page (no refetch). */
export function replaceInLists(qc: QueryClient, item: ReportListItem) {
  qc.setQueriesData<PageResponse<ReportListItem>>({ queryKey: reportKeys.lists }, (old) =>
    old && { ...old, items: old.items.map((r) => (r.code === item.code ? { ...r, ...item } : r)) },
  );
}
