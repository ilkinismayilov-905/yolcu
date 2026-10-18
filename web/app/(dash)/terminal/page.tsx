'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { BiletimArrival, CrowdLevel } from '@yolcu/shared/api';
import { Button, DemoBadge, Note, PageHeader, Panel, Plate, Status, Table, TD, type StatusKind } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { api } from '@/lib/api';
import { navItem } from '@/lib/nav';
import { useStream } from '@/lib/stream';

const arrivalsKey = ['admin', 'biletim', 'arrivals'] as const;
const LEVEL: Record<CrowdLevel, StatusKind> = { HIGH: 'bad', MEDIUM: 'warn', LOW: 'ok' };
const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');

// FAKE express plan (docs/01_SCOPE): calibrated on the mockup case, 228 tickets → M1 8 dəq, H1 12 dəq.
const REF_TICKETS = 228;
const PAX_PER_BUS = 50; // one extra 90-seat bus takes ~50 terminal passengers per run (the rest is city load)
const ivFor = (base: number, tickets: number) => Math.max(6, Math.min(15, Math.round((base * REF_TICKETS) / Math.max(tickets, 1))));

export default function Page() {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const q = useQuery({ queryKey: arrivalsKey, queryFn: () => api.get<BiletimArrival[]>('/admin/biletim/arrivals'), refetchInterval: 30_000 });
  useStream({
    hello: () => void qc.invalidateQueries({ queryKey: arrivalsKey }),
    // demo ganja-arrival and real Biletim.az alerts: the list has changed
    'alert.created': (a) => a.category === 'BILETIM' && void qc.invalidateQueries({ queryKey: arrivalsKey }),
  });

  const list = q.data ?? [];
  const tickets = list.reduce((s, a) => s + a.ticketsSold, 0);
  const span = list.length ? `${list[0].time}–${list.at(-1)?.time}` : '';
  const extra = Math.ceil(tickets / PAX_PER_BUS);

  return (
    <>
      <PageHeader title="Avtovağzal və Biletim.az" desc={navItem('/terminal').desc} actions={<SyncStatus at={q.dataUpdatedAt} error={q.isError} />} />

      <div className="grid grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] items-start gap-4 max-[1200px]:grid-cols-1">
        <Panel title="Gələn şəhərlərarası reyslər" meta="Növbəti 3 saat" flush>
          <Table
            head={['Gəliş', 'Haradan', 'Satılmış bilet', 'Vəziyyət', 'Şəhərə keçid']}
            loading={q.isPending}
            error={q.isError && !q.data ? errText(q.error) : null}
            onRetry={() => void q.refetch()}
            isEmpty={list.length === 0}
            empty="Növbəti 3 saatda gələn reys yoxdur"
          >
            {list.map((a, i) => (
              <tr key={`${a.plate}-${a.time}`}>
                <td className={TD}><b>{a.time}</b></td>
                <td className={TD}>{a.from}<div className="text-xs text-ink-3">{a.plate}</div></td>
                <td className={TD}><b>{a.ticketsSold}</b> <span className="text-xs text-ink-3">sərnişin</span></td>
                <td className={`${TD} text-xs text-ink-2`}>{a.status}</td>
                <td className={TD}><Status kind={LEVEL[a.level] ?? 'neutral'}>{i === 0 ? `Növbəti şəhər avtobusu ${a.nextCityBusMin} dəq` : `${a.nextCityBusMin} dəq`}</Status></td>
              </tr>
            ))}
          </Table>
        </Panel>

        <div className="flex flex-col gap-4">
          <Panel title="Ekspress xətt tövsiyələri">
            <div className="flex flex-col gap-3.5">
              <Line plate="M1" kind="x" title="Avtovağzal → 28 May" sub="Dayanacaqsız, təxminən 24 dəq" iv={list.length ? ivFor(8, tickets) : null} span={span} />
              <Line plate="H1" kind="e" title="Avtovağzal → Koroğlu" sub="Metroya keçid, təxminən 19 dəq" iv={list.length ? ivFor(12, tickets) : null} span={span} />
              <Note>
                {list.length ? (
                  <>
                    Tövsiyə {span} arası Biletim.az-da satılmış <b>{tickets} bilet</b> əsasında hesablanıb. Lazım olan əlavə avtobus: <b>{extra}</b>.
                    <span className="mt-1 block text-xs text-ink-3">
                      Sadə hesab: {REF_TICKETS} bilet üçün M1 8 dəq, H1 12 dəq; bilet artdıqca interval mütənasib qısalır (6–15 dəq). Əlavə avtobus = bilet ÷ {PAX_PER_BUS}, yuxarı yuvarlaqlaşdırılır.
                    </span>
                  </>
                ) : q.isPending ? 'Yüklənir…' : 'Gəliş yoxdur, tövsiyə hesablanmır.'}
              </Note>
              <div className="flex flex-wrap gap-2">
                <Button variant="p" disabled={!list.length} onClick={() => toast('M1 və H1 üçün plan yaradıldı və operatora göndərildi')}>Planı təsdiqlə</Button>
                <Button onClick={() => router.push('/split')}>Bölgü təklifinə bax</Button>
              </div>
            </div>
          </Panel>
          <FlowChart />
        </div>
      </div>
    </>
  );
}

function Line({ plate, kind, title, sub, iv, span }: { plate: string; kind: 'x' | 'e'; title: string; sub: string; iv: number | null; span: string }) {
  return (
    <div className="flex items-center gap-3">
      <Plate route={plate} kind={kind} lg />
      <div className="flex-1">
        <b>{title}</b>
        <div className="text-xs text-ink-2">{sub}</div>
      </div>
      <div className="text-right">
        <b>{iv === null ? '—' : `${iv} dəq`}</b>
        <div className="text-xs text-ink-3">interval{span && `, ${span}`}</div>
      </div>
    </div>
  );
}

/** "Biletim.az: sinxron, N dəq əvvəl" from the last successful fetch. */
function SyncStatus({ at, error }: { at: number; error: boolean }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(t);
  }, []);
  if (error) return <Status kind="bad">Biletim.az: əlaqə yoxdur</Status>;
  if (!at) return <Status>Biletim.az: yüklənir…</Status>;
  const min = Math.max(0, Math.floor((now - at) / 60_000));
  return <Status kind="ok">Biletim.az: sinxron, {min === 0 ? 'indicə' : `${min} dəq əvvəl`}</Status>;
}

// FAKE (docs/01_SCOPE): static 30-minute exit flow, mockup #flowChart.
const FLOW = [60, 140, 210, 320, 410, 380, 300, 240, 190, 150];
const FLOW_LAB = ['07:00', '07:30', '08:00', '08:30', '09:00', '09:30', '10:00', '10:30', '11:00', '11:30'];

function FlowChart() {
  const W = 900, H = 200, p = 28;
  const pts = FLOW.map((v, i) => [p + (i * (W - p * 2)) / (FLOW.length - 1), H - p - (v / 450) * (H - p * 2)] as const);
  const line = pts.map((q) => q.join(' ')).join(' L');
  const y3 = H - p - (360 / 450) * (H - p * 2);
  return (
    <Panel title="Avtovağzaldan çıxan axın" meta="30 dəqiqəlik" actions={<DemoBadge />}>
      <svg viewBox={`0 0 ${W} ${H}`} className="block h-[180px] w-full" role="img" aria-label="Avtovağzaldan çıxan sərnişin axını, 07:00–11:30">
        <g fontSize="12" className="fill-ink-3">
          <path d={`M${line} L${pts[pts.length - 1][0]} ${H - p} L${pts[0][0]} ${H - p} Z`} opacity=".1" className="fill-primary" />
          <path d={`M${line}`} fill="none" strokeWidth="2.5" className="stroke-primary" />
          <line x1={p} x2={W - p} y1={y3} y2={y3} strokeDasharray="6 5" className="stroke-jam" />
          <text x={W - p} y={y3 - 6} textAnchor="end" className="fill-jam">Mövcud marşrutların tutumu</text>
          {pts.map(([x], i) => <text key={FLOW_LAB[i]} x={x} y={H - 6} textAnchor="middle">{FLOW_LAB[i]}</text>)}
        </g>
      </svg>
    </Panel>
  );
}
