/**
 * 内部缓存失效端点（FLOW-P03 / s-758 消费侧）：
 * MQ 失效消费者（backend content_publish_invalidate）调 POST /api/revalidate {paths:[...]}
 * → revalidatePath 逐路径再生（ISR on-demand，秒级失效链；TTL 300s 为兜底）。
 * 鉴权：x-revalidate-token 与 REVALIDATE_TOKEN 环境变量比对（内部端点，不走 JWT）。
 */

import { revalidatePath } from 'next/cache'
import { NextResponse } from 'next/server'

export async function POST(req: Request) {
  const token = req.headers.get('x-revalidate-token')
  const expected = process.env.REVALIDATE_TOKEN
  if (!expected || !token || token !== expected) {
    return NextResponse.json({ code: 40100, message: 'unauthorized', data: null }, { status: 401 })
  }

  let body: unknown = null
  try {
    body = await req.json()
  } catch {
    body = null
  }
  const rawPaths = (body as { paths?: unknown } | null)?.paths
  const paths = Array.isArray(rawPaths)
    ? rawPaths.filter((p): p is string => typeof p === 'string' && p.startsWith('/') && p.length <= 512)
    : []

  if (paths.length === 0) {
    return NextResponse.json({ code: 42201, message: 'paths required', data: null }, { status: 422 })
  }

  const revalidated: string[] = []
  for (const p of paths.slice(0, 100)) {
    try {
      revalidatePath(p)
      revalidated.push(p)
    } catch {
      /* 单路径失败不阻断整批 */
    }
  }

  return NextResponse.json({ code: 0, message: 'ok', data: { revalidated } })
}
