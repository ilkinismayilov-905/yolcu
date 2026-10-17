import type { Metadata } from 'next';
import { Onest } from 'next/font/google';
import { Providers } from './providers';
import './globals.css';

const onest = Onest({ subsets: ['latin', 'latin-ext'], weight: ['400', '500', '600', '700', '800'], variable: '--font-onest' });

export const metadata: Metadata = { title: 'Yolcu · İdarəetmə mərkəzi' };

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
