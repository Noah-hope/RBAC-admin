import { createRouter, createWebHistory } from 'vue-router'
import { useSession } from '../stores/session'

export const navigation = [
  {
    path: '/applications',
    title: '应用与权限',
    permission: 'application:view',
    duty: 'total',
    icon: 'Grid',
    description: '登记业务应用，维护菜单、按钮与接口关联。',
  },
  {
    path: '/roles',
    title: '角色管理',
    permission: 'role:view',
    duty: 'admin',
    icon: 'Collection',
    description: '配置直接权限与角色继承，查看每项权限的实际来源。',
  },
  {
    path: '/organization',
    title: '用户与部门',
    permission: 'user:view',
    duty: 'admin',
    icon: 'OfficeBuilding',
    description: '维护组织结构、用户资料与账号状态。',
  },
  {
    path: '/assignments',
    title: '用户授权',
    permission: 'assignment:view',
    duty: 'admin',
    icon: 'User',
    description: '向管理范围内的用户分配或撤销业务角色。',
  },
  {
    path: '/me',
    title: '我的权限',
    icon: 'Key',
    description: '查看业务角色，以及各应用中可使用的菜单和按钮。',
  },
  {
    path: '/requests',
    title: '我的申请',
    icon: 'Tickets',
    description: '申请工作所需权限，查看处理进度。',
  },
  {
    path: '/reviews',
    title: '权限申请审核',
    permission: 'request:view',
    duty: 'admin',
    icon: 'Checked',
    description: '核对申请需求，选择合适的业务角色完成授权。',
  },
  {
    path: '/role-requests',
    title: '角色创建申请',
    permission: 'roleRequest:view',
    duty: 'admin',
    icon: 'DocumentAdd',
    description: '现有角色无法满足需求时，提出并处理角色创建申请。',
  },
  {
    path: '/logs',
    title: '操作记录',
    permission: 'audit:view',
    duty: 'admin',
    icon: 'Clock',
    description: '查看管理范围内的重要操作和处理结果。',
  },
] as const

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue') },
    {
      path: '/',
      component: () => import('../layouts/AdminLayout.vue'),
      redirect: '/me',
      children: [
        { path: 'applications', component: () => import('../views/ApplicationsView.vue') },
        { path: 'roles', component: () => import('../views/RolesView.vue') },
        { path: 'organization', component: () => import('../views/UsersView.vue') },
        { path: 'assignments', component: () => import('../views/AssignmentsView.vue') },
        { path: 'me', component: () => import('../views/MyPermissionsView.vue') },
        {
          path: 'requests',
          component: () => import('../views/RequestsView.vue'),
          props: { review: false },
        },
        {
          path: 'reviews',
          component: () => import('../views/RequestsView.vue'),
          props: { review: true },
        },
        { path: 'role-requests', component: () => import('../views/RoleRequestsView.vue') },
        { path: 'logs', component: () => import('../views/LogsView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
})
router.beforeEach(async (to) => {
  const session = useSession()
  await session.restore()
  if (to.path !== '/login' && !session.user)
    return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && session.user) return '/me'
})
