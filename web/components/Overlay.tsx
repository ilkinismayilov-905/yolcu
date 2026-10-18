'use client';

import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react';

/** Focus the dialog when it opens, give focus back when it closes. */
function useDialogFocus(open: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    ref.current?.focus();
    return () => prev?.focus?.();
  }, [open]);
  return ref;
}

const onEsc = (close: () => void) => (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    e.stopPropagation();
    close();
  }
};

function Scrim({ open, onClose, z }: { open: boolean; onClose: () => void; z: string }) {
  return (
    <div
      aria-hidden="true"
      onClick={onClose}
      className={`fixed inset-0 bg-scrim transition-opacity duration-200 ${z} ${open ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
    />
  );
}

/** Right-side panel: header (meta, title, extra), scrollable body, footer actions. Esc / scrim / ✕ close. */
export function Drawer({ open, onClose, meta, title, extra, footer, children }: {
  open: boolean; onClose: () => void; meta?: ReactNode; title: ReactNode; extra?: ReactNode; footer?: ReactNode; children?: ReactNode;
}) {
  const id = useId();
  const ref = useDialogFocus(open);
  return (
    <>
      <Scrim open={open} onClose={onClose} z="z-40" />
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        tabIndex={-1}
        inert={!open}
        onKeyDown={onEsc(onClose)}
        className={`fixed inset-y-0 right-0 z-41 flex w-[min(460px,96vw)] flex-col border-l border-line bg-surface outline-0 transition-transform duration-250 ease-in-out ${
          open ? '' : 'translate-x-full'
        }`}
      >
        <div className="flex items-start gap-2.5 border-b border-line px-5 py-[18px]">
          <div className="flex-1">
            {meta && <div className="text-[13px] text-ink-3">{meta}</div>}
            <h3 id={id} className="m-0 text-[17px] font-semibold">{title}</h3>
            {extra && <div className="mt-1.5">{extra}</div>}
          </div>
          <button
            onClick={onClose}
            aria-label="Bağla"
            className="grid size-[38px] cursor-pointer place-items-center rounded-[10px] border border-line bg-surface text-ink-2"
          >
            ✕
          </button>
        </div>
        {/* minmax(0,1fr): grid items may shrink below their min-content, so truncated rows don't widen the drawer */}
        <div className="grid flex-1 grid-cols-[minmax(0,1fr)] content-start gap-4 overflow-auto p-5">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-line px-5 py-3.5">{footer}</div>}
      </div>
    </>
  );
}

/** Centered dialog: title, subtitle, body, footer (2 buttons). */
export function Modal({ open, onClose, title, sub, footer, children }: {
  open: boolean; onClose: () => void; title: ReactNode; sub?: ReactNode; footer?: ReactNode; children?: ReactNode;
}) {
  const id = useId();
  const ref = useDialogFocus(open);
  return (
    <>
      <Scrim open={open} onClose={onClose} z="z-42" />
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        tabIndex={-1}
        inert={!open}
        onKeyDown={onEsc(onClose)}
        className={`fixed top-1/2 left-1/2 z-43 w-[min(460px,92vw)] -translate-x-1/2 rounded-2xl border border-line bg-surface outline-0 transition duration-200 ${
          open ? '-translate-y-1/2 opacity-100' : 'pointer-events-none -translate-y-[48%] opacity-0'
        }`}
      >
        <div className="px-5 pt-[18px] pb-1.5">
          <h3 id={id} className="m-0 mb-1 text-[17px] font-semibold">{title}</h3>
          {sub && <div className="text-[13px] text-ink-2">{sub}</div>}
        </div>
        <div className="px-5 py-2">{children}</div>
        {footer && <div className="flex justify-end gap-2 px-5 py-3.5">{footer}</div>}
      </div>
    </>
  );
}
