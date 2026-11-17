<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Lock,
  Grid,
  Collection,
  OfficeBuilding,
  User,
  Key,
  Tickets,
  Checked,
  DocumentAdd,
  Clock,
  Menu,
  ArrowRight,
} from '@element-plus/icons-vue'
import { navigation } from '../router'
import { useSession } from '../stores/session'
import { useUi } from '../stores/ui'
import { useTask } from '../composables/useTask'
import TaskError from '../components/TaskError.vue'
import BrandMark from '../components/BrandMark.vue'
const route = useRoute(),
  router = useRouter(),
  session = useSession(),
  ui = useUi()
const icons = {
  Lock,
  Grid,
  Collection,
  OfficeBuilding,
  User,
  Key,
  Tickets,
  Checked,
  DocumentAdd,
  Clock,
  Menu,
  ArrowRight,
}
const mobileNav = ref(false)
const { busy, error, run } = useTask()
const current = computed(() => navigation.find((n) => n.path === route.path))
type Nav = (typeof navigation)[number]
function accessible(item: Nav) {
  const duty = 'duty' in item ? item.duty : undefined
  return (
    session.has('permission' in item ? item.permission : undefined) &&
    (!duty || (duty === 'total' ? session.totalAdmin : session.administrator))
  )
}
const allowed = computed(() => !current.value || accessible(current.value))
const identity = computed(
  () =>
    ({ TOTAL_ADMIN: '总管理员', DEPARTMENT_ADMIN: '部门管理员', NONE: '普通用户' })[
      session.user?.managementRole ?? 'NONE'
    ],
)
async function logout() {
  await run(async () => {
    await session.logout()
    await router.replace('/login')
  })
}
</script>
<template>
  <div class="admin-shell">
    <aside class="sidebar" :class="{ 'is-open': mobileNav }">
      <RouterLink to="/me" class="brand">
        <BrandMark />
        <div>
          <strong>权限管理系统</strong>
          <small>统一管理应用与权限</small>
        </div>
      </RouterLink>
      <nav aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ locked: !accessible(item) }"
          @click="mobileNav = false"
        >
          <el-icon><component :is="icons[item.icon]" /></el-icon>
          <span>{{ item.title }}</span>
          <el-icon v-if="!accessible(item)" class="nav-lock" aria-hidden="true">
            <icons.Lock />
          </el-icon>
        </RouterLink>
      </nav>
      <div class="sidebar-foot">RBAC1 权限管理系统</div>
    </aside>
    <button v-if="mobileNav" class="nav-scrim" aria-label="关闭导航" @click="mobileNav = false" />
    <div class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          <el-button
            text
            class="mobile-toggle"
            aria-label="打开导航"
            @click="mobileNav = !mobileNav"
          >
            <el-icon><icons.Menu /></el-icon>
          </el-button>
          <span>权限管理系统</span>
          <el-icon><icons.ArrowRight /></el-icon>
          <strong>{{ current?.title ?? '权限中心' }}</strong>
        </div>
        <div class="top-identity">
          <span class="account-context">当前账号</span>
          <span class="avatar" aria-hidden="true">{{ session.user?.name.slice(0, 1) }}</span>
          <strong :title="identity">{{ session.user?.name }}</strong>
          <el-button :loading="busy" @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="content" id="main-content">
        <TaskError :message="error" />
        <RouterView v-if="allowed" :key="route.path" />
        <section v-else class="denied-panel">
          <span class="denied-mark">
            <el-icon><icons.Lock /></el-icon>
          </span>
          <h1>暂时无法访问</h1>
          <p>
            当前账号暂时无法使用「{{ current?.title }}」。管理员功能还需具备相应身份和管理范围。
          </p>
          <div class="inline-actions">
            <el-button
              type="primary"
              @click="ui.askAccess(current && 'permission' in current ? current.permission : '')"
            >
              申请权限
            </el-button>
            <el-button @click="router.push('/me')">返回我的权限</el-button>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>
