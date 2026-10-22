<script setup lang="ts">
import { ref, onMounted } from "vue";
import type { Role, Permission, ChangeRequest } from "../api/schema";
import { api, errorText } from "../state";
import { Mutation } from "../api/client";
import Panel from "./Panel.vue";
const props = defineProps<{ role: Role; draft?: ChangeRequest }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const permissions = ref<Permission[]>([]),
  selected = ref<string[]>(
    props.draft?.content.kind === "ROLE_CONFIGURATION"
      ? [...props.draft.content.permissionIds]
      : [...props.role.permissionIds],
  ),
  reason = ref(props.draft?.reason || ""),
  error = ref(""),
  busy = ref(true),
  loaded = ref(false);
const mutation = new Mutation();
async function load() {
  busy.value = true;
  error.value = "";
  try {
    const before = await api.call("getSystem", {
      path: { systemId: props.role.systemId },
    });
    const items: Permission[] = [];
    for (let page = 1; ; page++) {
      const r = await api.call("listPermissions", {
        path: { systemId: props.role.systemId },
        query: { page, pageSize: 100 },
      });
      items.push(...r.items);
      if (page * 100 >= r.total) break;
    }
    const after = await api.call("getSystem", {
      path: { systemId: props.role.systemId },
    });
    if (before.catalogVersion !== after.catalogVersion)
      throw new Error("目录在读取过程中变化，请重新读取");
    permissions.value = items;
    loaded.value = true;
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
async function save() {
  if (busy.value || !loaded.value) return;
  if (!selected.value.length) {
    error.value = "请选择至少一项权限";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const content = {
      kind: "ROLE_CONFIGURATION" as const,
      companyId: props.role.companyId,
      roleId: props.role.id,
      expectedConfigurationVersion: props.role.configurationVersion,
      permissionIds: [...selected.value],
    };
    if (props.draft) {
      const body = {
        expectedDraftVersion: props.draft.draftVersion,
        content,
        reason: reason.value,
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
  <Panel
    :title="role.name + ' · 配置权限'"
    wide
    :busy="busy"
    @close="emit('close')"
    ><form class="stack" @submit.prevent="save">
      <p class="hint">
        系统进入与各功能操作独立选择。保存草稿后，请在变更申请中预览并提交；OA
        批准并执行后才生效。
      </p>
      <p v-if="!loaded && !busy">
        <button class="button" type="button" @click="load">重新读取目录</button>
      </p>
      <div class="permission-grid">
        <label v-for="p in permissions" :key="p.id" class="permission-option"
          ><input
            v-model="selected"
            type="checkbox"
            :value="p.id"
            :disabled="busy || !p.enabled"
          /><span
            >{{ p.name
            }}<small
              >{{ p.code }}{{ !p.enabled ? " · 已停用" : ""
              }}{{
                p.permissionType === "SYSTEM_ACCESS" ? " · 系统进入权限" : ""
              }}</small
            ></span
          ></label
        >
      </div>
      <p v-if="loaded && !permissions.length" class="empty">
        该系统尚未登记可配置权限。
      </p>
      <p
        v-if="selected.some((id) => !permissions.some((p) => p.id === id))"
        class="warning"
      >
        原配置包含当前不可见的权限，已保留原标识。若提交失败，请核对目录和范围。
      </p>
      <label class="field"
        >办理原因<textarea v-model="reason" required maxlength="500" />
      </label>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button class="button primary" :disabled="busy || !loaded">
          保存配置草稿
        </button>
      </div>
    </form></Panel
  >
</template>
