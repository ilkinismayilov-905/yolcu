'use client';

import { useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CoinConversion, CoinConversionStatus, CoinRule, CoinSummary, PageResponse } from '@yolcu/shared/api';
import { CoinIcon, Kpi, PageHeader, Pager, Panel, Status, Table, TD, fmtNum, type StatusKind } from '@/components/ui';
import { api } from '@/lib/api';
import { useStream } from '@/lib/stream';

const PER_PAGE = 10;
const ST: Record<CoinConversionStatus, [string, StatusKind]> = { PENDING: ['Gözləyir', 'warn'], COMPLETED: ['Tamamlandı', 'ok'] };
const keys = {
  all: ['admin', 'coins'] as const,
  summary: ['admin', 'coins', 'summary'] as const,
  rules: ['admin', 'coins', 'rules'] as const,
  conv: ['admin', 'coins', 'conversions'] as const,
};
const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');
/** "12400.00" → "12 400", "5.50" → "5,50". */
const azn = (s: string) => {
  const n = Number(s);
  return fmtNum(n, Number.isInteger(n) ? 0 : 2);
};
const big = (n: number) => (n >= 1e6 ? `${fmtNum(n / 1e6, 1)} mln` : fmtNum(n));

export default function Page() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const summary = useQuery({ queryKey: keys.summary, queryFn: () => api.get<CoinSummary>('/admin/coins/summary') });
  const rules = useQuery({ queryKey: keys.rules, queryFn: () => api.get<CoinRule[]>('/admin/coins/rules') });
  const conv = useQuery({
    queryKey: [...keys.conv, page],
    queryFn: () => api.get<PageResponse<CoinConversion>>(`/admin/coins/conversions?page=${page}&size=${PER_PAGE}`),
    placeholderData: keepPreviousData,
  });

  useStream({
    hello: () => void qc.invalidateQueries({ queryKey: keys.all }),
    'conversion.updated': (c) => {
      let found = false;
      qc.setQueriesData<PageResponse<CoinConversion>>({ queryKey: keys.conv }, (old) => {
        if (!old?.items.some((x) => x.id === c.id)) return old;
        found = true;
        return { ...old, items: old.items.map((x) => (x.id === c.id ? c : x)) };
      });
      // new request: it lands on page 0 and shifts the pages, so refetch them
      if (!found) void qc.invalidateQueries({ queryKey: keys.conv });
      if (c.status === 'COMPLETED') void qc.invalidateQueries({ queryKey: keys.summary });
    },
  });

  const s = summary.data;
  const dash = summary.isError ? '—' : '…';
  const pages = Math.max(1, Math.ceil((conv.data?.total ?? 0) / PER_PAGE));

  return (
    <>
      <PageHeader
        title="Yolçu Coin proqramı"
        desc="Sərnişinlər rəy, sorğu və şikayət üçün Yolçu Coin qazanır. Yolçu Coin real pul deyil: partnyor bank vasitəsilə manata çevrilir və ya gediş haqqına xərclənir."
      />
      <div className="mb-4 grid grid-cols-4 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
        <Kpi label="Bu gün qazanılan" value={s ? fmtNum(s.earnedToday) : dash} sub={s ? `Yolçu Coin, ${fmtNum(s.earnedTodayUsers)} istifadəçi` : summary.isError ? errText(summary.error) : 'Yolçu Coin'} subTone={summary.isError ? 'bad' : undefined} />
        <Kpi label="Dövriyyədə" value={s ? big(s.inCirculation) : dash} sub="Yolçu Coin" />
        <Kpi label="Bu ay manata çevrilən" value={s ? `${azn(s.convertedThisMonthAzn)} ₼` : dash} sub="Partnyor bank vasitəsilə" />
        <Kpi label="Məzənnə" value={s ? `${fmtNum(s.rate)} = 1 ₼` : dash} sub="Bank müqaviləsinə görə dəyişə bilər" />
      </div>
      <div className="grid grid-cols-2 items-start gap-4 max-[820px]:grid-cols-1">
        <Panel title="Yolçu Coin qaydaları" meta="Hesablama serverdə aparılır" flush>
          <Table
            head={['Hərəkət', 'Coin', 'Limit']}
            loading={rules.isPending}
            error={rules.isError ? errText(rules.error) : null}
            onRetry={() => void rules.refetch()}
            isEmpty={rules.data?.length === 0}
            empty="Qayda yoxdur"
          >
            {rules.data?.map((r) => {
              const neg = /^[−-]/.test(r.amount);
              return (
                <tr key={r.code}>
                  <td className={TD}>{r.action}</td>
                  <td className={TD}>
                    {neg ? <b className="text-bad">{r.amount}</b> : <><CoinIcon /> <b>{r.amount.replace(/^\+/, '')}</b></>}
                  </td>
                  <td className={`${TD} whitespace-normal text-ink-2`}>{r.limit}</td>
                </tr>
              );
            })}
          </Table>
        </Panel>
        <Panel title="Manata çevirmə sorğuları" meta="Partnyor bank, pilot" flush>
          <Table
            head={['İstifadəçi', 'Coin', 'Məbləğ', 'Kart', 'Status']}
            loading={conv.isPending}
            error={conv.isError ? errText(conv.error) : null}
            onRetry={() => void conv.refetch()}
            isEmpty={conv.data?.items.length === 0}
            empty="Çevirmə sorğusu yoxdur"
          >
            {conv.data?.items.map((c) => {
              const [label, kind] = ST[c.status] ?? [c.status, 'neutral'];
              return (
                <tr key={c.id}>
                  <td className={TD}>
                    <b>{c.userName}</b>
                    <div className="text-xs text-ink-3">{c.phoneMask}</div>
                  </td>
                  <td className={`${TD} tabular-nums`}>{fmtNum(c.coins)}</td>
                  <td className={TD}>{fmtNum(Number(c.azn), 2)} ₼</td>
                  <td className={`${TD} text-xs text-ink-2`}>Partnyor bank {c.cardMask}</td>
                  <td className={TD}><Status kind={kind}>{label}</Status></td>
                </tr>
              );
            })}
          </Table>
          {pages > 1 && <Pager page={page} pages={pages} onPage={setPage} />}
        </Panel>
      </div>
    </>
  );
}
