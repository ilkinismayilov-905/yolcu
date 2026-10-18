'use client';

import { Suspense, useEffect, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import type { AutoModeRequest, BunchingCase, IntervalRequest, OccupancyBus, OkResponse, RouteOccupancy } from '@yolcu/shared/api';
import { simulate } from '@yolcu/shared/headway';
import { Bar, Button, DemoBadge, ErrorState, Kpi, SkeletonLines, loadErr, PageHeader, Panel, Plate, Status, Switch, Table, TD, TR_CLICK, fmtNum, type StatusKind } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { api } from '@/lib/api';
import { useKpis } from '@/lib/live';
import { navItem } from '@/lib/nav';
import { useStream } from '@/lib/stream';

const occKeys = {
  routes: ['admin', 'occupancy', 'routes'] as const,
  buses: (r: string) => ['admin', 'occupancy', 'buses', r] as const,
  bunching: ['admin', 'occupancy', 'bunching'] as const,
};
const IV_MIN = 6;
const IV_MAX = 15; // mockup slider range; the engine may recommend up to 16

const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');
const f1 = (n: number) => fmtNum(n, 1);
/** Mockup occColor(): >85 jam, >65 slow, else fluid. */
const occTone = (o: number) => (o > 85 ? 'jam' : o > 65 ? 'slow' : 'fluid');
const BG = { jam: 'bg-jam', slow: 'bg-slow', fluid: 'bg-fluid' } as const;
const TEXT = { jam: 'text-jam', slow: 'text-slow', fluid: 'text-fluid' } as const;
const FILL = { jam: 'fill-jam', slow: 'fill-slow', fluid: 'fill-fluid' } as const;
const signed = (d: number) => (d > 0 ? '+' : '−') + f1(Math.abs(d));

export default function Page() {
  // useSearchParams needs a Suspense boundary for the static export.
  return (
    <Suspense>
      <Occupancy />
    </Suspense>
  );
}

function Occupancy() {
  const qc = useQueryClient();
  const toast = useToast();
  const sp = useSearchParams();
  const router = useRouter();
  const path = usePathname();

  // route.updated only fires on interval changes; occupancy itself drifts with the sim, so poll too
  const routes = useQuery({ queryKey: occKeys.routes, queryFn: () => api.get<RouteOccupancy[]>('/admin/occupancy/routes'), refetchInterval: (q) => (q.state.data ? 5000 : false) }); // no data yet: stop polling so the error state stays (retry button / SSE hello refetch)
  const bunching = useQuery({ queryKey: occKeys.bunching, queryFn: () => api.get<BunchingCase[]>('/admin/occupancy/bunching'), refetchInterval: 30_000 });
  const kpis = useKpis();

  useStream({
    // (re)connected: refetch what failed or may have been missed while the stream was down.
    hello: () => void qc.invalidateQueries({ queryKey: ['admin', 'occupancy'] }),
    'route.updated': (r) => qc.setQueryData<RouteOccupancy[]>(occKeys.routes, (old) => old?.map((x) => (x.route === r.route ? r : x))),
  });

  const list = routes.data ?? [];
  const sel = list.find((r) => r.route === sp.get('route')) ?? list[0];
  const select = (route: string) => router.replace(`${path}?route=${encodeURIComponent(route)}`, { scroll: false });
  // Pin the default (first row) into the URL so the selection doesn't jump when the list order changes.
  const pin = !sp.get('route') && sel ? sel.route : null;
  useEffect(() => {
    if (pin) router.replace(`${path}?route=${encodeURIComponent(pin)}`, { scroll: false });
  }, [pin, path, router]);

  const auto = list.some((r) => r.autoMode);
  const autoMut = useMutation({
    mutationFn: (enabled: boolean) => api.put<OkResponse>('/admin/occupancy/auto', { enabled } satisfies AutoModeRequest),
    onSuccess: (_d, enabled) => {
      qc.setQueryData<RouteOccupancy[]>(occKeys.routes, (old) => old?.map((r) => ({ ...r, autoMode: enabled })));
      void qc.invalidateQueries({ queryKey: occKeys.routes });
      toast(enabled ? 'Avtomatik rejim açıldı: AI intervalı 6–15 dəq aralığında özü tənzimləyəcək' : 'Avtomatik rejim söndürüldü');
    },
    onError: (e) => toast(errText(e)),
  });

  // KPIs derived from the routes list, so route.updated refreshes them without a refetch.
  const buses = list.reduce((s, r) => s + r.busCount, 0);
  const avgOcc = buses ? Math.round(list.reduce((s, r) => s + r.avgOccupancy * r.busCount, 0) / buses) : null;
  const satGain = buses ? list.reduce((s, r) => s + (r.satAfter - r.satNow) * r.busCount, 0) / buses : null;
  const top = [...list].sort((a, b) => b.avgOccupancy - a.avgOccupancy).slice(0, 3).map((r) => r.route);
  const topText = top.length > 1 ? `${top.slice(0, -1).join(', ')} və ${top.at(-1)}` : top.join('');
  const dash = routes.isPending ? '…' : '—';

  return (
    <>
      <PageHeader
        title="Avtobus doluluğu"
        desc={navItem('/occupancy').desc}
        actions={
          <>
          <DemoBadge />
          <Switch on={auto} disabled={routes.isPending || autoMut.isPending} onChange={(v) => autoMut.mutate(v)}>
            Avtomatik tənzimləmə
          </Switch>
          </>
        }
      />

      <div className="mb-4 grid grid-cols-4 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
        <Kpi label="Orta doluluq, pik saat" value={avgOcc === null ? dash : `${avgOcc}%`} sub="08:00–09:30" />
        <Kpi label="90%-dən çox dolu avtobus" value={kpis.data ? fmtNum(kpis.data.overfullBuses) : kpis.isError ? '—' : '…'} sub={top.length ? `Ən çox ${topText}` : undefined} subTone="bad" />
        <Kpi label="Topa yığılma" value={bunching.data ? bunching.data.length : bunching.isError ? '—' : '…'} sub="Eyni marşrutda 3 dəqiqədən yaxın" />
        <Kpi label="Tövsiyələr tətbiq olunarsa" value={satGain === null ? dash : Math.abs(satGain) < 0.05 ? '0' : signed(satGain)} tone={satGain !== null && satGain >= 0.05 ? 'ok' : undefined} sub="Şəbəkə üzrə orta məmnunluq artımı" />
      </div>

      <div className="grid grid-cols-2 gap-4 max-[820px]:grid-cols-1">
        <Panel title="Marşrutlar" meta="Sətri seçin" flush className="[&_td]:px-3 [&_th]:px-3">
          <Table
            head={['Marşrut', 'Avtobus', 'Doluluq', 'İnterval', 'Tövsiyə', 'Məmnunluq']}
            loading={routes.isPending}
            error={routes.isError && !routes.data ? loadErr('Marşrutları', routes.error) : null}
            onRetry={() => void routes.refetch()}
            isEmpty={list.length === 0}
            empty="Xətdə marşrut yoxdur. Simulyator işə düşəndə burada görünəcək."
          >
            {list.map((r) => (
              <RouteRow key={r.route} r={r} selected={r.route === sel?.route} onSelect={() => select(r.route)} />
            ))}
          </Table>
        </Panel>
        {sel ? <BusList route={sel.route} /> : <Panel title="Xətdəki avtobuslar">{routes.isPending ? <SkeletonLines /> : <p className="m-0 text-ink-3">{routes.isError ? 'Marşrutlar yüklənmədi.' : list.length ? 'Marşrut seçin' : 'Xətdə marşrut yoxdur.'}</p>}</Panel>}
      </div>

      <div className="mt-4 grid grid-cols-2 gap-4 max-[820px]:grid-cols-1">
        {sel ? <IntervalPanel key={sel.route} r={sel} /> : <Panel title="İnterval">{routes.isPending ? <SkeletonLines /> : <p className="m-0 text-ink-3">{routes.isError ? 'Marşrutlar yüklənmədi.' : list.length ? 'Marşrut seçin' : 'Xətdə marşrut yoxdur.'}</p>}</Panel>}
        <BunchingPanel q={bunching} route={sel?.route} />
      </div>

      <SatChart />
    </>
  );
}

function RouteRow({ r, selected, onSelect }: { r: RouteOccupancy; selected: boolean; onSelect: () => void }) {
  const t = occTone(r.avgOccupancy);
  const d = r.satAfter - r.satNow;
  const sel = selected ? '[&>td]:bg-primary-soft hover:[&>td]:bg-primary-soft' : '';
  return (
    <tr
      className={`${TR_CLICK} ${sel}`}
      tabIndex={0}
      aria-selected={selected}
      onClick={onSelect}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), onSelect())}
    >
      <td className={TD}><Plate route={r.route} /></td>
      <td className={TD}>{r.busCount}</td>
      <td className={TD}>
        <div className="flex items-center gap-2.5">
          <Bar value={r.avgOccupancy} color={BG[t]} label="Doluluq" />
          <span className={`font-semibold tabular-nums ${TEXT[t]}`}>{r.avgOccupancy}%</span>
        </div>
      </td>
      <td className={TD}>{r.intervalMin} dəq</td>
      <td className={TD}>
        {r.recommendedMin === r.intervalMin ? (
          <Status kind="ok">Uyğundur</Status>
        ) : r.autoMode ? (
          <Status kind="info">{r.intervalMin} → {r.recommendedMin} dəq, tətbiq edildi</Status>
        ) : (
          <b>{r.intervalMin} → {r.recommendedMin} dəq</b>
        )}
      </td>
      <td className={TD}>{Math.abs(d) < 0.05 ? <span className="text-ink-3">0</span> : <span className={`font-semibold tabular-nums ${d > 0 ? 'text-ok' : 'text-bad'}`}>{signed(d)}</span>}</td>
    </tr>
  );
}

function BusList({ route }: { route: string }) {
  // shortcut: polled every 3 s instead of merging bus.positions (no per-bus `near`/passengers in that event).
  const q = useQuery({ queryKey: occKeys.buses(route), queryFn: () => api.get<OccupancyBus[]>(`/admin/occupancy/routes/${encodeURIComponent(route)}/buses`), refetchInterval: (q) => (q.state.data ? 3000 : false) });
  return (
    <Panel title={`${route}: xətdəki avtobuslar`} meta="Hər 30 saniyə" flush>
      {q.isPending ? (
        <SkeletonLines rows={5} className="p-4" />
      ) : !q.data ? (
        <ErrorState className="p-10" message={loadErr('Avtobusları', q.error)} onRetry={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <p className="m-0 p-10 text-center text-ink-2">Bu marşrutda xətdə avtobus yoxdur. Başqa marşrut seçin.</p>
      ) : (
        q.data.map((b, i) => {
          const o = b.capacity ? b.occupancy / b.capacity : 0;
          const [kind, text]: [StatusKind, string] = o > 0.85 ? ['bad', 'Dolu'] : o > 0.65 ? ['warn', 'Ayaq üstə yer var'] : ['ok', 'Oturacaq var'];
          return (
            <div key={b.bort} className="grid grid-cols-[104px_1fr_auto] items-center gap-3 border-b border-line px-4 py-2.5 last:border-b-0">
              <Salon occupancy={b.occupancy} capacity={b.capacity} seed={i * 53 + 7} />
              <div>
                <b>{b.bort}</b> <span className="ml-1 align-middle [&>span]:h-[18px] [&>span]:min-w-6 [&>span]:text-[10px]"><Plate route={route} kind={b.type === 'ELECTRIC' ? 'e' : 'd'} /></span>
                <div className="text-xs text-ink-3">{b.near} yaxınlığında</div>
              </div>
              <div className="text-right">
                <b>{b.occupancy}</b>
                <span className="text-ink-3"> / {b.capacity}</span>
                <div><Status kind={kind}>{text}</Status></div>
              </div>
            </div>
          );
        })
      )}
    </Panel>
  );
}

/** Salon miniature (mockup thumb()): seats + one dot per ~3 % occupancy, deterministic per seed. */
function Salon({ occupancy, capacity, seed }: { occupancy: number; capacity: number; seed: number }) {
  let r = seed;
  const rnd = () => (r = (r * 9301 + 49297) % 233280) / 233280;
  const o = capacity ? occupancy / capacity : 0;
  const fill = FILL[o > 0.85 ? 'jam' : o > 0.65 ? 'slow' : 'fluid'];
  const dots = Array.from({ length: Math.round(Math.min(1, o) * 28) }, () => [12 + rnd() * 82, 11 + rnd() * 22]);
  return (
    <svg viewBox="0 0 104 44" className="block h-11 w-[104px] rounded-lg bg-surface-2" aria-label={`${occupancy} / ${capacity} sərnişin`} role="img">
      <rect x="4" y="5" width="96" height="34" rx="8" className="fill-surface-3 stroke-line" />
      {Array.from({ length: 7 }, (_, i) => (
        <g key={i} className="fill-line">
          <rect x={12 + i * 12} y="9" width="8" height="5" rx="2" />
          <rect x={12 + i * 12} y="30" width="8" height="5" rx="2" />
        </g>
      ))}
      {dots.map(([x, y], i) => <circle key={i} cx={x} cy={y} r="2.4" className={fill} />)}
    </svg>
  );
}

function IntervalPanel({ r }: { r: RouteOccupancy }) {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const [iv, setIv] = useState(() => Math.min(IV_MAX, Math.max(IV_MIN, r.recommendedMin)));
  // Instant, no network: same formula as the backend IntervalEngine.
  const c = simulate(r.avgOccupancy, r.intervalMin, r.busCount, iv);
  const ex = c.busesNeeded - r.busCount;
  const note =
    ex > 0 ? `${ex} əlavə avtobus lazımdır. Təklif: 33-dən ${Math.min(2, ex)}${ex > 2 ? `, depodan ${ex - 2}` : ''}.` : ex < 0 ? `${-ex} avtobus azad olur, tələbatı yüksək marşruta verilə bilər.` : 'Avtobus sayı dəyişmir.';

  const apply = useMutation({
    mutationFn: ({ v }: { v: number; old: number }) => api.post<RouteOccupancy>(`/admin/occupancy/routes/${encodeURIComponent(r.route)}/interval`, { intervalMin: v } satisfies IntervalRequest),
    onSuccess: (row, { v, old }) => {
      qc.setQueryData<RouteOccupancy[]>(occKeys.routes, (prev) => prev?.map((x) => (x.route === row.route ? row : x)));
      toast(`${row.route}: interval ${old} → ${v} dəq. Operatora və sürücülərə göndərildi`);
    },
    onError: (e) => toast(errText(e)),
  });

  return (
    <Panel title={`${r.route}: interval`} actions={<Status kind="info">AI tövsiyəsi: {r.recommendedMin} dəq</Status>}>
      <input
        type="range"
        min={IV_MIN}
        max={IV_MAX}
        value={iv}
        onChange={(e) => setIv(Number(e.target.value))}
        aria-label="İnterval, dəqiqə"
        className="w-full accent-primary"
      />
      <div className="flex justify-between text-xs text-ink-3">
        <span>{IV_MIN} dəq</span>
        <span>Hazırda <b className="text-ink-2">{r.intervalMin}</b> dəq</span>
        <span>{IV_MAX} dəq</span>
      </div>
      <div className="mt-3.5 grid grid-cols-4 gap-3 [&_b]:block [&_b]:text-xl [&_b]:font-bold [&_b]:tracking-[-.01em] [&_span]:text-xs [&_span]:text-ink-3" data-testid="iv-stats">
        <div><b className={TEXT[occTone(c.occupancy)]}>{c.occupancy}%</b><span>Gözlənilən doluluq</span></div>
        <div><b>{f1(c.waitMin)} dəq</b><span>Orta gözləmə</span></div>
        <div><b>{c.busesNeeded}</b><span>Lazım olan avtobus</span></div>
        <div><b>{f1(c.satAfter)}</b><span>Gözlənilən məmnunluq</span></div>
      </div>
      <p className="my-3 text-xs text-ink-2">{note}</p>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="p"
          disabled={apply.isPending}
          onClick={() => (iv === r.intervalMin ? toast(`İnterval artıq ${iv} dəqiqədir`) : apply.mutate({ v: iv, old: r.intervalMin }))}
        >
          İntervalı tətbiq et
        </Button>
        <Button onClick={() => router.push('/split')}>Əlavə avtobusu haradan götürək?</Button>
      </div>
    </Panel>
  );
}

const STOPN = ['Avtovağzal', 'Elmlər', 'Füzuli küç.', '28 May', 'Gənclik', 'Xoyski pr.', 'Nərimanov'];

function BunchingPanel({ q, route }: { q: UseQueryResult<BunchingCase[]>; route?: string }) {
  const toast = useToast();
  const [sent, setSent] = useState<ReadonlySet<string>>(new Set());
  const b = q.data?.find((x) => x.route === route) ?? q.data?.[0];
  const msg = useMutation({
    mutationFn: (c: BunchingCase) => api.post<OkResponse>(`/admin/occupancy/bunching/${encodeURIComponent(c.route)}/message`),
    onSuccess: (_d, c) => {
      setSent((s) => new Set(s).add(`${c.route}:${c.front.bort}`));
      toast(`${c.front.bort} sürücüsünə: ${c.near} dayanacağında 3 dəqiqə gözləyin`);
    },
    onError: (e) => toast(errText(e)),
  });

  if (!b)
    return (
      <Panel title="Topa yığılma">
        {q.isPending ? (
          <SkeletonLines />
        ) : q.isError ? (
          <ErrorState message={loadErr('Topa yığılma xəbərdarlıqlarını', q.error)} onRetry={() => void q.refetch()} />
        ) : (
          <p className="m-0 text-ink-3">Hazırda topa yığılma yoxdur</p>
        )}
      </Panel>
    );

  const done = sent.has(`${b.route}:${b.front.bort}`);
  const bus = (x: number, electric: boolean, n: number) => (
    <g transform={`translate(${x} 22)`}>
      <rect x="-17" y="-11" width="34" height="22" rx="6" strokeWidth="2" className={`stroke-surface ${electric ? 'fill-bus-e' : 'fill-bus-d'}`} />
      <text textAnchor="middle" y="4.5" fontSize="11" fontWeight="800" className="fill-on-color">{n}</text>
    </g>
  );
  return (
    <Panel title={`Topa yığılma: ${b.route}`} actions={<Status kind="warn">İki avtobus arası {b.gapMin} dəq</Status>}>
      <svg viewBox="0 0 600 92" className="block h-[92px] w-full" role="img" aria-label={`${b.front.bort} və ${b.back.bort} arası ${b.gapMin} dəq`}>
        <line x1="46" y1="46" x2="554" y2="46" strokeWidth="5" strokeLinecap="round" opacity=".45" className="stroke-bus-e" />
        {[46, 130, 214, 300, 386, 470, 554].map((x, i) => (
          <g key={x}>
            <circle cx={x} cy="46" r="6" strokeWidth="2.5" className="fill-surface stroke-ink-2" />
            <text x={x} y="82" textAnchor="middle" fontSize="11" className="fill-ink-3">{i === 3 ? b.near : STOPN[i]}</text>
          </g>
        ))}
        <rect x="276" y="3" width="82" height="38" rx="9" fill="none" strokeDasharray="4 3" className="stroke-warn" />
        {bus(150, true, 79)}
        {bus(296, true, b.front.occupancy)}
        {bus(338, false, b.back.occupancy)}
        {bus(470, true, 88)}
      </svg>
      <p className="mt-2 mb-3 text-xs text-ink-2">
        {b.front.bort} doludur ({b.front.occupancy} nəfər), arxasındakı {b.back.bort} isə yarıboşdur ({b.back.occupancy} nəfər). Öndəki 3 dəqiqə saxlanılsa, interval bərabərləşir və sonrakı dayanacaqlarda gözləmə 5 dəqiqə azalır.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button variant="p" disabled={done || msg.isPending} onClick={() => msg.mutate(b)}>{done ? 'Mesaj göndərildi' : 'Sürücüyə mesaj göndər'}</Button>
        <Button variant="q" onClick={() => toast('Xəbərdarlıq bağlandı')}>Bağla</Button>
      </div>
    </Panel>
  );
}

// FAKE (docs/01_SCOPE): static satisfaction by occupancy band, mockup #satChart.
const SAT_BANDS: [string, number][] = [['50%-dən az', 4.6], ['50–65%', 4.5], ['65–75%', 4.3], ['75–85%', 4.0], ['85–100%', 3.5], ['100%+', 2.9]];

function SatChart() {
  const H = 190, p = 28, bw = 84;
  return (
    <Panel title="Doluluq və məmnunluq" meta="Son 30 gün, 48 min rəy" actions={<DemoBadge />} className="mt-4">
      <svg viewBox="0 0 900 190" className="block h-[190px] w-full" role="img" aria-label="Doluluq aralığına görə orta rəy">
        <g fontSize="12" className="fill-ink-3">
          {SAT_BANDS.map(([label, v], i) => {
            const x = p + 52 + i * 136, h = (v / 5) * (H - p * 2);
            const fill = v >= 4.3 ? 'fill-fluid' : v >= 3.8 ? 'fill-slow' : 'fill-jam';
            return (
              <g key={label}>
                <rect x={x} y={H - p - h} width={bw} height={h} rx="6" opacity=".85" className={fill} />
                <text x={x + bw / 2} y={H - p - h - 6} textAnchor="middle" fontWeight="700" className="fill-ink">{f1(v)}</text>
                <text x={x + bw / 2} y={H - 8} textAnchor="middle">{label}</text>
              </g>
            );
          })}
        </g>
      </svg>
      <p className="mt-1.5 mb-0 text-xs text-ink-2">Avtobus 85%-dən çox dolu olanda orta rəy 3,5-ə düşür. AI doluluğu 65–75% aralığında saxlamağa çalışır.</p>
    </Panel>
  );
}
