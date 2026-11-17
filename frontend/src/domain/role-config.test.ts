import { describe, expect, it } from 'vitest'
import { directDisabled, parentCandidates, previewPermissions, toggleDirect } from './role-config'
import type { Permission, RoleDetails, RoleInput } from '../api/types'

const menu: Permission = {
  id: 10,
  applicationId: 1,
  name: '课程',
  code: 'course:view',
  type: 'MENU',
  parentId: null,
}
const button: Permission = {
  id: 11,
  applicationId: 1,
  name: '新增课程',
  code: 'course:create',
  type: 'BUTTON',
  parentId: 10,
}
const catalog = [menu, button]
const role = (id: number, patch: Partial<RoleDetails> = {}): RoleDetails => ({
  id,
  name: `角色${id}`,
  description: '',
  roleType: 'BUSINESS',
  applications: [],
  inheritedRoles: [],
  inheritedByRoles: [],
  directPermissionCount: 0,
  effectivePermissionCount: 0,
  permissionIds: [],
  effectivePermissions: [],
  assignedUserCount: 0,
  ...patch,
})
const input = (permissionIds: number[] = []): RoleInput => ({
  name: '助教',
  description: '',
  permissionIds,
  inheritedRoleIds: [],
})

describe('role configuration', () => {
  it('deduplicates diamond inheritance using actual configuring roles', () => {
    const inherited = {
      ...menu,
      acquisition: 'INHERITED' as const,
      sourceRoles: [{ id: 1, name: '基础' }],
    }
    const preview = previewPermissions(
      catalog,
      [],
      [
        role(2, { effectivePermissions: [inherited] }),
        role(3, { effectivePermissions: [inherited] }),
      ],
      { id: 4, name: '助教' },
    )
    expect(preview).toEqual([
      { ...menu, acquisition: 'INHERITED', sourceRoles: [{ id: 1, name: '基础' }] },
    ])
  })
  it('retains direct configuration when a new parent also grants it', () => {
    const preview = previewPermissions(
      catalog,
      [10],
      [
        role(1, {
          effectivePermissions: [
            { ...menu, acquisition: 'DIRECT', sourceRoles: [{ id: 1, name: '基础' }] },
          ],
        }),
      ],
      { id: 4, name: '助教' },
    )
    expect(preview[0]).toMatchObject({
      acquisition: 'BOTH',
      sourceRoles: [
        { id: 4, name: '助教' },
        { id: 1, name: '基础' },
      ],
    })
  })
  it('removing a direct source leaves an inherited permission effective', () => {
    const parent = role(1, {
      effectivePermissions: [
        { ...menu, acquisition: 'DIRECT', sourceRoles: [{ id: 1, name: '基础' }] },
      ],
    })
    expect(previewPermissions(catalog, [], [parent], { id: 4, name: '助教' })[0]?.acquisition).toBe(
      'INHERITED',
    )
  })
  it('only disables newly repeated direct grants, preserving original overlap', () => {
    expect(directDisabled(10, [], new Set([10]))).toBe(true)
    expect(directDisabled(10, [10], new Set([10]))).toBe(false)
  })
  it('selecting a button also selects its menu when not inherited', () => {
    expect(toggleDirect(input(), 11, true, catalog, new Set())).toEqual([11, 10])
  })
  it('does not duplicate an inherited menu in the direct payload', () => {
    expect(toggleDirect(input(), 11, true, catalog, new Set([10]))).toEqual([11])
  })
  it('deselecting a menu removes dependent direct buttons if no inherited menu remains', () => {
    expect(toggleDirect(input([10, 11]), 10, false, catalog, new Set())).toEqual([])
    expect(toggleDirect(input([10, 11]), 10, false, catalog, new Set([10]))).toEqual([11])
  })
  it('excludes self, descendants and preset administrators from parent candidates', () => {
    const roles = [
      role(1),
      role(2, { inheritedRoles: [{ id: 1, name: '角色1' }] }),
      role(3, { inheritedRoles: [{ id: 2, name: '角色2' }] }),
      role(4),
      role(5, { roleType: 'TOTAL_ADMIN' }),
    ]
    expect(parentCandidates(roles, 1).map((item) => item.id)).toEqual([4])
  })
})
