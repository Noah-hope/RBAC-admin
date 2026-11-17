<script setup lang="ts">
defineProps<{ message: string }>()
</script>
<template>
  <el-alert
    v-if="message"
    :title="message"
    type="error"
    show-icon
    :closable="false"
    class="task-error"
    role="alert"
  />
</template>
