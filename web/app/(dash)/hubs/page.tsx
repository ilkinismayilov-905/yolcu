'use client';

import { Suspense, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AdminHub, CrowdLevel, OkResponse } from '@yolcu/shared/api';
import { Bar, Button, DemoBadge, ErrorState, PageHeader, Panel, Seg, Skeleton, Table, TD, loadErr } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { api } from '@/lib/api';
import { liveKeys, useAdminHubs } from '@/lib/live';
import { navItem } from '@/lib/nav';
import { useStream } from '@/lib/stream';

type Filter = 'all' | 'hub' | 'stop' | 'busy';
const FILTERS = [['all', 'Hamısı'], ['hub', 'Hub-lar'], ['stop', 'Dayanacaqlar'], ['busy', 'Yalnız sıx']] as const;
const isFilter = (v: string | null): v is Filter => FILTERS.some(([k]) => k === v);

// Mockup HCAM levels: bad "Çox sıx", warn "Sıx", ok "Normal"; colors = occupancy-bar tokens.
const LEVEL: Record<CrowdLevel, { label: string; tone: 'jam' | 'slow' | 'fluid' }> = {
  HIGH: { label: 'Çox sıx', tone: 'jam' },
  MEDIUM: { label: 'Sıx', tone: 'slow' },
  LOW: { label: 'Normal', tone: 'fluid' },
};
const lvl = (l: string) => LEVEL[l as CrowdLevel] ?? LEVEL.LOW;
const BG = { jam: 'bg-jam', slow: 'bg-slow', fluid: 'bg-fluid' } as const;
const TEXT = { jam: 'text-jam', slow: 'text-slow', fluid: 'text-fluid' } as const;
const signed = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${-n}` : '0');
const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');

export default function Page() {
  // useSearchParams needs a Suspense boundary for the static export.
  return (
    <Suspense>
      <Hubs />
    </Suspense>
  );
}

function Hubs() {
  const qc = useQueryClient();
  const sp = useSearchParams();
  const router = useRouter();
  const path = usePathname();
  const f = sp.get('f');
  const filter: Filter = isFilter(f) ? f : 'all';
  const setFilter = (v: Filter) => router.replace(v === 'all' ? path : `${path}?f=${v}`, { scroll: false });

  const hubs = useAdminHubs();
  useStream({
    hello: () => void qc.invalidateQueries({ queryKey: liveKeys.adminHubs }),
    // Counts every 5 s by SSE; level/recommendation stay from the last GET (server-side rule).
    'hub.counts': (d) => {
      const by = new Map(d.map((c) => [c.hubId, c]));
      qc.setQueryData<AdminHub[]>(liveKeys.adminHubs, (old) =>
        old?.map((h) => {
          const c = by.get(h.id);
          return c ? { ...h, count: c.count, trend10: c.trend, forecast15: c.forecast15 } : h;
        }),
      );
    },
  });

  const list = hubs.data ?? [];
  const cams = list.filter((h) => filter === 'all' || (filter === 'busy' ? h.level !== 'LOW' : h.kind === (filter === 'hub' ? 'HUB' : 'STOP')));
  const rows = [...list].sort((a, b) => b.count / b.capacity - a.count / a.capacity);

  return (
    <>
      <PageHeader
        title="Hub kameraları"
        desc={navItem('/hubs').desc}
        actions={<><DemoBadge /><Seg options={FILTERS} value={filter} onChange={setFilter} label="Kamera filtri" /></>}
      />

      {hubs.isPending ? (
        <div role="status" aria-label="Yüklənir" className="grid grid-cols-3 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-[268px] rounded-[14px]" />)}
        </div>
      ) : hubs.isError ? (
        <Panel>
          <ErrorState message={loadErr('Kameraları', hubs.error)} onRetry={() => void hubs.refetch()} />
        </Panel>
      ) : cams.length === 0 ? (
        <Panel><p className="m-0 text-center text-ink-2">{list.length ? 'Bu filtrə uyğun kamera yoxdur. Filtri dəyişin.' : 'Hələ kamera yoxdur. Kamera datası gələndə burada görünəcək.'}</p></Panel>
      ) : (
        <div className="grid grid-cols-3 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
          {cams.map((h) => <CamCard key={h.id} h={h} />)}
        </div>
      )}

      <Panel title="Hub-larda sıxlıq və 15 dəqiqəlik proqnoz" meta="Kamera, avtobus doluluğu və Biletim.az gəlişləri əsasında" flush className="mt-4">
        <Table
          head={['Hub', 'İndi', 'Doluluq', '15 dəq sonra', 'Tövsiyə', '']}
          loading={hubs.isPending}
          error={hubs.isError ? loadErr('Hub-ları', hubs.error) : null}
          onRetry={() => void hubs.refetch()}
          isEmpty={rows.length === 0}
          empty="Hələ hub yoxdur. Kamera datası gələndə burada görünəcək."
        >
          {rows.map((h) => <HubRow key={h.id} h={h} />)}
        </Table>
      </Panel>

      <Panel title="Kamera rəqəmi qərara necə çevrilir" meta="28 May nümunəsi" className="mt-4">
        <div className="grid grid-cols-4 gap-3 max-[820px]:grid-cols-2">
          {[
            ['Hub kamerası', '28 May-da 46 nəfər gözləyir, son 10 dəqiqədə +18'],
            ['Avtobus kamerası', 'Növbəti 65 artıq 88% dolu gəlir'],
            ['AI qərarı', '65 intervalı 12 → 9 dəq, depodan 1 ehtiyat avtobus'],
            ['Sərnişinə', 'Tətbiqdə gözləyənlərə metro və ya 88 təklif olunur'],
          ].map(([t, s], i) => (
            <div key={t} className="flex flex-col gap-1 rounded-xl border border-line bg-surface-2 px-3.5 py-3">
              <div className="text-xs text-ink-3">Addım {i + 1}</div>
              <b>{t}</b>
              <span className="text-xs text-ink-2">{s}</span>
            </div>
          ))}
        </div>
      </Panel>
    </>
  );
}

function CamCard({ h }: { h: AdminHub }) {
  const l = lvl(h.level);
  return (
    <div className="overflow-hidden rounded-[14px] border border-line bg-surface shadow-card">
      <div className="relative">
        <Scene count={h.count} capacity={h.capacity} tone={l.tone} seed={[...h.camera].reduce((s, c) => s * 31 + c.charCodeAt(0), 7) % 233280} />
        <div className="absolute inset-x-3 top-2.5 flex justify-between text-xs text-on-color">
          <span className="flex items-center gap-1.5 opacity-90"><span className="size-[7px] rounded-full bg-bad" />{h.camera}</span>
          <span className="rounded-[20px] bg-on-color/15 px-2 py-0.5 font-semibold">{l.label}</span>
        </div>
      </div>
      <div className="grid grid-cols-[1fr_auto] items-center gap-x-2.5 gap-y-0.5 px-3.5 py-3">
        <b>{h.name}</b>
        <span className="col-start-2 row-span-2 row-start-1 text-right text-2xl leading-none font-bold tabular-nums">
          {h.count}<small className="ml-1 text-xs font-medium text-ink-3">nəfər</small>
        </span>
        <span className="text-xs text-ink-2">Orta gözləmə {h.avgWaitMin} dəq, son 10 dəq {signed(h.trend10)}</span>
      </div>
    </div>
  );
}

/** FAKE camera frame (mockup scene()): one figure per ~2 % fill, positions fixed per camera, frame color = level. */
function Scene({ count, capacity, tone, seed }: { count: number; capacity: number; tone: 'jam' | 'slow' | 'fluid'; seed: number }) {
  let r = seed;
  const rnd = () => (r = (r * 9301 + 49297) % 233280) / 233280;
  const n = Math.round(Math.min(1, capacity ? count / capacity : 0) * 44);
  const pts = Array.from({ length: n }, () => [20 + rnd() * 380, 104 + rnd() * 62] as const).sort((a, b) => a[1] - b[1]);
  return (
    <svg viewBox="0 0 420 220" className="block h-auto w-full" role="img" aria-label={`Kamera görüntüsü: ${count} nəfər`}>
      <rect width="420" height="220" fill="var(--cam-bg)" />
      <path d="M0 108 L420 92 L420 220 L0 220 Z" fill="var(--cam-floor)" />
      <path d="M0 200 L420 188" stroke="var(--cam-curb)" strokeWidth="3" />
      <g fill="var(--cam-pole)">
        <rect x="252" y="34" width="150" height="10" rx="3" />
        <rect x="260" y="44" width="4" height="62" />
        <rect x="390" y="44" width="4" height="58" />
      </g>
      <rect x="272" y="50" width="106" height="34" rx="4" fill="var(--cam-sign)" stroke="var(--cam-pole)" />
      {pts.map(([x, y], i) => {
        const k = 0.6 + ((y - 104) / 62) * 0.5;
        return (
          <g key={i}>
            <rect x={x - 6 * k} y={y - 30 * k} width={12 * k} height={30 * k} rx={5 * k} fill={`var(--cam-p${(i % 6) + 1})`} />
            <circle cx={x} cy={y - 35 * k} r={5.5 * k} fill="var(--cam-skin)" />
            <rect x={x - 9 * k} y={y - 43 * k} width={18 * k} height={46 * k} fill="none" stroke={`var(--${tone})`} strokeWidth="1.1" />
          </g>
        );
      })}
    </svg>
  );
}

function HubRow({ h }: { h: AdminHub }) {
  const qc = useQueryClient();
  const toast = useToast();
  // The server recomputes the recommendation after apply; only the applied text stays disabled.
  const [applied, setApplied] = useState<string | null>(null);
  const apply = useMutation({
    mutationFn: (_rec: string) => api.post<OkResponse>(`/admin/hubs/${encodeURIComponent(h.id)}/apply`),
    onSuccess: (_d, rec) => {
      setApplied(rec);
      toast(`${h.name}: tövsiyə tətbiq edildi`);
      void qc.invalidateQueries({ queryKey: liveKeys.adminHubs });
    },
    onError: (e) => toast(errText(e)),
  });
  const p = h.capacity ? Math.round((h.count / h.capacity) * 100) : 0;
  const t = lvl(h.level).tone;
  const up = h.forecast15 > h.count;
  return (
    <tr>
      <td className={TD}><b>{h.name}</b></td>
      <td className={TD}>{h.count} nəfər</td>
      <td className={TD}>
        <div className="flex items-center gap-2.5">
          <div className="w-[110px]"><Bar value={p} color={BG[t]} label="Doluluq" /></div>
          <span className={`font-semibold tabular-nums ${TEXT[t]}`}>{p}%</span>
        </div>
      </td>
      <td className={TD}>
        {h.forecast15} nəfər <span className={up ? 'text-bad' : 'text-ok'}>{up ? '↑' : '↓'}</span>
      </td>
      <td className={`${TD} text-xs whitespace-normal! text-ink-2`}>{h.recommendation || 'Dəyişiklik lazım deyil'}</td>
      <td className={TD}>
        {h.recommendation && (
          <Button sm disabled={applied === h.recommendation || apply.isPending} onClick={() => h.recommendation && apply.mutate(h.recommendation)}>
            {applied === h.recommendation ? 'Tətbiq edildi' : 'Tətbiq et'}
          </Button>
        )}
      </td>
    </tr>
  );
}
