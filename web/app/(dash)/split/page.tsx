'use client';

import { useState } from 'react';
import { occNew, waitMin } from '@yolcu/shared/headway';
import { useToast } from '@/components/Toast';
import { Button, DemoBadge, PageHeader, Panel, Plate, Status, fmtNum } from '@/components/ui';
import { navItem } from '@/lib/nav';

// FAKE (01_SCOPE): mockup #p-split numbers, no backend. 33 → 88, 07:45–09:30.
const CAP = 90; // passengers per bus per hour at 100%
const BASE = { '33': { n: 5, demand: 170, types: ['d', 'e', 'd', 'd', 'e'] }, '88': { n: 4, demand: 380, types: ['e', 'd', 'd', 'e'] } } as const;
type Rt = keyof typeof BASE;
const HOURS = ['06', '07', '08', '09', '10', '11', '12'];
const DEMAND_88 = [120, 290, 380, 330, 190, 150, 160]; // passengers/hour
const MOVED_HOURS = [1, 2, 3]; // 07–09: moved buses run on 88

/** Route state with `k` buses moved: interval = 60 / buses, load via headway.ts occNew (load scales with interval). */
function routeState(rt: Rt, k: number) {
  const b = BASE[rt];
  const n = rt === '33' ? b.n - k : b.n + k;
  const iv0 = 60 / b.n;
  const iv = 60 / n;
  const occ0 = (b.demand / (b.n * CAP)) * 100;
  return { n, iv, wait: waitMin(iv), occ: occNew(occ0, iv0, iv) };
}

function scenario(k: number) {
  const r33 = routeState('33', k);
  const r88 = routeState('88', k);
  // passenger-weighted total waiting time
  const total = BASE['33'].demand * r33.wait + BASE['88'].demand * r88.wait;
  return { r33, r88, total };
}
const BASE_TOTAL = scenario(0).total;

const occTone = (o: number) => (o > 100 ? 'jam' : o > 85 ? 'slow' : 'fluid');
const TONE_TEXT = { jam: 'text-jam', slow: 'text-slow', fluid: 'text-fluid' } as const;
const TONE_BG = { jam: 'bg-jam', slow: 'bg-slow', fluid: 'bg-fluid' } as const;
const STAT = '[&_b]:block [&_b]:text-xl [&_b]:font-bold [&_b]:tracking-[-.01em] [&_span]:text-xs [&_span]:text-ink-3';

const CHECKS: [boolean, string][] = [
  [true, 'Eyni operator: BakuBus'],
  [true, 'Avtobus ölçüsü uyğundur, 12 m'],
  [true, 'Hər iki marşrut Avtovağzal deposundan çıxır'],
  [false, '2 sürücüdən biri 88 marşrutu üzrə təlim keçməyib. Dispetçer təsdiqi lazımdır'],
];

export default function Page() {
  const toast = useToast();
  const [k, setK] = useState(2);
  const s = scenario(k);
  const dw = Math.round(((s.total - BASE_TOTAL) / BASE_TOTAL) * 100);
  const risk = s.r88.occ > 100 ? 'Yüksək' : s.r88.occ > 85 ? 'Orta' : 'Yoxdur';

  return (
    <>
      <PageHeader title="Avtobus bölgüsü" desc={navItem('/split').desc} actions={<DemoBadge />} />
      <Panel title="33-dən 88-ə, 07:45–09:30 arası" actions={<Status kind="info">AI etibarlılığı 87%</Status>}>
        <div className="grid grid-cols-[1fr_90px_1fr] items-center gap-3 max-[820px]:grid-cols-1">
          <RouteCard rt="33" k={k} st={s.r33} />
          <div className="text-center">
            <div className="text-[28px] font-bold" data-testid="moved">{k}</div>
            <div className="text-xs text-ink-3">avtobus</div>
            <svg viewBox="0 0 80 20" width="80" className="mx-auto" aria-hidden="true">
              <path d="M2 10H70" stroke="var(--primary)" strokeWidth="2.5" strokeDasharray="5 4" />
              <path d="M64 4l8 6-8 6" stroke="var(--primary)" strokeWidth="2.5" fill="none" />
            </svg>
          </div>
          <RouteCard rt="88" k={k} st={s.r88} />
        </div>
        <div className="mt-4 grid grid-cols-2 gap-4 max-lg:grid-cols-1">
          <div>
            <label htmlFor="mvRange" className="mb-1.5 block text-[13px] font-medium text-ink-2">Neçə avtobus keçirilsin?</label>
            <input id="mvRange" type="range" min={0} max={3} value={k} onChange={(e) => setK(Number(e.target.value))} className="w-full accent-primary" />
            <div className="flex justify-between text-xs text-ink-3"><span>0</span><span>1</span><span>2</span><span>3</span></div>
            <div className={`mt-3.5 grid grid-cols-3 gap-3 ${STAT}`} data-testid="split-stats">
              <div><b>{dw > 0 ? '+' : dw < 0 ? '−' : ''}{Math.abs(dw)}%</b><span>Ümumi gözləmə vaxtı</span></div>
              <div><b>{risk}</b><span>88-də dolu avtobus riski</span></div>
              <div><b>9 dəq</b><span>Boş gediş, 33 → 88</span></div>
            </div>
          </div>
          <div>
            <div className="mb-2 text-xs text-ink-3">Yoxlanılan şərtlər</div>
            <ul className="m-0 grid list-none gap-2 p-0">
              {CHECKS.map(([ok, t]) => (
                <li key={t} className="flex gap-2.5 text-[13px]">
                  <span aria-hidden="true" className={`grid size-[18px] flex-none place-items-center rounded-full text-[11px] font-extrabold text-on-color ${ok ? 'bg-ok' : 'bg-warn'}`}>{ok ? '✓' : '!'}</span>
                  {t}
                </li>
              ))}
            </ul>
            <div className="mt-3.5 flex flex-wrap gap-2">
              <Button variant="p" onClick={() => toast('Tövsiyə təsdiqləndi və BakuBus dispetçerinə göndərildi')}>Təsdiqlə və operatora göndər</Button>
              <Button variant="q" onClick={() => toast('Tövsiyə rədd edildi')}>Rədd et</Button>
            </div>
          </div>
        </div>
      </Panel>
      <Panel className="mt-4" title="88: saatlıq tələbat və tutum" meta="Tələbat: avtobus kameraları, hub kameraları, metro axını">
        <HourChart n88={s.r88.n} />
      </Panel>
    </>
  );
}

function RouteCard({ rt, k, st }: { rt: Rt; k: number; st: ReturnType<typeof routeState> }) {
  const types = BASE[rt].types;
  const tone = occTone(st.occ);
  const mini = (t: string, extra = '') => `h-3 w-5 rounded-[3px] ${t === 'e' ? 'bg-bus-e' : 'bg-bus-d'} ${extra}`;
  return (
    <div className="rounded-[14px] border border-line bg-surface p-4 shadow-card" data-testid={`rc${rt}`}>
      <div className="flex items-center justify-between">
        <Plate route={rt} kind={rt === '88' ? 'e' : 'd'} lg />
        <span className="text-xs text-ink-3">BakuBus</span>
      </div>
      <div className="my-2.5 flex flex-wrap gap-1" aria-label={`${st.n} avtobus`}>
        {types.map((t, i) => <span key={i} className={mini(t, rt === '33' && i >= types.length - k ? 'opacity-20' : '')} />)}
        {rt === '88' && Array.from({ length: k }, (_, i) => <span key={`mv${i}`} className={mini('d', 'outline-2 outline-offset-2 outline-primary outline-dashed')} />)}
      </div>
      <div className={`mt-1.5 grid grid-cols-3 gap-3 ${STAT}`}>
        <div><b>{st.n}</b><span>Avtobus</span></div>
        <div><b>{Math.round(st.iv)} dəq</b><span>İnterval</span></div>
        <div><b>{fmtNum(st.wait, 1)}</b><span>Gözləmə, dəq</span></div>
      </div>
      <div className="mt-3 flex justify-between text-xs">
        <span className="text-ink-3">Doluluq 08:00–09:00</span>
        <b className={TONE_TEXT[tone]}>{Math.round(st.occ)}%</b>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded bg-surface-3">
        <i className={`block h-full rounded ${TONE_BG[tone]}`} style={{ width: `${Math.min(st.occ, 100)}%` }} />
      </div>
    </div>
  );
}

function HourChart({ n88 }: { n88: number }) {
  const W = 900, H = 220, p = 34, bw = 44, max = 480;
  const y = (v: number) => H - p - (v / max) * (H - p * 2);
  return (
    <div className="max-sm:overflow-x-auto">{/* phone: shrink to 560 px, then scroll inside */}
      <svg viewBox={`0 0 ${W} ${H}`} className="block h-[220px] w-full max-lg:h-auto max-sm:min-w-[560px]" role="img" aria-label="88: saatlıq tələbat və tutum" data-testid="hour-chart">
        <g fontSize="12" fill="var(--ink-3)">
          {[0, 120, 240, 360, 480].map((v) => (
            <g key={v}>
              <line x1={p} x2={W} y1={y(v)} y2={y(v)} stroke="var(--line)" />
              <text x="0" y={y(v) + 4}>{v}</text>
            </g>
          ))}
          {HOURS.map((h, i) => {
            const x = p + 30 + i * 120;
            const cap = (MOVED_HOURS.includes(i) ? n88 : BASE['88'].n) * CAP;
            const dem = DEMAND_88[i];
            return (
              <g key={h}>
                <rect x={x} y={y(dem)} width={bw} height={H - p - y(dem)} rx="5" fill={dem > cap ? 'var(--jam)' : 'var(--primary)'} />
                <rect x={x + bw + 6} y={y(cap)} width={bw} height={H - p - y(cap)} rx="5" fill="var(--surface-3)" data-cap={cap} />
                <text x={x + bw} y={H - 10} textAnchor="middle">{h}:00</text>
              </g>
            );
          })}
          <rect x={W - 300} y="4" width="12" height="12" rx="3" fill="var(--primary)" />
          <text x={W - 282} y="14">Tələbat, sərnişin/saat</text>
          <rect x={W - 110} y="4" width="12" height="12" rx="3" fill="var(--surface-3)" />
          <text x={W - 92} y="14">Tutum</text>
        </g>
      </svg>
    </div>
  );
}
