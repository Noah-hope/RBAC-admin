<script setup lang="ts">
import { ref, watch } from "vue";
import { api, state, errorText } from "../state";
import { display, date } from "../labels";
import type { Operation } from "../api/routes";
import ReferenceLabel from "./ReferenceLabel.vue";
import { referenceFields } from "../references";
const props = withDefaults(
  defineProps<{
    operation: Operation;
    params?: Record<string, string>;
    query?: Record<string, unknown>;
    columns: { key: string; label: string }[];
    search?: boolean;
    selectable?: boolean;
  }>(),
  { search: true },
);
const emit = defineEmits<{ select: [rows: any[]] }>();
const rows = ref<any[]>([]),
  selected = ref<string[]>([]),
  q = ref(""),
  current = ref(1),
  size = ref(20),
  total = ref(0),
  busy = ref(false),
  error = ref("");
let sequence = 0;
function value(row: any, key: string) {
  return key.split(".").reduce((a, k) => a?.[k], row);
}
async function reload() {
  const ticket = ++sequence;
  busy.value = true;
  error.value = "";
  selected.value = [];
  emit("select", []);
  try {
    const r = (await api.call(props.operation, {
      path: props.params,
      query: {
        ...props.query,
        ...(props.search ? { q: q.value } : {}),
        page: current.value,
        pageSize: size.value,
      },
    })) as any;
    if (ticket === sequence) {
      rows.value = r.items;
      total.value = r.total;
    }
  } catch (e) {
    if (ticket === sequence) {
      rows.value = [];
      total.value = 0;
      error.value = errorText(e);
    }
  } finally {
    if (ticket === sequence) busy.value = false;
  }
}
watch(
  () =>
    JSON.stringify([props.operation, props.params, props.query, state.epoch]),
  () => {
    current.value = 1;
    reload();
  },
  { immediate: true },
);
function searchNow() {
  current.value = 1;
  reload();
}
function toggle() {
  emit(
    "select",
    rows.value.filter((r) => selected.value.includes(r.id)),
  );
}
defineExpose({ reload, rows });
</script>
<template>
  <section class="table-card" :aria-busy="busy">
    <form class="table-toolbar" @submit.prevent="searchNow">
      <label v-if="search" class="search"
        ><span class="sr-only">搜索</span
        ><input
          v-model="q"
          placeholder="搜索名称或编号"
          maxlength="100"
        /><button class="button" type="submit">搜索</button></label
      ><slot name="toolbar" /><button
        class="button"
        type="button"
        :disabled="busy"
        aria-label="刷新列表"
        @click="reload"
      >
        刷新
      </button>
    </form>
    <p v-if="error" class="error-box" role="alert">{{ error }}</p>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th v-if="selectable">选择</th>
            <th v-for="c in columns" :key="c.key">{{ c.label }}</th>
            <th v-if="$slots.actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id || row.rowNumber">
            <td v-if="selectable">
              <input
                v-model="selected"
                type="checkbox"
                :value="row.id"
                :aria-label="'选择 ' + (row.name || row.id)"
                @change="toggle"
              />
            </td>
            <td v-for="c in columns" :key="c.key">
              <slot :name="c.key" :row="row"
                ><ReferenceLabel
                  v-if="referenceFields[c.key]"
                  :kind="referenceFields[c.key]!"
                  :id="value(row, c.key)"
                /><span
                  v-else
                  :class="{
                    badge: [
                      'status',
                      'state',
                      'enabled',
                      'executionStatus',
                      'approvalResult',
                      'deliveryStatus',
                    ].includes(c.key),
                  }"
                  >{{
                    c.key.endsWith("At")
                      ? date(value(row, c.key))
                      : display(value(row, c.key))
                  }}</span
                ></slot
              >
            </td>
            <td v-if="$slots.actions" class="actions">
              <slot name="actions" :row="row" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="busy" class="empty" role="status">正在读取…</div>
    <div v-else-if="!rows.length && !error" class="empty">
      暂无符合条件的记录
    </div>
    <footer class="pagination">
      <span>共 {{ total }} 条</span
      ><label
        >每页
        <select v-model.number="size" @change="searchNow">
          <option v-for="n in [10, 20, 50, 100]" :key="n" :value="n">
            {{ n }}
          </option>
        </select></label
      ><button
        class="button"
        :disabled="busy || current === 1"
        @click="
          current--;
          reload();
        "
      >
        上一页</button
      ><span>第 {{ current }} 页</span
      ><button
        class="button"
        :disabled="busy || current * size >= total"
        @click="
          current++;
          reload();
        "
      >
        下一页
      </button>
    </footer>
  </section>
</template>
