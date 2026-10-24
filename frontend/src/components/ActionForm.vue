<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import Panel from "./Panel.vue";
import EntitySelect from "./EntitySelect.vue";
import ImpactView from "./ImpactView.vue";
import { Mutation } from "../api/client";
import { errorText } from "../state";
import type { FormSpec, Values } from "../forms";
import type { Preview } from "../api/schema";
const props = defineProps<{ spec: FormSpec }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const values = reactive<Values>({ ...props.spec.initial });
for (const f of props.spec.fields)
  if (values[f.key] === undefined)
    values[f.key] = f.multiple ? [] : f.type === "boolean" ? false : "";
const error = ref(""),
  busy = ref(false),
  preview = ref<Preview>(),
  fixed = ref<Values>();
const mutation = new Mutation();
for (const field of props.spec.fields)
  if (field.dependsOn)
    watch(
      () => field.dependsOn!.map((k) => values[k]),
      () => (values[field.key] = field.multiple ? [] : ""),
    );
async function submit() {
  if (busy.value) return;
  error.value = "";
  busy.value = true;
  try {
    if (props.spec.preview && !preview.value) {
      const snapshot: Values = JSON.parse(JSON.stringify(values));
      fixed.value = snapshot;
      preview.value = await props.spec.preview(snapshot);
      return;
    }
    if (preview.value && Date.parse(preview.value.expiresAt) <= Date.now())
      throw new Error("预览已过期，请重新预览");
    const data = fixed.value || { ...values };
    await props.spec.save(
      data,
      mutation.key({ data, previewId: preview.value?.id }),
      preview.value,
    );
    props.spec.done?.();
    emit("saved");
    emit("close");
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <Panel
    :title="spec.title"
    :busy="busy"
    :wide="!!preview"
    @close="emit('close')"
    ><p v-if="spec.description" class="hint">{{ spec.description }}</p>
    <form class="stack" @submit.prevent="submit">
      <fieldset :disabled="busy || !!preview">
        <div class="form-grid">
          <label
            v-for="f in spec.fields"
            :key="f.key"
            class="field"
            :class="{ full: f.type === 'textarea' || f.multiple }"
            ><span
              >{{ f.label
              }}<span v-if="f.required" class="required"> *</span></span
            ><EntitySelect
              v-if="f.type === 'entity'"
              v-model="values[f.key]"
              :label="f.label"
              :operation="f.operation!"
              :params="f.params"
              :query="typeof f.query === 'function' ? f.query(values) : f.query"
              :multiple="f.multiple"
              :required="f.required"
              :disabled="busy || !!preview"
            /><textarea
              :aria-label="f.label"
              v-else-if="f.type === 'textarea'"
              v-model="values[f.key]"
              :required="f.required"
              :maxlength="f.maxLength || 500"
            /><select
              :aria-label="f.label"
              v-else-if="f.type === 'select'"
              v-model="values[f.key]"
              :required="f.required"
            >
              <option value="">请选择</option>
              <option v-for="o in f.options" :key="o.value" :value="o.value">
                {{ o.label }}
              </option></select
            ><input
              :aria-label="f.label"
              v-else-if="f.type === 'boolean'"
              v-model="values[f.key]"
              type="checkbox"
            /><input
              :aria-label="f.label"
              v-else-if="f.type === 'number'"
              v-model.number="values[f.key]"
              type="number"
              :required="f.required"
            /><input
              :aria-label="f.label"
              v-else
              v-model="values[f.key]"
              :type="f.type || 'text'"
              :required="f.required"
              :minlength="f.minLength"
              :maxlength="f.maxLength || 100"
              :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
            /><small v-if="f.hint">{{ f.hint }}</small></label
          >
        </div>
      </fieldset>
      <ImpactView v-if="preview" :preview="preview" />
      <p v-if="error" role="alert" class="error-box">{{ error }}</p>
      <div class="form-actions">
        <button
          v-if="preview"
          type="button"
          class="button"
          :disabled="busy"
          @click="
            preview = undefined;
            fixed = undefined;
            mutation.clear();
          "
        >
          返回修改 / 重新预览</button
        ><button class="button primary" :disabled="busy">
          {{
            busy
              ? "正在处理…"
              : spec.preview && !preview
                ? "预览影响"
                : preview
                  ? "确认办理"
                  : spec.submitLabel || "保存"
          }}
        </button>
      </div>
    </form></Panel
  >
</template>
