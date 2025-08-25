import { useAuthStore } from '@/stores/auth'

export async function apiPost(path: string, body: unknown) {
  const auth = useAuthStore()
  const headers: HeadersInit = { 'Content-Type': 'application/json' }

  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`

  const res = await fetch(`${import.meta.env.VITE_API_BASE}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })

  if (!res.ok) throw new Error(`API Error: ${res.status}`)
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

  if (!res.ok) throw new Error(`API Error: ${res.status}`)
  return res.json()
}
