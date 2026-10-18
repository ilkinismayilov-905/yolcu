'use client';

import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CreateSurveyRequest, PageResponse, SurveyAudience, SurveyListItem, SurveyResults, SurveyStatus } from '@yolcu/shared/api';
import { Bar, Button, CoinIcon, PageHeader, Pager, Panel, Seg, Status, Table, TD, TR_CLICK, fmtNum } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { api } from '@/lib/api';
import { navItem } from '@/lib/nav';
import { useStream } from '@/lib/stream';
import { AUDIENCES, REWARDS, SURVEYS_PER_PAGE, SURVEY_STATUS, applySurveyUpdate, parseOptions, shortDate, surveyKeys } from '@/lib/surveys';
import { LogoMark } from '@/components/Logo';

const TABS = [['list', 'Bütün sorğular'], ['new', 'Yeni sorğu']] as const;
const FILTER = [['', 'Hamısı'], ['ACTIVE', 'Aktiv'], ['CLOSED', 'Bitib'], ['DRAFT', 'Qaralama']] as const satisfies readonly (readonly [SurveyStatus | '', string])[];
const REWARD_SEG = REWARDS.map((r) => [r, `${r} coin`] as const);

const INPUT = 'w-full rounded-[9px] border border-line bg-surface px-3 text-ink outline-0 focus:border-primary aria-invalid:border-bad';
const LABEL = 'text-[13px] font-medium text-ink-2';
const errText = (e: unknown) => (e instanceof Error ? e.message : 'Xəta baş verdi');

export default function Page() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<'list' | 'new'>('list');
  const [status, setStatus] = useState<SurveyStatus | ''>('');
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState<SurveyListItem | null>(null);

  useStream({
    'survey.updated': (item) => {
      applySurveyUpdate(qc, item);
      setSel((s) => (s?.id === item.id ? item : s));
    },
  });

  const created = (s: SurveyListItem) => {
    setStatus('');
    setPage(0);
    setSel(s);
    setTab('list');
  };

  return (
    <>
      <PageHeader title="Sorğular" desc={navItem('/surveys').desc} actions={<Seg label="Bölmə" options={TABS} value={tab} onChange={setTab} />} />
      {/* Both tabs stay mounted (mockup toggles display) so the draft form survives tab switches. */}
      <div hidden={tab !== 'list'}>
        <SurveyList
          status={status}
          page={page}
          sel={sel}
          onStatus={(s) => {
            setStatus(s);
            setPage(0);
          }}
          onPage={setPage}
          onSelect={setSel}
        />
      </div>
      <div hidden={tab !== 'new'}>
        <NewSurvey onCreated={created} />
      </div>
    </>
  );
}

function SurveyList({ status, page, sel, onStatus, onPage, onSelect }: {
  status: SurveyStatus | ''; page: number; sel: SurveyListItem | null;
  onStatus: (s: SurveyStatus | '') => void; onPage: (p: number) => void; onSelect: (s: SurveyListItem) => void;
}) {
  const list = useQuery({
    queryKey: surveyKeys.list(status, page),
    queryFn: () => api.get<PageResponse<SurveyListItem>>(`/admin/surveys?${new URLSearchParams({ ...(status && { status }), page: String(page), size: String(SURVEYS_PER_PAGE) })}`),
    placeholderData: keepPreviousData,
  });
  const items = list.data?.items ?? [];
  const total = list.data?.total ?? 0;
  const pages = Math.max(1, Math.ceil(total / SURVEYS_PER_PAGE));
  // Mockup opens with the first survey selected; the cached row wins so SSE / mutations show up.
  const current = (sel && (items.find((s) => s.id === sel.id) ?? sel)) ?? items[0] ?? null;

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_380px] items-start gap-4 max-[1100px]:grid-cols-1">
      <Panel flush>
        <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3">
          <Seg label="Status" options={FILTER} value={status} onChange={onStatus} />
          <span className="ml-auto text-[13px] text-ink-3">{list.data ? `${total} sorğu` : ''}</span>
        </div>
        <Table
          head={['Sorğu', 'Status', 'Auditoriya', 'Cavab', 'Mükafat']}
          loading={list.isPending}
          error={list.isError ? errText(list.error) : null}
          onRetry={() => void list.refetch()}
          isEmpty={!items.length}
          empty="Bu filtrə uyğun sorğu yoxdur."
        >
          {items.map((s) => {
            const [label, kind] = SURVEY_STATUS[s.status];
            return (
              <tr
                key={s.id}
                tabIndex={0}
                className={`${TR_CLICK} ${current?.id === s.id ? '[&>td]:bg-primary-soft' : ''}`}
                onClick={() => onSelect(s)}
                onKeyDown={(e) => e.key === 'Enter' && onSelect(s)}
              >
                <td className={`${TD} min-w-[220px] whitespace-normal!`}>
                  <b>{s.title}</b>
                  <div className="text-[13px] text-ink-3">{s.publishedAt ? shortDate(s.publishedAt) : '—'}</div>
                </td>
                <td className={TD}><Status kind={kind}>{label}</Status></td>
                <td className={`${TD} min-w-[120px] whitespace-normal! text-ink-2`}>{s.audienceLabel}</td>
                <td className={`${TD} tabular-nums`}>{fmtNum(s.responses)}</td>
                <td className={TD}><CoinIcon /> {s.coinReward}</td>
              </tr>
            );
          })}
        </Table>
        <Pager page={Math.min(page, pages - 1)} pages={pages} onPage={onPage} />
      </Panel>
      <ResultsPanel survey={current} loadingList={list.isPending} />
    </div>
  );
}

function ResultsPanel({ survey, loadingList }: { survey: SurveyListItem | null; loadingList: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const id = survey?.id ?? 0;
  const results = useQuery({
    queryKey: surveyKeys.results(id),
    queryFn: () => api.get<SurveyResults>(`/admin/surveys/${id}/results`),
    enabled: !!survey && survey.status !== 'DRAFT',
  });
  const act = useMutation({
    mutationFn: ({ path }: { path: string; msg: string }) => api.post<unknown>(path),
    onSuccess: (_d, v) => {
      // survey.updated SSE also arrives; this keeps the UI right if the stream is down.
      void qc.invalidateQueries({ queryKey: surveyKeys.lists });
      void qc.invalidateQueries({ queryKey: surveyKeys.results(id) });
      toast(v.msg);
    },
    onError: (e) => toast(errText(e)),
  });

  if (!survey)
    return (
      <Panel title="Nəticələr">
        <div className="text-ink-3">{loadingList ? 'Yüklənir…' : 'Nəticələri görmək üçün sorğu seçin.'}</div>
      </Panel>
    );

  const busy = act.isPending;
  const r = results.data;
  return (
    <Panel title={survey.title} meta={survey.responses ? <span className="whitespace-nowrap">{fmtNum(survey.responses)} cavab</span> : ''}>
      {survey.status === 'DRAFT' ? (
        <div className="text-ink-2">
          Bu sorğu hələ göndərilməyib.{' '}
          <Button sm variant="p" className="ml-1.5" disabled={busy} onClick={() => act.mutate({ path: `/admin/surveys/${id}/publish`, msg: 'Sorğu göndərildi' })}>
            İndi göndər
          </Button>
        </div>
      ) : results.isError ? (
        <div className="text-bad">
          {errText(results.error)} <Button sm className="ml-2" onClick={() => void results.refetch()}>Yenidən cəhd et</Button>
        </div>
      ) : !r ? (
        <div className="text-ink-3">Yüklənir…</div>
      ) : (
        <div className="grid gap-2.5">
          {r.questions.map((q, qi) => (
            <div key={qi} className="grid gap-2.5">
              <div className="mt-1.5 font-semibold">{q.text}</div>
              {q.options.map((o) => (
                <div key={o.label}>
                  <div className="mb-1 flex justify-between gap-2 text-[13px]">
                    <span>{o.label}</span>
                    <span><span className="text-ink-3">{fmtNum(o.count)}</span> <b>{o.percent}%</b></span>
                  </div>
                  <Bar value={o.percent} label={`${o.label}: ${o.percent}%`} />
                </div>
              ))}
            </div>
          ))}
          {!r.questions.length && <div className="text-ink-3">Bu sorğuda sual yoxdur.</div>}
          {survey.status === 'ACTIVE' && (
            <div className="mt-2 flex flex-wrap gap-2">
              <Button disabled={busy} onClick={() => act.mutate({ path: `/admin/surveys/${id}/close`, msg: 'Sorğu bitirildi' })}>Sorğunu bitir</Button>
              <Button variant="q" disabled={busy} onClick={() => act.mutate({ path: `/demo/surveys/${id}/simulate-answers?count=50`, msg: '50 demo cavab əlavə edildi' })}>
                Demo: 50 cavab əlavə et
              </Button>
            </div>
          )}
        </div>
      )}
    </Panel>
  );
}

type FormField = 'title' | 'question' | 'options';

// Limits mirror the backend CreateSurveyRequest validation, so errors show inline instead of a 400 toast.
function validate(f: { title: string; question: string; options: string }) {
  const e: Partial<Record<FormField, string>> = {};
  const opts = parseOptions(f.options);
  if (!f.title.trim()) e.title = 'Başlıq yazın';
  else if (f.title.trim().length > 160) e.title = 'Başlıq 160 simvoldan uzun ola bilməz';
  if (!f.question.trim()) e.question = 'Sual yazın';
  else if (f.question.trim().length > 500) e.question = 'Sual 500 simvoldan uzun ola bilməz';
  if (opts.length < 2) e.options = 'Ən azı 2 fərqli variant yazın, vergüllə ayırın';
  else if (opts.length > 10) e.options = 'Ən çox 10 variant ola bilər';
  else if (opts.some((o) => o.length > 80)) e.options = 'Hər variant ən çox 80 simvol ola bilər';
  return e;
}

function NewSurvey({ onCreated }: { onCreated: (s: SurveyListItem) => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [f, setF] = useState({
    title: 'Gecə avtobuslarına ehtiyac varmı?',
    question: 'Saat 00:00-dan sonra işləyən avtobuslardan istifadə edərdinizmi?',
    options: 'Hər gün, Həftədə bir neçə dəfə, Nadir hallarda, Xeyr',
  });
  const [audience, setAudience] = useState<SurveyAudience>('ALL');
  const [reward, setReward] = useState<(typeof REWARDS)[number]>('10');
  const [errors, setErrors] = useState<Partial<Record<FormField, string>>>({});

  const create = useMutation({
    mutationFn: (req: CreateSurveyRequest) => api.post<SurveyListItem>('/admin/surveys', req),
    onSuccess: (s, req) => {
      void qc.invalidateQueries({ queryKey: surveyKeys.lists });
      toast(req.publish ? `Sorğu göndərildi: ${AUDIENCES[req.audience][0]}` : 'Qaralama saxlandı');
      onCreated(s);
    },
  });

  const submit = (publish: boolean) => (e?: FormEvent) => {
    e?.preventDefault();
    const errs = validate(f);
    setErrors(errs);
    create.reset();
    if (Object.keys(errs).length) return;
    create.mutate({
      title: f.title.trim(),
      audience,
      coinReward: Number(reward),
      questions: [{ text: f.question.trim(), options: parseOptions(f.options) }],
      publish,
    });
  };

  const field = (k: FormField, label: string, multiline?: boolean) => {
    const props = {
      id: `ns-${k}`,
      className: `${INPUT} ${multiline ? 'resize-y py-2.5' : 'h-9'}`,
      value: f[k],
      'aria-invalid': !!errors[k],
      'aria-describedby': errors[k] ? `ns-${k}-err` : undefined,
      onChange: (e: { target: { value: string } }) => {
        setF({ ...f, [k]: e.target.value });
        if (errors[k]) setErrors({ ...errors, [k]: undefined });
      },
    };
    return (
      <div className="grid gap-1.5">
        <label htmlFor={`ns-${k}`} className={LABEL}>{label}</label>
        {multiline ? <textarea rows={2} {...props} /> : <input {...props} />}
        {errors[k] && <span id={`ns-${k}-err`} className="text-xs text-bad">{errors[k]}</span>}
      </div>
    );
  };

  const busy = create.isPending;
  return (
    <div className="grid grid-cols-2 items-start gap-4 max-[1100px]:grid-cols-1">
      <Panel title="Yeni sorğu">
        <form noValidate onSubmit={submit(true)} className="grid gap-3.5">
          {field('title', 'Başlıq')}
          {field('question', 'Sual', true)}
          {field('options', 'Cavab variantları, vergüllə')}
          <div className="grid grid-cols-2 gap-3.5 max-[700px]:grid-cols-1">
            <div className="grid gap-1.5">
              <label htmlFor="ns-aud" className={LABEL}>Kimə göndərilsin</label>
              <select id="ns-aud" className={`${INPUT} h-9`} value={audience} onChange={(e) => setAudience(e.target.value as SurveyAudience)}>
                {Object.entries(AUDIENCES).map(([k, [label, reach]]) => (
                  <option key={k} value={k}>{label} ({reach})</option>
                ))}
              </select>
            </div>
            <div className="grid gap-1.5">
              <span className={LABEL}>Mükafat</span>
              <div><Seg label="Mükafat" options={REWARD_SEG} value={reward} onChange={setReward} /></div>
            </div>
          </div>
          {create.error && <p role="alert" className="m-0 text-[13px] text-bad">{errText(create.error)}</p>}
          <div className="flex gap-2">
            <Button type="submit" variant="p" disabled={busy}>Sorğunu göndər</Button>
            <Button type="button" variant="q" disabled={busy} onClick={submit(false)}>Qaralama kimi saxla</Button>
          </div>
        </form>
      </Panel>
      <Panel title="İstifadəçinin görəcəyi bildiriş">
        <div className="grid max-w-[380px] grid-cols-[36px_1fr] gap-2.5 rounded-[14px] border border-line bg-surface-2 p-3" aria-live="polite">
          <LogoMark size={36} />
          <div className="min-w-0">
            <b className="break-words">{f.title.trim() || 'Başlıq'}</b>
            <div className="text-[13px] text-ink-2">1 dəqiqəlik sorğu. Cavab verin, {reward} coin qazanın.</div>
          </div>
        </div>
      </Panel>
    </div>
  );
}
