<script setup lang="ts">
import { ref, onMounted, watch, useId } from "vue";
const props = defineProps<{ title: string; wide?: boolean; busy?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const el = ref<HTMLDialogElement>();
const titleId = useId();
onMounted(() => el.value?.showModal());
</script>
<template>
  <dialog
    ref="el"
    class="panel"
    :class="{ wide }"
    :aria-labelledby="titleId"
    @cancel.prevent="!busy && emit('close')"
  >
    <header>
      <h2 :id="titleId">{{ title }}</h2>
      <button
        class="icon-button"
        :disabled="busy"
        aria-label="关闭对话框"
        @click="emit('close')"
      >
        ×
      </button>
    </header>
    <div class="panel-body"><slot /></div>
  </dialog>
</template>
