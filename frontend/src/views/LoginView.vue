<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { useSession } from '../stores/session'
import { useTask } from '../composables/useTask'
import TaskError from '../components/TaskError.vue'
const session = useSession(),
  route = useRoute(),
  router = useRouter()
const username = ref(''),
  password = ref(''),
  showPassword = ref(false)
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
  <main class="login-page" aria-labelledby="login-title">
    <div class="login-card">
      <section class="login-form-area">
        <div class="login-heading">
          <BrandMark />
          <h1 id="login-title">权限管理中心</h1>
          <p>使用统一账号登录</p>
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
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入密码"
            size="large"
            required
          >
            <template #suffix>
              <button
                type="button"
                class="password-toggle"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :aria-pressed="showPassword"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </template>
          </el-input>
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
