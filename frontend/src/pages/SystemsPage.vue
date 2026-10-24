<script setup lang="ts">
import { ref } from "vue";
import { api, errorText } from "../state";
import type {
  Application,
  DirectoryNode,
  Permission,
  CatalogCommand,
} from "../api/schema";
import type { FormSpec, Field, Values } from "../forms";
import { entity, reason } from "../forms";
import DataTable from "../components/DataTable.vue";
import ActionForm from "../components/ActionForm.vue";
import Panel from "../components/Panel.vue";
import RecordDetails from "../components/RecordDetails.vue";
const table = ref<InstanceType<typeof DataTable>>(),
  nodes = ref<InstanceType<typeof DataTable>>(),
  permissions = ref<InstanceType<typeof DataTable>>(),
  detail = ref<Application>(),
  form = ref<FormSpec>(),
  error = ref(""),
  tab = ref("nodes");
function create() {
  form.value = {
    title: "登记业务系统",
    fields: [
      { key: "code", label: "系统编码", required: true, maxLength: 93 },
      { key: "name", label: "系统名称", required: true },
      { key: "description", label: "说明", type: "textarea" },
      reason,
    ],
    save: (v, key) =>
      api.call("createSystem", {
        body: {
          code: v.code,
          name: v.name,
          description: v.description,
          reason: v.reason,
        },
        key,
      }),
  };
}
async function edit(s: Application) {
  try {
    const fresh = await api.call("getSystem", { path: { systemId: s.id } });
    const input = (v: Values) => ({
      expectedVersion: fresh.version,
      name: v.name,
      description: v.description,
      enabled: v.enabled,
      reason: v.reason,
    });
    form.value = {
      title: "修改业务系统",
      description:
        "停用系统会影响进入资格、权限查询和相关待处理申请，须核对预览。",
      initial: fresh,
      fields: [
        { key: "name", label: "系统名称", required: true },
        { key: "description", label: "说明", type: "textarea" },
        { key: "enabled", label: "启用系统", type: "boolean" },
        reason,
      ],
      preview: (v) =>
        api.call("systemStatePreview", {
          path: { systemId: s.id },
          body: input(v),
        }),
      save: (v, key, p) =>
        api.call("updateSystem", {
          path: { systemId: s.id },
          body: { input: input(v), previewId: p!.id },
          key,
        }),
    };
  } catch (e) {
    error.value = errorText(e);
  }
}
async function open(s: Application) {
  try {
    detail.value = await api.call("getSystem", { path: { systemId: s.id } });
  } catch (e) {
    error.value = errorText(e);
  }
}
async function catalog(
  kind:
    "navigation" | "function" | "permission" | "nodeEdit" | "permissionEdit",
  item?: DirectoryNode | Permission,
) {
  if (!detail.value) return;
  error.value = "";
  try {
    const system = await api.call("getSystem", {
      path: { systemId: detail.value.id },
    });
    const path = { systemId: system.id };
    let fields: Field[],
      initial: Values = {},
      commands: (v: Values) => CatalogCommand[];
    const name: Field = { key: "name", label: "名称", required: true },
      code: Field = {
        key: "code",
        label: "稳定编码",
        required: true,
        maxLength: 100,
      },
      enabled: Field = { key: "enabled", label: "启用", type: "boolean" },
      parent = entity("parentId", "父级目录", "listDirectoryNodes", {
        params: path,
        required: false,
        hint: "不选则位于系统根目录。",
      });
    if (kind === "navigation" || kind === "function") {
      fields = [
        name,
        code,
        parent,
        { key: "sortOrder", label: "排序", type: "number", required: true },
        enabled,
      ];
      initial = { sortOrder: 0, enabled: true };
      if (kind === "function")
        fields.push(
          {
            key: "viewCode",
            label: "菜单可见操作编码",
            required: true,
            maxLength: 100,
            hint: "同时登记一项可作为菜单可见条件的独立操作。",
          },
          { key: "viewName", label: "菜单可见操作名称", required: true },
        );
      commands = (v) => {
        const node: any = {
          kind: "NODE_CREATE",
          localKey: "new-node",
          code: v.code,
          name: v.name,
          nodeType: kind === "navigation" ? "NAVIGATION" : "FUNCTION",
          parentId: v.parentId || null,
          parentLocalKey: null,
          sortOrder: v.sortOrder,
          enabled: v.enabled,
          visibilityPermissionId: null,
          visibilityPermissionLocalKey: kind === "function" ? "new-view" : null,
        };
        return kind === "navigation"
          ? [node]
          : [
              node,
              {
                kind: "PERMISSION_CREATE",
                localKey: "new-view",
                functionId: null,
                functionLocalKey: "new-node",
                code: v.viewCode,
                name: v.viewName,
                menuEligible: true,
                enabled: true,
              },
            ];
      };
    } else if (kind === "permission") {
      fields = [
        entity("functionId", "所属功能", "listDirectoryNodes", {
          params: path,
          query: { nodeType: "FUNCTION" },
        }),
        name,
        code,
        { key: "menuEligible", label: "可作为菜单可见条件", type: "boolean" },
        enabled,
      ];
      initial = { enabled: true, menuEligible: false };
      commands = (v) => [
        {
          kind: "PERMISSION_CREATE",
          localKey: "new-operation",
          functionId: v.functionId,
          functionLocalKey: null,
          code: v.code,
          name: v.name,
          menuEligible: v.menuEligible,
          enabled: v.enabled,
        },
      ];
    } else if (kind === "nodeEdit") {
      const n = item as DirectoryNode;
      fields = [
        name,
        parent,
        { key: "sortOrder", label: "排序", type: "number", required: true },
        enabled,
      ];
      if (n.nodeType === "FUNCTION")
        fields.push(
          entity("visibilityPermissionId", "菜单可见权限", "listPermissions", {
            params: path,
            query: { functionId: n.id, permissionType: "FUNCTION_OPERATION" },
            required: false,
            hint: "启用功能时必须选择该功能的一项菜单可见权限。",
          }),
        );
      initial = {
        ...n,
        parentId: n.parentId || "",
        visibilityPermissionId: n.visibilityPermissionId || "",
      };
      commands = (v) => [
        {
          kind: "NODE_UPDATE",
          id: n.id,
          expectedVersion: n.version,
          name: v.name,
          parentId: v.parentId || null,
          sortOrder: v.sortOrder,
          enabled: v.enabled,
          visibilityPermissionId: v.visibilityPermissionId || null,
        },
      ];
    } else {
      const p = item as Permission;
      fields = [
        name,
        { key: "menuEligible", label: "可作为菜单可见条件", type: "boolean" },
        enabled,
      ];
      initial = p;
      commands = (v) => [
        {
          kind: "PERMISSION_UPDATE",
          id: p.id,
          expectedVersion: p.version,
          name: v.name,
          menuEligible: v.menuEligible,
          enabled: v.enabled,
        },
      ];
    }
    const input = (v: Values) => ({
      expectedCatalogVersion: system.catalogVersion,
      commands: commands(v),
      reason: v.reason,
    });
    form.value = {
      title: {
        navigation: "登记导航目录",
        function: "登记功能与菜单可见操作",
        permission: "登记独立功能操作",
        nodeEdit: "编辑目录节点",
        permissionEdit: "编辑独立操作",
      }[kind],
      description:
        "登记或启停操作不会自动加入已有角色。菜单可见条件与角色授予的操作分别配置。",
      initial,
      fields: [...fields, reason],
      preview: (v) => api.call("catalogPreview", { path, body: input(v) }),
      save: (v, key, p) =>
        api.call("saveCatalog", {
          path,
          body: { ...input(v), previewId: p!.id },
          key,
        }),
      done: () => {
        nodes.value?.reload();
        permissions.value?.reload();
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
      <h1>系统与权限目录</h1>
      <p>登记业务系统、功能菜单与独立操作。编辑不隐含查看权限。</p>
    </div>
    <button class="button primary" @click="create">登记业务系统</button>
  </div>
  <p v-if="error" class="error-box" role="alert">{{ error }}</p>
  <DataTable
    ref="table"
    operation="listSystems"
    :columns="[
      { key: 'name', label: '业务系统' },
      { key: 'code', label: '系统编码' },
      { key: 'enabled', label: '已启用' },
      { key: 'catalogVersion', label: '目录版本' },
    ]"
    ><template #actions="{ row }"
      ><button class="link" @click="open(row)">管理目录</button
      ><button class="link" @click="edit(row)">修改系统</button></template
    ></DataTable
  ><Panel
    v-if="detail"
    :title="detail.name + ' · 权限目录'"
    wide
    @close="detail = undefined"
    ><div class="stack">
      <p class="hint">
        系统进入权限独立登记：{{ detail.systemAccessPermissionId }}
      </p>
      <div class="tabs">
        <button :class="{ active: tab === 'nodes' }" @click="tab = 'nodes'">
          导航与功能</button
        ><button
          :class="{ active: tab === 'permissions' }"
          @click="tab = 'permissions'"
        >
          独立操作
        </button>
      </div>
      <div class="toolbar-actions">
        <button class="button" @click="catalog('navigation')">
          登记导航目录</button
        ><button class="button" @click="catalog('function')">登记功能</button
        ><button class="button" @click="catalog('permission')">登记操作</button>
      </div>
      <DataTable
        v-if="tab === 'nodes'"
        ref="nodes"
        operation="listDirectoryNodes"
        :params="{ systemId: detail.id }"
        :search="false"
        :columns="[
          { key: 'name', label: '名称' },
          { key: 'code', label: '编码' },
          { key: 'nodeType', label: '类型' },
          { key: 'parentId', label: '父级' },
          { key: 'enabled', label: '已启用' },
        ]"
        ><template #actions="{ row }"
          ><button class="link" @click="catalog('nodeEdit', row)">
            编辑节点
          </button></template
        ></DataTable
      ><DataTable
        v-else
        ref="permissions"
        operation="listPermissions"
        :params="{ systemId: detail.id }"
        :columns="[
          { key: 'name', label: '操作名称' },
          { key: 'code', label: '操作编码' },
          { key: 'permissionType', label: '类型' },
          { key: 'menuEligible', label: '可作菜单条件' },
          { key: 'enabled', label: '已启用' },
        ]"
        ><template #actions="{ row }"
          ><button
            v-if="row.permissionType === 'FUNCTION_OPERATION'"
            class="link"
            @click="catalog('permissionEdit', row)"
          >
            编辑操作</button
          ><span v-else class="hint">由系统管理</span></template
        ></DataTable
      >
    </div></Panel
  ><ActionForm
    v-if="form"
    :spec="form"
    @close="form = undefined"
    @saved="table?.reload()"
  />
</template>
