'use client';

import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import type { ChangePasswordRequest, OkResponse } from '@yolcu/shared/api';
import { api, ROLE_LABEL } from '@/lib/api';
import { FEED_CATEGORIES } from '@/lib/feed';
import { beep, setPrefs, usePrefs } from '@/lib/prefs';
import { useToast } from '@/components/Toast';
import { Button, PageHeader, Panel, Switch } from '@/components/ui';

const INPUT = 'h-9 w-full rounded-[9px] border border-line bg-surface px-3 text-ink outline-0 focus:border-primary read-only:bg-surface-2 read-only:text-ink-2 aria-invalid:border-bad';
const LABEL = 'text-[13px] font-medium text-ink-2';
const CHK = 'flex items-center gap-2.5 border-b border-line py-[9px] last:border-b-0 [&_input]:size-4 [&_input]:accent-primary';

export default function ProfilePage() {
  return (
    <>
      <PageHeader title="Profil" desc="Şəxsi məlumatlar, şifrə və bildiriş ayarları." />
      <div className="grid grid-cols-2 items-start gap-4 max-[1100px]:grid-cols-1">
        <div className="grid gap-4">
          <Personal />
          <PasswordForm />
        </div>
        <Notifications />
      </div>
    </>
  );
}

function Personal() {
  // Same key as the dash layout: served from cache.
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, staleTime: Infinity });
  const u = me.data;
  const v = (s: string | null | undefined) => (me.isPending ? 'Yüklənir…' : (s ?? '—'));
  return (
    <Panel title="Şəxsi məlumatlar">
      {me.error ? (
        <div className="p-6 text-center">
          <p className="m-0 mb-3 text-bad">{me.error.message}</p>
          <Button sm onClick={() => me.refetch()}>Yenidən cəhd et</Button>
        </div>
      ) : (
        <div className="grid gap-3.5">
          {(
            [
              ['pf-name', 'Ad, soyad', u?.fullName],
              ['pf-email', 'Email', u?.email],
              ['pf-role', 'Rol', u && ROLE_LABEL[u.role]],
            ] as const
          ).map(([id, label, value]) => (
            <div key={id} className="grid gap-1.5">
              <label htmlFor={id} className={LABEL}>{label}</label>
              <input id={id} className={INPUT} readOnly value={v(value)} />
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

type PwField = 'current' | 'next' | 'repeat';

function validate(f: Record<PwField, string>): Partial<Record<PwField, string>> {
  const e: Partial<Record<PwField, string>> = {};
  if (!f.current) e.current = 'Hazırkı şifrəni daxil edin';
  if (!f.next) e.next = 'Yeni şifrəni daxil edin';
  else if (f.next.length < 6) e.next = 'Yeni şifrə ən azı 6 simvol olmalıdır';
  if (!f.repeat) e.repeat = 'Yeni şifrəni təkrar daxil edin';
  else if (f.repeat !== f.next) e.repeat = 'Şifrələr eyni deyil';
  return e;
}

const EMPTY: Record<PwField, string> = { current: '', next: '', repeat: '' };

function PasswordForm() {
  const toast = useToast();
  const [f, setF] = useState(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<PwField, string>>>({});
  const save = useMutation({
    mutationFn: (body: ChangePasswordRequest) => api.post<OkResponse>('/me/password', body),
    onSuccess: () => {
      setF(EMPTY);
      toast('Şifrə dəyişdirildi');
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs = validate(f);
    setErrors(errs);
    save.reset();
    if (Object.keys(errs).length) return;
    save.mutate({ currentPassword: f.current, newPassword: f.next });
  };

  const field = (k: PwField, label: string, autoComplete: string) => (
    <div className="grid gap-1.5">
      <label htmlFor={`pw-${k}`} className={LABEL}>{label}</label>
      <input
        id={`pw-${k}`}
        type="password"
        autoComplete={autoComplete}
        className={INPUT}
        value={f[k]}
        aria-invalid={!!errors[k]}
        aria-describedby={errors[k] ? `pw-${k}-err` : undefined}
        onChange={(e) => {
          setF({ ...f, [k]: e.target.value });
          if (errors[k]) setErrors({ ...errors, [k]: undefined });
        }}
      />
      {errors[k] && <span id={`pw-${k}-err`} className="text-xs text-bad">{errors[k]}</span>}
    </div>
  );

  return (
    <Panel title="Şifrəni dəyiş">
      <form noValidate onSubmit={submit} className="grid gap-3.5">
        {field('current', 'Hazırkı şifrə', 'current-password')}
        {field('next', 'Yeni şifrə', 'new-password')}
        {field('repeat', 'Yeni şifrə, təkrar', 'new-password')}
        {save.error && <p role="alert" className="m-0 text-[13px] text-bad">{save.error.message}</p>}
        <div>
          <Button variant="p" type="submit" disabled={save.isPending}>{save.isPending ? 'Saxlanılır…' : 'Şifrəni dəyiş'}</Button>
        </div>
      </form>
    </Panel>
  );
}

function Notifications() {
  const prefs = usePrefs();
  const [why, setWhy] = useState<string | null>(null);
  const supported = typeof Notification !== 'undefined';
  const notifyOn = prefs.notify && supported && Notification.permission === 'granted';

  async function toggleNotify(on: boolean) {
    setWhy(null);
    if (!on) return setPrefs({ notify: false });
    if (!supported) return setWhy('Bu brauzer masaüstü bildirişlərini dəstəkləmir.');
    const perm = await Notification.requestPermission();
    if (perm === 'granted') return setPrefs({ notify: true });
    setPrefs({ notify: false });
    setWhy('Brauzer bildirişlərə icazə vermədi. İcazəni ünvan sətrindəki sayt ayarlarından açıb yenidən cəhd edin.');
  }

  return (
    <Panel title="Bildirişlər">
      <div className="grid gap-4">
        <div className="grid gap-1">
          <Switch
            on={prefs.sound}
            onChange={(on) => {
              setPrefs({ sound: on });
              if (on) beep();
            }}
          >
            Yeni şikayət səsi
          </Switch>
          <span className="text-xs text-ink-3">Yeni şikayət gələndə qısa səs siqnalı.</span>
        </div>
        <div className="grid gap-1">
          <Switch on={notifyOn} onChange={(on) => void toggleNotify(on)}>Brauzer bildirişləri</Switch>
          <span className="text-xs text-ink-3">Yeni şikayət gələndə masaüstü bildirişi, tab arxa fonda olsa da.</span>
          {why && <span role="alert" className="text-xs text-bad">{why}</span>}
        </div>
        <div>
          <div className={`${LABEL} mb-1`}>Hadisələr lentində göstər</div>
          {Object.entries(FEED_CATEGORIES).map(([cat, [label]]) => (
            <label key={cat} className={CHK}>
              <input
                type="checkbox"
                checked={!prefs.feedHidden.includes(cat)}
                onChange={(e) =>
                  setPrefs({ feedHidden: e.target.checked ? prefs.feedHidden.filter((c) => c !== cat) : [...prefs.feedHidden, cat] })
                }
              />
              {label}
            </label>
          ))}
        </div>
      </div>
    </Panel>
  );
}
