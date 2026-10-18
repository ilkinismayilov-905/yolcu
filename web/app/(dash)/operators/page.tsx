'use client';

import { Suspense, useEffect, useRef, useState, type ReactNode } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { BusDetail, BusType, FleetBus, FleetBusStatus, OperatorStats, PageResponse } from '@yolcu/shared/api';
import { Bar, Button, ErrorState, PageHeader, Skeleton, SkeletonLines, loadErr, Pager, Panel, Plate, Seg, Status, Table, TD, TR_CLICK, fmtNum, type StatusKind } from '@/components/ui';
import { Drawer } from '@/components/Overlay';
import { api } from '@/lib/api';
import { navItem } from '@/lib/nav';
import { reportTime, statusOf } from '@/lib/reports';

const PER_PAGE = 12;
const STATUS_SEG = [
  ['', 'Hamısı'],
  ['LINE', 'Xətdə'],
  ['DEPOT', 'Depoda'],
  ['REPAIR', 'Təmirdə'],
] as const satisfies readonly (readonly [FleetBusStatus | '', string])[];
const FST: Record<FleetBusStatus, [string, StatusKind]> = { LINE: ['Xətdə', 'ok'], DEPOT: ['Depoda', 'neutral'], REPAIR: ['Təmirdə', 'warn'] };
const TYPE: Record<BusType, string> = { ELECTRIC: 'Elektrik', DIESEL: 'Dizel' };

const SELECT = 'h-9 rounded-[9px] border border-line bg-surface px-3 text-ink focus:border-primary';
const occColor = (o: number) => (o > 85 ? 'bg-jam' : o > 65 ? 'bg-slow' : 'bg-fluid');
const punctKind = (p: number): StatusKind => (p >= 85 ? 'ok' : p >= 75 ? 'warn' : 'bad');
const rating = (r: number | null) => (r == null ? '—' : `★ ${fmtNum(r, 1)}`);

interface Filters {
  operator: string;
  status: FleetBusStatus | '';
  type: BusType | '';
  q: string;
  page: number;
}

export default function Page() {
  // useSearchParams needs a Suspense boundary for the static export.
  return (
    <Suspense>
      <Operators />
    </Suspense>
  );
}

function useFilters(): [Filters, (patch: Partial<Filters>) => void] {
  const sp = useSearchParams();
  const router = useRouter();
  const path = usePathname();
  const st = sp.get('status') ?? '';
  const ty = sp.get('type') ?? '';
  const f: Filters = {
    operator: sp.get('operator') ?? '',
    status: Object.hasOwn(FST, st) ? (st as FleetBusStatus) : '',
    type: Object.hasOwn(TYPE, ty) ? (ty as BusType) : '',
    q: sp.get('q') ?? '',
    page: Math.max(0, Number(sp.get('page')) || 0),
  };
  const set = (patch: Partial<Filters>) => {
    const n = { ...f, page: 0, ...patch };
    const p = new URLSearchParams();
    if (n.operator) p.set('operator', n.operator);
    if (n.status) p.set('status', n.status);
    if (n.type) p.set('type', n.type);
    if (n.q) p.set('q', n.q);
    if (n.page) p.set('page', String(n.page));
    router.replace(p.size ? `${path}?${p}` : path, { scroll: false });
  };
  return [f, set];
}

function busesPath(f: Filters) {
  const p = new URLSearchParams();
  if (f.operator) p.set('operator', f.operator);
  if (f.status) p.set('status', f.status);
  if (f.type) p.set('type', f.type);
  if (f.q.trim()) p.set('q', f.q.trim());
  p.set('page', String(f.page));
  p.set('size', String(PER_PAGE));
  return `/admin/buses?${p}`;
}

function Operators() {
  const [f, setF] = useFilters();
  const [q, setQ] = useState(f.q);
  const [open, setOpen] = useState<string | null>(null);
  const autoOpened = useRef('');

  useEffect(() => setQ(f.q), [f.q]); // topbar search changes the URL
  useEffect(() => {
    if (q === f.q) return;
    const t = setTimeout(() => setF({ q }), 250);
    return () => clearTimeout(t);
  });

  const ops = useQuery({ queryKey: ['operators'], queryFn: () => api.get<OperatorStats[]>('/admin/operators') });
  const list = useQuery({
    queryKey: ['buses', f],
    queryFn: () => api.get<PageResponse<FleetBus>>(busesPath(f)),
    placeholderData: keepPreviousData,
  });

  // ?q= equal to a bort (e.g. from the topbar search) opens its drawer once per q.
  const exact = list.data?.items.find((b) => b.bort.toLowerCase() === f.q.trim().toLowerCase());
  useEffect(() => {
    if (exact && autoOpened.current !== f.q) {
      autoOpened.current = f.q;
      setOpen(exact.bort);
    }
  }, [exact, f.q]);

  const total = list.data?.total ?? 0;
  const pages = Math.max(1, Math.ceil(total / PER_PAGE));
  const opName = ops.data?.find((o) => o.code === f.operator)?.name;
  const toggleOp = (code: string) => setF({ operator: f.operator === code ? '' : code });

  return (
    <>
      <PageHeader title="Operatorlar və avtopark" desc={navItem('/operators').desc} />
      <div className="mb-4 grid grid-cols-4 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
        {ops.isError ? (
          <Panel className="col-span-full">
            <ErrorState message={loadErr('Operatorları', ops.error)} onRetry={() => void ops.refetch()} />
          </Panel>
        ) : !ops.data ? (
          [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[150px] rounded-[14px]" />)
        ) : !ops.data.length ? (
          <Panel className="col-span-full text-ink-3">Hələ operator yoxdur. Operatorlar seed-dən yüklənəndə burada görünəcək.</Panel>
        ) : (
          ops.data.map((o) => <OperatorCard key={o.code} o={o} on={f.operator === o.code} onClick={() => toggleOp(o.code)} />)
        )}
      </div>
      <Panel flush>
        <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3">
          <input
            type="search"
            aria-label="Avtobus axtarışı"
            className={`${SELECT} w-[240px]`}
            placeholder="Bort nömrəsi və ya marşrut"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <Seg label="Status" options={STATUS_SEG} value={f.status} onChange={(status) => setF({ status })} />
          <select aria-label="Növ" className={SELECT} value={f.type} onChange={(e) => setF({ type: e.target.value as Filters['type'] })}>
            <option value="">Bütün növlər</option>
            <option value="ELECTRIC">Elektrik</option>
            <option value="DIESEL">Dizel</option>
          </select>
          <span className="ml-auto text-[13px] text-ink-3">
            {list.data ? `${total} avtobus göstərilir${opName ? `, ${opName}` : ''}` : ''}
          </span>
        </div>
        <Table
          head={['Bort', 'Operator', 'Marşrut', 'Növ və model', 'Status', 'Doluluq', 'Sürücü rəyi']}
          loading={list.isPending}
          error={list.isError ? loadErr('Avtobusları', list.error) : null}
          onRetry={() => void list.refetch()}
          isEmpty={!list.data?.items.length}
          empty="Avtobus tapılmadı. Bort nömrəsini yoxlayın və ya filtrləri sıfırlayın."
        >
          {list.data?.items.map((b) => {
            const [label, kind] = FST[b.status];
            return (
              <tr
                key={b.bort}
                tabIndex={0}
                className={`${TR_CLICK} ${open === b.bort ? '[&>td]:bg-primary-soft' : ''}`}
                onClick={() => setOpen(b.bort)}
                onKeyDown={(e) => e.key === 'Enter' && setOpen(b.bort)}
              >
                <td className={TD}><b>{b.bort}</b></td>
                <td className={`${TD} text-ink-2`}>{b.operator}</td>
                <td className={TD}><Plate route={b.route} kind={b.type === 'ELECTRIC' ? 'e' : 'd'} /></td>
                <td className={TD}>
                  {TYPE[b.type]}
                  <div className="text-[13px] text-ink-3">{b.model}</div>
                </td>
                <td className={TD}><Status kind={kind}>{label}</Status></td>
                <td className={TD}><Occ value={b.occupancy} /></td>
                <td className={TD}>{rating(b.driverRating)}</td>
              </tr>
            );
          })}
        </Table>
        <Pager
          page={Math.min(f.page, pages - 1)}
          pages={pages}
          onPage={(page) => setF({ page })}
          extra={f.operator && <Button sm variant="q" onClick={() => setF({ operator: '' })}>Bütün operatorlar</Button>}
        />
      </Panel>
      <BusDrawer bort={open} onClose={() => setOpen(null)} />
    </>
  );
}

function OperatorCard({ o, on, onClick }: { o: OperatorStats; on: boolean; onClick: () => void }) {
  const stat = (v: ReactNode, l: string) => (
    <div>
      <b className="block text-base">{v}</b>
      <span className="text-[11px] text-ink-3">{l}</span>
    </div>
  );
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`grid cursor-pointer gap-2.5 rounded-[14px] border bg-surface px-4 py-3.5 text-left shadow-card ${
        on ? 'border-primary ring-3 ring-primary-soft' : 'border-line'
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-[15px] font-bold">{o.name}</span>
        <Status kind={punctKind(o.punctuality)}>{o.punctuality}% dəqiq</Status>
      </div>
      <div className="grid grid-cols-3 gap-1.5">
        {stat(o.onLine, `xətdə / ${o.fleetTotal}`)}
        {stat(rating(o.avgRating), 'orta rəy')}
        {stat(fmtNum(o.complaintsPer10k, 1), 'şikayət / 10 min')}
      </div>
      <div className="text-[13px] text-ink-3">
        {o.routes.length} marşrut: {o.routes.slice(0, 6).join(', ')}
        {o.routes.length > 6 ? '…' : ''}
      </div>
    </button>
  );
}

function Occ({ value }: { value: number | null }) {
  if (value == null) return <span className="text-ink-3">—</span>;
  return (
    <div className="flex items-center gap-2">
      <div className="w-[70px]"><Bar value={value} color={occColor(value)} label="Doluluq" /></div>
      <span className="text-[13px] tabular-nums">{value}%</span>
    </div>
  );
}

function BusDrawer({ bort, onClose }: { bort: string | null; onClose: () => void }) {
  const router = useRouter();
  // Keep the last bus while the drawer slides out.
  const [last, setLast] = useState(bort);
  if (bort && bort !== last) setLast(bort);
  const detail = useQuery({
    queryKey: ['buses', 'detail', last],
    queryFn: () => api.get<BusDetail>(`/admin/buses/${encodeURIComponent(last ?? '')}`),
    enabled: !!bort,
  });
  const d = detail.data;
  const [label, kind] = d ? FST[d.status] : ['', 'neutral' as StatusKind];

  return (
    <Drawer open={!!bort} onClose={onClose} meta={d?.operator ?? ' '} title={last ?? ''} extra={d && <Status kind={kind}>{label}</Status>}>
      {detail.isError ? (
        <ErrorState message={loadErr('Avtobusu', detail.error)} onRetry={() => void detail.refetch()} />
      ) : !d ? (
        <SkeletonLines rows={6} />
      ) : (
        <>
          <dl className="m-0 grid grid-cols-[130px_1fr] gap-x-3 gap-y-2 text-[13px] [&_dd]:m-0 [&_dd]:font-medium [&_dt]:text-ink-3">
            <dt>Marşrut</dt><dd><Plate route={d.route} kind={d.type === 'ELECTRIC' ? 'e' : 'd'} /></dd>
            <dt>Növ və model</dt><dd>{TYPE[d.type]}, {d.model}</dd>
            <dt>Tutum</dt><dd>{d.capacity} nəfər</dd>
            <dt>Sürücü rəyi</dt><dd>{rating(d.driverRating)}</dd>
            <dt>Hazırkı doluluq</dt><dd><Occ value={d.occupancy} /></dd>
          </dl>
          <Section title="Son şikayətlər">
            {d.reports.length ? (
              d.reports.map((r) => {
                const [sl, sk] = statusOf(r);
                return (
                  <button
                    key={r.code}
                    type="button"
                    onClick={() => router.push(`/reports?q=${encodeURIComponent(r.code)}`)}
                    className="flex w-full cursor-pointer items-start gap-3 border-b border-line py-2.5 text-left last:border-b-0 hover:bg-surface-2"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px] text-ink-3">#{r.code}, {reportTime(r.createdAt)}</div>
                      <b className="text-[13px]">{r.category}</b>
                      <div className="truncate text-[13px] text-ink-3">{r.textPreview}</div>
                    </div>
                    <Status kind={sk}>{sl}</Status>
                  </button>
                );
              })
            ) : (
              <div className="text-[13px] text-ink-3">Bu avtobus üçün şikayət yoxdur.</div>
            )}
          </Section>
          <Section title="Son rəylər">
            {d.reviews.length ? (
              d.reviews.map((v, i) => (
                <div key={i} className="border-b border-line py-2.5 text-[13px] last:border-b-0">
                  <div className="flex gap-3">
                    <span>Sürücü <b>★ {v.driverRating}</b></span>
                    <span>Yol <b>★ {v.roadRating}</b></span>
                    <span>Avtobus <b>★ {v.busRating}</b></span>
                    <span className="ml-auto text-ink-3">{reportTime(v.createdAt)}</span>
                  </div>
                  {!!v.tags.length && <div className="mt-1 text-ink-2">{v.tags.join(', ')}</div>}
                  {v.comment && <div className="mt-1 text-ink-3">“{v.comment}”</div>}
                </div>
              ))
            ) : (
              <div className="text-[13px] text-ink-3">Hələ rəy yoxdur.</div>
            )}
          </Section>
        </>
      )}
    </Drawer>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <div className="mb-1 text-[13px] text-ink-3">{title}</div>
      {children}
    </div>
  );
}
