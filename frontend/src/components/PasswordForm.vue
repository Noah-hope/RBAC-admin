<script setup lang="ts">
import { ref } from "vue";
import { api, clearSession, errorText } from "../state";
const current = ref(""),
  next = ref(""),
  confirm = ref(""),
  error = ref(""),
  busy = ref(false);
async function submit() {
  error.value = "";
  if (next.value !== confirm.value) {
    error.value = "两次新密码不一致";
    return;
  }
  if (next.value === current.value) {
    error.value = "新密码不能与当前密码相同";
    return;
  }
  busy.value = true;
  try {
    await api.call("changeOwnPassword", {
      body: {
        currentPassword: current.value,
        newPassword: next.value,
        confirmPassword: confirm.value,
      },
    });
    clearSession("密码已修改，请使用新密码重新登录");
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <form class="stack" @submit.prevent="submit">
    <p class="hint">新密码为 12～128 位。修改后全部旧会话失效。</p>
    <label class="field"
      >当前密码<input
        v-model="current"
        type="password"
        required
        maxlength="128"
        autocomplete="current-password" /></label
    ><label class="field"
      >新密码<input
        v-model="next"
        type="password"
        required
        minlength="12"
        maxlength="128"
        autocomplete="new-password" /></label
    ><label class="field"
      >确认新密码<input
        v-model="confirm"
        type="password"
        required
        minlength="12"
        maxlength="128"
        autocomplete="new-password"
    /></label>
    <p v-if="error" class="error-box" role="alert">{{ error }}</p>
    <button class="button primary" :disabled="busy">
      {{ busy ? "正在修改…" : "修改密码并重新登录" }}
    </button>
  </form>
</template>
