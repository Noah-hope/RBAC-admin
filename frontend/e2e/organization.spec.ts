import { test, expect } from '@playwright/test'

test('department administrator edits identity fields without sending global management fields', async ({
  page,
}) => {
  const writes: unknown[] = []
  await page.addInitScript(() => sessionStorage.setItem('rbac.access-token', 'department-token'))
  await page.route(/\/(auth|api\/v1)\//, async (route) => {
    const path = new URL(route.request().url()).pathname
    const user = {
      id: 9,
      name: '林晓',
      username: 'lin.xiao',
      department: { id: 2, name: '教学部' },
      status: 'ACTIVE',
      departmentAdministrator: false,
      businessRoles: [],
    }
    let data: unknown
    if (path === '/auth/me')
      data = {
        subject: '1',
        roles: [],
        permissions: ['user:view', 'user:update'],
        name: '部门管理员',
        no: 'manager',
        id: 1,
        departmentId: 2,
        managementRole: 'DEPARTMENT_ADMIN',
      }
    else if (path === '/api/v1/departments')
      data = { departments: [{ id: 2, name: '教学部', parentId: 1, children: [] }] }
    else if (path === '/api/v1/users') data = { items: [user], page: 1, pageSize: 20, total: 1 }
    else if (path === '/api/v1/users/9') {
      if (route.request().method() === 'PATCH') writes.push(route.request().postDataJSON())
      data = user
    } else data = {}
    await route.fulfill({ json: data })
  })
  await page.goto('/organization')
  await page.getByRole('button', { name: '编辑用户', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('姓名', { exact: true }).fill('林晓老师')
  await dialog.getByRole('button', { name: '保存用户', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes).toEqual([{ username: 'lin.xiao', name: '林晓老师', status: 'ACTIVE' }])
})
