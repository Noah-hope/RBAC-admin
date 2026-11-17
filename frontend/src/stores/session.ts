import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError, http, TOKEN_KEY } from '../api/http'
import type { AuthUser, LoginResult } from '../api/types'
export const useSession = defineStore('session', () => {
  const user = ref<AuthUser | null>(null)
  const restoreError = ref('')
  let restored = false
  const totalAdmin = computed(() => user.value?.managementRole === 'TOTAL_ADMIN')
  const administrator = computed(() => user.value != null && user.value.managementRole !== 'NONE')
  function clear() {
    user.value = null
    sessionStorage.removeItem(TOKEN_KEY)
    restoreError.value = ''
    restored = true
  }
  http.onUnauthorized = clear
  async function refresh() {
    user.value = await http.request<AuthUser>('/auth/me')
  }
  async function login(username: string, password: string) {
    const result = await http.request<LoginResult>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, systems: ['permission-center'] }),
      anonymous: true,
    })
    sessionStorage.setItem(TOKEN_KEY, result.accessToken)
    restoreError.value = ''
    await refresh()
    restored = true
  }
  async function restore(force = false) {
    if (restored && !force) return
    restored = true
    restoreError.value = ''
    if (!sessionStorage.getItem(TOKEN_KEY)) return
    try {
      await refresh()
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401))
        restoreError.value = (error as Error).message
    }
  }
  async function logout() {
    try {
      await http.request('/auth/logout', { method: 'POST' })
      clear()
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error
    }
  }
  function has(permission?: string) {
    return !permission || !!user.value?.permissions.includes(permission)
  }
  return {
    user,
    restoreError,
    totalAdmin,
    administrator,
    has,
    login,
    restore,
    logout,
    clear,
    refresh,
  }
})
