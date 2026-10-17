import { PageHeader, Panel } from '@/components/ui';

export default function Page() {
  return (
    <>
      <PageHeader title="Demo idarə paneli" desc="Demo ssenarilərini işə salın və ya hər şeyi başlanğıc vəziyyətə qaytarın." />
      <Panel>
        <p className="m-0 text-ink-3">Bu bölmə hazırlanır.</p>
      </Panel>
    </>
  );
}
