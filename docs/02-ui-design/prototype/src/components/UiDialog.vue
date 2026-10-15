<script setup lang="ts">
import { onMounted, ref, useId, watch } from 'vue';
import UiIcon from './UiIcon.vue';
const props = defineProps<{
  open: boolean;
  title: string;
  subtitle?: string;
  drawer?: boolean;
  wide?: boolean;
}>();
const emit = defineEmits<{ close: [] }>();
const element = ref<HTMLDialogElement>();
const id = useId();
function sync() {
  if (props.open && !element.value?.open) element.value?.showModal();
  else if (!props.open && element.value?.open) element.value.close();
}
watch(() => props.open, sync, { flush: 'post' });
onMounted(sync);
</script>
<template>
  <dialog
    ref="element"
    class="ui-dialog"
    :class="{ drawer, wide }"
    :aria-labelledby="id"
    @cancel.prevent="emit('close')"
    @click="$event.target === element && emit('close')"
  >
    <div class="dialog-frame">
      <header class="dialog-header">
        <div>
          <h2 :id="id">{{ title }}</h2>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <button class="icon-button" aria-label="关闭对话框" @click="emit('close')">
          <UiIcon name="X" />
        </button>
      </header>
      <div class="dialog-body"><slot /></div>
      <footer v-if="$slots.footer" class="dialog-footer"><slot name="footer" /></footer>
    </div>
  </dialog>
</template>
