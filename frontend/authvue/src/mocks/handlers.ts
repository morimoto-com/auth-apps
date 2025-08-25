import { http, HttpResponse } from 'msw'

interface LoginRequest {
  loginId: string
  password: string
}

export const handlers = [
  http.post('/api/login', async ({ request }) => {
    const { loginId, password } = (await request.json()) as LoginRequest

    if (loginId === 'test' && password === 'pass') {
      return HttpResponse.json({ status: 'success', token: 'dummy-token' })
    }
    return HttpResponse.json({ status: 'error', message: '認証失敗' }, { status: 401 })
  }),
]
