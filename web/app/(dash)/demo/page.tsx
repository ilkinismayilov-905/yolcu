'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { DemoResponse, DemoScenario } from '@yolcu/shared/api';
import { Modal } from '@/components/Overlay';
import { useToast } from '@/components/Toast';
import { Button, PageHeader, Panel, SkeletonLines } from '@/components/ui';
import { api } from '@/lib/api';

// 03_API_CONTRACT §10. Not in the sidebar: opened by URL during the demo.
const SCENARIOS: [DemoScenario, string, string, [string, string]][] = [
  ['artificial-jam', 'Süni tıxac → yeni hub', 'Fətəli Xan Xoyski prospekti 1 saniyəyə ləngiyir, 2,5 saniyəyə tıxac olur. Bir neçə saniyə sonra xəritədə yeni hub təklifi açılır.', ['/map', 'Canlı xəritə']],
  ['new-report', 'Yeni şikayət', 'Demo sərnişin adından şikayət yaranır. Şikayətlər səhifəsində və hadisələr lentində dərhal görünür.', ['/reports', 'Şikayətlər']],
  ['overload-88', '88 dolub', '88 marşrutunun doluluğu 95%-ə qalxır. Doluluq səhifəsində interval tövsiyəsi dəyişir.', ['/occupancy?route=88', 'Avtobus doluluğu']],
  ['bunching', 'Topa yığılma', 'İki 88 avtobusu bir-birinə yaxınlaşır, lentdə doluluq xəbərdarlığı çıxır.', ['/occupancy?route=88', 'Avtobus doluluğu']],
  ['crowd-28may', '28 May-da sıxlıq', '28 May hub-ında gözləyən insan sayı 46-dan 70-ə qalxır.', ['/hubs', 'Hub kameraları']],
  ['ganja-arrival', 'Gəncə reysi gəlir', 'Biletim.az: Gəncə reysi 5 dəqiqəyə gəlir, 45 bilet satılıb. Lentdə Biletim.az xəbərdarlığı çıxır.', ['/terminal', 'Avtovağzal']],
  ['stadium', 'Stadionda oyun', 'Tədbir xəbərdarlığı yaranır, sərnişinlərə bildiriş göndərilir.', ['/map', 'Canlı xəritə']],
  ['coin-conversion', 'Yolçu Coin çevirmə sorğusu', 'Sintetik sərnişin 1000 Yolçu Coin-i (2,00 ₼) manata çevirmək istəyir. Sorğu "Gözləyir" statusu ilə görünür, 60 saniyə sonra "Tamamlandı" olur.', ['/coins', 'Yolçu Coin proqramı']],
  ['rain', 'Yağış', 'Hava xəbərdarlığı: yağışa görə avtobus tələbatı artacaq.', ['/map', 'Canlı xəritə']],
];

const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');

export default function Page() {
  // same query as the dashboard layout (cached there)
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, staleTime: Infinity });
  const role = me.data?.role;
  return (
    <>
      <PageHeader title="Demo idarə paneli" desc="Demo ssenarilərini işə salın və ya hər şeyi başlanğıc vəziyyətə qaytarın." actions={role && role !== 'PASSENGER' && <ResetButton />} />
      {me.isPending ? (
        <Panel><SkeletonLines /></Panel>
      ) : role === 'ADMIN' || role === 'DISPATCHER' ? (
        <Scenarios />
      ) : (
        <Panel><p className="m-0 text-bad">Bu səhifə yalnız dispetçer və admin üçündür. Dispetçer hesabı ilə daxil olun.</p></Panel>
      )}
    </>
  );
}

function ResetButton() {
  const qc = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const reset = useMutation({
    mutationFn: () => api.post<DemoResponse>('/demo/reset'),
    onSuccess: (r) => {
      setOpen(false);
      void qc.invalidateQueries();
      toast(r.message || 'Demo data başlanğıc vəziyyətə qaytarıldı');
    },
    onError: (e) => toast(errText(e)),
  });
  return (
    <>
      <Button variant="p" onClick={() => setOpen(true)}>Hamısını sıfırla</Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Hamısını sıfırlayaq?"
        sub="Simulyator və demo data başlanğıc vəziyyətə qayıdır: demo sərnişinin bu günkü şikayət, səfər və Yolçu Coin dəyişiklikləri silinir."
        footer={
          <>
            <Button onClick={() => setOpen(false)}>Ləğv et</Button>
            <Button variant="p" disabled={reset.isPending} onClick={() => reset.mutate()}>{reset.isPending ? 'Sıfırlanır…' : 'Sıfırla'}</Button>
          </>
        }
      />
    </>
  );
}

function Scenarios() {
  const toast = useToast();
  const trigger = useMutation({
    mutationFn: (s: DemoScenario) => api.post<DemoResponse>(`/demo/trigger/${s}`),
    onSuccess: (r, s) => toast(r.message || `Ssenari işə salındı: ${SCENARIOS.find(([k]) => k === s)?.[1] ?? s}`),
    onError: (e) => toast(errText(e)),
  });
  return (
    <div className="grid grid-cols-4 gap-4 max-[1200px]:grid-cols-2 max-[820px]:grid-cols-1">
      {SCENARIOS.map(([k, title, desc, [href, where]]) => (
        <div key={k} className="flex flex-col gap-3 rounded-[14px] border border-line bg-surface p-4 shadow-card">
          <button
            type="button"
            disabled={trigger.isPending && trigger.variables === k}
            onClick={() => trigger.mutate(k)}
            className="min-h-16 cursor-pointer rounded-xl border border-primary bg-primary px-4 text-left text-[17px] font-bold text-primary-ink hover:brightness-106 disabled:cursor-wait disabled:opacity-60"
          >
            {title}
          </button>
          <p className="m-0 flex-1 text-[13px] text-ink-2">{desc}</p>
          <Link href={href} className="text-[13px] font-semibold text-primary no-underline">Nəticəyə bax: {where} →</Link>
        </div>
      ))}
    </div>
  );
}
