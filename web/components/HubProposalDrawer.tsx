'use client';

import { useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { HubProposal } from '@yolcu/shared/api';
import { Drawer } from '@/components/Overlay';
import { useToast } from '@/components/Toast';
import { Button, Status, fmtNum } from '@/components/ui';
import { api } from '@/lib/api';
import { liveKeys } from '@/lib/live';
import { clockTime } from '@/lib/reports';

const signed = (n: number) => (n > 0 ? '+' : n < 0 ? '−' : '') + fmtNum(Math.abs(n));

/** Mockup openHubProposal(): reason, 4 metrics, what to build, impact, "Plana əlavə et" → accept. */
export function HubProposalDrawer({ p, open, onClose }: { p: HubProposal | undefined; open: boolean; onClose: () => void }) {
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const anBtn = useRef<HTMLButtonElement>(null);
  const accept = useMutation({
    mutationFn: (id: number) => api.post<HubProposal>(`/admin/hub-proposals/${id}/accept`),
    onSuccess: (r) => {
      qc.setQueryData<HubProposal[]>(liveKeys.proposals, (old) => old?.map((x) => (x.id === r.id ? r : x)));
      anBtn.current?.focus(); // the accept button becomes disabled: keep focus inside the drawer so Esc still works
      toast('Hub təklifi 2027 planına əlavə edildi və şəhərsalma şöbəsinə göndərildi');
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Xəta baş verdi'),
  });
  const done = p?.status === 'ACCEPTED';

  return (
    <Drawer
      open={open && !!p}
      onClose={onClose}
      meta={p ? `AI təklifi, ${clockTime(p.createdAt)}` : 'AI təklifi'}
      title={p ? `Yeni hub: ${p.name}` : ''}
      extra={p && (done ? <Status kind="ok">Plana əlavə edilib</Status> : <Status kind="info">Etibarlılıq {p.confidence}%</Status>)}
      footer={
        p && (
          <>
            <Button ref={anBtn} onClick={() => (onClose(), router.push('/analytics'))}>Analitikaya bax</Button>
            <Button variant="p" disabled={done || accept.isPending} onClick={() => accept.mutate(p.id)}>
              {done ? 'Plana əlavə edilib' : accept.isPending ? 'Göndərilir…' : 'Plana əlavə et'}
            </Button>
          </>
        )
      }
    >
      {p && (
        <>
          <div className="rounded-xl border border-line bg-surface-2 px-3.5 py-3 text-[13px] text-ink-2">{p.reason}</div>
          <div className="grid grid-cols-2 gap-2.5">
            {(
              [
                [`${fmtNum(p.jamMinutes)} dəq`, 'Tıxac müddəti, bu gün'],
                [fmtNum(p.paxPerHour), 'Sərnişin/saat, pik'],
                [fmtNum(p.routesCount), 'Buradan keçən marşrut'],
                [`${fmtNum(p.metroM)} m`, 'Metroya qədər'],
              ] as const
            ).map(([v, l]) => (
              <div key={l} className="rounded-[10px] bg-surface-2 px-3 py-2.5">
                <b className="block text-lg">{v}</b>
                <span className="text-xs text-ink-3">{l}</span>
              </div>
            ))}
          </div>
          <div>
            <div className="mb-2 text-[13px] text-ink-3">Nə qurulmalıdır</div>
            <ul className="m-0 grid list-none gap-2 p-0">
              {p.items.map((it, i) => (
                <li key={it} className="flex gap-2.5 text-[13px]">
                  <span className="grid size-[18px] flex-none place-items-center rounded-full bg-ok text-[11px] font-extrabold text-on-color">{i + 1}</span>
                  {it}
                </li>
              ))}
            </ul>
          </div>
          <div>
            <div className="mb-2 text-[13px] text-ink-3">Gözlənilən təsir</div>
            <dl className="m-0 grid grid-cols-[130px_1fr] gap-x-3 gap-y-2 text-[13px] [&_dd]:m-0 [&_dd]:font-medium [&_dt]:text-ink-3">
              <dt>Keçid vaxtı</dt>
              <dd className={p.impact.transferMin < 0 ? 'text-ok' : ''}>{signed(p.impact.transferMin)} dəq</dd>
              <dt>Metroya keçən</dt>
              <dd>{signed(p.impact.toMetroPerDay)} sərnişin/gün</dd>
              <dt>Dayanacaqda gözləyən</dt>
              <dd className={p.impact.waitingPct < 0 ? 'text-ok' : ''}>{signed(p.impact.waitingPct)}%</dd>
              <dt>Təxmini xərc</dt>
              <dd>{p.impact.costAzn.includes('₼') ? p.impact.costAzn : `${p.impact.costAzn} ₼`}</dd>
            </dl>
          </div>
        </>
      )}
    </Drawer>
  );
}
