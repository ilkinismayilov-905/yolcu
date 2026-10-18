import type { Metadata } from 'next';

export const metadata: Metadata = { title: 'Daxil olun' };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
