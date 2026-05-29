import { createI18n } from 'vue-i18n'
import en from './locales/en.js'
import es from './locales/es.js'
import fr from './locales/fr.js'
import de from './locales/de.js'

export const LOCALES = [
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' },
]

const saved = (typeof localStorage !== 'undefined' && localStorage.getItem('me_locale')) || 'en'

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: saved,
  fallbackLocale: 'en',
  messages: { en, es, fr, de },
})

export function setLocale(code) {
  i18n.global.locale.value = code
  if (typeof localStorage !== 'undefined') localStorage.setItem('me_locale', code)
  if (typeof document !== 'undefined') document.documentElement.setAttribute('lang', code)
}
