import { useAuthStore } from '@/stores/auth'

export async function apiPost(path: string, body: unknown) {
  const auth = useAuthStore()
  const headers: HeadersInit = { 'Content-Type': 'application/json' }

  // JWT があれば付与
  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`

  const res = await fetch(`${import.meta.env.VITE_API_BASE}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    // トークンが無効ならログアウト処理しても良い
    if (res.status === 401) auth.logout()
    throw new Error(`API Error: ${res.status}`)
  }
  return res.json()
}

export async function apiGet(path: string) {
  const auth = useAuthStore()
  const headers: HeadersInit = {}

  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`

  const res = await fetch(`${import.meta.env.VITE_API_BASE}${path}`, {
    method: 'GET',
    headers,
  })

  if (!res.ok) {
    if (res.status === 401) auth.logout()
    throw new Error(`API Error: ${res.status}`)
  }
  return res.json()
}
