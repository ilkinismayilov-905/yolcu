'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import Map, { AttributionControl, Layer, Marker, NavigationControl, Source } from 'react-map-gl/maplibre';
import type { ExpressionSpecification, MapLibreEvent } from 'maplibre-gl';
import type { FeatureCollection } from 'geojson';
import { setWorkerUrl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import type { LiveBus } from '@yolcu/shared/api';
import { fmtNum } from '@/components/ui';
import { useAdminHubs, useBuses, useHubs, useLayer, useTraffic } from '@/lib/live';

// MapLibre 6 finds its worker next to its own .mjs, which bundling breaks; let the bundler emit the file instead.
setWorkerUrl(new URL('maplibre-gl/dist/maplibre-gl-worker.mjs', import.meta.url).href);

export type LayerKey = 'traffic' | 'crowd' | 'buses' | 'routes' | 'cams' | 'ai' | 'metro' | 'bike';

const STYLE = 'https://tiles.openfreemap.org/styles/positron';
const CENTER = { latitude: 40.3953, longitude: 49.867, zoom: 12 };
const SHOWN_ROUTES = ['88', '4']; // live map shows exactly one bus of each (08_DESIGN_SYSTEM §5)
const MOVE_MS = 2000; // = bus.positions period
const FONT = ['Noto Sans Regular']; // glyphs served by the OpenFreeMap style
const AZ_NAME: ExpressionSpecification = ['coalesce', ['get', 'name:az'], ['get', 'name']];
// Lines go under the base map labels so street names stay readable (first symbol layer of positron/liberty).
const UNDER_LABELS = 'waterway_line_label';
const EMPTY: FeatureCollection = { type: 'FeatureCollection', features: [] };

/** Token colors as literal strings (MapLibre paint cannot read CSS variables). */
function useTokens() {
  const [t, setT] = useState<Record<string, string> | null>(null);
  useEffect(() => {
    const cs = getComputedStyle(document.documentElement);
    const names = ['fluid', 'slow', 'jam', 'crowd', 'primary', 'metro', 'live', 'surface', 'ink', 'ink-2', 'on-color'];
    setT(Object.fromEntries(names.map((n) => [n, cs.getPropertyValue(`--${n}`).trim()])));
  }, []);
  return t;
}

/** Street / place labels in Azerbaijani (skips ref shields and our own `y-` layers). */
function localizeLabels(e: MapLibreEvent) {
  const m = e.target;
  for (const l of m.getStyle().layers) {
    if (l.type !== 'symbol' || l.id.startsWith('y-')) continue;
    const tf = m.getLayoutProperty(l.id, 'text-field');
    if (tf && JSON.stringify(tf).includes('name')) m.setLayoutProperty(l.id, 'text-field', AZ_NAME);
  }
}

const vis = (on: boolean) => ({ visibility: on ? ('visible' as const) : ('none' as const) });

export default function LiveMap({ layers, onBusClick }: { layers: Record<LayerKey, boolean>; onBusClick: (route: string) => void }) {
  const tk = useTokens();
  const traffic = useTraffic();
  const hubs = useHubs();
  const camHubs = useAdminHubs();
  const routes = useLayer('routes-lite');
  const metro = useLayer('metro-exits');
  const bikeLanes = useLayer('bike-lanes', layers.bike);
  const bikeParks = useLayer('bike-parkings', layers.bike);

  const trafficGeo = useMemo<FeatureCollection>(
    () => ({
      type: 'FeatureCollection',
      features: (traffic.data ?? []).map((s) => ({
        type: 'Feature',
        properties: { id: s.id, name: s.name, level: s.level },
        geometry: { type: 'LineString', coordinates: s.line.map(([lat, lon]) => [lon, lat]) },
      })),
    }),
    [traffic.data],
  );
  const hubGeo = useMemo<FeatureCollection>(
    () => ({
      type: 'FeatureCollection',
      features: (hubs.data ?? []).map((h) => ({
        type: 'Feature',
        properties: { id: h.id, count: h.count, label: `${fmtNum(h.count)} nəfər` },
        geometry: { type: 'Point', coordinates: [h.lon, h.lat] },
      })),
    }),
    [hubs.data],
  );

  if (!tk) return <div className="size-full bg-surface-2" />;
  return (
    <Map initialViewState={CENTER} mapStyle={STYLE} attributionControl={false} onLoad={localizeLabels} style={{ width: '100%', height: '100%' }}>
      <NavigationControl position="top-right" showCompass={false} />
      <AttributionControl position="bottom-right" compact={false} customAttribution="© OpenStreetMap contributors, OpenFreeMap" />

      <Source id="y-routes" type="geojson" data={routes.data ?? EMPTY}>
        <Layer id="y-routes" type="line" beforeId={UNDER_LABELS} layout={vis(layers.routes)} paint={{ 'line-color': tk.primary, 'line-width': 1, 'line-opacity': 0.12 }} />
      </Source>
      <Source id="y-bike-lanes" type="geojson" data={bikeLanes.data ?? EMPTY}>
        <Layer id="y-bike-lanes" type="line" beforeId={UNDER_LABELS} layout={vis(layers.bike)} paint={{ 'line-color': tk.live, 'line-width': 2.5, 'line-dasharray': [2, 1] }} />
      </Source>
      <Source id="y-bike-parks" type="geojson" data={bikeParks.data ?? EMPTY}>
        <Layer id="y-bike-parks" type="circle" layout={vis(layers.bike)} paint={{ 'circle-color': tk.live, 'circle-radius': 3.5, 'circle-stroke-color': tk['on-color'], 'circle-stroke-width': 1 }} />
      </Source>
      <Source id="y-traffic" type="geojson" data={trafficGeo}>
        <Layer
          id="y-traffic"
          type="line" beforeId={UNDER_LABELS}
          layout={{ ...vis(layers.traffic), 'line-cap': 'round', 'line-join': 'round' }}
          paint={{ 'line-color': ['match', ['get', 'level'], 'JAM', tk.jam, 'SLOW', tk.slow, tk.fluid], 'line-width': 5, 'line-opacity': 0.9 }}
        />
      </Source>
      <Source id="y-metro" type="geojson" data={metro.data ?? EMPTY}>
        <Layer id="y-metro" type="circle" layout={vis(layers.metro)} paint={{ 'circle-color': tk.metro, 'circle-radius': ['interpolate', ['linear'], ['zoom'], 11, 2.5, 15, 5], 'circle-stroke-color': tk['on-color'], 'circle-stroke-width': 1 }} />
        <Layer
          id="y-metro-label"
          type="symbol"
          minzoom={14}
          layout={{ ...vis(layers.metro), 'text-field': ['concat', ['get', 'station'], ', ', ['get', 'exit']], 'text-font': FONT, 'text-size': 11, 'text-offset': [0, 1.1], 'text-anchor': 'top' }}
          paint={{ 'text-color': tk.metro, 'text-halo-color': tk.surface, 'text-halo-width': 1.5 }}
        />
      </Source>
      <Source id="y-hubs" type="geojson" data={hubGeo}>
        <Layer
          id="y-hubs"
          type="circle"
          layout={vis(layers.crowd)}
          paint={{
            'circle-color': tk.crowd,
            'circle-opacity': 0.22,
            'circle-stroke-color': tk.crowd,
            'circle-stroke-width': 1.5,
            'circle-radius': ['interpolate', ['linear'], ['get', 'count'], 0, 8, 500, 30],
            'circle-radius-transition': { duration: 800 },
          }}
        />
        <Layer
          id="y-hubs-label"
          type="symbol"
          layout={{ ...vis(layers.crowd), 'text-field': ['get', 'label'], 'text-font': ['Noto Sans Bold'], 'text-size': 12, 'text-allow-overlap': true }}
          paint={{ 'text-color': tk.crowd, 'text-halo-color': tk.surface, 'text-halo-width': 2 }}
        />
      </Source>

      {layers.cams &&
        camHubs.data?.map((h) => (
          <Marker key={h.id} longitude={h.lon} latitude={h.lat} anchor="bottom-left" offset={[10, -10]}>
            <span title={`Kamera ${h.camera}, ${h.name}`} className="inline-flex items-center gap-1 rounded-md border border-line bg-surface px-1.5 py-0.5 text-[11px] font-semibold text-ink-2 shadow-card">
              <svg viewBox="0 0 24 24" className="size-3 fill-none stroke-current stroke-2" aria-hidden>
                <path d="M3 8h3l2-3h8l2 3h3v11H3z" />
                <circle cx="12" cy="13" r="3.5" />
              </svg>
              {h.camera}
            </span>
          </Marker>
        ))}
      {/* AI təklifləri: proposals arrive in WEB-1.7 (proposal.created); layer is empty until then. */}
      {layers.buses && <Buses onClick={onBusClick} />}
    </Map>
  );
}

type Pt = [number, number]; // [lon, lat]
interface Track { from: Pt; to: Pt; t0: number; bus: LiveBus }

const lerp = (a: Pt, b: Pt, k: number): Pt => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k];
const at = (tr: Track, now: number, ms: number) => lerp(tr.from, tr.to, ms ? Math.min(1, (now - tr.t0) / ms) : 1);

/** One bus of route 88 and one of route 4, same bort every tick, eased between 2 s SSE frames. */
function Buses({ onClick }: { onClick: (route: string) => void }) {
  const { data } = useBuses();
  const chosen = useRef<Record<string, string>>({});
  const tracks = useRef(new globalThis.Map<string, Track>());
  const [now, setNow] = useState(0);

  useEffect(() => {
    if (!data) return;
    const ms = matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : MOVE_MS;
    const t = performance.now();
    const keep = new Set<string>();
    for (const route of SHOWN_ROUTES) {
      const own = data.filter((b) => b.route === route);
      const bus = own.find((b) => b.bort === chosen.current[route]) ?? [...own].sort((a, b) => a.bort.localeCompare(b.bort))[0];
      if (!bus) continue;
      chosen.current[route] = bus.bort;
      keep.add(bus.bort);
      const prev = tracks.current.get(bus.bort);
      const to: Pt = [bus.lon, bus.lat];
      tracks.current.set(bus.bort, { from: prev ? at(prev, t, ms) : to, to, t0: t, bus });
    }
    for (const k of tracks.current.keys()) if (!keep.has(k)) tracks.current.delete(k);
    if (!ms) return setNow(t);
    let raf = 0;
    const step = (x: number) => {
      setNow(x);
      if (x - t < ms) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [data]);

  const ms = typeof window !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : MOVE_MS;
  return [...tracks.current.values()].map((tr) => {
    const [lon, lat] = at(tr, now, ms);
    const { bus } = tr;
    const e = bus.type === 'ELECTRIC';
    return (
      <Marker key={bus.bort} longitude={lon} latitude={lat} anchor="center">
        <button
          type="button"
          onClick={() => onClick(bus.route)}
          title={`${bus.route}, ${bus.bort}: ${bus.occupancy}% dolu`}
          aria-label={`Marşrut ${bus.route}, bort ${bus.bort}. Doluluğa bax`}
          className="relative grid size-9 cursor-pointer place-items-center"
        >
          <svg viewBox="0 0 36 36" className={`absolute inset-0 ${e ? 'fill-bus-e' : 'fill-bus-d'}`} style={{ transform: `rotate(${bus.bearing}deg)` }} aria-hidden>
            <path d="M18 0 24 8H12z" />
          </svg>
          <span className={`grid h-[22px] min-w-[30px] place-items-center rounded-md border-2 border-on-color px-1 text-xs font-extrabold text-on-color shadow-card ${e ? 'bg-bus-e' : 'bg-bus-d'}`}>{bus.route}</span>
        </button>
      </Marker>
    );
  });
}
