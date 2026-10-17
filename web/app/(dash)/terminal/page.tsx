import { PageHeader, Panel } from '@/components/ui';
import { navItem } from '@/lib/nav';

export default function Page() {
  return (
    <>
      <PageHeader title="Avtovağzal və Biletim.az" desc={navItem('/terminal').desc} />
      <Panel>
        <p className="m-0 text-ink-3">Bu bölmə hazırlanır.</p>
      </Panel>
    </>
  );
}
