import type { QueryClient } from '@tanstack/react-query';
import type { PageResponse, SurveyAudience, SurveyListItem, SurveyStatus } from '@yolcu/shared/api';
import type { StatusKind } from '@/components/ui';

// Survey constants shared by the /surveys page and the mock handlers. No runtime imports on purpose (no import cycles).

export const SURVEYS_PER_PAGE = 10;

export const SURVEY_STATUS: Record<SurveyStatus, [string, StatusKind]> = {
  ACTIVE: ['Aktiv', 'ok'],
  CLOSED: ['Bitib', 'info'],
  DRAFT: ['Qaralama', 'neutral'],
};

/** Audience code → label + reach shown in the form (mockup numbers). */
export const AUDIENCES: Record<SurveyAudience, [string, string]> = {
  ALL: ['Bütün istifadəçilər', '48 210'],
  AVTOVAGZAL_2KM: ['Avtovağzal ətrafı, 2 km', '6 430'],
  ROUTE_88_4: ['88 və 4 sərnişinləri', '11 902'],
};

export const REWARDS = ['5', '10', '20'] as const;

export const surveyKeys = {
  lists: ['surveys', 'list'] as const,
  list: (status: SurveyStatus | '', page: number) => ['surveys', 'list', status, page] as const,
  results: (id: number) => ['surveys', 'results', id] as const,
};

/** survey.updated: replace the row in every cached list page; pages whose status filter no longer matches refetch. */
export function applySurveyUpdate(qc: QueryClient, item: SurveyListItem) {
  for (const q of qc.getQueryCache().findAll({ queryKey: surveyKeys.lists })) {
    const status = q.queryKey[2] as SurveyStatus | '';
    const page = q.state.data as PageResponse<SurveyListItem> | undefined;
    if (!page) continue;
    const has = page.items.some((s) => s.id === item.id);
    if (has && (!status || status === item.status))
      qc.setQueryData<PageResponse<SurveyListItem>>(q.queryKey, { ...page, items: page.items.map((s) => (s.id === item.id ? item : s)) });
    else if (has || !status || status === item.status) void qc.invalidateQueries({ queryKey: q.queryKey, exact: true });
  }
  void qc.invalidateQueries({ queryKey: surveyKeys.results(item.id) });
}

const MONTHS = ['yan', 'fev', 'mar', 'apr', 'may', 'iyn', 'iyl', 'avq', 'sen', 'okt', 'noy', 'dek'];
/** "17 okt" */
export const shortDate = (iso: string) => {
  const d = new Date(iso);
  return `${d.getDate()} ${MONTHS[d.getMonth()]}`;
};

/** Form → request parts. Options: comma-separated, trimmed, empty and duplicate entries dropped. */
export const parseOptions = (s: string) => [...new Set(s.split(',').map((x) => x.trim()).filter(Boolean))];
