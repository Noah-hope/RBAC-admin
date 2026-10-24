<script setup lang="ts">
import { ref, watch } from "vue";
import { api, errorText } from "../state";
import { Mutation } from "../api/client";
import type {
  ImportFile,
  ImportInput,
  FieldMapping,
  OrganizationMapping,
} from "../api/schema";
import Panel from "./Panel.vue";
import { display } from "../labels";
const emit = defineEmits<{ close: []; created: [id: string] }>();
const file = ref<File>(),
  uploaded = ref<ImportFile>(),
  kind = ref<ImportInput["importType"]>("ORGANIZATION"),
  sourceSystem = ref(""),
  sourceBatchId = ref(crypto.randomUUID()),
  worksheet = ref(""),
  employment = ref<ImportInput["initialEmploymentState"]>("EXPLICIT_ACTIVE"),
  reason = ref(""),
  busy = ref(false),
  error = ref("");
const uploadMutation = new Mutation(),
  batchMutation = new Mutation();
const fields: FieldMapping["targetField"][] = [
  "employeeId",
  "employeeKey",
  "previousEmployeeKey",
  "name",
  "action",
  "companyCode",
  "departmentCode",
  "positionCode",
  "employmentId",
  "expectedVersion",
  "companyName",
  "departmentName",
  "positionName",
  "targetCompanyCode",
  "employmentState",
  "startedAt",
  "endedAt",
];
const fieldNames: Record<string, string> = {
  employeeId: "员工标识",
  employeeKey: "员工编号",
  previousEmployeeKey: "旧员工编号",
  name: "员工姓名",
  action: "操作",
  companyCode: "公司编码",
  departmentCode: "部门编码",
  positionCode: "岗位编码",
  employmentId: "任职标识",
  expectedVersion: "预期版本",
  companyName: "公司名称",
  departmentName: "部门名称",
  positionName: "岗位名称",
  targetCompanyCode: "迁入公司编码",
  employmentState: "任职状态",
  startedAt: "开始时间",
  endedAt: "结束时间",
};
const defaults: Record<
  ImportInput["importType"],
  FieldMapping["targetField"][]
> = {
  ORGANIZATION: [
    "action",
    "companyCode",
    "companyName",
    "departmentCode",
    "departmentName",
    "positionCode",
    "positionName",
    "expectedVersion",
  ],
  EMPLOYEE_INITIALIZATION: [
    "employeeKey",
    "name",
    "companyCode",
    "departmentCode",
    "positionCode",
  ],
  PERSONNEL_CHANGES: [
    "action",
    "employeeKey",
    "name",
    "companyCode",
    "departmentCode",
    "positionCode",
    "employmentId",
    "expectedVersion",
  ],
};
const mappings = ref<FieldMapping[]>(
    defaults[kind.value].map((f) => ({ sourceColumn: f, targetField: f })),
  ),
  orgs = ref<OrganizationMapping[]>([]);
watch(kind, () => {
  mappings.value = defaults[kind.value].map((f) => ({
    sourceColumn: f,
    targetField: f,
  }));
});
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
  uploaded.value = undefined;
  worksheet.value = "";
  uploadMutation.clear();
}
async function upload() {
  if (!file.value) return;
  error.value = "";
  if (file.value.size > 10 * 1024 * 1024) {
    error.value = "文件不能超过 10 MiB";
    return;
  }
  if (!/\.(csv|xlsx)$/i.test(file.value.name)) {
    error.value = "请选择 CSV 或 XLSX 文件";
    return;
  }
  busy.value = true;
  try {
    const bytes = await file.value.arrayBuffer(),
      sha = Array.from(
        new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
        (b) => b.toString(16).padStart(2, "0"),
      ).join("");
    const data = new FormData();
    data.append("file", file.value);
    uploaded.value = await api.call("uploadImportFile", {
      body: data,
      key: uploadMutation.key({ sha, name: file.value.name }),
    });
    worksheet.value =
      uploaded.value.sheetNames.length === 1
        ? uploaded.value.sheetNames[0]
        : "";
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
function template() {
  const blob = new Blob(["\uFEFF" + defaults[kind.value].join(",") + "\r\n"], {
    type: "text/csv;charset=utf-8",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = kind.value.toLowerCase() + ".csv";
  a.click();
  URL.revokeObjectURL(url);
}
async function precheck() {
  if (!uploaded.value || busy.value) return;
  error.value = "";
  if (
    new Set(mappings.value.map((m) => m.targetField)).size !==
    mappings.value.length
  ) {
    error.value = "目标字段不能重复映射";
    return;
  }
  busy.value = true;
  try {
    const body: ImportInput = {
      fileId: uploaded.value.id,
      sourceSystem: sourceSystem.value,
      sourceBatchId: sourceBatchId.value,
      importType: kind.value,
      worksheet: worksheet.value,
      fieldMappings: mappings.value,
      organizationMappings: orgs.value.map((o) => ({
        sourcePath: o.sourcePath,
        companyId: o.companyId || null,
        departmentId: o.departmentId || null,
        companyCode: o.companyCode || null,
        departmentCode: o.departmentCode || null,
      })),
      initialEmploymentState: employment.value,
      reason: reason.value,
    };
    const batch = await api.call("precheckImport", {
      body,
      key: batchMutation.key(body),
    });
    emit("created", batch.id);
    emit("close");
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <Panel title="导入资料并预检" wide :busy="busy" @close="emit('close')"
    ><div class="stack">
      <p class="hint">
        上传和预检不会修改正式资料。检查计划与问题报告后，再确认整批提交。文件最多
        10 MiB、1000 条记录。
      </p>
      <label class="field"
        >导入类型<select v-model="kind" :disabled="busy">
          <option
            v-for="t in [
              'ORGANIZATION',
              'EMPLOYEE_INITIALIZATION',
              'PERSONNEL_CHANGES',
            ]"
            :key="t"
            :value="t"
          >
            {{ display(t) }}
          </option>
        </select></label
      >
      <div class="toolbar-actions">
        <button type="button" class="button" @click="template">
          下载空白 CSV 模板</button
        ><a
          class="button"
          href="/import-guide.html"
          target="_blank"
          rel="noopener"
          >字段及操作说明</a
        >
      </div>
      <label class="file-input field"
        >选择文件<input
          type="file"
          accept=".csv,.xlsx"
          :disabled="busy"
          @change="selectFile" /></label
      ><button
        class="button"
        :disabled="!file || busy || !!uploaded"
        @click="upload"
      >
        {{ uploaded ? "文件已上传" : "上传文件" }}
      </button>
      <form v-if="uploaded" class="stack" @submit.prevent="precheck">
        <p class="success">
          已上传 {{ uploaded.fileName }}，{{ uploaded.sizeBytes }} 字节。
        </p>
        <fieldset :disabled="busy">
          <div class="form-grid">
            <label class="field"
              >来源系统<input
                v-model="sourceSystem"
                required
                maxlength="100"
                placeholder="HR" /></label
            ><label class="field"
              >来源批次号<input
                v-model="sourceBatchId"
                required
                maxlength="100" /></label
            ><label class="field"
              >工作表<select v-model="worksheet" required>
                <option value="">请选择工作表</option>
                <option
                  v-for="sheet in uploaded.sheetNames"
                  :key="sheet"
                  :value="sheet"
                >
                  {{ sheet }}
                </option>
              </select></label
            ><label class="field"
              >初始任职状态<select v-model="employment">
                <option value="EXPLICIT_ACTIVE">明确设为有效任职</option>
                <option value="FROM_SOURCE">按文件 employmentState 字段</option>
              </select></label
            >
          </div>
          <h3 class="mt-6 mb-3">列与字段映射</h3>
          <p class="hint">左侧填写文件中的原始列名；未使用的映射请移除。</p>
          <div v-for="(m, i) in mappings" :key="i" class="filters">
            <label class="field"
              >源列名<input
                v-model="m.sourceColumn"
                required
                maxlength="100" /></label
            ><label class="field"
              >目标字段<select v-model="m.targetField">
                <option v-for="f in fields" :key="f" :value="f">
                  {{ fieldNames[f] }} · {{ f }}
                </option>
              </select></label
            ><button
              class="link"
              type="button"
              :disabled="mappings.length === 1"
              @click="mappings.splice(i, 1)"
            >
              移除映射
            </button>
          </div>
          <button
            class="button"
            type="button"
            :disabled="mappings.length >= fields.length"
            @click="
              mappings.push({
                sourceColumn: '',
                targetField: fields.find(
                  (f) => !mappings.some((m) => m.targetField === f),
                )!,
              })
            "
          >
            添加字段映射
          </button>
          <details class="mt-6">
            <summary>显式组织映射（名称歧义或组织迁移时使用）</summary>
            <p class="hint">
              路径由源公司/部门名称组成，可按稳定标识或稳定编码指定目标。
            </p>
            <div v-for="(o, i) in orgs" :key="i" class="line-card mt-3">
              <div class="form-grid">
                <label class="field"
                  >来源路径<input
                    v-model="o.sourcePath"
                    required
                    maxlength="300" /></label
                ><label class="field"
                  >公司标识<input v-model="o.companyId" maxlength="64" /></label
                ><label class="field"
                  >部门标识<input
                    v-model="o.departmentId"
                    maxlength="64" /></label
                ><label class="field"
                  >公司编码<input
                    v-model="o.companyCode"
                    maxlength="100" /></label
                ><label class="field"
                  >部门编码<input v-model="o.departmentCode" maxlength="100"
                /></label>
              </div>
              <button type="button" class="link" @click="orgs.splice(i, 1)">
                移除此映射
              </button>
            </div>
            <button
              class="button mt-3"
              type="button"
              @click="
                orgs.push({
                  sourcePath: '',
                  companyId: null,
                  departmentId: null,
                  companyCode: null,
                  departmentCode: null,
                })
              "
            >
              添加组织映射
            </button>
          </details>
          <label class="field mt-6"
            >办理原因<textarea v-model="reason" required maxlength="500" />
          </label>
        </fieldset>
        <button class="button primary" :disabled="busy">生成预检报告</button>
      </form>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
    </div></Panel
  >
</template>
