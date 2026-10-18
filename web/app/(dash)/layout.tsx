'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, clearToken, getToken } from '@/lib/api';
import { StreamProvider } from '@/lib/stream';
import { Sidebar } from '@/components/Sidebar';
import { Topbar } from '@/components/Topbar';
import type { ReportStats } from '@yolcu/shared/api';
import { reportKeys } from '@/lib/reports';
import { useReportStream } from '@/lib/useReportStream';

function ReportStream() {
  useReportStream();
  return null;
}

export default function DashLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const qc = useQueryClient();
  const [authed, setAuthed] = useState(false);
  // < 1024 px the sidebar is an off-canvas menu; it closes itself when the page changes.
  const [nav, setNav] = useState(false);
  const path = usePathname();
  useEffect(() => setNav(false), [path]);
  useEffect(() => {
    if (getToken()) setAuthed(true);
    else router.replace('/login');
  }, [router]);

  // 401 here clears the token and redirects (client onUnauthorized).
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, enabled: authed, staleTime: Infinity });
  // Sidebar Şikayətlər badge = reports in status NEW (mockup #navRep); SSE keeps it current.
  const stats = useQuery({ queryKey: reportKeys.stats, queryFn: () => api.get<ReportStats>('/admin/reports/stats'), enabled: authed });

  // authed=false unmounts StreamProvider (closes the EventSource); replace() keeps the dashboard out of Back history.
  const logout = () => {
    clearToken();
    setAuthed(false);
    qc.clear();
    router.replace('/login');
  };

  if (!authed) return null;
  return (
    <StreamProvider>
      <ReportStream />
      <div className="grid h-dvh grid-cols-[248px_1fr] max-lg:grid-cols-1">
        <Sidebar user={me.data} reportsBadge={stats.data?.newCount} open={nav} onClose={() => setNav(false)} />
        <div className="flex min-w-0 flex-col">
          <Topbar user={me.data} onLogout={logout} onMenu={() => setNav(true)} />
          <main className="flex-1 overflow-auto p-6 max-sm:px-[max(16px,env(safe-area-inset-left))] max-sm:pt-4 max-sm:pb-[max(16px,env(safe-area-inset-bottom))]">
            <div className="mx-auto max-w-[1440px]">{children}</div>
          </main>
        </div>
      </div>
    </StreamProvider>
  );
}
