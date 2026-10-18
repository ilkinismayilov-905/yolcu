'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import type { Alert, DemoResponse } from '@yolcu/shared/api';
import { HubProposalDrawer } from '@/components/HubProposalDrawer';
import { useToast } from '@/components/Toast';
import { Button, DemoBadge, ErrorState, Kpi, SkeletonLines, loadErr, PageHeader, Panel, Toggle, fmtNum } from '@/components/ui';
import type { LayerKey } from '@/components/LiveMap';
import { FEED_CATEGORIES } from '@/lib/feed';
import { api } from '@/lib/api';
import { useFeed, useHubs, useKpis, useLiveStream, useProposals } from '@/lib/live';
import { useStream } from '@/lib/stream';
import { usePrefs } from '@/lib/prefs';
import { navItem } from '@/lib/nav';
import { clockTime } from '@/lib/reports';

// MapLibre needs window: client-only (also keeps it out of the static export prerender).
const LiveMap = dynamic(() => import('@/components/LiveMap'), { ssr: false, loading: () => <div className="size-full bg-surface-2" /> });

const LAYERS: [LayerKey, string, boolean][] = [
  ['traffic', 'Tıxac', true],
  ['crowd', 'Hub sıxlığı', true],
  ['buses', 'Avtobuslar', true],
  ['routes', 'Marşrutlar', true],
  ['cams', 'Kameralar', true],
  ['ai', 'AI təklifləri', true],
  ['metro', 'Metro', true],
  ['bike', 'Velo', false],
];

const CAT = FEED_CATEGORIES;

// Alert.target is a mockup page id ("p-occ"); our routes differ for a few.
const TARGET: Record<string, string> = { 'p-occ': '/occupancy', 'p-express': '/terminal', 'p-integ': '/integrations' };
const targetHref = (t: string | null) => (t ? (TARGET[t] ?? `/${t.replace(/^p-/, '')}`) : '/map');

const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');

export default function Page() {
  const router = useRouter();
  const [layers, setLayers] = useState(() => Object.fromEntries(LAYERS.map(([k, , on]) => [k, on])) as Record<LayerKey, boolean>);
  const fresh = useLiveStream();
  const proposals = useProposals();
  const [propId, setPropId] = useState<number>();
  const [propOpen, setPropOpen] = useState(false);
  const jam = useJamDemo((id) => (setPropId(id), setPropOpen(true)));

  return (
    <>
      <PageHeader
        title="Canlı xəritə"
        desc={navItem('/map').desc}
        actions={
          <Button disabled={jam.running} onClick={jam.start} title="Demo ssenarisi: Fətəli Xan Xoyski prospektində süni tıxac yaradır, sistem yeni hub yeri təklif edir">
            {jam.running ? 'Tıxac yaradılır…' : jam.done ? 'Ssenarini təkrarla' : 'Süni tıxac yarat (demo)'}
          </Button>
        }
      />
      <Kpis />
      <div className="grid grid-cols-[minmax(0,1fr)_360px] gap-4 max-[1200px]:grid-cols-1">
        {/* theme-light: the basemap stays light in dark mode, so overlays use light tokens too */}
        <div className="theme-light relative h-[calc(100vh-290px)] min-h-[500px] overflow-hidden rounded-[14px] border border-line bg-surface-2 text-ink">
          <LiveMap
            layers={layers}
            onBusClick={(route) => router.push(`/occupancy?route=${encodeURIComponent(route)}`)}
            onProposalClick={(p) => (setPropId(p.id), setPropOpen(true))}
          />
          <div className="absolute top-3 left-3 flex max-w-[calc(100%-64px)] flex-wrap gap-1.5">
            {LAYERS.map(([k, label]) => (
              <Toggle key={k} on={layers[k]} onChange={(v) => setLayers((s) => ({ ...s, [k]: v }))} className="shadow-card">
                {label}
              </Toggle>
            ))}
          </div>
          <div className="absolute right-3 bottom-[30px] rounded-md bg-surface">
            <DemoBadge />
          </div>
          <Legend layers={layers} />
        </div>
        <Panel title="Hadisələr" meta="Klikləyin, detallara keçin" flush>
          <Feed fresh={fresh} />
        </Panel>
      </div>
      <HubProposalDrawer p={proposals.data?.find((p) => p.id === propId)} open={propOpen} onClose={() => setPropOpen(false)} />
    </>
  );
}

const JAM_TIMEOUT_MS = 12_000; // backend promises the proposal ~4 s after the trigger

/** Mockup #jamBtn: POST artificial-jam; the street turns red via traffic.updated, proposal.created opens the drawer. */
function useJamDemo(onProposal: (id: number) => void) {
  const toast = useToast();
  const [running, setRunning] = useState(false);
  const [done, setDone] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  const finish = () => (clearTimeout(timer.current), setRunning(false), setDone(true));
  useStream({
    'proposal.created': (p) => {
      finish();
      onProposal(p.id);
    },
  });
  const start = () => {
    setRunning(true);
    toast('Demo: Fətəli Xan Xoyski prospektində süni tıxac yaradılır');
    api.post<DemoResponse>('/demo/trigger/artificial-jam').then(
      () => {
        timer.current = setTimeout(finish, JAM_TIMEOUT_MS);
      },
      (e: unknown) => {
        setRunning(false);
        toast(errText(e));
      },
    );
  };
  return { running, done, start };
}

function Kpis() {
  const { data: k, error, isPending } = useKpis();
  const v = (s: string) => (isPending ? '…' : error ? '—' : s);
  const speedDown = k ? k.avgSpeedKmh < k.avgSpeedYesterday : false;
  const top = [...(useHubs().data ?? [])].sort((a, b) => b.count - a.count).slice(0, 2).map((h) => h.name);
  return (
    <div className="mb-4 grid grid-cols-4 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
      <Kpi label="Orta hərəkət sürəti" value={v(k ? `${fmtNum(k.avgSpeedKmh)} km/s` : '')} sub={error ? 'Yüklənmədi. Səhifəni yeniləyin.' : k && `Dünən bu saatda ${fmtNum(k.avgSpeedYesterday)} km/s idi`} subTone={speedDown ? 'bad' : undefined} />
      <Kpi label="Hub-larda gözləyən" value={v(k ? fmtNum(k.waitingAtHubs) : '')} sub={top.length ? `Ən sıx: ${top.join(' və ')}` : undefined} />
      <Kpi label="Avtobuslarda orta doluluq" value={v(k ? `${fmtNum(k.avgOccupancy)}%` : '')} sub={k && `${fmtNum(k.overfullBuses)} avtobus 90%-dən çox doludur`} subTone={k && k.overfullBuses > 0 ? 'bad' : undefined} />
      <Kpi
        label="Sərnişin məmnunluğu"
        value={k ? <>{fmtNum(k.satisfaction, 1)}<span className="text-base font-normal text-ink-3"> / 5</span></> : v('')}
        sub={k && `Bu həftə ${k.satisfactionWeekDelta >= 0 ? '+' : '−'}${fmtNum(Math.abs(k.satisfactionWeekDelta), 1)}`}
        subTone={k ? (k.satisfactionWeekDelta >= 0 ? 'ok' : 'bad') : undefined}
      />
    </div>
  );
}

function Feed({ fresh }: { fresh: ReadonlySet<number> }) {
  const { data, error, isPending, refetch } = useFeed();
  const { feedHidden } = usePrefs();
  const box = 'max-h-[calc(100vh-345px)] min-h-[445px] overflow-auto max-[1200px]:max-h-[420px] max-[1200px]:min-h-0';
  if (isPending) return <SkeletonLines rows={8} className={`${box} p-4`} />;
  if (error)
    return (
      <ErrorState className={`${box} p-10`} message={loadErr('Hadisələri', error)} onRetry={() => void refetch()} />
    );
  if (!data.length) return <div className={`${box} p-10 text-center text-ink-2`}>Hələ hadisə yoxdur. Yeni hadisələr burada avtomatik görünəcək.</div>;
  const shown = data.filter((a) => !feedHidden.includes(a.category));
  if (!shown.length)
    return (
      <div className={`${box} p-10 text-center text-ink-2`}>
        Seçilmiş kateqoriyalarda hadisə yoxdur. Kateqoriyaları <Link href="/profile" className="text-primary">profildə</Link> dəyişin.
      </div>
    );
  return (
    <div className={box}>
      {shown.map((a) => (
        <FeedItem key={a.id} a={a} isNew={fresh.has(a.id)} />
      ))}
    </div>
  );
}

function FeedItem({ a, isNew }: { a: Alert; isNew: boolean }) {
  const [label, color] = CAT[a.category] ?? [a.category, 'text-ink-2'];
  return (
    <Link
      href={targetHref(a.target)}
      className={`grid w-full grid-cols-[1fr_auto] gap-x-2.5 gap-y-0.5 border-b border-line px-4 py-3 text-left text-ink no-underline hover:bg-surface-2 ${isNew ? 'flash' : ''}`}
    >
      <span className={`flex items-center gap-1.5 text-xs font-semibold ${color}`}>
        <i className="size-1.5 rounded-full bg-current" />
        {label}
      </span>
      <span className="text-xs text-ink-3">{clockTime(a.createdAt)}</span>
      <span className="col-span-full font-semibold">{a.title}</span>
      {a.body && <span className="col-span-full text-[13px] text-ink-2">{a.body}</span>}
    </Link>
  );
}

function Legend({ layers }: { layers: Record<LayerKey, boolean> }) {
  const sw = (c: string) => <i className={`h-1 w-4 rounded-sm ${c}`} />;
  const bx = (c: string, r = 'rounded-[3px]') => <i className={`h-2.5 w-3.5 ${r} ${c}`} />;
  const items: [boolean, ReactNode, string][] = [
    [layers.traffic, sw('bg-fluid'), 'Sərbəst'],
    [layers.traffic, sw('bg-slow'), 'Ləng'],
    [layers.traffic, sw('bg-jam'), 'Tıxac'],
    [layers.buses, bx('bg-bus-e'), 'Elektrik avtobus'],
    [layers.buses, bx('bg-bus-d'), 'Dizel avtobus'],
    [layers.crowd, bx('bg-crowd', 'rounded-md'), 'Hub-da insan sayı'],
    [layers.metro, <i key="m" className="size-2.5 rounded-full bg-metro" />, 'Metro çıxışı'],
    [layers.bike, sw('bg-live'), 'Velo zolağı və parkinq'],
  ];
  return (
    <div className="absolute bottom-[30px] left-3 flex max-w-[calc(100%-120px)] flex-wrap gap-4 rounded-xl border border-line bg-surface px-3 py-2.5 text-xs shadow-card">
      {items
        .filter(([on]) => on)
        .map(([, icon, label]) => (
          <span key={label} className="flex items-center gap-1.5 text-ink-2">
            {icon}
            {label}
          </span>
        ))}
    </div>
  );
}
