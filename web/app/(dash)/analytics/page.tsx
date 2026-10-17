import { PageHeader, Panel } from '@/components/ui';
import { navItem } from '@/lib/nav';

export default function Page() {
  return (
    <>
      <PageHeader title="Data analitika" desc={navItem('/analytics').desc} />
      <Panel>
        <p className="m-0 text-ink-3">Bu bölmə hazırlanır.</p>
      </Panel>
    </>
  );
}
