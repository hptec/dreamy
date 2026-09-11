// admin/store REST API 客户端(release.sh 同源约定:JSON snake_case,R{code,data} 包裹)
// 用法:API_BASE=http://dreamy.cerestech.cn:60080/api node ...
const BASE = process.env.API_BASE ?? 'http://dreamy.cerestech.cn:60080/api'
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@dreamy.com'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'Admin@123456'

let token = ''

async function raw(method, path, body, auth = true) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'content-type': 'application/json',
      ...(auth && token ? { authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || (json.code !== undefined && json.code !== 0 && json.code !== 200)) {
    throw new Error(`${method} ${path} → ${res.status} ${JSON.stringify(json).slice(0, 300)}`)
  }
  return json.data ?? json
}

export async function login() {
  const res = await fetch(BASE + '/admin/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD })
  })
  const json = await res.json()
  const data = json.data ?? json
  if (!data?.token) throw new Error(`admin 登录失败: ${JSON.stringify(json).slice(0, 200)}`)
  token = data.token
  return data
}

export const get = (p) => raw('GET', p)
export const post = (p, b) => raw('POST', p, b)
export const put = (p, b) => raw('PUT', p, b)
export const patch = (p, b) => raw('PATCH', p, b)
export const del = (p) => raw('DELETE', p)

// 分页拉全量(通用列表端点均支持 page/page_size)
export async function listAll(path, key) {
  const out = []
  let page = 1
  for (;;) {
    const data = await get(`${path}${path.includes('?') ? '&' : '?'}page=${page}&page_size=100`)
    const rows = data[key] ?? data.list ?? data.records ?? []
    out.push(...rows)
    const total = data.total ?? out.length
    if (out.length >= total || rows.length === 0) return out
    page++
  }
}
