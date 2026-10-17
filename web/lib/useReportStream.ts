'use client';

import { useQueryClient } from '@tanstack/react-query';
import type { PageResponse, ReportListItem, ReportStats } from '@yolcu/shared/api';
import { useToast } from '@/components/Toast';
import { useStream } from './stream';
import { matchesFilters, replaceInLists, reportKeys, type ReportFilters } from './reports';

/**
 * Dashboard-wide SSE wiring for reports (mounted in the dash layout so the sidebar badge updates on every page).
 * report.created: prepend to first-page lists whose filters match, bump stats locally, toast; other lists refetch.
 * report.updated: replace the item in cached lists and the detail, refetch stats.
 */
export function useReportStream() {
  const qc = useQueryClient();
  const toast = useToast();
  useStream({
    'report.created': (item) => {
      for (const q of qc.getQueryCache().findAll({ queryKey: reportKeys.lists })) {
        const f = q.queryKey[2] as ReportFilters;
        const page = q.state.data as PageResponse<ReportListItem> | undefined;
        if (!page || page.items.some((r) => r.code === item.code)) continue;
        if (f.page === 0 && matchesFilters(item, f))
          qc.setQueryData<PageResponse<ReportListItem>>(q.queryKey, {
            ...page,
            items: [item, ...page.items].slice(0, page.size),
            total: page.total + 1,
          });
        else if (matchesFilters(item, { ...f, page: 0 })) void qc.invalidateQueries({ queryKey: q.queryKey, exact: true });
      }
      qc.setQueryData<ReportStats>(reportKeys.stats, (s) =>
        s && {
          ...s,
          today: s.today + 1,
          newCount: s.newCount + (item.status === 'NEW' ? 1 : 0),
          open: s.open + (item.status === 'NEW' || item.status === 'REVIEW' ? 1 : 0),
        },
      );
      toast(`Yeni şikayət: ${item.route}${item.bort ? `, bort ${item.bort}` : ''}`);
    },
    'report.updated': (item) => {
      replaceInLists(qc, item);
      void qc.invalidateQueries({ queryKey: reportKeys.detail(item.code) });
      void qc.invalidateQueries({ queryKey: reportKeys.stats });
    },
  });
}
