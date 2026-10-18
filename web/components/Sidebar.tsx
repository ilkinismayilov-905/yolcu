'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { UserDto } from '@yolcu/shared/api';
import { ICON, NAV } from '@/lib/nav';
import { ROLE_LABEL } from '@/lib/api';
import { LogoMark } from './Logo';

export function Sidebar({ user, reportsBadge }: { user?: UserDto; reportsBadge?: number }) {
  const path = usePathname();
  return (
    <aside className="flex flex-col overflow-auto border-r border-line bg-surface px-3 py-[18px] max-[820px]:hidden">
      <div className="flex items-center gap-2.5 px-2.5 pt-0.5 pb-5">
        <LogoMark size={32} />
        <div>
          <b className="block text-base leading-[1.1] tracking-[.01em]">Yolcu</b>
          <span className="text-xs text-ink-3">AYNA idarəetmə mərkəzi</span>
        </div>
      </div>
      <nav>
        {NAV.map(({ group, items }) => (
          <div key={group} className="mb-3.5">
            <div className="px-2.5 pb-1.5 text-xs font-medium text-ink-3">{group}</div>
            {items.map((it) => {
              const on = path === it.href || path.startsWith(it.href + '/');
              return (
                <Link
                  key={it.href}
                  href={it.href}
                  aria-current={on ? 'page' : undefined}
                  className={`relative flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 ${
                    on ? 'bg-primary-soft font-semibold text-primary' : 'font-medium text-ink-2 hover:bg-surface-2 hover:text-ink'
                  }`}
                >
                  <svg
                    className="size-[18px] flex-none"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.9"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    dangerouslySetInnerHTML={{ __html: ICON[it.icon] }}
                  />
                  {it.label}
                  {it.badge === 'reports' && !!reportsBadge && (
                    <span className="ml-auto grid h-5 min-w-5 place-items-center rounded-[10px] bg-bad px-1.5 text-[11px] font-bold text-on-color">
                      {reportsBadge}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>
      <Link
        href="/profile"
        aria-current={path.startsWith('/profile') ? 'page' : undefined}
        className="mt-auto block rounded-b-lg border-t border-line px-2.5 pt-3 pb-1 text-xs text-ink-3 no-underline hover:bg-surface-2"
      >
        <b className="block font-semibold text-ink">{user?.fullName ?? '…'}</b>
        {user ? ROLE_LABEL[user.role] : ''}
      </Link>
    </aside>
  );
}
