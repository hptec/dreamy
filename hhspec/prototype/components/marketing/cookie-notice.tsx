'use client'

import { useState, useEffect } from 'react'

export function CookieNotice() {
  const [show, setShow] = useState(false)

  useEffect(() => {
    if (!localStorage.getItem('dreamy_cookie_ok')) {
      const t = setTimeout(() => setShow(true), 1500)
      return () => clearTimeout(t)
    }
  }, [])

  const accept = () => {
    localStorage.setItem('dreamy_cookie_ok', '1')
    setShow(false)
  }

  if (!show) return null

  return (
    <div className="fixed bottom-4 left-4 right-4 z-50 mx-auto max-w-2xl animate-fadeup rounded-sm border border-line bg-surface p-5 shadow-lift sm:flex sm:items-center sm:gap-6">
      <p className="text-sm text-ink-soft">
        We use cookies to give you the best experience and show you the gowns you’ll love. By continuing, you agree to our cookie policy.
      </p>
      <div className="mt-3 flex shrink-0 gap-2 sm:mt-0">
        <button onClick={accept} className="cursor-pointer rounded-sm bg-ink px-5 py-2 text-xs font-medium uppercase tracking-luxe text-canvas transition-colors hover:bg-gold-deep">Accept</button>
        <button onClick={accept} className="cursor-pointer rounded-sm border border-line px-5 py-2 text-xs font-medium uppercase tracking-luxe transition-colors hover:border-ink">Decline</button>
      </div>
    </div>
  )
}
