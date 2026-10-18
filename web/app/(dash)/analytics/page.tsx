'use client';

import { Suspense, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import type { AlightingPoint, AnalyticsKpis, OdMatrix } from '@yolcu/shared/api';
import { tollRecommended, tollScenario, tollScore, type TollCandidate } from '@yolcu/shared/toll';
import staticData from '../../../../mocks/analytics_static.json';
import { Bar, Button, DemoBadge, Kpi, Note, PageHeader, Panel, Plate, Seg, Table, TD, TR_CLICK, fmtNum } from '@/components/ui';
import { api } from '@/lib/api';
import { navItem } from '@/lib/nav';

const TABS = [
  ['flow', 'Axın və matris'],
  ['fc', 'Proqnoz'],
  ['toll', 'Ödənişli yollar'],
  ['data', 'Datanın tətbiqi'],
] as const;
type Tab = (typeof TABS)[number][0];

const OD_FROM = '07:00';
const OD_TO = '10:00';
const anKeys = {
  kpis: ['admin', 'analytics', 'kpis'] as const,
  od: ['admin', 'analytics', 'od', OD_FROM, OD_TO] as const,
  alighting: ['admin', 'analytics', 'alighting'] as const,
};

const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');
const mix = (token: string, pct: number) => `color-mix(in srgb, var(--${token}) ${Math.round(pct)}%, transparent)`;

export default function Page() {
  // useSearchParams needs a Suspense boundary for the static export.
  return (
    <Suspense>
      <Analytics />
    </Suspense>
  );
}

function Analytics() {
  const sp = useSearchParams();
  const router = useRouter();
  const path = usePathname();
  const tab: Tab = TABS.find(([k]) => k === sp.get('tab'))?.[0] ?? 'flow';
  return (
    <>
      <PageHeader
        title="Data analitika"
        desc={navItem('/analytics').desc}
        actions={<Seg label="Bölmə" options={TABS} value={tab} onChange={(t) => router.replace(t === 'flow' ? path : `${path}?tab=${t}`, { scroll: false })} />}
      />
      {tab === 'flow' && <Flow />}
      {tab === 'fc' && <Forecast />}
      {tab === 'toll' && <Toll />}
      {tab === 'data' && <DataUse />}
    </>
  );
}

// ---------- Axın və matris (KPIs, OD, alighting REAL; hub matrix FAKE) ----------

function Flow() {
  return (
    <div className="grid gap-4">
      <FlowKpis />
      <div className="grid grid-cols-2 gap-4 max-[1200px]:grid-cols-1">
        <OdPanel />
        <HubWeekPanel />
      </div>
      <AlightingPanel />
    </div>
  );
}

function FlowKpis() {
  const { data: k, error, isPending } = useQuery({ queryKey: anKeys.kpis, queryFn: () => api.get<AnalyticsKpis>('/admin/analytics/kpis') });
  const v = (s: () => string) => (isPending ? '…' : error || !k ? '—' : s());
  const trips = (n: number) => (n >= 1e6 ? `${fmtNum(n / 1e6, 2)} mln` : fmtNum(n));
  return (
    <div className="grid grid-cols-4 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
      <Kpi label="İzlənən səfər, son 30 gün" value={v(() => trips(k!.trackedTrips30d))} sub={error ? errText(error) : k && `Razılıq verən ${fmtNum(k.consentUsers)} istifadəçi`} subTone={error ? 'bad' : undefined} />
      <Kpi label="Orta səfər müddəti" value={v(() => `${fmtNum(k!.avgTripMin)} dəq`)} sub="Qapıdan qapıya, keçidlər daxil" />
      <Kpi label="Ən uzun keçid gözləməsi" value={v(() => `${fmtNum(k!.longestTransferWaitMin)} dəq`)} sub="Avtovağzal → şəhər avtobusu" subTone="bad" />
      <Kpi label="Proaktiv təklif qəbulu" value={v(() => `${fmtNum(k!.suggestionAcceptance)}%`)} sub="Tətbiq yolu özü təklif edəndə" subTone="ok" />
    </div>
  );
}

function StateBox({ loading, error, onRetry, empty }: { loading: boolean; error: unknown; onRetry: () => void; empty?: string }) {
  return (
    <div className="p-10 text-center text-ink-2">
      {loading ? (
        'Yüklənir…'
      ) : error ? (
        <>
          <span className="text-bad">{errText(error)}</span>
          <Button sm className="ml-3" onClick={onRetry}>Yenidən cəhd et</Button>
        </>
      ) : (
        empty
      )}
    </div>
  );
}

function OdPanel() {
  const q = useQuery({ queryKey: anKeys.od, queryFn: () => api.get<OdMatrix>(`/admin/analytics/od?from=${OD_FROM}&to=${OD_TO}`) });
  const d = q.data;
  const max = d ? Math.max(1, ...d.matrix.flat()) : 1;
  const inK = max >= 10_000; // mockup shows thousands ("8,2"); the seeded demo trips are only hundreds per cell
  return (
    <Panel title="Haradan haraya: səhər piki" meta={`${OD_FROM}–${OD_TO}, ${inK ? 'min səfər' : 'səfər'}`}>
      {!d || !d.zones.length || !d.matrix.flat().some(Boolean) ? (
        <StateBox loading={q.isPending} error={q.error} onRetry={() => q.refetch()} empty="Bu zaman aralığında səfər yoxdur." />
      ) : (
        <div className="overflow-x-auto">
          <table className="border-separate border-spacing-[3px]">
            <thead>
              <tr>
                <th className="px-1.5 py-1 text-right text-[11px] font-medium whitespace-nowrap text-ink-3">Haradan ↓ / Haraya →</th>
                {d.zones.map((z) => (
                  <th key={z} className="px-1.5 py-1 text-center text-[11px] font-medium text-ink-3">{z}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {d.matrix.map((row, i) => (
                <tr key={d.zones[i]}>
                  <th className="px-1.5 py-1 text-right text-[11px] font-medium whitespace-nowrap text-ink-3">{d.zones[i]}</th>
                  {row.map((v, j) => {
                    const k = inK ? fmtNum(v / 1000, 1) : fmtNum(v);
                    return v ? (
                      <td key={j} title={`${d.zones[i]} → ${d.zones[j]}: ${fmtNum(v)} səfər`} className={`h-[30px] w-11 rounded-md text-center text-[11px] font-semibold ${v / max > 0.55 ? 'text-on-color' : 'text-ink'}`} style={{ background: mix('primary', 10 + (v / max) * 80) }}>
                        {k}
                      </td>
                    ) : (
                      <td key={j} className="h-[30px] w-11 rounded-md bg-surface-2 text-center text-[11px] text-ink-3">—</td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="m-0 mt-2.5 text-[13px] text-ink-2">Ən güclü axın Avtovağzal → 28 May və Koroğlu → Nərimanov istiqamətindədir. Ekspress xətt və interval qərarları bu matrisə əsaslanır.</p>
    </Panel>
  );
}

/** Mockup hwMatrix formula (FAKE camera data): morning + evening peak, weaker at the weekend. */
function hubLoad(di: number, h: number) {
  const wk = di < 5;
  let v = 0.12 + 0.7 * Math.exp(-((h - 8.4) ** 2) / 1.4) * (wk ? 1 : 0.35) + 0.55 * Math.exp(-((h - 18.2) ** 2) / 2) * (wk ? 1 : 0.6) + (di === 5 && h >= 12 && h <= 20 ? 0.25 : 0);
  if (di === 4 && h >= 19 && h <= 21) v += 0.3;
  return Math.min(1, v);
}

function HubWeekPanel() {
  const { days, hours, hub } = staticData.hubHeatmap;
  return (
    <Panel title="Hub sıxlığı: saat və həftə günü" meta={`${hub}, kamera datası`} actions={<DemoBadge />}>
      <div className="overflow-x-auto">
        <table className="border-separate border-spacing-[3px]">
          <thead>
            <tr>
              <th />
              {hours.map((h) => (
                <th key={h} className="px-0.5 text-center text-[11px] font-medium text-ink-3">{h % 3 === 0 ? h : ''}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {days.map((d, di) => (
              <tr key={d}>
                <th className="pr-1.5 text-right text-[11px] font-medium whitespace-nowrap text-ink-3">{d}</th>
                {hours.map((h) => {
                  const v = hubLoad(di, h);
                  return <td key={h} title={`${d} ${h}:00, ${Math.round(v * 100)}%`} className="h-[22px] w-[26px] rounded" style={{ background: mix('crowd', 8 + v * 88) }} />;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-2.5 flex gap-3.5 text-xs text-ink-3">
        {(
          [
            [12, 'Az'],
            [55, 'Orta'],
            [100, 'Çox sıx'],
          ] as const
        ).map(([p, l]) => (
          <span key={l} className="flex items-center gap-1.5">
            <i className="h-2.5 w-[18px] rounded-[3px]" style={{ background: mix('crowd', p) }} />
            {l}
          </span>
        ))}
      </div>
    </Panel>
  );
}

function AlightingPanel() {
  const q = useQuery({ queryKey: anKeys.alighting, queryFn: () => api.get<AlightingPoint[]>('/admin/analytics/alighting?limit=5') });
  return (
    <Panel title="Sərnişinlər harada düşür" meta="Səfər izləmə: GPS ilə avtomatik aşkarlanan düşmə nöqtələri" flush>
      <Table head={['Dayanacaq', 'Gündə düşən', 'Pik dəqiqə', 'Nəticə']} loading={q.isPending} error={q.error ? errText(q.error) : null} onRetry={() => q.refetch()} isEmpty={!q.data?.length} empty="Hələ düşmə nöqtəsi yoxdur.">
        {q.data?.map((a) => (
          <tr key={a.stop}>
            <td className={TD}><b>{a.stop}</b></td>
            <td className={`${TD} tabular-nums`}>{fmtNum(a.perDay)}</td>
            <td className={TD}>{a.peak}</td>
            <td className={`${TD} whitespace-normal text-ink-2`}>{a.note}</td>
          </tr>
        ))}
      </Table>
    </Panel>
  );
}

// ---------- Proqnoz (FAKE) ----------

type FcKey = keyof typeof staticData.forecast;
const FC_SEG: readonly (readonly [FcKey, string])[] = [
  ['net', 'Şəbəkə'],
  ['88', '88'],
  ['4', '4'],
  ['M1', 'M1'],
];
const DAYS = staticData.hubHeatmap.days;
const FC_HUBS: [string, number, number, string][] = [
  ['Koroğlu', 560, 510, 'Platforma 2-yə nəzarətçi'],
  ['28 May', 430, 340, '65 və 88 intervalı 9 dəq'],
  ['Avtovağzal', 310, 220, 'M1 ekspress 07:30-dan'],
  ['Gənclik', 220, 160, 'Xoyski pr. tıxacına diqqət'],
  ['Nərimanov', 180, 110, 'Dəyişiklik lazım deyil'],
];
const FC_FACTORS: [string, number][] = [
  ['Həftə günü və saat', 38],
  ['Son 4 həftənin eyni günü', 24],
  ['Hava (yağış +15%)', 14],
  ['Tədbirlər (stadion, konsert)', 11],
  ['Biletim.az gəlişləri', 8],
  ['Bayram və məktəb təqvimi', 5],
];

function Forecast() {
  const [seg, setSeg] = useState<FcKey>('net');
  const c = staticData.forecast[seg];
  return (
    <div className="grid gap-4">
      <Panel title="Gündəlik sərnişin: fakt və proqnoz" actions={<><DemoBadge /><Seg label="Seqment" options={FC_SEG} value={seg} onChange={setSeg} /></>}>
        <FcChart base={c.base} amp={c.amp} />
        <div className="mt-1.5 flex flex-wrap items-center gap-4 text-xs text-ink-3">
          <span className="flex items-center gap-1.5"><i className="h-[3px] w-[18px] bg-ink-2" />Fakt</span>
          <span className="flex items-center gap-1.5"><i className="h-[3px] w-[18px] bg-primary" />Proqnoz</span>
          <span className="flex items-center gap-1.5"><i className="h-2.5 w-[18px] rounded-[3px] bg-primary-soft" />80% etibarlılıq aralığı</span>
          <span className="ml-auto">Son 4 həftədə proqnoz xətası: MAPE {c.mape}</span>
        </div>
      </Panel>
      <div className="grid grid-cols-2 gap-4 max-[1200px]:grid-cols-1">
        <Panel title="Sabah 08:00–09:00: ən sıx hub-lar" meta="Model: həftə günü + hava + tədbir + son 4 həftə" flush>
          <Table head={['Hub', 'Proqnoz', 'Bu gün', 'Hazırlıq']} empty="">
            {FC_HUBS.map(([h, f, t, prep]) => (
              <tr key={h}>
                <td className={TD}><b>{h}</b></td>
                <td className={`${TD} tabular-nums`}>{fmtNum(f)} nəfər</td>
                <td className={`${TD} text-ink-2`}>{fmtNum(t)}</td>
                <td className={`${TD} whitespace-normal text-[13px] text-ink-2`}>{prep}</td>
              </tr>
            ))}
          </Table>
        </Panel>
        <Panel title="Proqnoza təsir edən amillər" meta="Modeldə çəki">
          <div className="grid gap-3">
            {FC_FACTORS.map(([l, v]) => (
              <div key={l}>
                <div className="flex justify-between text-[13px]"><span>{l}</span><b>{v}%</b></div>
                <div className="mt-1"><Bar value={v * 2.4} label={l} /></div>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </div>
  );
}

/** Mockup drawFc(): 14 days of fact, 8 days forecast from "today" with ±8% band. */
function FcChart({ base, amp }: { base: number; amp: number }) {
  const W = 900, H = 240, p = 36, N = 21, T = 13;
  const vals = Array.from({ length: N }, (_, i) => base * (i % 7 < 5 ? 1 : 0.62) + Math.sin(i * 1.7) * amp * 0.4 + (i >= 14 && i % 7 === 4 ? amp * 0.8 : 0));
  const max = Math.max(...vals) * 1.15;
  const x = (i: number) => p + (i * (W - p * 2)) / (N - 1);
  const y = (v: number) => H - p - (v / max) * (H - p * 2);
  const pts = (from: number, to: number, k = 1) => vals.slice(from, to).map((v, i) => `${x(i + from)},${y(v * k)}`);
  const band = [...pts(T, N, 1.08), ...pts(T, N, 0.92).reverse()].join(' ');
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="h-[240px] w-full text-[11px]" role="img" aria-label="Fakt və proqnoz qrafiki">
      {[0, 0.25, 0.5, 0.75, 1].map((f) => (
        <g key={f}>
          <line x1={p} x2={W - p} y1={y(max * f)} y2={y(max * f)} className="stroke-line" />
          <text x={0} y={y(max * f) + 4} className="fill-ink-3">{Math.round(max * f)}k</text>
        </g>
      ))}
      <polygon points={band} className="fill-primary-soft" />
      <polyline points={pts(0, T + 1).join(' ')} fill="none" className="stroke-ink-2" strokeWidth={2.2} />
      <polyline points={pts(T, N).join(' ')} fill="none" className="stroke-primary" strokeWidth={2.5} strokeDasharray="6 4" />
      <line x1={x(T)} x2={x(T)} y1={p - 10} y2={H - p} className="stroke-ink-3" strokeDasharray="3 3" />
      <text x={x(T) + 6} y={p - 2} className="fill-ink-3">Bu gün</text>
      {vals.map((_, i) =>
        i % 2 ? null : (
          <text key={i} x={x(i)} y={H - 10} textAnchor="middle" className="fill-ink-3">{DAYS[(i + 4) % 7]}</text>
        ),
      )}
    </svg>
  );
}

// ---------- Ödənişli yollar (FAKE, packages/shared/toll.ts) ----------

const TOLL: (TollCandidate & { score: number })[] = staticData.tollCorridors
  .map((c) => ({ n: c.name, cars: c.carsPerHour, jam: c.jam, pt: c.ptAlternative, thru: c.through, res: c.residential }))
  .map((t) => ({ ...t, score: tollScore(t) }))
  .sort((a, b) => b.score - a.score);
const HOURS = [
  ['2.5', '07:30–10:00'],
  ['5', '07:30–10:00 və 17:00–19:30'],
] as const;

function Toll() {
  const [sel, setSel] = useState(0);
  const [price, setPrice] = useState(1);
  const [hours, setHours] = useState<(typeof HOURS)[number][0]>('2.5');
  const t = TOLL[sel];
  const r = tollScenario(t, price, Number(hours));
  const ptColor = (pt: number) => (pt >= 70 ? 'bg-fluid' : pt >= 50 ? 'bg-slow' : 'bg-jam');
  const scoreColor = (s: number) => (s >= 70 ? 'text-ok' : s >= 60 ? 'text-warn' : 'text-bad');
  return (
    <div className="grid gap-4">
      <Note>
        Ödənişli yol yalnız <b>güclü ictimai nəqliyyat alternativi</b> olan dəhlizdə ədalətlidir: sürücü maşını qoyanda eyni yolu avtobus və ya metro ilə tez gedə bilməlidir. Bal bu prinsiplə hesablanır.
      </Note>
      <Panel title="Ödənişli yol namizədləri" meta="Radar, səfər izləmə və marşrut datası əsasında" actions={<DemoBadge />} flush>
        <Table head={['Dəhliz', 'Pik saat, maşın/saat', 'Tıxac', 'İctimai nəqliyyat alternativi', 'Tranzit payı', 'Bal']} empty="">
          {TOLL.map((c, i) => (
            <tr
              key={c.n}
              tabIndex={0}
              aria-selected={i === sel}
              onClick={() => setSel(i)}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), setSel(i))}
              className={`${TR_CLICK} ${i === sel ? '[&>td]:bg-primary-soft hover:[&>td]:bg-primary-soft' : ''}`}
            >
              <td className={TD}><b>{c.n}</b></td>
              <td className={`${TD} tabular-nums`}>{fmtNum(c.cars)}</td>
              <td className={TD}>{c.jam}%</td>
              <td className={TD}>
                <div className="flex items-center gap-2">
                  <div className="w-20"><Bar value={c.pt} color={ptColor(c.pt)} label="İctimai nəqliyyat alternativi" /></div>
                  <span className="text-[13px]">{c.pt}%</span>
                </div>
              </td>
              <td className={TD}>{c.thru}%</td>
              <td className={TD}><b className={scoreColor(c.score)}>{c.score}</b></td>
            </tr>
          ))}
        </Table>
      </Panel>
      <div className="grid grid-cols-2 gap-4 max-[1200px]:grid-cols-1">
        <Panel title={`Ssenari: ${t.n}`} actions={<span className="inline-flex items-center gap-[7px] text-[13px] font-medium"><i className="size-2 rounded-full bg-primary" />Seçilmiş dəhliz</span>}>
          <div className="grid gap-1.5">
            <label htmlFor="tollPrice" className="text-[13px] font-medium text-ink-2">
              Pik saatda bir keçid haqqı: <b className="text-ink">{fmtNum(price, 2)} ₼</b>
            </label>
            <input id="tollPrice" type="range" min={0.5} max={3} step={0.25} value={price} onChange={(e) => setPrice(Number(e.target.value))} className="w-full accent-primary" />
          </div>
          <div className="mt-3 grid gap-1.5">
            <span className="text-[13px] font-medium text-ink-2">Saatlar</span>
            <div><Seg label="Saatlar" options={HOURS} value={hours} onChange={setHours} /></div>
          </div>
          <div className="mt-3.5 grid grid-cols-4 gap-3 max-[820px]:grid-cols-2">
            {(
              [
                [`−${r.reductionPct}%`, 'Avtomobil axını'],
                [`+${fmtNum(r.shiftToTransit)}`, 'İctimai nəqliyyata, gündə'],
                [`${fmtNum(r.revenueAzn)} ₼`, 'Gündəlik gəlir'],
                [`+${r.extraBuses}`, 'Lazım olan avtobus'],
              ] as const
            ).map(([v, l]) => (
              <div key={l}>
                <b className="block text-xl font-bold tracking-[-.01em]">{v}</b>
                <span className="text-xs text-ink-3">{l}</span>
              </div>
            ))}
          </div>
          <p className={`m-0 mt-3 text-[13px] ${tollRecommended(t) ? 'text-ink-2' : 'font-semibold text-bad'}`}>
            {tollRecommended(t)
              ? `Model: qiymət elastikliyi −0,2, azalan maşınların 55%-i ictimai nəqliyyata keçir. Əlavə ${r.extraBuses} avtobus dəhlizdəki marşrutlara verilməlidir.`
              : 'Bu dəhlizdə ictimai nəqliyyat alternativi zəifdir: ödəniş tıxacı başqa yollara köçürə bilər. Tövsiyə edilmir.'}
          </p>
        </Panel>
        <Panel title="Pilot tövsiyəsi">
          <div className="grid gap-3.5">
            <PilotRow plate={<Plate route="1" kind="x" lg />} title="Yusif Səfərov küç., 07:30–10:00, 1 ₼" sub="Ən yüksək tıxac, 88 və metro alternativi güclüdür" />
            <PilotRow plate={<Plate route="2" kind="x" lg />} title="Koroğlu Rəhimov küç., 3 aydan sonra" sub="Əvvəl 4 marşrutunun intervalı 8 dəqiqəyə endirilməlidir" />
            <PilotRow
              plate={<span className="inline-grid h-[38px] min-w-14 place-items-center rounded-[9px] bg-ink-3 text-lg font-extrabold text-on-color">—</span>}
              title="Heydər Əliyev pr. tövsiyə edilmir"
              sub="Tranzit axın yüksəkdir, alternativ marşrut yoxdur"
            />
            <Note>Gəlirin bir hissəsi dəhlizdəki avtobus intervalına və yeni hub-a yönəldilərsə, ictimai qəbul daha yüksək olur. Bu, sorğu ilə yoxlanıla bilər.</Note>
            <div>
              <Link href="/surveys" className="inline-flex h-9 items-center rounded-[9px] border border-line bg-surface px-3.5 font-semibold text-ink no-underline hover:bg-surface-2">Sərnişinlərdən soruş</Link>
            </div>
          </div>
        </Panel>
      </div>
    </div>
  );
}

function PilotRow({ plate, title, sub }: { plate: ReactNode; title: string; sub: string }) {
  return (
    <div className="flex items-center gap-3">
      {plate}
      <div>
        <b>{title}</b>
        <div className="text-[13px] text-ink-2">{sub}</div>
      </div>
    </div>
  );
}

// ---------- Datanın tətbiqi (FAKE) ----------

function DataUse() {
  const { sources, uses } = staticData.useMatrix;
  return (
    <div className="grid gap-4">
      <Panel title="Hansı data hansı qərara gedir" meta="AYNA daxilində istifadə" actions={<DemoBadge />} flush>
        <Table head={['Qərar', ...sources]} empty="">
          {uses.map(([u, v]) => (
            <tr key={u as string}>
              <td className={TD}><b>{u as string}</b></td>
              {(v as number[]).map((x, i) => (
                <td key={i} className={`${TD} text-center`}>
                  {x ? <span className="inline-block size-2.5 rounded-full bg-primary" aria-label="istifadə olunur" /> : <span className="text-ink-3">·</span>}
                </td>
              ))}
            </tr>
          ))}
        </Table>
      </Panel>
      <Panel title="Data məhsulları: kimə və necə" meta="Yalnız aqreqat və anonim data. Gəlirlər təxminidir" flush>
        <Table head={['Məhsul', 'Kimə', 'Nə daxildir', 'Model', 'İllik, təxmini']} empty="">
          {staticData.dataProducts.map(([name, who, what, model, rev]) => (
            <tr key={name}>
              <td className={`${TD} whitespace-normal`}><b>{name}</b></td>
              <td className={`${TD} whitespace-normal text-ink-2`}>{who}</td>
              <td className={`${TD} whitespace-normal text-[13px] text-ink-2`}>{what}</td>
              <td className={TD}>{model}</td>
              <td className={`${TD} tabular-nums`}>{rev}</td>
            </tr>
          ))}
        </Table>
      </Panel>
      <Note icon="🔒">
        <b>Data satışının qaydaları.</b> Heç bir məhsulda fərdi məlumat yoxdur: ad, telefon, kart nömrəsi, fərdi səfər izi satılmır. Ən kiçik qrup 10 nəfərdir, ondan az olan xanalar gizlədilir. Səfər izləmə yalnız istifadəçi razılıq verəndə işləyir və tətbiqdə istənilən vaxt söndürülür. Alıcılar xam dataya yox, hesabata və ya API-yə çıxış alır. Hər müqavilə fərdi məlumatlar haqqında qanunvericiliyə uyğun hüquqi baxışdan keçir.
      </Note>
    </div>
  );
}
