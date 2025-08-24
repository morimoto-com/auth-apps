import { http, HttpResponse } from 'msw'

interface LoginRequest {
  loginId: string
  password: string
}

export const handlers = [
  http.post('/api/login', async ({ request }) => {
    const { loginId, password } = (await request.json()) as LoginRequest
    console.log('logintest!!')
    if (loginId === 'test' && password === 'pass') {
      console.log('logintest!! OK')
      return HttpResponse.json({ status: 'success', token: 'dummy-token' })
    }
    return HttpResponse.json({ status: 'error', message: '認証失敗' }, { status: 401 })
  }),
]
