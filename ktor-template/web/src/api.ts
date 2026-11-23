export const baseUrl = import.meta.env.BASE_URL

interface Health {
  name: string
  status: string
}

interface Greeting {
  message: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, { cache: 'no-store', ...init })
  if (!response.headers.get('Content-Type')?.includes('application/json')) {
    throw new Error(`服务未返回 JSON（HTTP ${response.status}）`)
  }

  const body = await response.json()
  if (!response.ok) {
    throw new Error(typeof body?.message === 'string' ? body.message : `请求失败（HTTP ${response.status}）`)
  }
  return body as T
}

export const getHealth = () => request<Health>('health')

export const sendGreeting = (name: string) => request<Greeting>('api/hello', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name }),
})
