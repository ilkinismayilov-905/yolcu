'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { UserDto } from '@yolcu/shared/api';
import { ICON, NAV } from '@/lib/nav';
import { logout } from '@/lib/api';

const ROLE_LABEL: Record<UserDto['role'], string> = { DISPATCHER: 'Dispetçer', ADMIN: 'Administrator', PASSENGER: 'Sərnişin' };

export function Sidebar({ user, reportsBadge }: { user?: UserDto; reportsBadge?: number }) {
  const path = usePathname();
  return (
    <aside className="flex flex-col overflow-auto border-r border-line bg-surface px-3 py-[18px] max-[820px]:hidden">
      <div className="flex items-center gap-2.5 px-2.5 pt-0.5 pb-5">
        <div className="grid size-8 place-items-center rounded-[9px] bg-primary text-on-color">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6">
            <circle cx="8.5" cy="12" r="5" />
            <circle cx="15.5" cy="12" r="5" />
          </svg>
        </div>
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
      <div className="mt-auto border-t border-line px-2.5 pt-3 text-xs text-ink-3">
        <b className="block font-semibold text-ink">{user?.fullName ?? '…'}</b>
        {user ? `${ROLE_LABEL[user.role]}, növbə 07:00–15:00` : ''}
        <button onClick={logout} className="mt-2 block cursor-pointer font-medium text-ink-2 hover:text-ink">
          Çıxış
        </button>
      </div>
    </aside>
  );
}
