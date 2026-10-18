import type { MetadataRoute } from 'next';

// Static file at /manifest.webmanifest (works in the static export too)
export const dynamic = 'force-static';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Yolçu — AYNA idarəetmə mərkəzi',
    short_name: 'Yolçu',
    description: 'AYNA üçün vahid nəqliyyat platforması',
    start_url: '/map/',
    display: 'standalone',
    // = --primary and --bg tokens in app/globals.css (manifest needs literal colours)
    theme_color: '#0B6FCB',
    background_color: '#F4F6F9',
    icons: [
      { src: '/icon.svg', type: 'image/svg+xml', sizes: 'any' },
      { src: '/brand/logo-icon-512.png', type: 'image/png', sizes: '512x512' },
      { src: '/apple-icon.png', type: 'image/png', sizes: '180x180' },
    ],
  };
}
