import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { FeatureCollection } from 'geojson';
import type { AdminHub, Alert, GeoLayerName, HubProposal, Kpis, LiveBus, LiveHub, TrafficSegment } from '@yolcu/shared/api';
import { api } from './api';
import { useStream } from './stream';

export const liveKeys = {
  buses: ['live', 'buses'] as const,
  hubs: ['live', 'hubs'] as const,
  traffic: ['live', 'traffic'] as const,
  adminHubs: ['admin', 'hubs'] as const,
  kpis: ['admin', 'kpis'] as const,
  feed: ['admin', 'feed'] as const,
  proposals: ['admin', 'hub-proposals'] as const,
  layer: (n: GeoLayerName) => ['geo', 'layer', n] as const,
};

export const FEED_LIMIT = 50;

// Positions/counts arrive by SSE; the REST call only seeds the first frame.
export const useBuses = () => useQuery({ queryKey: liveKeys.buses, queryFn: () => api.get<LiveBus[]>('/live/buses'), staleTime: Infinity });
export const useHubs = () => useQuery({ queryKey: liveKeys.hubs, queryFn: () => api.get<LiveHub[]>('/live/hubs'), staleTime: Infinity });
export const useTraffic = () => useQuery({ queryKey: liveKeys.traffic, queryFn: () => api.get<TrafficSegment[]>('/live/traffic'), staleTime: Infinity });
export const useAdminHubs = () => useQuery({ queryKey: liveKeys.adminHubs, queryFn: () => api.get<AdminHub[]>('/admin/hubs'), refetchInterval: 30_000 }); // counts by SSE; level/recommendation re-read
export const useKpis = () => useQuery({ queryKey: liveKeys.kpis, queryFn: () => api.get<Kpis>('/admin/kpis'), refetchInterval: 30_000 });
export const useFeed = () => useQuery({ queryKey: liveKeys.feed, queryFn: () => api.get<Alert[]>(`/admin/feed?limit=${FEED_LIMIT}`) });

/** All hub proposals (NEW + decided); `proposal.created` prepends. */
export const useProposals = () => useQuery({ queryKey: liveKeys.proposals, queryFn: () => api.get<HubProposal[]>('/admin/hub-proposals') });

/** Static GeoJSON layer, fetched once per session. */
export const useLayer = (name: GeoLayerName, enabled = true) =>
  useQuery({ queryKey: liveKeys.layer(name), queryFn: () => api.get<FeatureCollection>(`/geo/layers/${name}`), staleTime: Infinity, gcTime: Infinity, enabled });

/** Writes live SSE events into the query cache; returns ids of alerts that came by SSE (for the highlight). */
export function useLiveStream() {
  const qc = useQueryClient();
  const [freshAlerts, setFresh] = useState<ReadonlySet<number>>(new Set());
  useStream({
    // hello = (re)connected: the backend is back, retry whatever failed while it was down.
    hello: () => void qc.refetchQueries({ predicate: (q) => q.state.status === 'error' }),
    'bus.positions': (d) => qc.setQueryData(liveKeys.buses, d),
    'traffic.updated': (d) => qc.setQueryData(liveKeys.traffic, d),
    'hub.counts': (d) => {
      const by = new Map(d.map((c) => [c.hubId, c]));
      qc.setQueryData<LiveHub[]>(liveKeys.hubs, (old) =>
        old?.map((h) => {
          const c = by.get(h.id);
          return c ? { ...h, count: c.count, trend10: c.trend, forecast15: c.forecast15 } : h;
        }),
      );
    },
    'proposal.created': (p) => qc.setQueryData<HubProposal[]>(liveKeys.proposals, (old) => [p, ...(old ?? []).filter((x) => x.id !== p.id)]),
    'alert.created': (a) => {
      qc.setQueryData<Alert[]>(liveKeys.feed, (old) => [a, ...(old ?? []).filter((x) => x.id !== a.id)].slice(0, FEED_LIMIT));
      setFresh((s) => new Set(s).add(a.id));
    },
  });
  return freshAlerts;
}
