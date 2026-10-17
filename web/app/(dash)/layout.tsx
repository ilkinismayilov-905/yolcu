'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { api, getToken } from '@/lib/api';
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
  const [authed, setAuthed] = useState(false);
  useEffect(() => {
    if (getToken()) setAuthed(true);
    else router.replace('/login');
  }, [router]);

  // 401 here clears the token and redirects (client onUnauthorized).
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, enabled: authed, staleTime: Infinity });
  // Sidebar Şikayətlər badge = reports in status NEW (mockup #navRep); SSE keeps it current.
  const stats = useQuery({ queryKey: reportKeys.stats, queryFn: () => api.get<ReportStats>('/admin/reports/stats'), enabled: authed });

  if (!authed) return null;
  return (
    <StreamProvider>
      <ReportStream />
      <div className="grid h-screen grid-cols-[248px_1fr] max-[820px]:grid-cols-1">
        <Sidebar user={me.data} reportsBadge={stats.data?.newCount} />
        <div className="flex min-w-0 flex-col">
          <Topbar user={me.data} />
          <main className="flex-1 overflow-auto p-6">
            <div className="mx-auto max-w-[1440px]">{children}</div>
          </main>
        </div>
      </div>
    </StreamProvider>
  );
}
