import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { allPages, ApiError, http, TOKEN_KEY } from './http'

beforeEach(() => {
  sessionStorage.clear()
  http.onUnauthorized = vi.fn()
})
afterEach(() => vi.unstubAllGlobals())
describe('HTTP boundary', () => {
  it('sends the current bearer and returns an unwrapped response', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'live-token')
    vi.stubGlobal('fetch', async (path: string, init: RequestInit) => {
      expect(path).toBe('/api/v1/roles')
      expect(new Headers(init.headers).get('Authorization')).toBe('Bearer live-token')
      return Response.json({ items: [{ id: 7 }], page: 1, pageSize: 20, total: 1 })
    })
    expect(await http.request('/api/v1/roles')).toEqual({
      items: [{ id: 7 }],
      page: 1,
      pageSize: 20,
      total: 1,
    })
  })
  it('does not send an old bearer when signing in', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'old-token')
    vi.stubGlobal('fetch', async (_path: string, init: RequestInit) => {
      expect(new Headers(init.headers).has('Authorization')).toBe(false)
      expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
      return Response.json({ accessToken: 'fresh', tokenType: 'Bearer', expiresIn: 7200 })
    })
    expect(
      await http.request('/auth/login', { method: 'POST', body: '{}', anonymous: true }),
    ).toHaveProperty('accessToken', 'fresh')
  })
  it('handles a 204 logout without parsing JSON', async () => {
    vi.stubGlobal('fetch', async () => new Response(null, { status: 204 }))
    await expect(http.request('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
  it('expires authenticated sessions on 401', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'expired')
    let expired = false
    http.onUnauthorized = () => {
      expired = true
    }
    vi.stubGlobal('fetch', async () => Response.json({ detail: '会话失效' }, { status: 401 }))
    await expect(http.request('/auth/me')).rejects.toMatchObject({
      status: 401,
      message: '会话失效',
    })
    expect(expired).toBe(true)
  })
  it('preserves sessions and server detail on 403', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'valid')
    let expired = false
    http.onUnauthorized = () => {
      expired = true
    }
    vi.stubGlobal('fetch', async () =>
      Response.json({ detail: '超出部门管理范围' }, { status: 403 }),
    )
    await expect(http.request('/api/v1/users')).rejects.toMatchObject({
      status: 403,
      message: '超出部门管理范围',
    })
    expect(expired).toBe(false)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('valid')
  })
  it('reports a useful error when a proxy returns non-JSON failure', async () => {
    vi.stubGlobal('fetch', async () => new Response('Bad Gateway', { status: 502 }))
    await expect(http.request('/api/v1/roles')).rejects.toBeInstanceOf(ApiError)
  })
  it('reads every page for complete role and API selectors', async () => {
    const pages = [
      { items: [1, 2], page: 1, pageSize: 2, total: 3 },
      { items: [3], page: 2, pageSize: 2, total: 3 },
    ]
    expect(await allPages(async (page) => pages[page - 1]!)).toEqual([1, 2, 3])
  })
})
