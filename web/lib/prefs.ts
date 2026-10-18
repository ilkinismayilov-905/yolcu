'use client';

import { useSyncExternalStore } from 'react';

/** Dispatcher UI preferences (localStorage); one store so the avatar menu, /profile and SSE listeners stay in sync. */
export interface Prefs {
  /** Beep on report.created. */
  sound: boolean;
  /** Desktop Notification on report.created (only effective while permission is "granted"). */
  notify: boolean;
  /** Alert categories hidden in the /map feed (hidden list, so new categories show by default). */
  feedHidden: string[];
}

const KEY = 'yolcu.prefs';
const THEME_KEY = 'yolcu.theme';
const DEFAULTS: Prefs = { sound: false, notify: false, feedHidden: [] };

const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

function subscribe(l: () => void) {
  listeners.add(l);
  const onStorage = (e: StorageEvent) => {
    if (e.key === KEY) cache = null;
    if (e.key === KEY || e.key === THEME_KEY) l();
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(l);
    window.removeEventListener('storage', onStorage);
  };
}

let cache: Prefs | null = null;

export function getPrefs(): Prefs {
  if (cache) return cache;
  try {
    cache = { ...DEFAULTS, ...(JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Prefs>) };
  } catch {
    cache = DEFAULTS;
  }
  return cache;
}

export function setPrefs(patch: Partial<Prefs>) {
  cache = { ...getPrefs(), ...patch };
  try {
    localStorage.setItem(KEY, JSON.stringify(cache));
  } catch {
    // storage full / disabled: keep the in-memory value for this session
  }
  emit();
}

export const usePrefs = () => useSyncExternalStore(subscribe, getPrefs, () => DEFAULTS);

// Theme: data-theme on <html> (set before paint in app/layout.tsx); without a saved choice the system preference applies.
const isDark = () => {
  const t = document.documentElement.dataset.theme;
  return t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
};

export function setDark(dark: boolean) {
  const theme = dark ? 'dark' : 'light';
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    // ignore: theme still applies for this page load
  }
  emit();
}

export const useDark = () => useSyncExternalStore(subscribe, isDark, () => false);

let audio: AudioContext | null = null;

/** Short two-tone beep via Web Audio (no asset). Browsers start audio only after a user gesture on the page. */
export function beep() {
  try {
    audio ??= new AudioContext();
    void audio.resume();
    const t = audio.currentTime;
    const osc = audio.createOscillator();
    const gain = audio.createGain();
    osc.frequency.setValueAtTime(880, t);
    osc.frequency.setValueAtTime(1320, t + 0.12);
    gain.gain.setValueAtTime(0.0001, t);
    gain.gain.exponentialRampToValueAtTime(0.2, t + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.3);
    osc.connect(gain).connect(audio.destination);
    osc.start(t);
    osc.stop(t + 0.32);
  } catch {
    // no audio support: silent
  }
}
