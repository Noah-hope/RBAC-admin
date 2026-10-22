<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { api, errorText } from "../state";
import { routes, type Operation } from "../api/routes";
const props = defineProps<{
  modelValue: string | string[];
  operation: Operation;
  params?: Record<string, string>;
  query?: Record<string, unknown>;
  multiple?: boolean;
  label: string;
  required?: boolean;
  disabled?: boolean;
}>();
const emit = defineEmits<{ "update:modelValue": [value: any] }>();
const rows = ref<any[]>([]),
  page = ref(1),
  q = ref(""),
  total = ref(0),
  error = ref(""),
  busy = ref(false);
let sequence = 0;
const names = ref<Record<string, string>>({});
const picked = computed(() =>
  Array.isArray(props.modelValue)
    ? props.modelValue
    : props.modelValue
      ? [props.modelValue]
      : [],
);
async function load() {
  const ticket = ++sequence;
  busy.value = true;
  error.value = "";
  try {
    const r = (await api.call(props.operation, {
      path: props.params,
      query: {
        ...props.query,
        page: page.value,
        pageSize: 20,
        ...((routes[props.operation].query as readonly string[]).includes("q")
          ? { q: q.value }
          : {}),
      },
    })) as any;
    if (ticket === sequence) {
      rows.value = r.items;
      total.value = r.total;
      for (const row of r.items)
        names.value[row.id] =
          row.name || row.loginName || row.employeeKey || row.id;
    }
  } catch (e) {
    if (ticket === sequence) {
      error.value = errorText(e);
      rows.value = [];
    }
  } finally {
    if (ticket === sequence) busy.value = false;
  }
}
watch(
  () => JSON.stringify([props.operation, props.params, props.query]),
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
function choose(id: string) {
  if (!id) return;
  if (props.multiple) {
    if (!picked.value.includes(id))
      emit("update:modelValue", [...picked.value, id]);
  } else emit("update:modelValue", id);
}
</script>
<template>
  <div class="entity-select">
    <div class="selector-search">
      <input
        v-model="q"
        :aria-label="label + '筛选'"
        placeholder="筛选候选项"
        :disabled="disabled"
        @keydown.enter.prevent="
          page = 1;
          load();
        "
      /><button
        class="button small"
        type="button"
        :disabled="disabled || busy"
        @click="
          page = 1;
          load();
        "
      >
        查找
      </button>
    </div>
    <select
      :aria-label="label"
      :disabled="disabled || busy"
      :value="multiple ? '' : modelValue"
      :required="required && !picked.length"
      @change="choose(($event.target as HTMLSelectElement).value)"
    >
      <option value="">请选择{{ label }}</option>
      <option
        v-if="!multiple && modelValue && !rows.some((r) => r.id === modelValue)"
        :value="modelValue as string"
      >
        {{ names[modelValue as string] || modelValue }}
      </option>
      <option v-for="r in rows" :key="r.id" :value="r.id">
        {{ r.name || r.loginName || r.employeeKey || r.id
        }}{{ r.code ? " · " + r.code : ""
        }}{{ r.status && r.status !== "ACTIVE" ? " · " + r.status : "" }}
      </option>
    </select>
    <div class="selector-pages">
      <button
        type="button"
        :disabled="disabled || busy || page === 1"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span
      ><button
        type="button"
        :disabled="disabled || busy || page * 20 >= total"
        @click="
          page++;
          load();
        "
      >
        下一页</button
      ><button
        v-if="!required && !multiple && picked.length"
        type="button"
        :disabled="disabled"
        @click="emit('update:modelValue', '')"
      >
        清除
      </button>
    </div>
    <div v-if="multiple" class="chips">
      <button
        v-for="id in picked"
        :key="id"
        type="button"
        :disabled="disabled"
        :aria-label="'移除 ' + (names[id] || id)"
        @click="
          emit(
            'update:modelValue',
            picked.filter((x) => x !== id),
          )
        "
      >
        {{ names[id] || id }} ×
      </button>
    </div>
    <p v-if="error" class="error-box" role="alert">{{ error }}</p>
  </div>
</template>
