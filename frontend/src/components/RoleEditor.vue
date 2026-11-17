<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../api'
import type { Catalog, RoleDetails, RoleInput, RoleRequest } from '../api/types'
import {
  directDisabled,
  parentCandidates,
  previewPermissions,
  toggleDirect,
} from '../domain/role-config'
import { useTask } from '../composables/useTask'
import PermissionSources from './PermissionSources.vue'
import TaskError from './TaskError.vue'
const props = defineProps<{
  title: string
  seed?: RoleDetails | RoleRequest
  editingId?: number
  readonly?: boolean
  submitLabel?: string
  save?: (input: RoleInput) => Promise<unknown>
}>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { busy, error, run } = useTask()
const ready = ref(false),
  catalog = ref<Catalog>({ applications: [] }),
  roles = ref<RoleDetails[]>([]),
  selectedApp = ref<number>()
const form = reactive<RoleInput>({
  name: props.seed?.name ?? '',
  description: props.seed?.description ?? '',
  permissionIds: [...(props.seed?.permissionIds ?? [])],
  inheritedRoleIds: props.seed?.inheritedRoles.map((r) => r.id) ?? [],
})
const originalDirect = props.editingId ? [...form.permissionIds] : []
const allPermissions = computed(() => catalog.value.applications.flatMap((a) => a.permissions))
const parents = computed(() => roles.value.filter((r) => form.inheritedRoleIds.includes(r.id)))
const inherited = computed(
  () => new Set(parents.value.flatMap((r) => r.effectivePermissions.map((p) => p.id))),
)
watch(inherited, (inheritedIds) => {
  if (!props.readonly)
    form.permissionIds = form.permissionIds.filter(
      (id) => !inheritedIds.has(id) || originalDirect.includes(id),
    )
})
const preview = computed(() =>
  previewPermissions(allPermissions.value, form.permissionIds, parents.value, {
    id: props.editingId ?? 0,
    name: form.name || '当前角色',
  }),
)
const candidates = computed(() => parentCandidates(roles.value, props.editingId))
const missingParents = computed(() =>
  form.inheritedRoleIds.filter((id) => !roles.value.some((r) => r.id === id)),
)
const app = computed(() => catalog.value.applications.find((a) => a.id === selectedApp.value))
const menuMissing = computed(() =>
  preview.value.find(
    (p) => p.type === 'BUTTON' && !preview.value.some((menu) => menu.id === p.parentId),
  ),
)
const repeated = computed(() =>
  form.permissionIds.find((id) => directDisabled(id, originalDirect, inherited.value)),
)
const validation = computed(() =>
  missingParents.value.length
    ? '存在已删除的父角色，请移除后重新选择。'
    : repeated.value
      ? '已有继承权限不能新增为重复的直接配置，请取消重复的直接勾选。'
      : menuMissing.value
        ? `按钮「${menuMissing.value.name}」缺少所属菜单，请补充菜单权限。`
        : '',
)
const visiblePreview = computed(() =>
  preview.value.filter((p) => p.applicationId === selectedApp.value),
)
async function load() {
  await run(async () => {
    const [loaded, summaries] = await Promise.all([api.catalog(), api.allRoles()])
    const details = await Promise.all(summaries.map((role) => api.role(role.id)))
    catalog.value = loaded
    roles.value = details
    selectedApp.value = loaded.applications[0]?.id
    ready.value = true
  })
}
function toggle(id: number, checked: boolean) {
  form.permissionIds = toggleDirect(form, id, checked, allPermissions.value, inherited.value)
}
async function submit() {
  if (validation.value || !props.save) return
  await run(async () => {
    await props.save!({
      name: form.name.trim(),
      description: form.description.trim(),
      permissionIds: [...form.permissionIds],
      inheritedRoleIds: [...form.inheritedRoleIds],
    })
    emit('saved')
    emit('close')
  }, '配置已保存')
}
onMounted(load)
</script>
<template>
  <el-dialog
    :model-value="true"
    :title="title"
    width="960px"
    class="role-editor"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="
      (done: () => void) => {
        if (!busy) {
          done()
          emit('close')
        }
      }
    "
  >
    <form @submit.prevent="submit" v-loading="busy">
      <TaskError :message="error" />
      <el-button v-if="!ready && error" @click="load">重新加载</el-button>
      <template v-if="ready">
        <fieldset :disabled="busy" class="plain-fieldset">
          <div class="form-grid">
            <div>
              <label for="role-name">角色名称</label>
              <input
                id="role-name"
                v-model="form.name"
                :disabled="readonly"
                required
                maxlength="24"
                placeholder="例如：课程助教"
              />
            </div>
            <div>
              <label for="role-description">角色说明</label>
              <input
                id="role-description"
                v-model="form.description"
                :disabled="readonly"
                maxlength="48"
                placeholder="说明角色的工作职责"
              />
            </div>
          </div>
          <section class="parent-picker">
            <div class="section-heading">
              <h3>继承角色</h3>
              <span class="muted">继承父角色在所有应用中的有效权限</span>
            </div>
            <div class="parent-options">
              <label v-for="role in candidates" :key="role.id" class="check-card">
                <input
                  type="checkbox"
                  :disabled="readonly"
                  :value="role.id"
                  v-model="form.inheritedRoleIds"
                  :aria-label="`继承 ${role.name}`"
                />
                <span>
                  <strong>{{ role.name }}</strong>
                  <small>{{ role.effectivePermissionCount }} 项有效权限</small>
                </span>
              </label>
              <p v-if="!candidates.length" class="muted">
                没有可选父角色。当前角色、子角色及预置管理员角色不能作为父角色。
              </p>
            </div>
            <div v-for="id in missingParents" :key="id" class="missing-parent">
              已删除：{{ seed?.inheritedRoles.find((r) => r.id === id)?.name ?? id }}
              <el-button
                v-if="!readonly"
                type="danger"
                text
                @click="form.inheritedRoleIds = form.inheritedRoleIds.filter((p) => p !== id)"
              >
                移除父角色
              </el-button>
            </div>
          </section>
          <div class="section-heading">
            <h3>权限配置</h3>
            <span class="muted">
              仅继承权限只读；已有直接与继承重叠的配置可以保留或取消直接来源。
            </span>
          </div>
          <div class="permission-app-tabs" role="tablist" aria-label="权限应用">
            <button
              v-for="a in catalog.applications"
              :key="a.id"
              type="button"
              role="tab"
              :aria-selected="selectedApp === a.id"
              :class="{ active: selectedApp === a.id }"
              @click="selectedApp = a.id"
            >
              {{ a.name }}
              <span>{{ preview.filter((p) => p.applicationId === a.id).length }}</span>
            </button>
          </div>
          <div class="permission-config">
            <div class="permission-choices">
              <h4>直接权限</h4>
              <div
                v-for="menu in app?.permissions.filter((p) => p.type === 'MENU')"
                :key="menu.id"
                class="permission-group"
              >
                <label class="permission-check">
                  <input
                    type="checkbox"
                    :aria-label="`直接配置 ${menu.name}`"
                    :checked="form.permissionIds.includes(menu.id)"
                    :disabled="readonly || directDisabled(menu.id, originalDirect, inherited)"
                    @change="toggle(menu.id, ($event.target as HTMLInputElement).checked)"
                  />
                  <strong>{{ menu.name }}</strong>
                  <span v-if="inherited.has(menu.id)" class="inherited-note">继承</span>
                </label>
                <label
                  v-for="p in app?.permissions.filter((p) => p.parentId === menu.id)"
                  :key="p.id"
                  class="permission-check is-child"
                >
                  <input
                    type="checkbox"
                    :aria-label="`直接配置 ${p.name}`"
                    :checked="form.permissionIds.includes(p.id)"
                    :disabled="readonly || directDisabled(p.id, originalDirect, inherited)"
                    @change="toggle(p.id, ($event.target as HTMLInputElement).checked)"
                  />
                  <span>{{ p.name }}</span>
                  <span v-if="inherited.has(p.id)" class="inherited-note">继承</span>
                </label>
              </div>
              <p v-if="!app?.permissions.length" class="empty-note">此应用尚未登记权限。</p>
            </div>
            <div class="permission-preview">
              <h4>
                有效权限与来源
                <span v-if="!missingParents.length" class="muted">
                  {{ visiblePreview.length }} 项
                </span>
              </h4>
              <el-alert
                v-if="missingParents.length"
                title="父角色已删除，无法计算完整有效权限。"
                type="warning"
                :closable="false"
              />
              <PermissionSources v-else :permissions="visiblePreview" />
            </div>
          </div>
        </fieldset>
        <el-alert
          v-if="!readonly && validation"
          :title="validation"
          type="warning"
          :closable="false"
          show-icon
          class="task-error"
        />
      </template>
      <div class="dialog-actions">
        <span class="muted" v-if="!readonly">
          直接权限 {{ form.permissionIds.length }} 项 · 有效权限 {{ preview.length }} 项
        </span>
        <el-button :disabled="busy" @click="emit('close')">
          {{ readonly ? '关闭' : '取消' }}
        </el-button>
        <el-button
          v-if="!readonly"
          native-type="submit"
          type="primary"
          :loading="busy"
          :disabled="!ready || !!validation || !form.name.trim()"
        >
          {{ submitLabel ?? '保存角色' }}
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>
