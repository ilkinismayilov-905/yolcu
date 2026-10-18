'use client';

import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { useRouter } from 'next/navigation';
import type { UserDto } from '@yolcu/shared/api';
import { ROLE_LABEL } from '@/lib/api';
import { beep, setDark, setPrefs, useDark, usePrefs } from '@/lib/prefs';
import { SwitchTrack } from './ui';
import { LogoMark } from './Logo';

const initials = (name = '') => name.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();

const ITEM = 'flex w-full cursor-pointer items-center gap-2.5 rounded-lg px-2.5 py-2 text-left font-medium text-ink outline-0 hover:bg-surface-2 focus-visible:bg-surface-2';

/** Topbar avatar + dropdown menu (WAI-ARIA menu button pattern). */
export function AvatarMenu({ user, onLogout }: { user?: UserDto; onLogout: () => void }) {
  const router = useRouter();
  const dark = useDark();
  const { sound } = usePrefs();
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);
  const btn = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const id = useId();

  const items = () => [...(menu.current?.querySelectorAll<HTMLElement>('[role^="menuitem"]') ?? [])];
  const close = (refocus = true) => {
    setOpen(false);
    if (refocus) btn.current?.focus();
  };

  // Focus the first item when opened; click outside closes (without stealing focus from where the user clicked).
  useEffect(() => {
    if (!open) return;
    items()[0]?.focus();
    const onDown = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const onMenuKey = (e: KeyboardEvent) => {
    const list = items();
    const i = list.indexOf(document.activeElement as HTMLElement);
    const go = (n: number) => list[(n + list.length) % list.length]?.focus();
    if (e.key === 'ArrowDown') go(i + 1);
    else if (e.key === 'ArrowUp') go(i - 1);
    else if (e.key === 'Home') go(0);
    else if (e.key === 'End') go(list.length - 1);
    else if (e.key === 'Escape') close();
    else if (e.key === 'Tab') return setOpen(false);
    else return;
    e.preventDefault();
  };

  const onBtnKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      setOpen(true);
    }
  };

  return (
    <div ref={wrap} className="relative">
      <button
        ref={btn}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={id}
        aria-label={`Profil menyusu${user ? `: ${user.fullName}` : ''}`}
        onClick={() => setOpen((o) => !o)}
        onKeyDown={onBtnKey}
        className="grid size-9 cursor-pointer place-items-center rounded-full bg-primary-soft text-[13px] font-bold text-primary"
      >
        {initials(user?.fullName)}
      </button>
      {open && (
        <div
          ref={menu}
          id={id}
          role="menu"
          aria-label="Profil"
          onKeyDown={onMenuKey}
          className="absolute top-[calc(100%+8px)] right-0 z-30 w-[280px] rounded-[14px] border border-line bg-surface p-1.5 shadow-card"
        >
          <div className="flex items-center gap-2.5 border-b border-line px-2.5 pt-1.5 pb-3 mb-1.5">
            <span className="grid size-9 flex-none place-items-center rounded-full bg-primary-soft text-[13px] font-bold text-primary">{initials(user?.fullName)}</span>
            <div className="min-w-0">
              <b className="block truncate font-semibold">{user?.fullName ?? '…'}</b>
              <span className="block text-xs text-ink-2">{user ? ROLE_LABEL[user.role] : ''}</span>
              {user?.email && <span className="block truncate text-xs text-ink-3">{user.email}</span>}
            </div>
          </div>
          <button
            type="button"
            role="menuitem"
            tabIndex={-1}
            className={ITEM}
            onClick={() => {
              close();
              router.push('/profile');
            }}
          >
            Profil
          </button>
          <button type="button" role="menuitemcheckbox" aria-checked={dark} tabIndex={-1} className={ITEM} onClick={() => setDark(!dark)}>
            <span className="flex-1">Tünd rejim</span>
            <SwitchTrack on={dark} />
          </button>
          <button
            type="button"
            role="menuitemcheckbox"
            aria-checked={sound}
            tabIndex={-1}
            className={ITEM}
            onClick={() => {
              setPrefs({ sound: !sound });
              if (!sound) beep(); // preview; also unlocks audio for later SSE beeps
            }}
          >
            <span className="flex-1">Yeni şikayət səsi</span>
            <SwitchTrack on={sound} />
          </button>
          <div role="separator" className="my-1.5 border-t border-line" />
          <button
            type="button"
            role="menuitem"
            tabIndex={-1}
            className={ITEM}
            onClick={() => {
              setOpen(false);
              onLogout();
            }}
          >
            Çıxış
          </button>
          <div className="mt-1.5 flex items-center gap-2 border-t border-line px-2.5 pt-2.5 pb-1 text-xs text-ink-3" role="presentation">
            <LogoMark size={18} />
            Yolcu · AYNA idarəetmə mərkəzi
          </div>
        </div>
      )}
    </div>
  );
}
