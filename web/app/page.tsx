'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

// Client-side: static export has no server to redirect from
export default function Home() {
  const router = useRouter();
  useEffect(() => router.replace('/map'), [router]);
  return null;
}
