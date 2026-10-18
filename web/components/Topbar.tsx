'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { SearchResult, UserDto } from '@yolcu/shared/api';
import { DemoBadge } from './ui';
import { useStream } from '@/lib/stream';
import { setDark, useDark } from '@/lib/prefs';
import { AvatarMenu } from './AvatarMenu';
import { useToast } from './Toast';
import { api } from '@/lib/api';

const ICON_BTN = 'grid size-[38px] flex-none cursor-pointer place-items-center rounded-[10px] border border-line bg-surface text-ink-2 max-sm:size-11';

const hhmm = () => new Date().toLocaleTimeString('az-AZ', { hour: '2-digit', minute: '2-digit', hour12: false });

export function Topbar({ user, onLogout, onMenu }: { user?: UserDto; onLogout: () => void; onMenu: () => void }) {
  const dark = useDark();
  const router = useRouter();
  const toast = useToast();
  const { status } = useStream();
  const [clock, setClock] = useState('');
  // Phone: search is an icon; tapping it opens the field across the whole topbar.
  const [searchOpen, setSearchOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (searchOpen) searchRef.current?.focus();
  }, [searchOpen]);
  useEffect(() => {
    setClock(hhmm());
    const t = setInterval(() => setClock(hhmm()), 20000);
    return () => clearInterval(t);
  }, []);

  const live = status === 'open';

  return (
    <header className="relative flex h-[60px] flex-none items-center gap-3 border-b border-line bg-surface px-6 max-lg:px-4 max-sm:box-content max-sm:gap-2 max-sm:px-3 max-sm:pt-[env(safe-area-inset-top)]">
      <button
        onClick={onMenu}
        aria-label="Menyunu aç"
        aria-controls="sidebar"
        className={`${ICON_BTN} lg:hidden`}
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d="M4 7h16M4 12h16M4 17h16" />
        </svg>
      </button>
      <button onClick={() => setSearchOpen(true)} aria-label="Axtarış" aria-expanded={searchOpen} className={`${ICON_BTN} sm:hidden`}>
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
      </button>
      <label
        className={`flex h-[38px] w-[340px] items-center gap-2 rounded-[10px] border border-line bg-surface-2 px-3 text-ink-3 focus-within:border-primary has-[input:focus-visible]:outline-2 has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-primary max-lg:w-auto max-lg:max-w-[340px] max-lg:flex-1 ${
          searchOpen ? 'max-sm:absolute max-sm:inset-x-3 max-sm:bottom-[11px] max-sm:z-10 max-sm:h-11 max-sm:max-w-none' : 'max-sm:hidden'
        }`}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
        <input
          aria-label="Qlobal axtarış"
          className="flex-1 border-0 bg-transparent text-ink outline-0"
          placeholder="Bort nömrəsi, marşrut və ya şikayət ID"
          ref={searchRef}
          onBlur={() => setSearchOpen(false)}
          onKeyDown={async (e) => {
            if (e.key === 'Escape') return e.currentTarget.blur();
            if (e.key !== 'Enter') return;
            const v = e.currentTarget.value.trim().replace(/^#/, '');
            if (!v) return;
            try {
              const r = await api.get<SearchResult>(`/admin/search?q=${encodeURIComponent(v)}`);
              if (r.type === 'BUS') router.push(`/operators?q=${encodeURIComponent(r.ref)}`);
              else if (r.type === 'REPORT') router.push(`/reports?q=${encodeURIComponent(r.ref)}`);
              else toast('Heç nə tapılmadı. Bort nömrəsini, marşrutu və ya şikayət ID-ni yoxlayın.');
            } catch (err) {
              toast(err instanceof Error ? err.message : 'Xəta baş verdi');
            }
          }}
        />
      </label>
      <span className="max-sm:hidden">
        <DemoBadge />
      </span>
      <div className="flex-1" />
      <div className="flex items-center gap-2 text-[13px] text-ink-2" title={live ? 'SSE bağlantısı aktivdir' : 'Bağlantı bərpa olunur'}>
        <span className={`size-2 rounded-full ${live ? 'pulse bg-ok text-ok' : 'bg-bad'}`} />
        {live ? 'Canlı' : 'Qoşulur'}
        <span className="-ml-2 max-sm:hidden">,</span>
        <span className="max-sm:hidden">{clock}</span>
      </div>
      <button onClick={() => setDark(!dark)} aria-pressed={dark} className={ICON_BTN} aria-label="Mövzunu dəyiş">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
        </svg>
      </button>
      <AvatarMenu user={user} onLogout={onLogout} />
    </header>
  );
}
