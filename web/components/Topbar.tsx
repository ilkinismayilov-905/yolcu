'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { UserDto } from '@yolcu/shared/api';
import { DemoBadge } from './ui';
import { useStream } from '@/lib/stream';
import { setDark, useDark } from '@/lib/prefs';
import { AvatarMenu } from './AvatarMenu';

const hhmm = () => new Date().toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit', hour12: false });

export function Topbar({ user, onLogout }: { user?: UserDto; onLogout: () => void }) {
  const dark = useDark();
  const router = useRouter();
  const { status } = useStream();
  const [clock, setClock] = useState('');
  useEffect(() => {
    setClock(hhmm());
    const t = setInterval(() => setClock(hhmm()), 20000);
    return () => clearInterval(t);
  }, []);

  const live = status === 'open';

  return (
    <header className="flex h-[60px] items-center gap-3 border-b border-line bg-surface px-6">
      <label className="flex h-[38px] w-[340px] items-center gap-2 rounded-[10px] border border-line bg-surface-2 px-3 text-ink-3 max-[820px]:hidden">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
        <input
          className="flex-1 border-0 bg-transparent text-ink outline-0"
          placeholder="Bort nömrəsi, marşrut və ya şikayət ID"
          onKeyDown={(e) => {
            if (e.key !== 'Enter') return;
            const v = e.currentTarget.value.trim().replace(/^#/, '');
            if (!v) return;
            // Same rule as the mockup: A-xxxx -> reports, anything else -> fleet search.
            router.push(/^A-/i.test(v) ? `/reports?q=${encodeURIComponent(v)}` : `/operators?q=${encodeURIComponent(v)}`);
          }}
        />
      </label>
      <DemoBadge />
      <div className="flex-1" />
      <div className="flex items-center gap-2 text-[13px] text-ink-2" title={live ? 'SSE bağlantısı aktivdir' : 'Bağlantı bərpa olunur'}>
        <span className={`size-2 rounded-full ${live ? 'pulse bg-ok text-ok' : 'bg-bad'}`} />
        {live ? 'Canlı' : 'Qoşulur'}, <span>{clock}</span>
      </div>
      <button
        onClick={() => setDark(!dark)}
        aria-pressed={dark}
        className="grid size-[38px] cursor-pointer place-items-center rounded-[10px] border border-line bg-surface text-ink-2"
        aria-label="Mövzunu dəyiş"
      >
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
        </svg>
      </button>
      <AvatarMenu user={user} onLogout={onLogout} />
    </header>
  );
}
