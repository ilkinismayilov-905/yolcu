import type { Metadata } from 'next';
import { Onest } from 'next/font/google';
import { Providers } from './providers';
import './globals.css';

const onest = Onest({ subsets: ['latin', 'latin-ext'], weight: ['400', '500', '600', '700', '800'], variable: '--font-onest' });

export const metadata: Metadata = {
  // shortcut: OG/manifest URLs resolve against :8080 (the one-process demo); set NEXT_PUBLIC_SITE_URL for a real host
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:8080'),
  title: { default: 'Yolçu — AYNA idarəetmə mərkəzi', template: '%s | Yolçu' },
  description: 'AYNA üçün vahid nəqliyyat platforması',
  applicationName: 'Yolçu',
  openGraph: { title: 'Yolçu — AYNA idarəetmə mərkəzi', description: 'AYNA üçün vahid nəqliyyat platforması', siteName: 'Yolçu', locale: 'az_AZ', type: 'website' },
};

// Apply saved theme before paint; without a saved choice the system preference applies (CSS media query).
const themeInit = `try{var t=localStorage.getItem('yolcu.theme');if(t)document.documentElement.dataset.theme=t}catch(e){}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="az" className={onest.variable} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInit }} />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
