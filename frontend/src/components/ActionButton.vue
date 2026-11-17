<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { useSession } from '../stores/session'
import { useUi } from '../stores/ui'
const props = defineProps<{
  permission?: string
  type?: 'primary' | 'danger' | 'success' | 'warning' | 'info'
  text?: boolean
  disabled?: boolean
  loading?: boolean
}>()
const emit = defineEmits<{ click: [] }>()
const session = useSession(),
  ui = useUi()
function click() {
  if (session.has(props.permission)) emit('click')
  else ui.askAccess(props.permission)
}
</script>
<template>
  <el-button
    :type="type"
    :text="text"
    :disabled="disabled"
    :loading="loading"
    :title="session.has(permission) ? undefined : '无权限，点击申请'"
    @click="click"
  >
    <el-icon v-if="!session.has(permission)" aria-hidden="true"><Lock /></el-icon>
    <slot />
  </el-button>
</template>
