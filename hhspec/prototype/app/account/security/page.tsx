'use client'

import { useState } from 'react'
import { Check, Laptop, Smartphone, Tablet, ShieldCheck, LogOut } from 'lucide-react'
import { linkedAccounts as seedAccounts, sessions as seedSessions, type AuthProvider, type LinkedAccount, type Session } from '@/data/account'
import { cn } from '@/lib/utils'

function ProviderMark({ provider }: { provider: AuthProvider }) {
  if (provider === 'google')
    return (
      <svg viewBox="0 0 24 24" className="h-5 w-5">
        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.76h3.56c2.08-1.92 3.28-4.74 3.28-8.09Z" />
        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.56-2.76c-.98.66-2.24 1.06-3.72 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23Z" />
        <path fill="#FBBC05" d="M5.84 14.1a6.6 6.6 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84Z" />
        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38Z" />
      </svg>
    )
  if (provider === 'apple')
    return (
      <svg viewBox="0 0 24 24" className="h-5 w-5 fill-ink">
        <path d="M16.36 12.78c-.02-2.3 1.88-3.4 1.96-3.46-1.07-1.56-2.73-1.78-3.32-1.8-1.41-.14-2.76.83-3.47.83-.72 0-1.82-.81-2.99-.79-1.54.02-2.96.9-3.75 2.27-1.6 2.78-.41 6.9 1.15 9.16.76 1.1 1.67 2.34 2.86 2.3 1.15-.05 1.58-.74 2.97-.74 1.38 0 1.77.74 2.98.72 1.23-.02 2.01-1.12 2.76-2.23.87-1.28 1.23-2.52 1.25-2.58-.03-.01-2.4-.92-2.42-3.65ZM14.09 5.8c.63-.77 1.06-1.83.94-2.9-.91.04-2.02.61-2.67 1.37-.58.68-1.09 1.77-.95 2.81 1.02.08 2.05-.52 2.68-1.28Z" />
      </svg>
    )
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5 stroke-ink" fill="none" strokeWidth="1.8">
      <rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" />
    </svg>
  )
}

function deviceIcon(device: string) {
  if (/iphone|pixel|galaxy|phone/i.test(device)) return Smartphone
  if (/ipad|tab/i.test(device)) return Tablet
  return Laptop
}

export default function SecurityPage() {
  const [accounts, setAccounts] = useState<LinkedAccount[]>(seedAccounts)
  const [list, setList] = useState<Session[]>(seedSessions)

  const connectedCount = accounts.filter((a) => a.connected).length

  function toggle(provider: AuthProvider) {
    setAccounts((prev) =>
      prev.map((a) => {
        if (a.provider !== provider) return a
        if (a.isPrimary) return a // 主邮箱不可解绑
        if (a.connected && connectedCount <= 1) return a // 至少保留一种
        return a.connected
          ? { ...a, connected: false, identifier: '', lastUsed: undefined }
          : {
              ...a,
              connected: true,
              lastUsed: 'Just now',
              identifier: provider === 'google' ? 'jane.doe@gmail.com' : a.hiddenEmail ? 'Hidden (private relay)' : 'jane@email.com'
            }
      })
    )
  }

  function revoke(id: string) {
    setList((prev) => prev.filter((s) => s.id !== id))
  }
  function revokeOthers() {
    setList((prev) => prev.filter((s) => s.current))
  }

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">Login &amp; Security</h1>
      <p className="mt-1 text-ink-soft">Manage how you sign in and the devices connected to your account.</p>

      {/* Linked login methods */}
      <section className="mt-8">
        <div className="flex items-center gap-2">
          <ShieldCheck className="h-4 w-4 text-gold-deep" />
          <h2 className="font-display text-xl font-medium">Login methods</h2>
        </div>
        <p className="mt-1 text-sm text-ink-soft">Connect multiple methods — they all sign you into the same account.</p>

        <div className="mt-4 space-y-3">
          {accounts.map((a) => {
            const locked = a.isPrimary || (a.connected && connectedCount <= 1)
            return (
              <div key={a.provider} className="flex items-center gap-4 rounded-sm border border-line bg-surface p-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-muted">
                  <ProviderMark provider={a.provider} />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium">{a.label}</p>
                    {a.isPrimary && <span className="rounded-full bg-ink/85 px-2 py-0.5 text-[10px] uppercase tracking-luxe text-canvas">Primary</span>}
                    {a.verified && (
                      <span className="inline-flex items-center gap-0.5 rounded-full bg-sage/15 px-2 py-0.5 text-[10px] font-medium uppercase tracking-luxe text-sage">
                        <Check className="h-3 w-3" /> Verified
                      </span>
                    )}
                  </div>
                  <p className="mt-0.5 truncate text-xs text-ink-soft">
                    {a.connected ? a.identifier : 'Not connected'}
                    {a.connected && a.lastUsed && <span className="text-ink-faint"> · last used {a.lastUsed}</span>}
                  </p>
                  {a.provider === 'apple' && (
                    <p className="mt-1 text-[11px] text-ink-faint">Apple may hide your email with a private relay address.</p>
                  )}
                </div>
                <button
                  type="button"
                  disabled={locked}
                  onClick={() => toggle(a.provider)}
                  className={cn(
                    'shrink-0 rounded-sm border px-4 py-2 text-xs font-medium uppercase tracking-luxe transition-colors',
                    locked
                      ? 'cursor-not-allowed border-line text-ink-faint'
                      : a.connected
                        ? 'border-line text-ink hover:border-blush hover:text-blush'
                        : 'border-ink text-ink hover:bg-ink hover:text-canvas'
                  )}
                  title={a.isPrimary ? 'Primary email cannot be removed' : a.connected && connectedCount <= 1 ? 'Keep at least one login method' : undefined}
                >
                  {a.connected ? (a.isPrimary ? 'Primary' : 'Disconnect') : 'Connect'}
                </button>
              </div>
            )
          })}
        </div>
        <p className="mt-3 text-xs text-ink-faint">Keep at least one login method connected. Your primary email stays verified and cannot be removed.</p>
      </section>

      {/* Active sessions */}
      <section className="mt-12">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <Laptop className="h-4 w-4 text-gold-deep" />
              <h2 className="font-display text-xl font-medium">Devices &amp; sessions</h2>
            </div>
            <p className="mt-1 text-sm text-ink-soft">Where you&apos;re currently signed in.</p>
          </div>
          {list.length > 1 && (
            <button onClick={revokeOthers} className="inline-flex items-center gap-2 rounded-sm border border-line px-4 py-2 text-xs font-medium uppercase tracking-luxe text-ink transition-colors hover:border-blush hover:text-blush">
              <LogOut className="h-3.5 w-3.5" /> Sign out other devices
            </button>
          )}
        </div>

        <div className="mt-4 space-y-3">
          {list.map((s) => {
            const Icon = deviceIcon(s.device)
            return (
              <div key={s.id} className="flex items-center gap-4 rounded-sm border border-line bg-surface p-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-muted">
                  <Icon className="h-5 w-5 text-ink" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium">{s.device}</p>
                    {s.current && <span className="rounded-full bg-sage/15 px-2 py-0.5 text-[10px] font-medium uppercase tracking-luxe text-sage">This device</span>}
                    <span className="inline-flex items-center gap-1 text-[11px] text-ink-faint"><ProviderMark provider={s.method} /> via {s.method}</span>
                  </div>
                  <p className="mt-0.5 truncate text-xs text-ink-soft">{s.browser} · {s.location}</p>
                  <p className="text-xs text-ink-faint">{s.lastActive}</p>
                </div>
                {!s.current && (
                  <button onClick={() => revoke(s.id)} className="shrink-0 rounded-sm border border-line px-4 py-2 text-xs font-medium uppercase tracking-luxe text-ink transition-colors hover:border-blush hover:text-blush">
                    Sign out
                  </button>
                )}
              </div>
            )
          })}
        </div>
      </section>
    </div>
  )
}
