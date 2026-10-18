'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@yolcu/shared/api';
import { api, API_MODE, getToken, setToken } from '@/lib/api';
import { Button } from '@/components/ui';
import { LogoFull } from '@/components/Logo';

const inputCls = 'h-10 rounded-[9px] border border-line bg-surface px-3 text-ink focus:border-primary';

export default function LoginPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [email, setEmail] = useState('dispatcher@yolcu.az');
  const [password, setPassword] = useState('demo123');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (getToken()) router.replace('/map');
  }, [router]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api.login({ email: email.trim(), password });
      if (res.user.role === 'PASSENGER') {
        setError('Bu hesabın idarəetmə mərkəzinə girişi yoxdur. Dispetçer hesabı ilə daxil olun.');
        return;
      }
      setToken(res.token);
      qc.setQueryData(['me'], res.user);
      router.replace('/map');
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? 'Email və ya şifrə yanlışdır. Yoxlayın və yenidən daxil olun.'
          : err instanceof ApiError ? err.message : 'Daxil olmaq olmadı. Bir az sonra yenidən cəhd edin.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center content-center gap-6 p-6">
      <div className="grid justify-items-center gap-2">
        <LogoFull height={132} />
        <span className="text-sm text-ink-3">AYNA idarəetmə mərkəzi</span>
      </div>
      <form onSubmit={submit} className="grid w-full max-w-[380px] gap-4 rounded-[14px] border border-line bg-surface p-6 shadow-card">
        <h1 className="m-0 text-[22px] font-bold tracking-[-.01em]">Daxil olun</h1>
        <div className="grid gap-1.5">
          <label htmlFor="email" className="text-[13px] font-medium text-ink-2">Email</label>
          <input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} className={inputCls} />
        </div>
        <div className="grid gap-1.5">
          <label htmlFor="password" className="text-[13px] font-medium text-ink-2">Şifrə</label>
          <input id="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} />
        </div>
        {error && (
          <p role="alert" className="m-0 text-[13px] font-medium text-bad">
            {error}
          </p>
        )}
        <Button variant="p" type="submit" disabled={busy} className="h-10">
          {busy ? 'Yoxlanılır…' : 'Daxil ol'}
        </Button>
        <p className="m-0 text-xs text-ink-3">
          Demo hesablar: dispatcher@yolcu.az və admin@yolcu.az, şifrə demo123{API_MODE === 'mock' ? '. Mock rejim: backend lazım deyil.' : '.'}
        </p>
      </form>
    </main>
  );
}
