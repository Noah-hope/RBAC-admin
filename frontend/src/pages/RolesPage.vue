<script setup lang="ts">
import { ref } from "vue";
import { api, isSuper, state, errorText } from "../state";
import type { Role, ChangeRequest } from "../api/schema";
import type { FormSpec } from "../forms";
import { entity, reason } from "../forms";
import DataTable from "../components/DataTable.vue";
import EntitySelect from "../components/EntitySelect.vue";
import ActionForm from "../components/ActionForm.vue";
import Panel from "../components/Panel.vue";
import RecordDetails from "../components/RecordDetails.vue";
import ConfigurationEditor from "../components/ConfigurationEditor.vue";
const table = ref<InstanceType<typeof DataTable>>(),
  company = ref(""),
  system = ref(""),
  filterState = ref(""),
  form = ref<FormSpec>(),
  detail = ref<Role>(),
  configRole = ref<Role>(),
  configDraft = ref<ChangeRequest>(),
  error = ref("");
const canEdit = (r: Role) => isSuper.value || r.departmentId !== null;
function create() {
  form.value = {
    title: "新建角色",
    description: "角色编码由服务端生成。创建后继续配置独立功能权限。",
    fields: [
      { key: "name", label: "角色名称", required: true },
      { key: "description", label: "说明", type: "textarea", maxLength: 500 },
      entity("companyId", "公司", "listCompanies"),
      entity("departmentId", "归属部门", "listDepartments", {
        required: !isSuper.value,
        query: (v) => ({ companyId: v.companyId }),
        dependsOn: ["companyId"],
        hint: isSuper.value ? "不选部门则为公司级共享角色" : "",
      }),
      entity("systemId", "系统", "listSystems"),
      reason,
    ],
    initial: {
      companyId: state.appointment?.companyId || company.value,
      departmentId: state.appointment?.departmentId || "",
      systemId: system.value,
    },
    save: async (v, key) => {
      const r = await api.call("createRole", {
        body: {
          name: v.name,
          description: v.description,
          companyId: v.companyId,
          departmentId: v.departmentId || null,
          systemId: v.systemId,
          reason: v.reason,
        },
        key,
      });
      configRole.value = r.role;
      configDraft.value = await api.call("getChangeRequest", {
        path: { requestId: r.draftId },
      });
    },
  };
}
async function configure(r: Role) {
  error.value = "";
  try {
    configDraft.value = undefined;
    configRole.value = await api.call("getRole", { path: { roleId: r.id } });
  } catch (e) {
    error.value = errorText(e);
  }
}
async function edit(r: Role) {
  try {
    const fresh = await api.call("getRole", { path: { roleId: r.id } });
    form.value = {
      title: "修改角色资料",
      initial: fresh,
      fields: [
        { key: "name", label: "角色名称", required: true },
        { key: "description", label: "说明", type: "textarea" },
        reason,
      ],
      save: (v, key) =>
        api.call("updateRoleMetadata", {
          path: { roleId: r.id },
          body: {
            expectedVersion: fresh.metadataVersion,
            name: v.name,
            description: v.description,
            reason: v.reason,
          },
          key,
        }),
    };
  } catch (e) {
    error.value = errorText(e);
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>角色权限</h1>
      <p>维护角色归属与独立功能配置，配置变更经审批后生效。</p>
    </div>
    <button class="button primary" @click="create">新建角色</button>
  </div>
  <div class="filters">
    <label class="field"
      >公司<EntitySelect
        v-model="company"
        label="公司筛选"
        operation="listCompanies" /></label
    ><label class="field"
      >系统<EntitySelect
        v-model="system"
        label="系统筛选"
        operation="listSystems" /></label
    ><label class="field"
      >角色状态<select v-model="filterState">
        <option value="">全部</option>
        <option value="UNCONFIGURED">待配置</option>
        <option value="EFFECTIVE">已生效</option>
      </select></label
    >
  </div>
  <p v-if="error" class="error-box" role="alert">{{ error }}</p>
  <DataTable
    ref="table"
    operation="listRoles"
    :query="{ companyId: company, systemId: system, state: filterState }"
    :columns="[
      { key: 'name', label: '角色名称' },
      { key: 'code', label: '角色编码' },
      { key: 'departmentId', label: '归属' },
      { key: 'state', label: '状态' },
      { key: 'configurationVersion', label: '配置版本' },
    ]"
    ><template #departmentId="{ row }">{{
      row.departmentId ? "部门角色" : "公司级共享角色"
    }}</template
    ><template #actions="{ row }"
      ><button class="link" @click="detail = row">详情</button
      ><button v-if="canEdit(row)" class="link" @click="edit(row)">
        修改资料</button
      ><button
        v-if="canEdit(row)"
        class="link"
        :disabled="!!row.activeChangeRequestId"
        @click="configure(row)"
      >
        配置权限</button
      ><span v-else class="hint">共享角色只读</span></template
    ></DataTable
  ><ActionForm
    v-if="form"
    :spec="form"
    @close="form = undefined"
    @saved="table?.reload()"
  /><Panel v-if="detail" title="角色详情" @close="detail = undefined"
    ><RecordDetails :value="detail" /></Panel
  ><ConfigurationEditor
    v-if="configRole"
    :role="configRole"
    :draft="configDraft"
    @close="configRole = undefined"
    @saved="table?.reload()"
  />
</template>
