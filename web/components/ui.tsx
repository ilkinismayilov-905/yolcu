import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonVariant = 'default' | 'p' | 'q';

export function Button({
  variant = 'default',
  sm,
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; sm?: boolean }) {
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
