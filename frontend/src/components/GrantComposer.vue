<script setup lang="ts">
import { ref } from "vue";
import { api, state, errorText } from "../state";
import { Mutation } from "../api/client";
import type { Employee, ChangeRequest, GrantLine } from "../api/schema";
import Panel from "./Panel.vue";
import EntitySelect from "./EntitySelect.vue";
const props = defineProps<{ employees?: Employee[]; draft?: ChangeRequest }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const company = ref(
    props.draft?.content.companyId || state.appointment?.companyId || "",
  ),
  reason = ref(props.draft?.reason || ""),
  busy = ref(false),
  error = ref("");
let serial = 0;
const lines = ref(
  (props.draft?.content.kind === "ROLE_GRANT"
    ? props.draft.content.items
    : (props.employees || []).map((e) => ({
        employeeId: e.id,
        roleId: "",
        sourceDepartmentId: state.appointment?.departmentId || "",
      }))
  ).map((x) => ({ ...x, row: serial++ })),
);
if (!lines.value.length)
  lines.value.push({
    employeeId: "",
    roleId: "",
    sourceDepartmentId: state.appointment?.departmentId || "",
    row: serial++,
  });
const mutation = new Mutation();
function changedCompany() {
  lines.value = lines.value.map((x) => ({
    ...x,
    roleId: "",
    sourceDepartmentId: "",
  }));
}
async function save() {
  if (busy.value) return;
  error.value = "";
  if (
    !company.value ||
    lines.value.some((l) => !l.employeeId || !l.roleId || !l.sourceDepartmentId)
  ) {
    error.value = "每条明细须选择员工、角色和来源部门";
    return;
  }
  if (
    new Set(
      lines.value.map((l) =>
        [l.employeeId, l.roleId, l.sourceDepartmentId].join("/"),
      ),
    ).size !== lines.value.length
  ) {
    error.value = "不能重复添加同一员工、角色和来源部门";
    return;
  }
  busy.value = true;
  try {
    const keys = lines.value.map((l) => ({
      employeeId: l.employeeId,
      roleId: l.roleId,
      sourceCompanyId: company.value,
      sourceDepartmentId: l.sourceDepartmentId,
    }));
    const baselines = await api.call("grantBaselines", {
      body: { items: keys },
    });
    const items: GrantLine[] = baselines.items.map((b) => ({
      ...b.key,
      expectedRoleConfigurationVersion: b.roleConfigurationVersion,
      expectedSourceEpoch: b.sourceEpoch,
      expectedRelationVersion: b.relationVersion,
      employmentIds: b.employmentIds,
    }));
    const content = {
      kind: "ROLE_GRANT" as const,
      companyId: company.value,
      items,
    };
    if (props.draft) {
      const body = {
        content,
        reason: reason.value,
        expectedDraftVersion: props.draft.draftVersion,
      };
      await api.call("updateChangeDraft", {
        path: { requestId: props.draft.id },
        body,
        key: mutation.key(body),
      });
    } else {
      const body = { content, reason: reason.value };
      await api.call("createChangeDraft", { body, key: mutation.key(body) });
    }
    emit("saved");
    emit("close");
    location.hash = "/requests";
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <Panel title="申请分配角色" wide :busy="busy" @close="emit('close')"
    ><form class="stack" @submit.prevent="save">
      <p class="hint">
        每条明细指定一个员工、一个角色和一个来源部门。保存时读取当前任职及授权基线；至多
        100 条，同一申请只属于一家公司。
      </p>
      <label class="field"
        >来源公司<EntitySelect
          v-model="company"
          label="来源公司"
          operation="listCompanies"
          required
          :disabled="busy"
          @update:model-value="changedCompany"
      /></label>
      <div v-for="(line, index) in lines" :key="line.row" class="line-card">
        <div class="line-header">
          <strong>明细 {{ index + 1 }}</strong
          ><button
            v-if="lines.length > 1"
            class="link"
            type="button"
            :disabled="busy"
            @click="lines.splice(index, 1)"
          >
            移除
          </button>
        </div>
        <div class="form-grid">
          <label class="field"
            >员工<EntitySelect
              v-model="line.employeeId"
              label="员工"
              operation="listEmployees"
              :query="{ companyId: company, status: 'ACTIVE' }"
              required
              :disabled="busy" /></label
          ><label class="field"
            >角色<EntitySelect
              v-model="line.roleId"
              label="角色"
              operation="listRoles"
              :query="{ companyId: company, state: 'EFFECTIVE' }"
              required
              :disabled="busy" /></label
          ><label class="field"
            >来源部门<EntitySelect
              v-model="line.sourceDepartmentId"
              label="来源部门"
              operation="listDepartments"
              :query="{ companyId: company }"
              required
              :disabled="busy"
          /></label>
        </div>
      </div>
      <button
        class="button"
        type="button"
        :disabled="busy || lines.length >= 100"
        @click="
          lines.push({
            employeeId: '',
            roleId: '',
            sourceDepartmentId: '',
            row: serial++,
          })
        "
      >
        添加明细</button
      ><label class="field"
        >办理原因<textarea v-model="reason" required maxlength="500" />
      </label>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button class="button primary" :disabled="busy">保存分配草稿</button>
      </div>
    </form></Panel
  >
</template>
