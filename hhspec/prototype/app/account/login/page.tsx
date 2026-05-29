'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { cn } from '@/lib/utils'

export default function LoginPage() {
  const router = useRouter()
  const [mode, setMode] = useState<'login' | 'register'>('login')

  return (
    <div className="grid min-h-[80vh] lg:grid-cols-2">
      {/* Visual */}
      <div className="relative hidden lg:block">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="Outdoor bride" className="h-full w-full object-cover" />
        <div className="absolute inset-0 bg-ink/25" />
        <div className="absolute bottom-12 left-12 text-canvas">
          <p className="font-display text-4xl font-medium leading-tight">Your story<br />starts here</p>
        </div>
      </div>

      {/* Form */}
      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <Link href="/" className="font-display text-2xl font-semibold">Dreamy</Link>
          <div className="mt-8 flex gap-1 rounded-sm bg-muted p-1">
            {(['login', 'register'] as const).map((m) => (
              <button key={m} onClick={() => setMode(m)} className={cn('flex-1 cursor-pointer rounded-sm py-2 text-sm font-medium capitalize transition-colors', mode === m ? 'bg-surface shadow-soft' : 'text-ink-soft')}>
                {m === 'login' ? 'Sign In' : 'Create Account'}
              </button>
            ))}
          </div>

          <form onSubmit={(e) => { e.preventDefault(); router.push('/account') }} className="mt-6 space-y-4">
            {mode === 'register' && (
              <div>
                <label htmlFor="name" className="eyebrow mb-1.5 block">Full Name</label>
                <input id="name" required className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
              </div>
            )}
            <div>
              <label htmlFor="email" className="eyebrow mb-1.5 block">Email</label>
              <input id="email" type="email" required defaultValue="jane@email.com" className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
            </div>
            <div>
              <label htmlFor="password" className="eyebrow mb-1.5 block">Password</label>
              <input id="password" type="password" required defaultValue="password" className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
            </div>
            {mode === 'login' && <Link href="#" className="block text-right text-xs text-gold-deep underline">Forgot password?</Link>}
            <button type="submit" className="btn-primary w-full">{mode === 'login' ? 'Sign In' : 'Create Account'}</button>
          </form>

          <p className="mt-6 text-center text-xs text-ink-soft">
            {mode === 'login' ? "New to Dreamy? " : 'Already have an account? '}
            <button onClick={() => setMode(mode === 'login' ? 'register' : 'login')} className="cursor-pointer text-gold-deep underline">
              {mode === 'login' ? 'Create an account' : 'Sign in'}
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}
