import type { StreamEvent, StreamEventMap, StreamEventName } from '@yolcu/shared/api';

// Mock mode has no SSE: mock handlers emit here and StreamProvider fans it out like a server event.
const subs = new Set<(e: StreamEvent) => void>();

export const onMockEvent = (l: (e: StreamEvent) => void) => {
  subs.add(l);
  return () => void subs.delete(l);
};

export function emitMock<K extends StreamEventName>(type: K, data: StreamEventMap[K]) {
  const ev = { type, data, receivedAt: Date.now() } as StreamEvent;
  setTimeout(() => subs.forEach((l) => l(ev)), 0); // after the mutation response, like a real stream
}
