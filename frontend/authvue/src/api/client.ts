export async function apiPost(path: string, body: unknown) {
  const res = await fetch(`${import.meta.env.VITE_API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!res.ok) throw new Error(`API Error: ${res.status}`)
  return res.json()
}
