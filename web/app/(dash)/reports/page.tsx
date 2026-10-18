'use client';

import { Suspense, useEffect, useState, type ReactNode } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  REPORT_CATEGORIES,
  type OkResponse,
  type PageResponse,
  type ReportDetail,
  type ReportListItem,
  type ReportShareRequest,
  type ReportShareTarget,
  type ReportStats,
  type ReportStatusFilter,
} from '@yolcu/shared/api';
import { Button, CoinIcon, Kpi, PageHeader, Pager, Panel, Plate, Seg, Status, Table, TD, TR_CLICK } from '@/components/ui';
import { Drawer, Modal } from '@/components/Overlay';
import { useToast } from '@/components/Toast';
import { API_MODE, api } from '@/lib/api';
import { navItem } from '@/lib/nav';
import {
  OPERATORS,
  REPORTS_PER_PAGE,
  clockTime,
  exportReportsCsv,
  isClosed,
  replaceInLists,
  reportKeys,
  reportTime,
  reportsPath,
  statusOf,
  type ReportFilters,
} from '@/lib/reports';

const STATUS_SEG = [
  ['', 'Hamısı'],
  ['NEW', 'Yeni'],
  ['REVIEW', 'Baxılır'],
  ['FORWARDED', 'Operatorda'],
  ['CLOSED', 'Bağlanıb'],
] as const satisfies readonly (readonly [ReportStatusFilter | '', string])[];

const SELECT = 'h-9 rounded-[9px] border border-line bg-surface px-3 text-ink outline-0 focus:border-primary';
const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');

export default function Page() {
  // useSearchParams needs a Suspense boundary for the static export.
  return (
    <Suspense>
      <Reports />
    </Suspense>
  );
}

function useFilters(): [ReportFilters, (patch: Partial<ReportFilters>) => void] {
  const sp = useSearchParams();
  const router = useRouter();
  const path = usePathname();
  const st = sp.get('status') ?? '';
  const f: ReportFilters = {
    q: sp.get('q') ?? '',
    status: STATUS_SEG.some(([k]) => k === st) ? (st as ReportFilters['status']) : '',
    category: sp.get('category') ?? '',
    operator: sp.get('operator') ?? '',
    page: Math.max(0, Number(sp.get('page')) || 0),
  };
  const set = (patch: Partial<ReportFilters>) => {
    const n = { ...f, page: 0, ...patch };
    const p = new URLSearchParams();
    if (n.q) p.set('q', n.q);
    if (n.status) p.set('status', n.status);
    if (n.category) p.set('category', n.category);
    if (n.operator) p.set('operator', n.operator);
    if (n.page) p.set('page', String(n.page));
    router.replace(p.size ? `${path}?${p}` : path, { scroll: false });
  };
  return [f, set];
}

function Reports() {
  const toast = useToast();
  const [f, setF] = useFilters();
  const [q, setQ] = useState(f.q);
  const [open, setOpen] = useState<ReportListItem | null>(null);

  useEffect(() => setQ(f.q), [f.q]); // topbar search changes the URL
  useEffect(() => {
    if (q === f.q) return;
    const t = setTimeout(() => setF({ q }), 250);
    return () => clearTimeout(t);
  });

  const stats = useQuery({ queryKey: reportKeys.stats, queryFn: () => api.get<ReportStats>('/admin/reports/stats') });
  const list = useQuery({
    queryKey: reportKeys.list(f),
    queryFn: () => api.get<PageResponse<ReportListItem>>(reportsPath(f)),
    placeholderData: keepPreviousData,
  });
  // shortcut: mock mode has no CSV (toast only); generate client-side if offline demo needs it.
  const csv = useMutation({
    mutationFn: () => (API_MODE === 'mock' ? Promise.reject(new Error('Mock rejimində CSV ixrac yoxdur')) : exportReportsCsv(f)),
    onSuccess: (n) => toast(`CSV yükləndi: ${n} şikayət`),
    onError: (e) => toast(errText(e)),
  });
  const total = list.data?.total ?? 0;
  const pages = Math.max(1, Math.ceil(total / REPORTS_PER_PAGE));
  const s = stats.data;
  const kv = (v: number | undefined) => (stats.isError ? '—' : (v ?? '…'));

  return (
    <>
      <PageHeader
        title="Şikayətlər"
        desc={navItem('/reports').desc}
        actions={
          <Button disabled={csv.isPending} aria-busy={csv.isPending} onClick={() => csv.mutate()}>
            {csv.isPending ? 'Hazırlanır…' : 'CSV ixrac'}
          </Button>
        }
      />
      <div className="mb-4 grid grid-cols-4 gap-4 max-[1100px]:grid-cols-2">
        <Kpi label="Bu gün daxil olub" value={kv(s?.today)} sub="Hamısı mobil tətbiqdən" />
        <Kpi label="Cavab gözləyir" value={kv(s?.open)} sub="Yeni və baxılır statusunda" />
        <Kpi label="Operatora göndərilib" value={kv(s?.forwarded)} sub="Orta cavab 5 saat" />
        <Kpi label="24 saatdan çox gecikən" value={kv(s?.late)} sub="Operator cavab verməyib" tone="bad" />
      </div>
      <Panel flush>
        <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3">
          <input
            type="search"
            aria-label="Axtarış"
            className={`${SELECT} w-[260px]`}
            placeholder="ID, bort, marşrut və ya mətn"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <Seg label="Status" options={STATUS_SEG} value={f.status} onChange={(status) => setF({ status })} />
          <select aria-label="Mövzu" className={SELECT} value={f.category} onChange={(e) => setF({ category: e.target.value })}>
            <option value="">Bütün mövzular</option>
            {REPORT_CATEGORIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
          <select aria-label="Operator" className={SELECT} value={f.operator} onChange={(e) => setF({ operator: e.target.value })}>
            <option value="">Bütün operatorlar</option>
            {OPERATORS.map((o) => (
              <option key={o.code} value={o.code}>{o.name}</option>
            ))}
          </select>
          <span className="ml-auto text-[13px] text-ink-3">{list.data ? `${total} şikayət` : ''}</span>
        </div>
        <Table
          head={['ID', 'Vaxt', 'Marşrut', 'Bort', 'Operator', 'Mövzu', 'Status']}
          loading={list.isPending}
          error={list.isError ? errText(list.error) : null}
          onRetry={() => void list.refetch()}
          isEmpty={!list.data?.items.length}
          empty="Bu filtrə uyğun şikayət yoxdur. Filtrləri dəyişin və ya axtarışı təmizləyin."
        >
          {list.data?.items.map((r) => {
            const [label, kind] = statusOf(r);
            // shortcut: "new row" highlight = created in the last 15 s (SSE arrivals); needs sane server clock.
            const fresh = Date.now() - new Date(r.createdAt).getTime() < 15_000;
            return (
              <tr
                key={r.code}
                tabIndex={0}
                className={`${TR_CLICK} ${fresh ? 'flash' : ''} ${open?.code === r.code ? '[&>td]:bg-primary-soft' : ''}`}
                onClick={() => setOpen(r)}
                onKeyDown={(e) => e.key === 'Enter' && setOpen(r)}
              >
                <td className={`${TD} text-ink-3`}>#{r.code}</td>
                <td className={TD}>{reportTime(r.createdAt)}</td>
                <td className={TD}><Plate route={r.route} /></td>
                <td className={TD}>{r.bort ?? '—'}</td>
                <td className={TD}>{r.operator ?? '—'}</td>
                <td className={TD}>
                  <b>{r.category}</b>
                  <div className="max-w-[260px] truncate text-[13px] text-ink-3">{r.textPreview}</div>
                </td>
                <td className={TD}><Status kind={kind}>{label}</Status></td>
              </tr>
            );
          })}
        </Table>
        <Pager page={Math.min(f.page, pages - 1)} pages={pages} onPage={(page) => setF({ page })} />
      </Panel>
      <ReportDrawer item={open} onClose={() => setOpen(null)} />
    </>
  );
}

const ANSWER = 'Sürücü ilə izahat işi aparıldı';

function ReportDrawer({ item, onClose }: { item: ReportListItem | null; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [shareOpen, setShareOpen] = useState(false);
  // Keep the last item while the drawer slides out.
  const [last, setLast] = useState(item);
  if (item && item !== last) setLast(item);
  const code = last?.code ?? '';

  const detail = useQuery({
    queryKey: reportKeys.detail(code),
    queryFn: () => api.get<ReportDetail>(`/admin/reports/${encodeURIComponent(code)}`),
    enabled: !!item,
  });
  const r: ReportListItem | undefined = detail.data ?? qc.getQueryData<ReportDetail>(reportKeys.detail(code)) ?? last ?? undefined;

  const act = useMutation({
    mutationFn: ({ path, body, method }: { path: string; body?: unknown; method: 'PATCH' | 'POST'; msg: string }) =>
      api.request<ReportDetail>(method, `/admin/reports/${encodeURIComponent(code)}/${path}`, body),
    onSuccess: (d, v) => {
      qc.setQueryData(reportKeys.detail(d.code), d);
      replaceInLists(qc, d);
      void qc.invalidateQueries({ queryKey: reportKeys.stats });
      toast(v.msg);
    },
    onError: (e) => toast(errText(e)),
  });

  if (!r) return <Drawer open={false} onClose={onClose} title="" />;
  const d = detail.data;
  const [label, kind] = statusOf(r);
  const closed = isClosed(r.status);
  const at = (type: string) => d?.events.find((e) => e.type === type)?.createdAt;
  const busy = act.isPending;
  // coinsGiven is the total; RESOLVED includes the +15 verification award.
  const verified = d?.status === 'RESOLVED' && d.coinsGiven >= 15;

  return (
    <>
      <Drawer
        open={!!item}
        onClose={onClose}
        meta={`#${r.code}, ${reportTime(r.createdAt)}`}
        title={r.category}
        extra={<Status kind={kind}>{label}</Status>}
        footer={
          <>
            <Button onClick={() => setShareOpen(true)}>Paylaş</Button>
            {r.status === 'NEW' && (
              <Button disabled={busy} onClick={() => act.mutate({ method: 'PATCH', path: 'status', body: { status: 'REVIEW', note: '' }, msg: 'Status dəyişdi: baxılır' })}>
                Baxılır et
              </Button>
            )}
            {(r.status === 'NEW' || r.status === 'REVIEW') && (
              <Button variant="p" disabled={busy} onClick={() => act.mutate({ method: 'POST', path: 'forward', msg: `#${r.code} ${r.operator ?? 'Operator'}-yə göndərildi. Cavab müddəti 24 saat` })}>
                Operatora göndər
              </Button>
            )}
            {(r.status === 'FORWARDED' || r.status === 'ANSWERED') && (
              <Button variant="p" disabled={busy} onClick={() => act.mutate({ method: 'POST', path: 'resolve', body: { answerText: ANSWER }, msg: 'Həll edildi. Sərnişinə bildiriş və +15 Yolçu Coin göndərildi' })}>
                Həll edildi
              </Button>
            )}
          </>
        }
      >
        {detail.isError ? (
          <div className="text-bad">
            {errText(detail.error)} <Button sm className="ml-2" onClick={() => void detail.refetch()}>Yenidən cəhd et</Button>
          </div>
        ) : !d ? (
          <div className="text-ink-3">Yüklənir…</div>
        ) : (
          <>
            <div className="rounded-xl border border-line bg-surface-2 px-3.5 py-3 text-[13px] text-ink">“{d.text}”</div>
            {d.hasPhoto && (
              <div className="grid h-[140px] place-items-center rounded-[10px] bg-[repeating-linear-gradient(135deg,var(--surface-2),var(--surface-2)_10px,var(--surface-3)_10px,var(--surface-3)_20px)] text-[13px] text-ink-3">
                Sərnişinin əlavə etdiyi foto
              </div>
            )}
            <dl className="m-0 grid grid-cols-[130px_1fr] gap-x-3 gap-y-2 text-[13px] [&_dd]:m-0 [&_dd]:font-medium [&_dt]:text-ink-3">
              <dt>Marşrut</dt><dd><Plate route={d.route} /></dd>
              <dt>Bort nömrəsi</dt><dd>{d.bort ?? '—'}</dd>
              <dt>Operator</dt><dd>{d.operator ?? '—'}</dd>
              <dt>Yer</dt><dd>{d.place ? `${d.place} yaxınlığında` : '—'}</dd>
              <dt>Mənbə</dt><dd>Mobil tətbiq, GPS və bilet ilə təsdiqlənib</dd>
              <dt>Sərnişinə verilən</dt>
              <dd>
                <CoinIcon />{' '}
                {verified ? `${d.coinsGiven - 15} coin, təsdiq üçün +15` : `${d.coinsGiven} coin`}
              </dd>
            </dl>
            <div>
              <div className="mb-2 text-[13px] text-ink-3">Tarixçə</div>
              <ul className="m-0 grid list-none p-0">
                <Step done><b>Göndərildi</b> <span className="text-ink-3">{clockTime(d.createdAt)}</span></Step>
                <Step done={d.status !== 'NEW'}>
                  <b>AYNA baxır</b> {at('STATUS') && <span className="text-ink-3">{clockTime(at('STATUS')!)}</span>}
                </Step>
                <Step done={d.status === 'FORWARDED' || closed}>
                  <b>Operatora göndərildi</b> <span className="text-ink-3">{d.operator}</span>
                </Step>
                <Step done={closed} last>
                  <b>Operator cavab verdi</b>
                  {closed && d.answerText && <span className="text-ink-3"> {d.answerText}</span>}
                </Step>
              </ul>
            </div>
          </>
        )}
      </Drawer>
      <ShareModal open={shareOpen && !!item} onClose={() => setShareOpen(false)} report={r} />
    </>
  );
}

function Step({ done, last, children }: { done?: boolean; last?: boolean; children: ReactNode }) {
  return (
    <li className="relative pb-3.5 pl-[22px] text-[13px]">
      <span className={`absolute top-1.5 left-1 size-2 rounded-full ${done ? 'bg-primary' : 'bg-surface-3'}`} />
      {!last && <span className="absolute top-4 bottom-0 left-[7.5px] w-px bg-line" />}
      {children}
    </li>
  );
}

const TARGETS: [ReportShareTarget, string][] = [
  ['OPERATOR', 'Operator (avtobusun daşıyıcısı)'],
  ['YPX', 'Dövlət Yol Polisi'],
  ['CITY', 'Bakı Şəhər İcra Hakimiyyəti'],
];

function ShareModal({ open, onClose, report }: { open: boolean; onClose: () => void; report: ReportListItem }) {
  const toast = useToast();
  const [targets, setTargets] = useState<ReportShareTarget[]>(['OPERATOR']);
  const [hidePersonal, setHide] = useState(true);
  const share = useMutation({
    mutationFn: (body: ReportShareRequest) => api.post<OkResponse>(`/admin/reports/${encodeURIComponent(report.code)}/share`, body),
    onSuccess: (_d, b) => {
      onClose();
      toast(b.hidePersonal ? 'Şikayət paylaşıldı. Sərnişinin şəxsi məlumatları gizlədildi' : 'Şikayət paylaşıldı');
    },
    onError: (e) => toast(errText(e)),
  });
  const chk = 'flex items-center gap-2.5 border-b border-line py-[9px] last:border-b-0 [&_input]:size-4 [&_input]:accent-primary';
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Şikayəti paylaş"
      sub={`#${report.code}, ${report.operator ?? ''}`}
      footer={
        <>
          <Button onClick={onClose}>Ləğv et</Button>
          <Button variant="p" disabled={!targets.length || share.isPending} onClick={() => share.mutate({ targets, hidePersonal })}>
            Paylaş
          </Button>
        </>
      }
    >
      {TARGETS.map(([t, l]) => (
        <label key={t} className={chk}>
          <input
            type="checkbox"
            checked={targets.includes(t)}
            onChange={(e) => setTargets((ts) => (e.target.checked ? [...ts, t] : ts.filter((x) => x !== t)))}
          />
          {l}
        </label>
      ))}
      <label className={chk}>
        <input type="checkbox" checked={hidePersonal} onChange={(e) => setHide(e.target.checked)} /> Sərnişinin şəxsi məlumatlarını gizlət
      </label>
    </Modal>
  );
}
