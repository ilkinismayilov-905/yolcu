import { PageHeader, Panel } from '@/components/ui';
import { navItem } from '@/lib/nav';

export default function Page() {
  return (
    <>
      <PageHeader title="Şikayətlər" desc={navItem('/reports').desc} />
      <Panel>
        <p className="m-0 text-ink-3">Bu bölmə hazırlanır.</p>
      </Panel>
    </>
  );
}
