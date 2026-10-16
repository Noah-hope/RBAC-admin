<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ value: string; dot?: boolean }>();
const tone = computed(() =>
  [
    '有效',
    '身份有效',
    '在职',
    '已启用',
    '已生效',
    '执行成功',
    '已关联 OA',
    '已批准',
    '允许进入',
  ].includes(props.value)
    ? 'green'
    : ['发送失败', '执行失败', '已驳回'].includes(props.value)
      ? 'red'
      : ['待审批', '待执行', '待发送', '待配置', '待任命'].includes(props.value)
        ? 'amber'
        : 'gray',
);
</script>
<template>
  <span class="status-badge" :class="tone"><span v-if="dot" class="status-dot" />{{ value }}</span>
</template>
