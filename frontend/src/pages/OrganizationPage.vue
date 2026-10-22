<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { api, errorText } from "../state";
import { Mutation } from "../api/client";
import type { ImportBatch } from "../api/schema";
import DataTable from "../components/DataTable.vue";
import EntitySelect from "../components/EntitySelect.vue";
import Panel from "../components/Panel.vue";
import RecordDetails from "../components/RecordDetails.vue";
import ImportWizard from "../components/ImportWizard.vue";
const tab = ref("companies"),
  company = ref(""),
  department = ref(""),
  wizard = ref(false),
  batch = ref<ImportBatch>(),
  table = ref<InstanceType<typeof DataTable>>(),
  busy = ref(false),
  error = ref(""),
  confirmed = ref(false);
const mutation = new Mutation();
watch(company, () => (department.value = ""));
const view = computed(
  () =>
    ({
      companies: {
        op: "listCompanies" as const,
        columns: [
          { key: "code", label: "公司编码" },
          { key: "name", label: "公司名称" },
        ],
        query: {},
      },
      departments: {
        op: "listDepartments" as const,
        columns: [
          { key: "code", label: "部门编码" },
          { key: "name", label: "部门名称" },
          { key: "companyId", label: "所属公司" },
        ],
        query: { companyId: company.value },
      },
      positions: {
        op: "listPositions" as const,
        columns: [
          { key: "code", label: "岗位编码" },
          { key: "name", label: "岗位名称" },
          { key: "departmentId", label: "所属部门" },
        ],
        query: { companyId: company.value, departmentId: department.value },
      },
      imports: {
        op: "listImportBatches" as const,
        columns: [
          { key: "input.sourceBatchId", label: "来源批次号" },
          { key: "input.importType", label: "资料类型" },
          { key: "state", label: "状态" },
          { key: "blockingIssueCount", label: "阻断问题" },
          { key: "warningCount", label: "警告" },
          { key: "createdAt", label: "创建时间" },
        ],
        query: {},
      },
    })[tab.value as "companies" | "departments" | "positions" | "imports"],
);
async function open(id: string) {
  error.value = "";
  confirmed.value = false;
  try {
    batch.value = await api.call("getImportBatch", { path: { batchId: id } });
    mutation.clear();
  } catch (e) {
    error.value = errorText(e);
  }
}
async function commit() {
  if (!batch.value || busy.value || !confirmed.value) return;
  busy.value = true;
  error.value = "";
  try {
    const body = {
      expectedVersion: batch.value.version,
      contentDigest: batch.value.contentDigest,
    };
    batch.value = await api.call("commitImport", {
      path: { batchId: batch.value.id },
      body,
      key: mutation.key(body),
    });
    confirmed.value = false;
    table.value?.reload();
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function recheck() {
  if (!batch.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    batch.value = await api.call("precheckImport", {
      body: batch.value.input,
      key: mutation.key({
        recheck: batch.value.id,
        version: batch.value.version,
        input: batch.value.input,
      }),
    });
    confirmed.value = false;
    table.value?.reload();
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>员工组织</h1>
      <p>公司、平级部门、岗位与任职通过资料导入维护；导入不会自动授权。</p>
    </div>
    <button class="button primary" @click="wizard = true">导入资料</button>
  </div>
  <div class="tabs">
    <button
      v-for="(name, key) in {
        companies: '公司',
        departments: '部门',
        positions: '岗位',
        imports: '导入批次',
      }"
      :key="key"
      :class="{ active: tab === key }"
      @click="tab = key"
    >
      {{ name }}
    </button>
  </div>
  <div v-if="tab === 'departments' || tab === 'positions'" class="filters">
    <label class="field"
      >公司<EntitySelect
        v-model="company"
        label="公司筛选"
        operation="listCompanies" /></label
    ><label v-if="tab === 'positions'" class="field"
      >部门<EntitySelect
        v-model="department"
        label="部门筛选"
        operation="listDepartments"
        :query="{ companyId: company }"
    /></label>
  </div>
  <p v-if="error && !batch" class="error-box" role="alert">{{ error }}</p>
  <DataTable
    ref="table"
    :key="tab"
    :operation="view.op"
    :columns="view.columns"
    :query="view.query"
    :search="tab !== 'imports'"
    ><template v-if="tab === 'imports'" #actions="{ row }"
      ><button class="link" @click="open(row.id)">预检与结果</button></template
    ></DataTable
  ><ImportWizard
    v-if="wizard"
    @close="wizard = false"
    @created="
      tab = 'imports';
      open($event);
      table?.reload();
    "
  /><Panel
    v-if="batch"
    title="导入计划与结果"
    wide
    :busy="busy"
    @close="batch = undefined"
    ><div class="stack">
      <RecordDetails
        :value="{
          id: batch.id,
          state: batch.state,
          version: batch.version,
          contentDigest: batch.contentDigest,
          failureCode: batch.failureCode,
        }"
      />
      <h3>{{ batch.actualCounts ? "实际处理结果" : "预期影响" }}</h3>
      <RecordDetails :value="batch.actualCounts || batch.plannedCounts" />
      <p class="warning">
        阻断问题 {{ batch.blockingIssueCount }} 项；警告
        {{ batch.warningCount }} 项。资料缺行不代表员工离职。
      </p>
      <h3>问题报告</h3>
      <DataTable
        :key="batch.id + ':issues:' + batch.version"
        operation="listImportIssues"
        :params="{ batchId: batch.id }"
        :search="false"
        :columns="[
          { key: 'rowNumber', label: '行' },
          { key: 'severity', label: '级别' },
          { key: 'field', label: '字段' },
          { key: 'message', label: '说明' },
        ]"
      />
      <h3>冻结变更计划</h3>
      <DataTable
        :key="batch.id + ':changes:' + batch.version"
        operation="listImportPlanChanges"
        :params="{ batchId: batch.id }"
        :search="false"
        :columns="[
          { key: 'rowNumber', label: '行' },
          { key: 'action', label: '操作' },
          { key: 'summary', label: '变更说明' },
          { key: 'fieldChanges', label: '字段变化' },
        ]"
      />
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <template v-if="batch.state === 'READY'"
        ><label
          ><input v-model="confirmed" type="checkbox" :disabled="busy" />
          我已核对全部变更及警告，确认按本次冻结计划整批提交</label
        ><button
          class="button primary"
          :disabled="busy || !confirmed"
          @click="commit"
        >
          确认整批提交
        </button></template
      ><template
        v-else-if="['STALE', 'FAILED', 'BLOCKED'].includes(batch.state)"
        ><p class="hint">
          原批次不可直接提交。修正源数据时重新上传；仅状态变化时可重新预检。
        </p>
        <button class="button" :disabled="busy" @click="recheck">
          按原文件重新预检
        </button></template
      ><button class="button" :disabled="busy" @click="open(batch.id)">
        刷新批次状态
      </button>
    </div></Panel
  >
</template>
