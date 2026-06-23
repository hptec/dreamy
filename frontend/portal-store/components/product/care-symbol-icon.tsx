// ISO 3758 textile care symbols — SVG line art keyed by the emoji stored in CareItem.symbol

const S = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.5,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

// Shared shape primitives
const Tub = () => <path d="M4 8h16l-2 11H6L4 8z" />
const Tri = () => <path d="M12 4L21 19H3L12 4z" />
const Circ = () => <circle cx="12" cy="12" r="9" />
const Iron = () => <path d="M4 16h16l2-6H10C6 10 4 12 4 16z" />
const Slash = () => <line x1="5" y1="19" x2="19" y2="5" />

const ICONS: Record<string, React.ReactNode> = {
  '🫧': (  // hand wash
    <svg {...S}><Tub /><path d="M8 13c1.3-1 2.7-1 4 0s2.7 1 4 0" /></svg>
  ),
  '🌀': (  // 30°C machine wash
    <svg {...S}><Tub /><path d="M8 12c1.3-1 2.7-1 4 0s2.7 1 4 0" /><path d="M8 14.5c1.3-1 2.7-1 4 0s2.7 1 4 0" /></svg>
  ),
  '🚫': (  // no wash
    <svg {...S}><Tub /><Slash /></svg>
  ),
  '🧊': (  // no bleach
    <svg {...S}><Tri /><line x1="8.5" y1="10" x2="15.5" y2="18" /><line x1="15.5" y1="10" x2="8.5" y2="18" /></svg>
  ),
  '△': (  // can bleach
    <svg {...S}><Tri /></svg>
  ),
  '🌡': (  // tumble dry low heat (circle + 1 dot)
    <svg {...S}><Circ /><circle cx="12" cy="12" r="2" fill="currentColor" stroke="none" /></svg>
  ),
  '🪝': (  // hang dry
    <svg {...S}>
      <rect x="3" y="6" width="18" height="15" />
      <path d="M12 6V3" />
      <path d="M9 3q1.5-2 3 0" />
    </svg>
  ),
  '❌': (  // no tumble dry
    <svg {...S}><Circ /><Slash /></svg>
  ),
  '♨': (  // low heat iron (1 dot)
    <svg {...S}><Iron /><circle cx="12" cy="14" r="1.5" fill="currentColor" stroke="none" /></svg>
  ),
  '💨': (  // steam iron
    <svg {...S}>
      <Iron />
      <path d="M9 9Q8.5 7 9 5" />
      <path d="M13 9q-.5-2 0-4" />
    </svg>
  ),
  '🚷': (  // no iron
    <svg {...S}><Iron /><Slash /></svg>
  ),
  '⭕': (  // dry clean only
    <svg {...S}><Circ /></svg>
  ),
  '⊗': (  // no dry clean
    <svg {...S}><Circ /><Slash /></svg>
  ),
}

export function CareSymbolIcon({ symbol, className = 'h-7 w-7' }: { symbol: string; className?: string }) {
  const icon = ICONS[symbol]
  if (!icon) return <span className="text-xl leading-none">{symbol}</span>
  return <span className={`inline-flex items-center justify-center ${className}`}>{icon}</span>
}

// symbol → i18n key（t.fabric.care[key]）。后端存的 label 是中文兜底，消费端按 symbol 取本地化文案。
export const CARE_SYMBOL_KEYS = {
  '🫧': 'handWashCold',
  '🌀': 'machineWash30',
  '🚫': 'doNotWash',
  '🧊': 'doNotBleach',
  '△': 'bleachOk',
  '🌡': 'tumbleDryLow',
  '🪝': 'lineDry',
  '❌': 'doNotTumbleDry',
  '♨': 'ironLow',
  '💨': 'steamOnly',
  '🚷': 'doNotIron',
  '⭕': 'dryCleanOnly',
  '⊗': 'doNotDryClean',
} as const

export type CareSymbolKey = (typeof CARE_SYMBOL_KEYS)[keyof typeof CARE_SYMBOL_KEYS]
