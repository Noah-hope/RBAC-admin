import { test, expect, type Page } from '@playwright/test'

const menu = {
  id: 10,
  applicationId: 2,
  name: '课程管理',
  code: 'course:view',
  type: 'MENU',
  parentId: null,
}
const button = {
  id: 11,
  applicationId: 2,
  name: '新增课程',
  code: 'course:create',
  type: 'BUTTON',
  parentId: 10,
}
const role = {
  id: 4,
  name: '基础查看',
  description: '可查看课程',
  roleType: 'BUSINESS',
  applications: [{ applicationId: 2, applicationName: '课程系统', permissionCount: 1 }],
  inheritedRoles: [],
  inheritedByRoles: [],
  directPermissionCount: 1,
  effectivePermissionCount: 1,
  permissionIds: [10],
  effectivePermissions: [
    { ...menu, acquisition: 'DIRECT', sourceRoles: [{ id: 4, name: '基础查看' }] },
  ],
  assignedUserCount: 0,
}
const user = {
  id: 9,
  name: '林晓',
  username: 'lin.xiao',
  department: { id: 2, name: '教学部' },
  status: 'ACTIVE',
  departmentAdministrator: false,
  businessRoles: [{ id: 4, name: '基础查看' }],
}
const permissions = [
  'application:view',
  'application:create',
  'application:update',
  'permission:create',
  'permission:update',
  'api:manage',
  'role:view',
  'role:create',
  'role:update',
  'role:delete',
  'user:view',
  'user:create',
  'user:update',
  'department:create',
  'department:update',
  'assignment:view',
  'assignment:manage',
  'assignment:batch',
  'audit:view',
  'request:view',
  'request:review',
  'roleRequest:view',
  'roleRequest:create',
  'roleRequest:review',
]
const request = {
  id: 7,
  applicantUserId: 9,
  applicantName: '林晓',
  applicantDepartmentId: 2,
  permission: button,
  reason: '需要创建课程',
  status: 'PENDING',
  reviewerUserId: null,
  approvedRoleId: null,
  createdAt: '2026-11-17T00:00:00Z',
  reviewedAt: null,
}
const roleRequest = {
  id: 8,
  applicantUserId: 9,
  applicantName: '林晓',
  name: '助教',
  description: '协助教学',
  permissionIds: [11],
  inheritedRoles: [{ id: 4, name: '基础查看', exists: true }],
  status: 'PENDING',
  reviewerUserId: null,
  createdRoleId: null,
  createdAt: '2026-11-17T00:00:00Z',
  reviewedAt: null,
  effectivePermissions: [
    { ...menu, acquisition: 'INHERITED', sourceRoles: [{ id: 4, name: '基础查看' }] },
    { ...button, acquisition: 'DIRECT', sourceRoles: [] },
  ],
}
const list = (items: unknown[]) => ({ items, page: 1, pageSize: 20, total: items.length })

async function backend(page: Page, managementRole = 'TOTAL_ADMIN', granted = permissions) {
  const writes: { path: string; method: string; body: Record<string, unknown> }[] = []
  await page.route(/\/(auth|api\/v1)\//, async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname,
      method = req.method()
    const body = req.postDataJSON() as Record<string, unknown> | null
    if (method !== 'GET') writes.push({ path, method, body: body ?? {} })
    let data: unknown
    if (path === '/auth/login')
      data = { accessToken: 'test-session', tokenType: 'Bearer', expiresIn: 7200 }
    else if (path === '/auth/logout') {
      await route.fulfill({ status: 204 })
      return
    } else if (path === '/auth/me')
      data = {
        subject: '9',
        roles: ['4'],
        permissions: granted,
        name: managementRole === 'TOTAL_ADMIN' ? '总管理员' : '林晓',
        no: 'lin.xiao',
        id: 9,
        departmentId: 2,
        managementRole,
      }
    else if (path === '/api/v1/permission-catalog')
      data = {
        applications: [{ id: 2, name: '课程系统', code: 'courses', permissions: [menu, button] }],
      }
    else if (path === '/api/v1/roles')
      data = method === 'GET' ? list([role]) : { ...role, ...body, id: 5 }
    else if (path === '/api/v1/roles/4') data = method === 'GET' ? role : { ...role, ...body }
    else if (path === '/api/v1/departments')
      data =
        method === 'GET'
          ? {
              departments: [
                {
                  id: 1,
                  name: '总公司',
                  parentId: null,
                  children: [{ id: 2, name: '教学部', parentId: 1, children: [] }],
                },
              ],
            }
          : { id: 3, ...body }
    else if (path === '/api/v1/users')
      data = method === 'GET' ? list([user]) : { user: { ...user, ...body } }
    else if (path === '/api/v1/users/9') data = { ...user, ...body }
    else if (path === '/api/v1/users/9/roles') data = { userId: 9, roleIds: body?.roleIds }
    else if (path === '/api/v1/user-role-assignments')
      data = { action: body?.action, affectedUsers: 1 }
    else if (path === '/api/v1/applications')
      data =
        method === 'GET' ? list([{ id: 2, name: '课程系统', code: 'courses' }]) : { id: 3, ...body }
    else if (path === '/api/v1/applications/2/permissions')
      data =
        method === 'GET'
          ? {
              applicationId: 2,
              applicationName: '课程系统',
              menus: [
                {
                  id: 10,
                  name: '课程管理',
                  code: 'course:view',
                  buttons: [{ id: 11, name: '新增课程', code: 'course:create' }],
                },
              ],
            }
          : { id: 12, ...body }
    else if (path === '/api/v1/applications/2/permissions/10') data = { ...menu, apis: [] }
    else if (path === '/api/v1/applications/2/permissions/11') data = { ...button, apis: [] }
    else if (path === '/api/v1/applications/2/apis')
      data =
        method === 'GET'
          ? list([
              {
                id: 20,
                applicationId: 2,
                operationId: 'listCourses',
                httpMethod: 'GET',
                path: '/courses',
                permissionIds: [10],
              },
            ])
          : { id: 21, ...body }
    else if (path === '/api/v1/me/permissions')
      data = {
        businessRoles: [{ id: 4, name: '基础查看' }],
        applications: [
          {
            applicationId: 2,
            applicationName: '课程系统',
            applicationCode: 'courses',
            menus: [{ ...menu, sourceRoles: [{ id: 4, name: '基础查看' }], buttons: [] }],
          },
        ],
      }
    else if (path === '/api/v1/me/requestable-permissions')
      data = {
        applications: [
          { id: 2, name: '课程系统', code: 'courses', groups: [{ menu, permissions: [button] }] },
        ],
      }
    else if (path === '/api/v1/permission-requests')
      data = method === 'GET' ? list([request]) : { ...request, ...body }
    else if (path === '/api/v1/permission-requests/7')
      data = { ...request, approvalRoles: [{ role, selectable: true, extraPermissionIds: [] }] }
    else if (path === '/api/v1/permission-requests/7/reviews')
      data = { ...request, status: 'APPROVED' }
    else if (path === '/api/v1/permission-requests/7/withdrawals')
      data = { ...request, status: 'WITHDRAWN' }
    else if (path === '/api/v1/role-creation-requests')
      data = method === 'GET' ? list([roleRequest]) : { ...roleRequest, ...body }
    else if (path === '/api/v1/role-creation-requests/8') data = roleRequest
    else if (path === '/api/v1/role-creation-requests/8/reviews')
      data = { ...roleRequest, status: 'APPROVED', createdRoleId: 5 }
    else if (path === '/api/v1/operation-logs')
      data = list([
        {
          id: 1,
          occurredAt: '2026-11-17T00:00:00Z',
          actorName: '总管理员',
          action: '配置角色',
          targetNames: ['基础查看'],
          detail: '保存角色配置',
          result: 'SUCCESS',
        },
      ])
    else {
      await route.fulfill({ status: 404, json: { detail: `未准备的接口 ${method} ${path}` } })
      return
    }
    await route.fulfill({ json: data })
  })
  return writes
}
async function login(page: Page) {
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的权限', exact: true })).toBeVisible()
}

test('login, restore and server-confirmed logout', async ({ page }) => {
  const writes = await backend(page)
  await login(page)
  await expect(page.getByRole('tab', { name: /课程系统/ })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '我的权限', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  expect(writes.find((w) => w.path === '/auth/login')?.body).toEqual({
    username: 'admin',
    password: 'password',
    systems: ['permission-center'],
  })
  expect(writes.some((w) => w.path === '/auth/logout')).toBe(true)
})
test('new role keeps inherited menus read-only and saves only direct buttons', async ({ page }) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '角色管理' }).click()
  await page.getByRole('button', { name: '新增角色', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('角色名称', { exact: true }).fill('课程助教')
  await dialog.getByLabel('继承 基础查看', { exact: true }).check()
  await expect(dialog.getByLabel('直接配置 课程管理', { exact: true })).toBeDisabled()
  await dialog.getByLabel('直接配置 新增课程', { exact: true }).check()
  await dialog.getByRole('button', { name: '保存角色', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path === '/api/v1/roles' && w.method === 'POST')?.body).toEqual({
    name: '课程助教',
    description: '',
    permissionIds: [11],
    inheritedRoleIds: [4],
  })
})
test('editing users omits creation passwords and preserves typed department IDs', async ({
  page,
}) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '用户与部门' }).click()
  await page.getByRole('button', { name: '编辑用户', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('姓名', { exact: true }).fill('林晓老师')
  await dialog.getByRole('button', { name: '保存用户', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.method === 'PATCH')?.body).toEqual({
    username: 'lin.xiao',
    name: '林晓老师',
    departmentId: 2,
    status: 'ACTIVE',
    departmentAdministrator: false,
  })
})
test('applications and APIs use independent registry operations', async ({ page }) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '应用与权限' }).click()
  await expect(page.getByRole('cell', { name: 'course:create', exact: true })).toBeVisible()
  await page.getByRole('tab', { name: 'API 登记' }).click()
  await page.getByRole('button', { name: '登记接口', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('接口标识', { exact: true }).fill('createCourse')
  await dialog.getByLabel('请求路径', { exact: true }).fill('/courses')
  await dialog.getByLabel('请求方法', { exact: true }).selectOption('POST')
  await dialog.getByRole('button', { name: '保存接口', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path.endsWith('/apis') && w.method === 'POST')?.body).toEqual({
    operationId: 'createCourse',
    httpMethod: 'POST',
    path: '/courses',
  })
})
test('permission review shows full candidate permissions and assigns a selected role', async ({
  page,
}) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '权限申请审核' }).click()
  await page.getByRole('button', { name: '审核申请', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('course:view', { exact: true })).toBeVisible()
  await dialog.getByLabel('批准角色 基础查看', { exact: true }).check()
  await dialog.getByRole('button', { name: '批准并分配角色', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path.endsWith('/7/reviews'))?.body).toEqual({
    decision: 'APPROVE',
    roleId: 4,
  })
})
test('accepting a role request only opens the editor until saving', async ({ page }) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '角色创建申请' }).click()
  await page.getByRole('button', { name: '受理申请', exact: true }).click()
  const dialog = page.getByRole('dialog')
  expect(writes.filter((w) => w.path.endsWith('/8/reviews'))).toHaveLength(0)
  await dialog.getByRole('button', { name: '创建角色并通过', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path.endsWith('/8/reviews'))?.body).toEqual({
    decision: 'APPROVE',
    role: { name: '助教', description: '协助教学', permissionIds: [11], inheritedRoleIds: [4] },
  })
})
test('ordinary users retain locked navigation without requesting protected page data', async ({
  page,
}) => {
  const writes = await backend(page, 'NONE', [])
  let roleReads = 0
  page.on('request', (req) => {
    if (new URL(req.url()).pathname === '/api/v1/roles') roleReads++
  })
  await login(page)
  await page.getByRole('link', { name: '角色管理' }).click()
  await expect(page.getByRole('heading', { name: '暂时无法访问' })).toBeVisible()
  expect(roleReads).toBe(0)
  await page.getByRole('button', { name: '申请权限', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('申请权限', { exact: true }).selectOption('11')
  await dialog.getByLabel('申请理由', { exact: true }).fill('工作需要')
  await dialog.getByRole('button', { name: '提交申请', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path === '/api/v1/permission-requests')?.body).toEqual({
    permissionId: 11,
    reason: '工作需要',
  })
})
test('backend failure at login produces actionable feedback', async ({ page }) => {
  await page.route('**/auth/login', (route) =>
    route.fulfill({ status: 503, json: { detail: '登录服务暂不可用' } }),
  )
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('登录服务暂不可用')
})

test('choosing a parent converts new overlapping direct grants into inherited grants', async ({
  page,
}) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '角色管理' }).click()
  await page.getByRole('button', { name: '新增角色', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('角色名称', { exact: true }).fill('新助教')
  await dialog.getByLabel('直接配置 新增课程', { exact: true }).check()
  await dialog.getByLabel('继承 基础查看', { exact: true }).check()
  await dialog.getByRole('button', { name: '保存角色', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path === '/api/v1/roles' && w.method === 'POST')?.body).toMatchObject(
    { permissionIds: [11], inheritedRoleIds: [4] },
  )
})

test('saving failure retains all role configuration for correction', async ({ page }) => {
  await backend(page)
  await login(page)
  await page.route('**/api/v1/roles', async (route) => {
    if (route.request().method() === 'POST')
      await route.fulfill({ status: 400, json: { detail: '子角色仍有按钮但缺少菜单' } })
    else await route.fallback()
  })
  await page.getByRole('link', { name: '角色管理' }).click()
  await page.getByRole('button', { name: '新增角色', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('角色名称', { exact: true }).fill('保留表单')
  await dialog.getByLabel('继承 基础查看', { exact: true }).check()
  await dialog.getByRole('button', { name: '保存角色', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('子角色仍有按钮但缺少菜单')
  await expect(dialog.getByLabel('角色名称', { exact: true })).toHaveValue('保留表单')
  await expect(dialog.getByLabel('继承 基础查看', { exact: true })).toBeChecked()
})

test('single user authorization sends an empty final list when all business roles are removed', async ({
  page,
}) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '用户授权' }).click()
  await page.getByRole('button', { name: '分配角色', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '分配角色', exact: true })
  await dialog.getByLabel('选择角色 基础查看', { exact: true }).uncheck()
  await dialog.getByRole('button', { name: '保存分配', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path === '/api/v1/users/9/roles')?.body).toEqual({ roleIds: [] })
})

test('batch authorization sends an additive action with selected users and roles', async ({
  page,
}) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '用户授权' }).click()
  await page.getByRole('row', { name: /林晓/ }).getByRole('checkbox').press('Space')
  await page.getByRole('button', { name: '批量分配', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('选择角色 基础查看', { exact: true }).check()
  await dialog.getByRole('button', { name: '保存分配', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes.find((w) => w.path === '/api/v1/user-role-assignments')?.body).toEqual({
    action: 'GRANT',
    userIds: [9],
    roleIds: [4],
  })
})

test('withdrawals and logs use the server state', async ({ page }) => {
  const writes = await backend(page)
  await login(page)
  await page.getByRole('link', { name: '我的申请', exact: true }).click()
  await page.getByRole('button', { name: '撤回', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => writes.some((w) => w.path.endsWith('/7/withdrawals'))).toBe(true)
  await page.getByRole('link', { name: '操作记录' }).click()
  await expect(page.getByRole('cell', { name: '保存角色配置', exact: true })).toBeVisible()
})

test('mobile navigation remains operable without horizontal page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await backend(page)
  await login(page)
  await page.getByRole('button', { name: '打开导航' }).click()
  await page.getByRole('link', { name: '角色管理' }).click()
  await expect(page.getByRole('heading', { name: '角色管理', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({
    path: 'test-results/mobile-roles.png',
    fullPage: true,
    animations: 'disabled',
  })
})

test('representative desktop pages have no runtime errors', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await backend(page)
  await login(page)
  await page.getByRole('link', { name: '角色管理' }).click()
  await expect(page.getByRole('cell', { name: '基础查看', exact: false })).toBeVisible()
  await expect(page.getByRole('cell', { name: '课程系统', exact: true })).toBeVisible()
  await page.screenshot({
    path: 'test-results/desktop-roles.png',
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: '配置权限' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('dialog').getByText('course:view', { exact: true })).toBeVisible()
  await page.screenshot({
    path: 'test-results/desktop-role-editor.png',
    fullPage: true,
    animations: 'disabled',
  })
  expect(errors).toEqual([])
})

test('deleted parent snapshots remain visible without showing an incomplete effective preview', async ({
  page,
}) => {
  await backend(page, 'DEPARTMENT_ADMIN')
  await page.route('**/api/v1/role-creation-requests/8', (route) =>
    route.fulfill({
      json: {
        ...roleRequest,
        inheritedRoles: [{ id: 99, name: '已删除的父角色', exists: false }],
        effectivePermissions: null,
      },
    }),
  )
  await login(page)
  await page.getByRole('link', { name: '角色创建申请' }).click()
  await page.getByRole('button', { name: '查看详情', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('已删除：已删除的父角色', { exact: true })).toBeVisible()
  await expect(
    dialog.getByText('父角色已删除，无法计算完整有效权限。', { exact: true }),
  ).toBeVisible()
})
