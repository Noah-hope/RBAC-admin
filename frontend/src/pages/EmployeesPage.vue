<script setup lang="ts">
import { ref, watch } from "vue";
import { api, isSuper, state, errorText } from "../state";
import type { Employee, Grant, SourceCloseInput } from "../api/schema";
import type { FormSpec } from "../forms";
import { reason, entity } from "../forms";
import DataTable from "../components/DataTable.vue";
import EntitySelect from "../components/EntitySelect.vue";
import Panel from "../components/Panel.vue";
import ActionForm from "../components/ActionForm.vue";
import RecordDetails from "../components/RecordDetails.vue";
import GrantComposer from "../components/GrantComposer.vue";
const table = ref<InstanceType<typeof DataTable>>(),
  detail = ref<Employee>(),
  form = ref<FormSpec>(),
  grantOpen = ref(false),
  picked = ref<Employee[]>([]),
  company = ref(""),
  department = ref(""),
  status = ref(""),
  error = ref("");
watch(company, () => (department.value = ""));
async function open(e: Employee) {
  try {
    detail.value = await api.call("getEmployee", {
      path: { employeeId: e.id },
    });
  } catch (e) {
    error.value = errorText(e);
  }
}
async function changeState(e: Employee) {
  try {
    const fresh = await api.call("getEmployee", { path: { employeeId: e.id } });
    const make = (v: any) => ({
      expectedVersion: fresh.version,
      status:
        fresh.status === "ACTIVE" ? ("INACTIVE" as const) : ("ACTIVE" as const),
      reason: v.reason,
    });
    form.value = {
      title: fresh.status === "ACTIVE" ? "停用员工" : "恢复员工",
      description:
        "全局停用会关闭员工授权并撤销后台资格；恢复员工不会恢复旧权限或任命。",
      fields: [reason],
      preview: (v) =>
        api.call("employeeStatePreview", {
          path: { employeeId: e.id },
          body: make(v),
        }),
      save: (v, key, p) =>
        api.call("setEmployeeState", {
          path: { employeeId: e.id },
          body: { input: make(v), previewId: p!.id },
          key,
        }),
    };
  } catch (e) {
    error.value = errorText(e);
  }
}
function closeSource(e: Employee) {
  let input: SourceCloseInput;
  form.value = {
    title: "关闭部门授权来源",
    description:
      "直接关闭所选部门、系统的来源，并使相关待处理申请失效。其他独立来源仍按规则有效。",
    initial: {
      companyId: state.appointment?.companyId || company.value,
      departmentId: state.appointment?.departmentId || department.value,
    },
    fields: [
      entity("companyId", "来源公司", "listCompanies"),
      entity("departmentId", "来源部门", "listDepartments", {
        query: (v) => ({ companyId: v.companyId }),
        dependsOn: ["companyId"],
      }),
      entity("systemIds", "业务系统", "listSystems", { multiple: true }),
      reason,
    ],
    preview: async (v) => {
      if (!v.systemIds.length) throw new Error("请选择至少一个业务系统");
      const r = await api.call("sourceBaselines", {
        body: {
          employeeId: e.id,
          companyId: v.companyId,
          departmentId: v.departmentId,
          systemIds: v.systemIds,
        },
      });
      input = {
        employeeId: e.id,
        companyId: v.companyId,
        departmentId: v.departmentId,
        systems: r.items.map((b) => ({
          systemId: b.source.systemId,
          expectedSourceEpoch: b.sourceEpoch,
          expectedVersion: b.version,
        })),
        reason: v.reason,
      };
      return api.call("sourceClosePreview", { body: input });
    },
    save: (_v, key, p) =>
      api.call("closeSource", { body: { input, previewId: p!.id }, key }),
    done: () => {
      detail.value = undefined;
    },
  };
}
function revoke(g: Grant) {
  form.value = {
    title: "申请撤销角色",
    description: "一般撤销通过 OA 审批后执行，当前授权不会立即删除。",
    fields: [reason],
    save: async (v, key) => {
      await api.call("createChangeDraft", {
        body: {
          content: {
            kind: "ROLE_REVOCATION",
            companyId: g.companyId,
            items: [
              { grantId: g.id, expectedRelationVersion: g.relationVersion },
            ],
          },
          reason: v.reason,
        },
        key,
      });
      location.hash = "/requests";
    },
  };
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>员工授权</h1>
      <p>按任职与来源办理授权，每一项权限均可追溯。</p>
    </div>
    <button
      class="button primary"
      :disabled="picked.length > 100"
      @click="grantOpen = true"
    >
      申请分配角色{{ picked.length ? "（" + picked.length + "）" : "" }}
    </button>
  </div>
  <div class="filters">
    <label class="field"
      >公司<EntitySelect
        v-model="company"
        label="公司筛选"
        operation="listCompanies" /></label
    ><label class="field"
      >部门<EntitySelect
        v-model="department"
        label="部门筛选"
        operation="listDepartments"
        :query="{ companyId: company }" /></label
    ><label class="field"
      >员工状态<select v-model="status">
        <option value="">全部状态</option>
        <option value="ACTIVE">有效</option>
        <option value="INACTIVE">已停用</option>
      </select></label
    >
  </div>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <DataTable
    ref="table"
    operation="listEmployees"
    selectable
    :query="{ companyId: company, departmentId: department, status }"
    :columns="[
      { key: 'name', label: '员工姓名' },
      { key: 'employeeKey', label: '员工编号' },
      { key: 'status', label: '员工状态' },
      { key: 'createdAt', label: '创建时间' },
    ]"
    @select="picked = $event"
    ><template #actions="{ row }"
      ><button class="link" @click="open(row)">任职与授权</button
      ><button class="link" @click="closeSource(row)">关闭来源</button
      ><button v-if="isSuper" class="link" @click="changeState(row)">
        {{ row.status === "ACTIVE" ? "停用员工" : "恢复员工" }}
      </button></template
    ></DataTable
  ><Panel
    v-if="detail"
    :title="detail.name + ' · 任职与授权'"
    wide
    @close="detail = undefined"
    ><div class="stack">
      <RecordDetails :value="detail" />
      <h3>任职记录</h3>
      <DataTable
        operation="listEmployments"
        :params="{ employeeId: detail.id }"
        :search="false"
        :columns="[
          { key: 'companyId', label: '公司' },
          { key: 'departmentId', label: '部门' },
          { key: 'positionId', label: '岗位' },
          { key: 'status', label: '状态' },
        ]"
      />
      <h3>授权来源与角色</h3>
      <DataTable
        operation="listGrants"
        :query="{ employeeId: detail.id }"
        :search="false"
        :columns="[
          { key: 'roleId', label: '角色' },
          { key: 'sourceDepartmentId', label: '来源部门' },
          { key: 'systemId', label: '系统' },
          { key: 'status', label: '关系状态' },
          { key: 'sourceEpoch', label: '来源周期' },
        ]"
        ><template #actions="{ row }"
          ><button
            v-if="row.status === 'ACTIVE'"
            class="link"
            @click="revoke(row)"
          >
            申请撤销
          </button></template
        ></DataTable
      >
    </div></Panel
  ><ActionForm
    v-if="form"
    :spec="form"
    @close="form = undefined"
    @saved="table?.reload()"
  /><GrantComposer
    v-if="grantOpen"
    :employees="picked"
    @close="grantOpen = false"
    @saved="table?.reload()"
  />
</template>
