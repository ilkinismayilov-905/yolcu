'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { STREAM_EVENTS, type StreamEvent, type StreamEventName } from '@yolcu/shared/api';
import { API_MODE, API_URL, getToken } from './api';
import { onMockEvent } from './mockBus';

export type StreamStatus = 'connecting' | 'open' | 'reconnecting';
export type StreamHandlers = { [K in StreamEventName]?: (data: Extract<StreamEvent, { type: K }>['data']) => void };

type Listener = (e: StreamEvent) => void;

const StreamCtx = createContext<{ status: StreamStatus; subscribe: (l: Listener) => () => void } | null>(null);

/** Query key holding the latest payload of an SSE event, e.g. useQuery({ queryKey: streamKey('bus.positions'), ... }). */
export const streamKey = (name: StreamEventName) => ['stream', name] as const;

const RECONNECT_MS = 3000;

export function StreamProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [status, setStatus] = useState<StreamStatus>(API_MODE === 'mock' ? 'open' : 'connecting');
  const listeners = useRef(new Set<Listener>());

  useEffect(() => {
    if (API_MODE === 'mock')
      // mock: no backend, always "connected"; events come from mock handlers
      return onMockEvent((ev) => {
        qc.setQueryData(streamKey(ev.type), ev.data);
        listeners.current.forEach((l) => l(ev));
      });
    let es: EventSource | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let closed = false;

    const connect = () => {
      const token = getToken();
      if (!token) return;
      es = new EventSource(`${API_URL}/api/v1/stream?token=${encodeURIComponent(token)}`);
      es.onopen = () => setStatus('open');
      es.onerror = () => {
        // Browser auto-retry is unreliable after server restarts: close and reconnect ourselves.
        es?.close();
        if (closed) return;
        setStatus('reconnecting');
        clearTimeout(timer);
        timer = setTimeout(connect, RECONNECT_MS);
      };
      for (const type of STREAM_EVENTS) {
        es.addEventListener(type, (msg: MessageEvent<string>) => {
          let data: unknown;
          try {
            data = msg.data ? JSON.parse(msg.data) : {};
          } catch {
            return;
          }
          setStatus('open');
          qc.setQueryData(streamKey(type), data);
          const ev = { type, data, receivedAt: Date.now() } as StreamEvent;
          listeners.current.forEach((l) => l(ev));
        });
      }
    };

    connect();
    return () => {
      closed = true;
      clearTimeout(timer);
      es?.close();
    };
  }, [qc]);

  const subscribe = useCallback((l: Listener) => {
    listeners.current.add(l);
    return () => void listeners.current.delete(l);
  }, []);

  return (
    <StreamCtx.Provider value={{ status, subscribe }}>
      {status === 'reconnecting' && (
        <div role="status" className="fixed inset-x-0 top-0 z-50 bg-warn px-4 py-1.5 text-center text-[13px] font-semibold text-on-color">
          Bağlantı bərpa olunur
        </div>
      )}
      {children}
    </StreamCtx.Provider>
  );
}

/** Connection status + optional per-event handlers (latest handlers are always used). */
export function useStream(handlers?: StreamHandlers) {
  const ctx = useContext(StreamCtx);
  if (!ctx) throw new Error('useStream must be used inside <StreamProvider>');
  const ref = useRef(handlers);
  useEffect(() => {
    ref.current = handlers;
  });
  const { subscribe } = ctx;
  useEffect(
    () =>
      subscribe((e) => {
        const h = ref.current?.[e.type] as ((d: unknown) => void) | undefined;
        h?.(e.data);
      }),
    [subscribe],
  );
  return { status: ctx.status };
}
