// Fixed exchange rates (prototype) — base currency is USD.
export const CURRENCIES = [
  { code: 'USD', symbol: '$', label: 'US Dollar', rate: 1.0, locale: 'en-US' },
  { code: 'EUR', symbol: '€', label: 'Euro', rate: 0.92, locale: 'de-DE' },
  { code: 'GBP', symbol: '£', label: 'British Pound', rate: 0.78, locale: 'en-GB' },
  { code: 'CAD', symbol: 'C$', label: 'Canadian Dollar', rate: 1.35, locale: 'en-CA' },
  { code: 'AUD', symbol: 'A$', label: 'Australian Dollar', rate: 1.5, locale: 'en-AU' },
]

export const CURRENCY_MAP = Object.fromEntries(CURRENCIES.map((c) => [c.code, c]))

// Region -> default currency + language
export const REGIONS = [
  { code: 'US', label: 'United States', currency: 'USD', locale: 'en', flag: '🇺🇸' },
  { code: 'CA', label: 'Canada', currency: 'CAD', locale: 'en', flag: '🇨🇦' },
  { code: 'GB', label: 'United Kingdom', currency: 'GBP', locale: 'en', flag: '🇬🇧' },
  { code: 'DE', label: 'Germany', currency: 'EUR', locale: 'de', flag: '🇩🇪' },
  { code: 'FR', label: 'France', currency: 'EUR', locale: 'fr', flag: '🇫🇷' },
  { code: 'ES', label: 'Spain', currency: 'EUR', locale: 'es', flag: '🇪🇸' },
  { code: 'AU', label: 'Australia', currency: 'AUD', locale: 'en', flag: '🇦🇺' },
]

// Convert a USD base amount to the target currency, applying brand price-ending policy.
export function convert(usdAmount, code) {
  const cur = CURRENCY_MAP[code] || CURRENCY_MAP.USD
  const raw = usdAmount * cur.rate
  // Brand policy: premium line (>= 500) ends .00; entry line ends .99
  if (usdAmount >= 500) return Math.round(raw)
  return Math.floor(raw) + 0.99
}

export function formatMoney(usdAmount, code) {
  const cur = CURRENCY_MAP[code] || CURRENCY_MAP.USD
  const value = convert(usdAmount, code)
  try {
    return new Intl.NumberFormat(cur.locale, {
      style: 'currency',
      currency: cur.code,
      minimumFractionDigits: value % 1 === 0 ? 0 : 2,
      maximumFractionDigits: 2,
    }).format(value)
  } catch (e) {
    return `${cur.symbol}${value.toFixed(value % 1 === 0 ? 0 : 2)}`
  }
}
