// Brand assets live in web/public/brand (see docs/08_DESIGN_SYSTEM.md "Brend").
// Plain <img>: static SVGs, no optimisation needed, and it works in the static export.

/** Square mark (white tile, red/green road "Y"). Readable on light and dark backgrounds. */
export function LogoMark({ size = 32, className = '' }: { size?: number; className?: string }) {
  return <img src="/brand/logo-icon.svg" alt="Yolçu" width={size} height={size} className={`block flex-none rounded-[22%] ${className}`} />;
}

/** Full stacked logo (mark + wordmark). The light/dark file is swapped by theme CSS (.logo-on-light / .logo-on-dark). */
export function LogoFull({ height = 120, className = '' }: { height?: number; className?: string }) {
  const width = Math.round((height * 508) / 626); // source viewBox 508 × 626
  return (
    <span className={`inline-block ${className}`} style={{ width, height }}>
      <img src="/brand/logo-light.svg" alt="Yolçu" width={width} height={height} className="logo-on-light block" />
      <img src="/brand/logo-dark.svg" alt="Yolçu" width={width} height={height} className="logo-on-dark block" />
    </span>
  );
}
