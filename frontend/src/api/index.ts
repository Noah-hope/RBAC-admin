import { allPages, http } from './http'
import type {
  Application,
  ApiInput,
  ApiRecord,
  Catalog,
  Department,
  MyPermissions,
  OperationLog,
  Page,
  PermissionDetails,
  PermissionRequest,
  PermissionTree,
  RequestablePermissions,
  RoleDetails,
  RoleInput,
  RoleRequest,
  RoleSummary,
  User,
  UserInput,
} from './types'

export type Params = Record<string, string | number | boolean | undefined>
function query(path: string, params: Params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  return search.size ? `${path}?${search}` : path
}
const get = <T>(path: string, params?: Params) => http.request<T>(query(path, params))
const send = <T>(method: string, path: string, body?: unknown) =>
  http.request<T>(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
const base = '/api/v1'
export const api = {
  applications: (params?: Params) => get<Page<Application>>(`${base}/applications`, params),
  allApplications: () => allPages((page) => api.applications({ page, pageSize: 100 })),
  saveApplication: (id: number | undefined, name: string, code?: string) =>
    send<Application>(
      id ? 'PATCH' : 'POST',
      `${base}/applications${id ? `/${id}` : ''}`,
      id ? { name } : { name, ...(code ? { code } : {}) },
    ),
  permissionTree: (app: number) => get<PermissionTree>(`${base}/applications/${app}/permissions`),
  permission: (app: number, id: number) =>
    get<PermissionDetails>(`${base}/applications/${app}/permissions/${id}`),
  savePermission: (
    app: number,
    id: number | undefined,
    input: {
      name: string
      code: string
      type: 'MENU' | 'BUTTON'
      parentId: number | null
      apiIds: number[]
    },
  ) =>
    send<PermissionDetails>(
      id ? 'PATCH' : 'POST',
      `${base}/applications/${app}/permissions${id ? `/${id}` : ''}`,
      id ? { name: input.name, code: input.code, apiIds: input.apiIds } : input,
    ),
  apis: (app: number, params?: Params) =>
    get<Page<ApiRecord>>(`${base}/applications/${app}/apis`, params),
  allApis: (app: number) => allPages((page) => api.apis(app, { page, pageSize: 100 })),
  saveApi: (app: number, id: number | undefined, input: ApiInput) =>
    send<ApiRecord>(
      id ? 'PATCH' : 'POST',
      `${base}/applications/${app}/apis${id ? `/${id}` : ''}`,
      input,
    ),
  deleteApi: (app: number, id: number) =>
    send<void>('DELETE', `${base}/applications/${app}/apis/${id}`),
  catalog: () => get<Catalog>(`${base}/permission-catalog`),
  roles: (params?: Params) => get<Page<RoleSummary>>(`${base}/roles`, params),
  allRoles: () => allPages((page) => api.roles({ page, pageSize: 100, roleType: 'BUSINESS' })),
  role: (id: number) => get<RoleDetails>(`${base}/roles/${id}`),
  saveRole: (id: number | undefined, input: RoleInput) =>
    send<RoleDetails>(id ? 'PUT' : 'POST', `${base}/roles${id ? `/${id}` : ''}`, input),
  deleteRole: (id: number) => send<void>('DELETE', `${base}/roles/${id}`),
  departments: () => get<{ departments: Department[] }>(`${base}/departments`),
  saveDepartment: (id: number | undefined, name: string, parentId: number | null) =>
    send<Department>(id ? 'PATCH' : 'POST', `${base}/departments${id ? `/${id}` : ''}`, {
      name,
      parentId,
    }),
  users: (params?: Params) => get<Page<User>>(`${base}/users`, params),
  user: (id: number) => get<User>(`${base}/users/${id}`),
  saveUser: (id: number | undefined, input: UserInput) =>
    id
      ? send<User>('PATCH', `${base}/users/${id}`, input)
      : send<{ user: User }>('POST', `${base}/users`, input),
  replaceRoles: (id: number, roleIds: number[]) =>
    send('PUT', `${base}/users/${id}/roles`, { roleIds }),
  batchRoles: (action: 'GRANT' | 'REVOKE', userIds: number[], roleIds: number[]) =>
    send<{ affectedUsers: number }>('PATCH', `${base}/user-role-assignments`, {
      action,
      userIds,
      roleIds,
    }),
  mine: () => get<MyPermissions>(`${base}/me/permissions`),
  requestable: () => get<RequestablePermissions>(`${base}/me/requestable-permissions`),
  requests: (params?: Params) =>
    get<Page<PermissionRequest>>(`${base}/permission-requests`, params),
  request: (id: number) => get<PermissionRequest>(`${base}/permission-requests/${id}`),
  submitRequest: (permissionId: number, reason: string) =>
    send<PermissionRequest>('POST', `${base}/permission-requests`, { permissionId, reason }),
  withdrawRequest: (id: number) =>
    send<PermissionRequest>('POST', `${base}/permission-requests/${id}/withdrawals`),
  reviewRequest: (id: number, decision: 'APPROVE' | 'REJECT', roleId?: number) =>
    send<PermissionRequest>('POST', `${base}/permission-requests/${id}/reviews`, {
      decision,
      ...(decision === 'APPROVE' ? { roleId } : {}),
    }),
  roleRequests: (params?: Params) =>
    get<Page<RoleRequest>>(`${base}/role-creation-requests`, params),
  roleRequest: (id: number) => get<RoleRequest>(`${base}/role-creation-requests/${id}`),
  submitRoleRequest: (input: RoleInput) =>
    send<RoleRequest>('POST', `${base}/role-creation-requests`, input),
  reviewRoleRequest: (id: number, decision: 'APPROVE' | 'REJECT', role?: RoleInput) =>
    send<RoleRequest>('POST', `${base}/role-creation-requests/${id}/reviews`, {
      decision,
      ...(decision === 'APPROVE' ? { role } : {}),
    }),
  logs: (params?: Params) => get<Page<OperationLog>>(`${base}/operation-logs`, params),
}
