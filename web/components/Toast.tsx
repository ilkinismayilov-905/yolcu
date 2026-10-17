'use client';

import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';

const ToastCtx = createContext<(msg: string) => void>(() => {});

/** One-line toast at the bottom (mockup `toast(m)`): last message wins, hides after 2.8 s. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState('');
  const [on, setOn] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const show = useCallback((m: string) => {
    setMsg(m);
    setOn(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setOn(false), 2800);
  }, []);
  return (
    <ToastCtx.Provider value={show}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className={`pointer-events-none fixed bottom-6 left-1/2 z-60 max-w-[90vw] -translate-x-1/2 rounded-[10px] bg-ink px-4 py-[11px] font-medium text-surface transition duration-250 ${
          on ? 'translate-y-0 opacity-100' : 'translate-y-4 opacity-0'
        }`}
      >
        {msg}
      </div>
    </ToastCtx.Provider>
  );
}

export const useToast = () => useContext(ToastCtx);
