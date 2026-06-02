/**
 * snake_case ↔ camelCase 边界转换（shared-contracts field_mapping_rule）。
 * 后端 Jackson property-naming-strategy=SNAKE_CASE，线上 JSON 全 snake_case；
 * 前端在 API 客户端边界统一转换为 camelCase，禁止页面直接消费 snake_case 字段。
 */

function snakeToCamelKey(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, c: string) => c.toUpperCase())
}

function camelToSnakeKey(key: string): string {
  return key.replace(/([A-Z])/g, (m) => `_${m.toLowerCase()}`)
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    Object.getPrototypeOf(value) === Object.prototype
  )
}

/** 深度转换响应体 snake_case → camelCase */
export function deepCamelize<T = unknown>(input: unknown): T {
  if (Array.isArray(input)) {
    return input.map((v) => deepCamelize(v)) as unknown as T
  }
  if (isPlainObject(input)) {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(input)) {
      out[snakeToCamelKey(k)] = deepCamelize(v)
    }
    return out as T
  }
  return input as T
}

/** 深度转换请求体 camelCase → snake_case */
export function deepSnakeize<T = unknown>(input: unknown): T {
  if (Array.isArray(input)) {
    return input.map((v) => deepSnakeize(v)) as unknown as T
  }
  if (isPlainObject(input)) {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(input)) {
      out[camelToSnakeKey(k)] = deepSnakeize(v)
    }
    return out as T
  }
  return input as T
}
