<script setup lang="ts">
import { ref } from "vue";
import { api, errorText } from "../state";
import type { Account, Scope, Appointment } from "../api/schema";
import type { FormSpec, Field, Values } from "../forms";
import { entity, reason } from "../forms";
import DataTable from "../components/DataTable.vue";
import ActionForm from "../components/ActionForm.vue";
import Panel from "../components/Panel.vue";
import RecordDetails from "../components/RecordDetails.vue";
const tab = ref("accounts"),
  accounts = ref<InstanceType<typeof DataTable>>(),
  scopes = ref<InstanceType<typeof DataTable>>(),
  form = ref<FormSpec>(),
  scopeDetail = ref<Scope>(),
  accountDetail = ref<Account>(),
  error = ref("");
const passwords: Field[] = [
  {
    key: "temporaryPassword",
    label: "临时密码",
    type: "password",
    required: true,
    minLength: 12,
    maxLength: 128,
  },
  {
    key: "confirmPassword",
    label: "确认密码",
    type: "password",
    required: true,
    minLength: 12,
    maxLength: 128,
  },
];
function checkPassword(v: Values) {
  if (v.temporaryPassword !== v.confirmPassword)
    throw new Error("两次密码不一致");
}
function create() {
  form.value = {
    title: "创建后台账号",
    description: "账号关联员工。创建账号不自动产生管理任命或业务权限。",
    fields: [
      entity("employeeId", "员工", "listEmployees", {
        query: { status: "ACTIVE" },
      }),
      { key: "loginName", label: "登录名", required: true },
      ...passwords,
      reason,
    ],
    save: (v, key) => {
      checkPassword(v);
      return api.call("createAccount", {
        body: {
          employeeId: v.employeeId,
          loginName: v.loginName,
          temporaryPassword: v.temporaryPassword,
          confirmPassword: v.confirmPassword,
          reason: v.reason,
        },
        key,
      });
    },
  };
}
async function reset(a: Account) {
  try {
    const r = await api.call("getAccount", { path: { accountId: a.id } });
    form.value = {
      title: "重置临时密码",
      description: "重置后全部旧会话失效，下次登录必须改密。",
      fields: [...passwords, reason],
      save: (v, key) => {
        checkPassword(v);
        return api.call("resetAccountPassword", {
          path: { accountId: a.id },
          body: {
            expectedVersion: r.version,
            temporaryPassword: v.temporaryPassword,
            confirmPassword: v.confirmPassword,
            reason: v.reason,
          },
          key,
        });
      },
    };
  } catch (e) {
    error.value = errorText(e);
  }
}
async function toggle(a: Account) {
  try {
    const r = await api.call("getAccount", { path: { accountId: a.id } });
    form.value = {
      title: r.enabled ? "停用后台账号" : "启用后台账号",
      description: "账号状态与员工状态独立。重新启用不会恢复旧会话。",
      fields: [reason],
      save: (v, key) =>
        api.call("setAccountState", {
          path: { accountId: a.id },
          body: {
            expectedVersion: r.version,
            enabled: !r.enabled,
            reason: v.reason,
          },
          key,
        }),
    };
  } catch (e) {
    error.value = errorText(e);
  }
}
const scopeFields: Field[] = [
  entity("companyId", "公司", "listCompanies"),
  entity("departmentId", "管理部门", "listDepartments", {
    query: (v) => ({ companyId: v.companyId }),
    dependsOn: ["companyId"],
  }),
  entity("systemIds", "可管理系统", "listSystems", {
    multiple: true,
    required: false,
  }),
  entity("accountIds", "协作管理员账号", "listAccounts", {
    multiple: true,
    required: false,
    query: { enabled: true },
    hint: "选择的账号须在管理部门具有有效任职。取消选择即撤销对应任命。",
  }),
  reason,
];
async function scopeForm(s?: Scope) {
  error.value = "";
  try {
    let fresh: Scope | undefined,
      appointments: Appointment[] = [];
    if (s) {
      fresh = await api.call("getScope", { path: { scopeId: s.id } });
      for (let page = 1; ; page++) {
        const r = await api.call("listScopeAppointments", {
          path: { scopeId: s.id },
          query: { status: "ACTIVE", page, pageSize: 100 },
        });
        appointments.push(...r.items);
        if (page * 100 >= r.total) break;
      }
    }
    const input = (v: Values) => {
      return {
        companyId: v.companyId,
        departmentId: v.departmentId,
        systemIds: v.systemIds,
        accountIds: v.accountIds,
        reason: v.reason,
      };
    };
    form.value = {
      title: s ? "修改管理范围及任命" : "创建管理范围",
      description:
        "一个范围对应一家公司的一个平级部门。系统和账号以本次完整选择替换；影响须预览后确认。",
      fields: scopeFields,
      initial: fresh
        ? { ...fresh, accountIds: appointments.map((a) => a.accountId) }
        : {},
      preview: (v) =>
        fresh
          ? api.call("scopeEditPreview", {
              path: { scopeId: fresh.id },
              body: { ...input(v), expectedVersion: fresh.version },
            })
          : api.call("scopeCreatePreview", { body: input(v) }),
      save: (v, key, p) =>
        fresh
          ? api.call("replaceScope", {
              path: { scopeId: fresh.id },
              body: {
                input: { ...input(v), expectedVersion: fresh.version },
                previewId: p!.id,
              },
              key,
            })
          : api.call("createScope", {
              body: { input: input(v), previewId: p!.id },
              key,
            }),
      done: () => {
        scopeDetail.value = undefined;
      },
    };
  } catch (e) {
    error.value = errorText(e);
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>管理员与范围</h1>
      <p>后台账号、员工任命与业务授权分别管理。</p>
    </div>
    <button v-if="tab === 'accounts'" class="button primary" @click="create">
      创建后台账号</button
    ><button v-else class="button primary" @click="scopeForm()">
      创建管理范围
    </button>
  </div>
  <div class="tabs">
    <button :class="{ active: tab === 'accounts' }" @click="tab = 'accounts'">
      后台账号</button
    ><button :class="{ active: tab === 'scopes' }" @click="tab = 'scopes'">
      管理范围
    </button>
  </div>
  <p v-if="error" class="error-box" role="alert">{{ error }}</p>
  <DataTable
    v-if="tab === 'accounts'"
    ref="accounts"
    operation="listAccounts"
    :columns="[
      { key: 'loginName', label: '登录名' },
      { key: 'employeeId', label: '关联员工' },
      { key: 'enabled', label: '已启用' },
      { key: 'mustChangePassword', label: '下次需改密' },
    ]"
    ><template #actions="{ row }"
      ><button class="link" @click="accountDetail = row">详情</button
      ><button class="link" @click="toggle(row)">
        {{ row.enabled ? "停用账号" : "启用账号" }}</button
      ><button class="link" @click="reset(row)">重置密码</button></template
    ></DataTable
  ><DataTable
    v-else
    ref="scopes"
    operation="listScopes"
    :search="false"
    :columns="[
      { key: 'companyId', label: '公司' },
      { key: 'departmentId', label: '管理部门' },
      { key: 'systemIds', label: '可管理系统' },
      { key: 'version', label: '版本' },
    ]"
    ><template #actions="{ row }"
      ><button class="link" @click="scopeDetail = row">查看任命</button
      ><button class="link" @click="scopeForm(row)">
        修改范围与任命
      </button></template
    ></DataTable
  ><ActionForm
    v-if="form"
    :spec="form"
    @close="form = undefined"
    @saved="
      accounts?.reload();
      scopes?.reload();
    "
  /><Panel
    v-if="accountDetail"
    title="后台账号详情"
    @close="accountDetail = undefined"
    ><RecordDetails :value="accountDetail" /></Panel
  ><Panel
    v-if="scopeDetail"
    title="管理范围及任命"
    wide
    @close="scopeDetail = undefined"
    ><div class="stack">
      <RecordDetails :value="scopeDetail" /><DataTable
        operation="listScopeAppointments"
        :params="{ scopeId: scopeDetail.id }"
        :search="false"
        :columns="[
          { key: 'accountId', label: '账号' },
          { key: 'employeeId', label: '员工' },
          { key: 'status', label: '任命状态' },
          { key: 'version', label: '任命版本' },
        ]"
      /></div
  ></Panel>
</template>
