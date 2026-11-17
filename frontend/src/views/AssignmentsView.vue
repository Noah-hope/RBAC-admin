<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Department, Page, RoleDetails, RoleSummary, User } from '../api/types'
import { useSession } from '../stores/session'
import { confirmAction, useTask } from '../composables/useTask'
import ActionButton from '../components/ActionButton.vue'
import TaskError from '../components/TaskError.vue'
import PermissionSources from '../components/PermissionSources.vue'
const session = useSession(),
  { busy, error, run } = useTask(),
  { busy: saving, error: formError, run: runForm } = useTask()
const rows = ref<Page<User>>({ items: [], page: 1, pageSize: 20, total: 0 }),
  page = ref(1),
  keyword = ref(''),
  departmentId = ref<number>()
const departments = ref<Department[]>([]),
  selectedUsers = ref<User[]>([]),
  table = ref<{ clearSelection: () => void }>()
function flatten(nodes: Department[], depth = 0): { id: number; name: string }[] {
  return nodes.flatMap((n) => [
    { id: n.id, name: `${'　'.repeat(depth)}${n.name}` },
    ...flatten(n.children, depth + 1),
  ])
}
const dialog = ref(false),
  action = ref<'REPLACE' | 'GRANT' | 'REVOKE'>('REPLACE'),
  target = ref<User>(),
  roleIds = ref<number[]>([]),
  roles = ref<RoleSummary[]>([]),
  preview = ref<RoleDetails>()
async function loadRows() {
  rows.value = await api.users({
    page: page.value,
    keyword: keyword.value,
    departmentId: departmentId.value,
    includeDepartmentName: true,
  })
}
async function load() {
  await run(async () => {
    departments.value = (await api.departments()).departments
    await loadRows()
  })
}
function search() {
  page.value = 1
  table.value?.clearSelection()
  void run(loadRows)
}
async function open(mode: 'REPLACE' | 'GRANT' | 'REVOKE', user?: User) {
  await run(async () => {
    roles.value = await api.allRoles()
    target.value = user ? await api.user(user.id) : undefined
    action.value = mode
    roleIds.value = mode === 'REPLACE' ? target.value!.businessRoles.map((r) => r.id) : []
    preview.value = undefined
    formError.value = ''
    dialog.value = true
  })
}
async function showRole(id: number) {
  await runForm(async () => {
    preview.value = await api.role(id)
  })
}
async function save() {
  if (
    action.value === 'REPLACE' &&
    !roleIds.value.length &&
    !(await confirmAction(
      `将撤销「${target.value!.name}」的全部业务角色，是否继续？`,
      '撤销全部业务角色',
    ))
  )
    return
  const done = await runForm(async () => {
    if (action.value === 'REPLACE') await api.replaceRoles(target.value!.id, [...roleIds.value])
    else
      await api.batchRoles(
        action.value,
        selectedUsers.value.map((u) => u.id),
        [...roleIds.value],
      )
    dialog.value = false
    table.value?.clearSelection()
    return true
  }, '角色授权已更新')
  if (done) {
    await run(loadRows)
    await run(() => session.refresh())
  }
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>用户授权</h1>
      <p>角色分配保存后直接生效；批量操作保留用户的其他角色。</p>
    </div>
  </div>
  <TaskError :message="error" />
  <div class="scope">
    <strong>管理范围</strong>
    <span>
      {{ session.totalAdmin ? '全部部门' : `${departments[0]?.name ?? '本部门'}及下级部门` }}
    </span>
  </div>
  <section class="panel" v-loading="busy">
    <div class="panel-heading">
      <h2>用户列表</h2>
      <form class="table-search assignment-search" @submit.prevent="search">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名、用户名或部门"
          aria-label="搜索授权用户"
          maxlength="100"
          clearable
        />
        <select v-model="departmentId" aria-label="筛选部门" @change="search">
          <option :value="undefined">全部管理范围</option>
          <option
            v-for="department in flatten(departments)"
            :key="department.id"
            :value="department.id"
          >
            {{ department.name }}
          </option>
        </select>
        <el-button native-type="submit">搜索</el-button>
      </form>
    </div>
    <div class="batch-tools" role="group" aria-label="批量授权">
      <span class="muted" aria-live="polite">已选 {{ selectedUsers.length }} 人</span>
      <div class="inline-actions">
        <ActionButton
          permission="assignment:batch"
          :disabled="!selectedUsers.length"
          @click="open('REVOKE')"
        >
          批量撤销
        </ActionButton>
        <ActionButton
          permission="assignment:batch"
          type="primary"
          :disabled="!selectedUsers.length"
          @click="open('GRANT')"
        >
          批量分配
        </ActionButton>
      </div>
    </div>
    <el-table
      ref="table"
      :data="rows.items"
      row-key="id"
      @selection-change="(users: User[]) => (selectedUsers = users)"
      empty-text="没有匹配的用户。"
    >
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          <div class="person-cell">
            <span class="avatar square" aria-hidden="true">{{ row.name.slice(0, 1) }}</span>
            <div>
              <strong class="cell-title">{{ row.name }}</strong>
              <span class="cell-subtitle">{{ row.username }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="部门" min-width="125">
        <template #default="{ row }">{{ row.department?.name ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="业务角色" min-width="230">
        <template #default="{ row }">
          <div class="tag-list">
            <el-tag v-for="role in row.businessRoles" :key="role.id" type="success">
              {{ role.name }}
            </el-tag>
            <span v-if="!row.businessRoles.length" class="muted">未分配</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <ActionButton
            permission="assignment:manage"
            text
            type="primary"
            @click="open('REPLACE', row)"
          >
            分配角色
          </ActionButton>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <span class="muted">共 {{ rows.total }} 名用户</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="rows.total"
        layout="prev, pager, next"
        @current-change="run(loadRows)"
      />
    </div>
  </section>
  <el-dialog
    v-model="dialog"
    :title="
      action === 'REPLACE' ? '分配角色' : action === 'GRANT' ? '批量分配角色' : '批量撤销角色'
    "
    width="800px"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :before-close="
      (done: () => void) => {
        if (!saving) done()
      }
    "
  >
    <form @submit.prevent="save">
      <TaskError :message="formError" />
      <p class="dialog-summary">
        {{
          action === 'REPLACE'
            ? `为 ${target?.name} 保存最终业务角色列表。`
            : `对选中的 ${selectedUsers.length} 名用户${action === 'GRANT' ? '分配' : '撤销'}以下角色，其他角色保留。`
        }}
      </p>
      <div class="checkbox-list">
        <div v-for="role in roles" :key="role.id" class="role-option">
          <label>
            <input
              v-model="roleIds"
              type="checkbox"
              :value="role.id"
              :disabled="saving"
              :aria-label="`选择角色 ${role.name}`"
            />
            <span>
              <strong>{{ role.name }}</strong>
              <small>
                {{ role.description || '未填写说明' }} ·
                {{ role.effectivePermissionCount }} 项有效权限
              </small>
            </span>
          </label>
          <el-button text type="primary" @click="showRole(role.id)" :disabled="saving">
            查看权限
          </el-button>
        </div>
        <p v-if="!roles.length" class="empty-note">尚无可分配的业务角色。</p>
      </div>
      <div v-if="preview" class="preview-section">
        <h3>{{ preview.name }}的全部有效权限</h3>
        <PermissionSources
          :permissions="preview.effectivePermissions"
          :applications="preview.applications"
        />
      </div>
      <div class="dialog-actions">
        <el-button :disabled="saving" @click="dialog = false">取消</el-button>
        <el-button
          type="primary"
          native-type="submit"
          :loading="saving"
          :disabled="action !== 'REPLACE' && !roleIds.length"
        >
          保存分配
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>
