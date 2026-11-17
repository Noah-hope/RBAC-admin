export type ManagementRole = 'TOTAL_ADMIN' | 'DEPARTMENT_ADMIN' | 'NONE'
export interface Page<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}
export interface Ref {
  id: number
  name: string
}
export interface AuthUser {
  subject: string
  roles: string[]
  permissions: string[]
  name: string
  no: string
  id: number
  departmentId: number | null
  managementRole: ManagementRole
}
export interface LoginResult {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}
export interface Application extends Ref {
  code: string
}
export interface Permission extends Ref {
  applicationId: number
  code: string
  type: 'MENU' | 'BUTTON'
  parentId: number | null
}
export interface CatalogApplication extends Application {
  permissions: Permission[]
}
export interface Catalog {
  applications: CatalogApplication[]
}
export interface ApiRecord {
  id: number
  applicationId: number
  operationId: string
  httpMethod: string
  path: string
  permissionIds: number[]
}
export type ApiInput = Pick<ApiRecord, 'operationId' | 'httpMethod' | 'path'>
export interface PermissionDetails extends Permission {
  apis: ApiRecord[]
}
export interface Menu extends Ref {
  code: string
  buttons: (Ref & { code: string })[]
}
export interface PermissionTree {
  applicationId: number
  applicationName: string
  menus: Menu[]
}
export interface EffectivePermission extends Permission {
  acquisition: 'DIRECT' | 'INHERITED' | 'BOTH'
  sourceRoles: Ref[]
}
export interface RoleSummary extends Ref {
  description: string
  roleType: 'BUSINESS' | 'TOTAL_ADMIN' | 'DEPARTMENT_ADMIN'
  applications: { applicationId: number; applicationName: string; permissionCount: number }[]
  inheritedRoles: Ref[]
  inheritedByRoles: Ref[]
  directPermissionCount: number
  effectivePermissionCount: number
}
export interface RoleDetails extends RoleSummary {
  permissionIds: number[]
  effectivePermissions: EffectivePermission[]
  assignedUserCount: number
}
export interface RoleInput {
  name: string
  description: string
  permissionIds: number[]
  inheritedRoleIds: number[]
}
export interface Department extends Ref {
  parentId: number | null
  children: Department[]
}
export interface User extends Ref {
  username: string
  department: Ref | null
  status: 'ACTIVE' | 'DISABLED'
  departmentAdministrator: boolean
  businessRoles: Ref[]
}
export interface UserInput {
  name: string
  username: string
  departmentId: number
  status: 'ACTIVE' | 'DISABLED'
  departmentAdministrator?: boolean
  initialPassword?: string
}
export interface MyPermissions {
  businessRoles: Ref[]
  applications: {
    applicationId: number
    applicationName: string
    applicationCode: string
    menus: (Menu & {
      sourceRoles: Ref[]
      buttons: (Ref & { code: string; sourceRoles: Ref[] })[]
    })[]
  }[]
}
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SATISFIED' | 'WITHDRAWN'
export interface PermissionRequest {
  id: number
  applicantUserId: number
  applicantName: string
  applicantDepartmentId: number | null
  permission: Permission
  reason: string
  status: RequestStatus
  reviewerUserId: number | null
  approvedRoleId: number | null
  createdAt: string
  reviewedAt: string | null
  approvalRoles?: { role: RoleDetails; selectable: boolean; extraPermissionIds: number[] }[]
}
export interface RequestablePermissions {
  applications: (Application & { groups: { menu: Permission; permissions: Permission[] }[] })[]
}
export interface RoleRequest {
  id: number
  applicantUserId: number
  applicantName: string
  name: string
  description: string
  permissionIds: number[]
  inheritedRoles: (Ref & { exists: boolean })[]
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reviewerUserId: number | null
  createdRoleId: number | null
  createdAt: string
  reviewedAt: string | null
  effectivePermissions?: EffectivePermission[] | null
}
export interface OperationLog {
  id: number
  occurredAt: string
  actorName: string
  action: string
  targetNames: string[]
  detail: string
  result: string
}
