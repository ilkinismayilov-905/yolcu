// Feed categories (mockup CAT): label + token text color.
// No imports on purpose: shared by the /map feed and /profile, must never sit in an import cycle.
export const FEED_CATEGORIES: Record<string, [string, string]> = {
  HUB: ['Hub kamerası', 'text-crowd'],
  OCC: ['Doluluq', 'text-bad'],
  AI: ['AI tövsiyəsi', 'text-primary'],
  BILETIM: ['Biletim.az', 'text-ok'],
  REPORT: ['Şikayət', 'text-warn'],
  EVENT: ['Tədbir', 'text-ink-2'],
  WEATHER: ['Hava', 'text-ink-2'],
};
