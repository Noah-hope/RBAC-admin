<script setup lang="ts">
import { computed } from 'vue';
import UiIcon from './UiIcon.vue';
const props = defineProps<{ total: number; page: number; size: number }>();
const emit = defineEmits<{ 'update:page': [value: number]; 'update:size': [value: number] }>();
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));
</script>
<template>
  <div class="data-pagination">
    <label
      >每页条数<select
        aria-label="每页条数"
        :value="size"
        @change="
          emit('update:size', Number(($event.target as HTMLSelectElement).value));
          emit('update:page', 1);
        "
      >
        <option :value="10">10</option>
        <option :value="20">20</option>
        <option :value="50">50</option>
      </select></label
    >
    <span
      >{{ total ? (page - 1) * size + 1 : 0 }}–{{ Math.min(page * size, total) }} /
      {{ total }} 条</span
    >
    <button
      class="icon-button"
      aria-label="上一页"
      :disabled="page <= 1"
      @click="emit('update:page', page - 1)"
    >
      <UiIcon name="ChevronRight" class="rotate-180" />
    </button>
    <button
      class="icon-button"
      aria-label="下一页"
      :disabled="page >= pages"
      @click="emit('update:page', page + 1)"
    >
      <UiIcon name="ChevronRight" />
    </button>
  </div>
</template>
