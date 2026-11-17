import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSession } from './session'
import { TOKEN_KEY } from '../api/http'

const identity = {
  subject: '9',
  roles: ['4'],
  permissions: ['user:view'],
  name: '林晓',
  no: 'lin.xiao',
  id: 9,
  departmentId: 2,
  managementRole: 'DEPARTMENT_ADMIN',
}
beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
})
afterEach(() => vi.unstubAllGlobals())
it('signs in for permission-center then obtains current identity', async () => {
  vi.stubGlobal('fetch', async (path: string, init: RequestInit) => {
    if (path === '/auth/login') {
      expect(JSON.parse(init.body as string)).toEqual({
        username: 'lin.xiao',
        password: 'secret',
        systems: ['permission-center'],
      })
      return Response.json({ accessToken: 'signed', tokenType: 'Bearer', expiresIn: 7200 })
    }
    expect(path).toBe('/auth/me')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer signed')
    return Response.json(identity)
  })
  const session = useSession()
  await session.login('lin.xiao', 'secret')
  expect(session.user).toEqual(identity)
  expect(sessionStorage.getItem(TOKEN_KEY)).toBe('signed')
})
it('restores identity from the server instead of trusting stored permissions', async () => {
  sessionStorage.setItem(TOKEN_KEY, 'stored')
  vi.stubGlobal('fetch', async () => Response.json(identity))
  const session = useSession()
  await session.restore()
  expect(session.user?.permissions).toEqual(['user:view'])
})
it('clears an expired restored session', async () => {
  sessionStorage.setItem(TOKEN_KEY, 'expired')
  vi.stubGlobal('fetch', async () => Response.json({ detail: '已失效' }, { status: 401 }))
  const session = useSession()
  await session.restore()
  expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  expect(session.user).toBeNull()
})
it('retains the token when restore fails because the backend is offline', async () => {
  sessionStorage.setItem(TOKEN_KEY, 'stored')
  vi.stubGlobal('fetch', async () => {
    throw new TypeError('Failed to fetch')
  })
  const session = useSession()
  await session.restore()
  expect(sessionStorage.getItem(TOKEN_KEY)).toBe('stored')
  expect(session.restoreError).toContain('连接')
})
it('keeps a session available for retry when logout cannot reach the backend', async () => {
  sessionStorage.setItem(TOKEN_KEY, 'stored')
  vi.stubGlobal('fetch', async () => {
    throw new TypeError('Failed to fetch')
  })
  await expect(useSession().logout()).rejects.toThrow()
  expect(sessionStorage.getItem(TOKEN_KEY)).toBe('stored')
})
it('removes the local bearer after server-confirmed logout', async () => {
  sessionStorage.setItem(TOKEN_KEY, 'stored')
  vi.stubGlobal('fetch', async () => new Response(null, { status: 204 }))
  await useSession().logout()
  expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
})
