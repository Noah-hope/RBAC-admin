<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Connection, Key } from '@element-plus/icons-vue'
import { useSession } from '../stores/session'
import { useTask } from '../composables/useTask'
import TaskError from '../components/TaskError.vue'
const session = useSession(),
  route = useRoute(),
  router = useRouter()
const username = ref(''),
  password = ref('')
const { busy, error, run } = useTask()
function destination() {
  const value = route.query.redirect
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//') &&
    !value.startsWith('/login')
    ? value
    : '/me'
}
async function submit() {
  await run(async () => {
    await session.login(username.value, password.value)
    password.value = ''
    await router.replace(destination())
  })
}
async function retry() {
  await run(async () => {
    await session.restore(true)
    if (session.user) await router.replace(destination())
  })
}
</script>
<template>
  <main class="login-page">
    <div class="login-card">
      <section class="login-story">
        <div class="brand">
          <span class="brand-mark">
            <el-icon><Lock /></el-icon>
          </span>
          <div>
            <strong>权限管理中心</strong>
            <small>统一身份 · 统一授权</small>
          </div>
        </div>
        <div class="login-copy">
          <h2>
            让每一项权限
            <br />
            都有清晰的归属。
          </h2>
          <p>
            连接业务应用、角色与用户，
            <br />
            在同一处维护准确、可追溯的访问权限。
          </p>
        </div>
        <div class="access-model">
          <div class="access-hub">
            <el-icon><Key /></el-icon>
            <span>角色与权限</span>
          </div>
          <div class="access-links">
            <span>应用</span>
            <el-icon><Connection /></el-icon>
            <span>业务角色</span>
            <el-icon><Connection /></el-icon>
            <span>用户</span>
          </div>
          <p>权限按应用配置，通过角色授予用户。</p>
        </div>
        <p class="login-foot">支持角色继承与权限来源追溯</p>
      </section>
      <section class="login-form-area">
        <div class="login-heading">
          <span class="login-symbol">
            <el-icon><Key /></el-icon>
          </span>
          <h1>登录权限中心</h1>
          <p>使用统一账号继续</p>
        </div>
        <TaskError :message="error || session.restoreError" />
        <el-button v-if="session.restoreError" @click="retry" :loading="busy">
          重试恢复会话
        </el-button>
        <form class="form-stack" @submit.prevent="submit">
          <label for="login-username">用户名</label>
          <el-input
            id="login-username"
            v-model="username"
            autocomplete="username"
            maxlength="32"
            placeholder="请输入用户名"
            size="large"
            required
          />
          <label for="login-password">密码</label>
          <el-input
            id="login-password"
            v-model="password"
            autocomplete="current-password"
            type="password"
            show-password
            placeholder="请输入密码"
            size="large"
            required
          />
          <el-button
            type="primary"
            size="large"
            native-type="submit"
            :loading="busy"
            class="login-submit"
          >
            登录
          </el-button>
        </form>
        <p class="login-help">账号开通或使用遇到问题，请联系管理员。</p>
      </section>
    </div>
  </main>
</template>
