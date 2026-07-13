'use client'

import { useId, useState } from 'react'
import { request } from '@/lib/api/client'
import type { Locale } from '@/lib/api/types'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const HOME_BLOCK_SOURCE = 4

const statusCopy: Record<Locale, { invalid: string; success: string; failure: string; submitting: string }> = {
  en: {
    invalid: 'Please enter a valid email address.',
    success: "You're on the list.",
    failure: 'We could not subscribe you. Please try again.',
    submitting: 'Subscribing...',
  },
  es: {
    invalid: 'Introduce un correo electronico valido.',
    success: 'Ya estas en la lista.',
    failure: 'No pudimos completar la suscripcion. Intentalo de nuevo.',
    submitting: 'Suscribiendo...',
  },
  fr: {
    invalid: 'Saisissez une adresse e-mail valide.',
    success: 'Votre inscription est confirmee.',
    failure: "L'inscription a echoue. Veuillez reessayer.",
    submitting: 'Inscription...',
  },
}

export function HomeNewsletterForm({
  locale,
  placeholder,
  cta,
}: {
  locale: Locale
  placeholder?: string | null
  cta?: string | null
}) {
  const inputId = useId()
  const copy = statusCopy[locale]
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [subscribed, setSubscribed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedEmail = email.trim()
    if (!EMAIL_RE.test(normalizedEmail)) {
      setError(copy.invalid)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await request<{ subscribed: boolean }>('/api/store/newsletter', {
        method: 'POST',
        body: { email: normalizedEmail, source: HOME_BLOCK_SOURCE, locale },
      })
      setSubscribed(true)
    } catch {
      setError(copy.failure)
    } finally {
      setSubmitting(false)
    }
  }

  if (subscribed) {
    return (
      <p className="mt-8 rounded-sm bg-sage/15 px-4 py-3 text-sm text-sage-deep" role="status">
        {copy.success}
      </p>
    )
  }

  return (
    <form className="mt-8" onSubmit={submit} noValidate>
      <div className="flex flex-col gap-3 sm:flex-row">
        <label htmlFor={inputId} className="sr-only">Email</label>
        <input
          id={inputId}
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(event) => {
            setEmail(event.target.value)
            if (error) setError(null)
          }}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${inputId}-error` : undefined}
          placeholder={placeholder || 'Your email'}
          className={`flex-1 rounded-sm border bg-canvas px-4 py-3 text-sm focus:border-gold focus:outline-none ${error ? 'border-blush' : 'border-line'}`}
        />
        <button type="submit" disabled={submitting} className="btn-primary disabled:cursor-not-allowed disabled:opacity-60">
          {submitting ? copy.submitting : (cta || 'Subscribe')}
        </button>
      </div>
      {error && <p id={`${inputId}-error`} className="mt-2 text-left text-xs text-blush" role="alert">{error}</p>}
    </form>
  )
}
