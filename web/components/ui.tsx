import type { ComponentProps, ReactNode } from 'react';

type ButtonVariant = 'default' | 'p' | 'q';

export function Button({
  variant = 'default',
  sm,
  className = '',
  ...props
}: ComponentProps<'button'> & { variant?: ButtonVariant; sm?: boolean }) {
  const v = {
    default: 'border-line bg-surface hover:bg-surface-2',
    p: 'border-primary bg-primary text-primary-ink hover:brightness-106',
    q: 'border-transparent bg-transparent text-ink-2 hover:bg-surface-2',
  }[variant];
  const size = sm ? 'h-[30px] px-2.5 text-[13px] rounded-lg' : 'h-9 px-3.5 rounded-[9px]';
  return (
    <button
      className={`inline-flex cursor-pointer items-center justify-center gap-2 whitespace-nowrap border font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${v} ${size} ${className}`}
      {...props}
    />
  );
}

export function Panel({ title, meta, actions, flush, children, className = '' }: {
  title?: ReactNode; meta?: ReactNode; actions?: ReactNode; flush?: boolean; children?: ReactNode; className?: string;
}) {
  return (
    <div className={`rounded-[14px] border border-line bg-surface shadow-card ${className}`}>
      {title !== undefined && (
        <div className="flex items-center gap-2.5 border-b border-line px-4 py-3.5">
          <h2 className="m-0 text-[15px] font-semibold">{title}</h2>
          {meta && <span className="text-xs text-ink-3">{meta}</span>}
          {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={flush ? '' : 'p-4'}>{children}</div>
    </div>
  );
}

export function DemoBadge() {
  return <span className="rounded-md border border-dashed border-line px-2 py-0.5 text-xs text-ink-3">Demo data</span>;
}

export type StatusKind = 'ok' | 'warn' | 'bad' | 'info' | 'crowd' | 'neutral';
const DOT: Record<StatusKind, string> = { ok: 'bg-ok', warn: 'bg-warn', bad: 'bg-bad', info: 'bg-primary', crowd: 'bg-crowd', neutral: 'bg-ink-3' };

export function Status({ kind = 'neutral', children }: { kind?: StatusKind; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-[7px] whitespace-nowrap text-[13px] font-medium">
      <span className={`size-2 rounded-full ${DOT[kind]}`} />
      {children}
    </span>
  );
}

export function PageHeader({ title, desc, actions }: { title: string; desc?: string; actions?: ReactNode }) {
  return (
    <div className="mb-[18px] flex items-end gap-4">
      <div>
        <h1 className="m-0 mb-0.5 text-[22px] font-bold tracking-[-.01em]">{title}</h1>
        {desc && <p className="m-0 max-w-[70ch] text-ink-2">{desc}</p>}
      </div>
      {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Kpi({ label, value, sub, tone, subTone }: { label: string; value: ReactNode; sub?: ReactNode; tone?: 'bad' | 'ok'; subTone?: 'bad' | 'ok' }) {
  return (
    <div className="rounded-[14px] border border-line bg-surface px-4 py-3.5 shadow-card">
      <div className="text-[13px] text-ink-2">{label}</div>
      <div className={`my-0.5 text-[26px] font-bold tracking-[-.02em] ${tone === 'bad' ? 'text-bad' : tone === 'ok' ? 'text-ok' : ''}`}>{value}</div>
      {sub && <div className={`text-xs ${subTone === 'bad' ? 'text-bad' : subTone === 'ok' ? 'text-ok' : 'text-ink-3'}`}>{sub}</div>}
    </div>
  );
}

export function Seg<T extends string>({ options, value, onChange, label }: {
  options: readonly (readonly [T, string])[]; value: T; onChange: (v: T) => void; label?: string;
}) {
  return (
    <div role="group" aria-label={label} className="inline-flex gap-0.5 rounded-[10px] border border-line bg-surface-2 p-[3px]">
      {options.map(([k, l]) => (
        <button
          key={k}
          type="button"
          aria-pressed={k === value}
          onClick={() => onChange(k)}
          className={`cursor-pointer rounded-[7px] px-3 py-1.5 text-[13px] ${
            k === value ? 'bg-surface font-semibold text-ink shadow-card' : 'font-medium text-ink-2 hover:text-ink'
          }`}
        >
          {l}
        </button>
      ))}
    </div>
  );
}

const ELECTRIC = ['88', '33', 'H1', 'M1'];
export type PlateKind = 'e' | 'd' | 'x' | 'm';
const PLATE_BG: Record<PlateKind, string> = { e: 'bg-bus-e', d: 'bg-bus-d', x: 'bg-primary', m: 'bg-metro' };

/** Route plate; kind defaults to electric (green) / diesel (red) by route number as in the mockup. */
export function Plate({ route, kind, lg }: { route: string; kind?: PlateKind; lg?: boolean }) {
  const k = kind ?? (ELECTRIC.includes(route) ? 'e' : 'd');
  const size = lg ? 'h-[38px] min-w-14 rounded-[9px] text-lg' : 'h-6 min-w-[34px] rounded-md text-xs';
  return <span className={`inline-grid place-items-center px-[7px] font-extrabold tracking-[.01em] text-on-color ${PLATE_BG[k]} ${size}`}>{route}</span>;
}

/** Progress bar, `value` 0–100; `color` is a token bg class (bg-ok, bg-jam...). */
export function Bar({ value, color = 'bg-primary', label }: { value: number; color?: string; label?: string }) {
  const v = Math.max(0, Math.min(100, value));
  return (
    <div role="meter" aria-valuenow={Math.round(v)} aria-valuemin={0} aria-valuemax={100} aria-label={label} className="h-1.5 min-w-[70px] overflow-hidden rounded bg-surface-3">
      <i className={`block h-full rounded ${color}`} style={{ width: `${v}%` }} />
    </div>
  );
}

/** Table with header and loading / error / empty rows. Rows: <tr><td className={TD}>. */
export function Table({ head, loading, error, onRetry, empty, isEmpty, children }: {
  head: string[]; loading?: boolean; error?: string | null; onRetry?: () => void; empty: ReactNode; isEmpty?: boolean; children?: ReactNode;
}) {
  const msg = loading ? (
    'Yüklənir…'
  ) : error ? (
    <>
      <span className="text-bad">{error}</span>
      {onRetry && <Button sm className="ml-3" onClick={onRetry}>Yenidən cəhd et</Button>}
    </>
  ) : isEmpty ? (
    empty
  ) : null;
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            {head.map((h) => (
              <th key={h} className="whitespace-nowrap border-b border-line bg-surface px-4 py-2.5 text-left text-xs font-medium text-ink-3">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="[&_tr:last-child>td]:border-b-0">
          {msg ? (
            <tr>
              <td colSpan={head.length} className="p-10 text-center text-ink-2">{msg}</td>
            </tr>
          ) : (
            children
          )}
        </tbody>
      </table>
    </div>
  );
}
export const TD = 'whitespace-nowrap border-b border-line px-4 py-[11px] align-middle';
export const TR_CLICK = 'cursor-pointer hover:[&>td]:bg-surface-2 focus-visible:[&>td]:bg-surface-2';

export function Pager({ page, pages, onPage, extra }: { page: number; pages: number; onPage: (p: number) => void; extra?: ReactNode }) {
  return (
    <div className="flex items-center gap-2 border-t border-line px-4 py-2.5 text-[13px] text-ink-2">
      <span>Səhifə {page + 1} / {pages}</span>
      <span className="flex-1" />
      {extra}
      <Button sm disabled={page === 0} onClick={() => onPage(page - 1)}>Əvvəlki</Button>
      <Button sm disabled={page >= pages - 1} onClick={() => onPage(page + 1)}>Növbəti</Button>
    </div>
  );
}

/** Layer chip (mockup .toggle): bordered pill with a checkbox square. */
export function Toggle({ on, onChange, children, className = '' }: { on: boolean; onChange: (v: boolean) => void; children: ReactNode; className?: string }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={() => onChange(!on)}
      className={`inline-flex cursor-pointer items-center gap-2 rounded-[20px] border bg-surface py-[5px] pr-3 pl-2 text-[13px] font-medium ${
        on ? 'border-primary text-ink' : 'border-line text-ink-2'
      } ${className}`}
    >
      <i className={`grid size-3.5 place-items-center rounded-[4px] border-[1.5px] ${on ? 'border-primary bg-primary' : 'border-ink-3'}`}>
        {on && <span className="h-[3px] w-1.5 translate-x-px -translate-y-px -rotate-45 border-b-2 border-l-2 border-on-color" />}
      </i>
      {children}
    </button>
  );
}

/** Visual switch track (no role); for items that carry their own role, e.g. menuitemcheckbox. */
export function SwitchTrack({ on }: { on: boolean }) {
  return (
    <span aria-hidden="true" className={`relative h-[22px] w-[38px] shrink-0 rounded-xl transition-colors ${on ? 'bg-primary' : 'bg-surface-3'}`}>
      <span className={`absolute top-[3px] left-[3px] size-4 rounded-full bg-on-color shadow-card transition-transform ${on ? 'translate-x-4' : ''}`} />
    </span>
  );
}

/** On/off switch with its label (mockup .switch), e.g. auto mode. */
export function Switch({ on, onChange, children, disabled }: { on: boolean; onChange: (v: boolean) => void; children: ReactNode; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      disabled={disabled}
      onClick={() => onChange(!on)}
      className="inline-flex cursor-pointer items-center gap-2.5 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
    >
      <SwitchTrack on={on} />
      {children}
    </button>
  );
}

/** 12480 → "12 480", 4.2 → "4,2" (08_DESIGN_SYSTEM §6). */
export const fmtNum = (n: number, digits = 0) =>
  n.toFixed(digits).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

/** Mockup .note: grey info box with a leading icon. */
export function Note({ icon = 'i', children }: { icon?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-line bg-surface-2 px-3.5 py-3 text-[13px] text-ink-2 [&_b]:text-ink">
      <span aria-hidden="true">{icon}</span>
      <span>{children}</span>
    </div>
  );
}

/** Yolçu Coin icon (mockup .coin): yellow circle with "Y". */
export function CoinIcon() {
  return <span aria-hidden="true" className="inline-grid size-[18px] place-items-center rounded-full bg-coin align-[-3px] text-[10px] font-extrabold text-on-color">Y</span>;
}
