type IconProps = { size?: number };

const base = (size = 20) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
});

export function IconZap({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
    </svg>
  );
}

export function IconCart({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="9" cy="20" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="18" cy="20" r="1.4" fill="currentColor" stroke="none" />
      <path d="M3 4h2l2.2 11.2a2 2 0 0 0 2 1.6h7.6a2 2 0 0 0 2-1.6L21 8H6" />
    </svg>
  );
}

export function IconShield({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M12 3 4.5 6v6c0 4.5 3 7.5 7.5 9 4.5-1.5 7.5-4.5 7.5-9V6L12 3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export function IconChartUp({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 19h16" />
      <path d="m5 15 4-4 3 3 6-7" />
      <path d="M14 7h4v4" />
    </svg>
  );
}

export function IconCard({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <rect x="3" y="6" width="18" height="13" rx="2.2" />
      <path d="M3 10.5h18" />
      <path d="M7 15h4" />
    </svg>
  );
}

export function IconCursorClick({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M6 4v3M4 8H1M6.5 6.5 4.4 4.4" />
      <path d="M13 13.5 21 16l-3.4 1.3L16.3 21 13 13.5Z" />
    </svg>
  );
}

export function IconLayers({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="m12 3 8 4.5-8 4.5-8-4.5L12 3Z" />
      <path d="m4 12 8 4.5 8-4.5" />
      <path d="m4 16.5 8 4.5 8-4.5" />
    </svg>
  );
}

export function IconLock({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}

export function IconUser({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="12" cy="8" r="3.4" />
      <path d="M5 20c1.4-4 4-6 7-6s5.6 2 7 6" />
    </svg>
  );
}

export function IconTruck({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M3 7h11v8H3z" />
      <path d="M14 10h4l3 3v2h-7" />
      <circle cx="7" cy="18" r="1.6" />
      <circle cx="17" cy="18" r="1.6" />
    </svg>
  );
}

export function IconWallet({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 7a2 2 0 0 1 2-2h11a1 1 0 0 1 1 1v2" />
      <rect x="3" y="7" width="18" height="12" rx="2" />
      <circle cx="16" cy="13" r="1.4" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function IconArrowRight({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 12h15M13 6l6 6-6 6" />
    </svg>
  );
}

export function IconChevron({ size, direction = "left" }: IconProps & { direction?: "left" | "right" }) {
  return (
    <svg {...base(size)}>
      <path d={direction === "left" ? "M14 6 8 12l6 6" : "M10 6l6 6-6 6"} />
    </svg>
  );
}

export function IconPlay({ size }: IconProps) {
  return (
    <svg {...base(size)} fill="currentColor" stroke="none">
      <path d="M8 5.5v13l11-6.5-11-6.5Z" />
    </svg>
  );
}

export function IconMessage({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 5h16v11H8l-4 4V5Z" />
    </svg>
  );
}

export function IconCheckBadge({ size }: IconProps) {
  return (
    <svg {...base(size)} fill="currentColor" stroke="none">
      <circle cx="12" cy="12" r="10" />
      <path d="m8.5 12.3 2.4 2.4 4.6-5.4" stroke="#fff" strokeWidth="1.8" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconSparkArrow({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M17 7 7 17M7 7h10v10" />
    </svg>
  );
}
