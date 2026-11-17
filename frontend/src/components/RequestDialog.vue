<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import type { RequestablePermissions } from '../api/types'
import { useUi } from '../stores/ui'
import { useSession } from '../stores/session'
import { useTask } from '../composables/useTask'
import TaskError from './TaskError.vue'
const ui = useUi(),
  session = useSession()
const { busy, error, run } = useTask()
const directory = ref<RequestablePermissions>({ applications: [] })
const appId = ref<number>(),
  permissionId = ref<number>(),
  reason = ref('')
const app = computed(() => directory.value.applications.find((a) => a.id === appId.value))
watch(
  () => ui.requestOpen,
  (open) => {
    if (open)
      void run(async () => {
        permissionId.value = undefined
        reason.value = ''
        directory.value = { applications: [] }
        directory.value = await api.requestable()
        const matching = directory.value.applications.find((a) =>
          a.groups.some((g) => g.permissions.some((p) => p.code === ui.requestedCode)),
        )
        appId.value = (matching ?? directory.value.applications[0])?.id
        permissionId.value = matching?.groups
          .flatMap((g) => g.permissions)
          .find((p) => p.code === ui.requestedCode)?.id
      })
  },
)
async function submit() {
  await run(async () => {
    await api.submitRequest(permissionId.value!, reason.value.trim())
    ui.requestRevision++
    ui.requestOpen = false
  }, '申请已提交')
}
</script>
<template>
  <el-dialog
    v-model="ui.requestOpen"
    title="申请权限"
    width="540px"
    :close-on-click-modal="false"
    :before-close="
      (done: () => void) => {
        if (!busy) done()
      }
    "
  >
    <form v-loading="busy" @submit.prevent="submit">
      <TaskError :message="error" />
      <el-alert
        v-if="session.user?.managementRole !== 'NONE'"
        title="管理员职责和部门范围仍由系统校验，申请权限不会改变管理员身份。"
        type="info"
        :closable="false"
      />
      <div class="form-stack">
        <label for="request-app">业务应用</label>
        <select id="request-app" v-model="appId" required @change="permissionId = undefined">
          <option v-for="a in directory.applications" :key="a.id" :value="a.id">
            {{ a.name }}
          </option>
        </select>
        <label for="request-permission">申请权限</label>
        <select id="request-permission" v-model="permissionId" required>
          <option :value="undefined" disabled>请选择所需权限</option>
          <optgroup v-for="g in app?.groups" :key="g.menu.id" :label="g.menu.name">
            <option v-for="p in g.permissions" :key="p.id" :value="p.id">
              {{ p.name }}（{{ p.type === 'MENU' ? '菜单' : '按钮' }}）
            </option>
          </optgroup>
        </select>
        <p class="muted">目录仅列出当前可申请的权限。未获得菜单时，请先申请菜单。</p>
        <label for="request-reason">申请理由</label>
        <textarea
          id="request-reason"
          v-model="reason"
          required
          maxlength="500"
          rows="4"
          placeholder="说明工作中为什么需要这项权限"
        />
      </div>
      <div v-if="!busy && !directory.applications.length && !error" class="empty-note">
        目前没有可申请的权限。
      </div>
      <div class="dialog-actions">
        <el-button :disabled="busy" @click="ui.requestOpen = false">取消</el-button>
        <el-button
          type="primary"
          native-type="submit"
          :loading="busy"
          :disabled="!permissionId || !reason.trim()"
        >
          提交申请
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>
