'use client';

import integrations from '../../../../mocks/integrations.json';
import { useToast } from '@/components/Toast';
import { Button, DemoBadge, PageHeader } from '@/components/ui';

// FAKE (01_SCOPE): static list from mocks/integrations.json (Biletim.az first), no backend.
export default function Page() {
  const toast = useToast();
  return (
    <>
      <PageHeader title="İnteqrasiyalar" desc="Yolçunun məlumat aldığı və ya göndərdiyi sistemlər. Demo-da simulyasiya olunanlar ayrıca qeyd edilib." actions={<DemoBadge />} />
      {integrations.length === 0 ? (
        <p className="m-0 text-ink-3">Hələ inteqrasiya yoxdur. Sistem qoşulanda burada görünəcək.</p>
      ) : (
        <div className="grid grid-cols-3 gap-4 max-[1200px]:grid-cols-2 max-sm:grid-cols-1" data-testid="integ">
          {integrations.map((i) => {
            const live = i.status === 'LIVE';
            return (
              <div key={i.name} className="grid gap-2 rounded-[14px] border border-line bg-surface p-4 shadow-card">
                <div className="flex items-center justify-between">
                  <b>{i.name}</b>
                  <span role="img" aria-label={live ? 'Qoşulub' : 'Simulyasiya'} title={live ? 'Qoşulub' : 'Simulyasiya'} className={`size-2 rounded-full ${live ? 'bg-ok' : 'bg-warn'}`} />
                </div>
                <div className="text-[13px] text-ink-2">{i.desc}</div>
                <div className={`text-[13px] ${live ? 'text-ok' : 'text-warn'}`}>{i.note}</div>
                <div>
                  <Button sm onClick={() => toast(`${i.name}: bağlantı yoxlanıldı, cavab 120 ms`)}>Bağlantını yoxla</Button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </>
  );
}
