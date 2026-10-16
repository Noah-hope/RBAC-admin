<script setup lang="ts">
import { onMounted, ref, useId, watch, nextTick } from 'vue';
import UiIcon from './UiIcon.vue';
import { actionError } from '../store';
const props = defineProps<{
  open: boolean;
  title: string;
  subtitle?: string;
  drawer?: boolean;
  wide?: boolean;
  dirty?: boolean;
}>();
const emit = defineEmits<{ close: [] }>();
const element = ref<HTMLDialogElement>();
const id = useId();
const discard = ref(false);
const continueButton = ref<HTMLButtonElement>();
let previousFocus: HTMLElement | null = null;
function requestClose() {
  if (props.dirty) {
    previousFocus = document.activeElement as HTMLElement;
    discard.value = true;
    nextTick(() => continueButton.value?.focus());
  } else emit('close');
}
function keepEditing() {
  discard.value = false;
  nextTick(() => previousFocus?.focus());
}
defineExpose({ requestClose });
function sync() {
  if (props.open && !element.value?.open) {
    actionError.value = '';
    element.value?.showModal();
  } else if (!props.open && element.value?.open) {
    discard.value = false;
    element.value.close();
  }
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
    @cancel.prevent="requestClose"
    @click="$event.target === element && requestClose()"
  >
    <div v-if="discard" class="discard-confirm" role="alertdialog" aria-label="放弃未保存修改">
      <h2>放弃未保存修改？</h2>
      <p>这次编辑还没有保存。放弃不会改变已生效配置或撤回已提交申请。</p>
      <div class="detail-actions">
        <button ref="continueButton" class="button primary" @click="keepEditing">继续编辑</button
        ><button
          class="button"
          @click="
            discard = false;
            emit('close');
          "
        >
          放弃修改
        </button>
      </div>
    </div>
    <div class="dialog-frame" :inert="discard">
      <header class="dialog-header">
        <div>
          <h2 :id="id">{{ title }}</h2>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <button class="icon-button" aria-label="关闭对话框" @click="requestClose">
          <UiIcon name="X" />
        </button>
      </header>
      <div class="dialog-body">
        <p v-if="actionError" class="error-box mb-4" role="alert">{{ actionError }}</p>
        <slot />
      </div>
      <footer v-if="$slots.footer" class="dialog-footer"><slot name="footer" /></footer>
    </div>
  </dialog>
</template>
