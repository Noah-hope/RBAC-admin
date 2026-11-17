<script setup lang="ts">
import { useSession } from './stores/session'
import RequestDialog from './components/RequestDialog.vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
const session = useSession()
</script>
<template>
  <ElConfigProvider :locale="zhCn">
    <RouterView />
    <RequestDialog v-if="session.user" />
  </ElConfigProvider>
</template>
