import type { EffectivePermission, Permission, RoleDetails, RoleInput } from '../api/types'
export function previewPermissions(
  catalog: Permission[],
  direct: number[],
  parents: RoleDetails[],
  self: { id: number; name: string },
): EffectivePermission[] {
  return catalog.flatMap((permission) => {
    const inheritedSources = new Map<number, { id: number; name: string }>()
    for (const role of parents) {
      for (const source of role.effectivePermissions.find((p) => p.id === permission.id)
        ?.sourceRoles ?? [])
        inheritedSources.set(source.id, source)
    }
    const isDirect = direct.includes(permission.id)
    if (!isDirect && !inheritedSources.size) return []
    const sources = new Map(isDirect ? [[self.id, self]] : [])
    for (const [id, source] of inheritedSources) sources.set(id, source)
    return [
      {
        ...permission,
        acquisition: isDirect ? (inheritedSources.size ? 'BOTH' : 'DIRECT') : 'INHERITED',
        sourceRoles: [...sources.values()],
      },
    ]
  })
}
export function toggleDirect(
  input: RoleInput,
  id: number,
  checked: boolean,
  catalog: Permission[],
  inheritedIds: Set<number>,
): number[] {
  const ids = new Set(input.permissionIds)
  const permission = catalog.find((p) => p.id === id)!
  if (checked) {
    ids.add(id)
    if (permission.parentId && !inheritedIds.has(permission.parentId)) ids.add(permission.parentId)
  } else {
    ids.delete(id)
    if (permission.type === 'MENU' && !inheritedIds.has(id)) {
      for (const child of catalog.filter((p) => p.parentId === id)) ids.delete(child.id)
    }
  }
  return [...ids]
}
export function parentCandidates(roles: RoleDetails[], editingId?: number): RoleDetails[] {
  const excluded = new Set(editingId === undefined ? [] : [editingId])
  let changed = true
  while (changed) {
    changed = false
    for (const role of roles) {
      if (!excluded.has(role.id) && role.inheritedRoles.some((p) => excluded.has(p.id))) {
        excluded.add(role.id)
        changed = true
      }
    }
  }
  return roles.filter((role) => role.roleType === 'BUSINESS' && !excluded.has(role.id))
}
export function directDisabled(id: number, originalIds: number[], inherited: Set<number>): boolean {
  return inherited.has(id) && !originalIds.includes(id)
}
