export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}
export const TOKEN_KEY = 'rbac.access-token'
export const http = {
  onUnauthorized: () => {},
  async request<T>(path: string, options: RequestInit & { anonymous?: boolean } = {}): Promise<T> {
    const { anonymous, ...init } = options
    const headers = new Headers(init.headers)
    const token = anonymous ? null : sessionStorage.getItem(TOKEN_KEY)
    if (token) headers.set('Authorization', `Bearer ${token}`)
    if (init.body) headers.set('Content-Type', 'application/json')
    headers.set('Accept', 'application/json, application/problem+json')
    let response: Response
    try {
      response = await fetch(path, { ...init, headers })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      throw new ApiError(0, '无法连接服务，请检查网络及后端是否已启动。')
    }
    if (!response.ok) {
      let message =
        response.status >= 500 ? '服务暂时不可用，请稍后重试。' : `请求失败（${response.status}）`
      if (response.headers.get('Content-Type')?.includes('json')) {
        const problem = (await response.json()) as { detail?: string; title?: string }
        message = problem.detail || problem.title || message
      }
      if (response.status === 401 && token) http.onUnauthorized()
      throw new ApiError(response.status, message)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  },
}
export async function allPages<T>(
  load: (page: number) => Promise<import('./types').Page<T>>,
): Promise<T[]> {
  const first = await load(1)
  const items = [...first.items]
  for (let page = 2; page <= Math.ceil(first.total / first.pageSize); page++)
    items.push(...(await load(page)).items)
  return items
}
