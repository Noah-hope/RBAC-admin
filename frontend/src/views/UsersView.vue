<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import type { Department, Page, User, UserInput } from '../api/types'
import { useSession } from '../stores/session'
import { useTask } from '../composables/useTask'
import ActionButton from '../components/ActionButton.vue'
import TaskError from '../components/TaskError.vue'
const session = useSession(),
  { busy, error, run } = useTask(),
  { busy: saving, error: formError, run: runForm } = useTask()
const departments = ref<Department[]>([]),
  selectedDepartment = ref<number>(),
  selectedNode = ref<Department>()
const rows = ref<Page<User>>({ items: [], page: 1, pageSize: 20, total: 0 }),
  page = ref(1),
  keyword = ref(''),
  includeDepartmentName = ref(false)
function flatten(nodes: Department[], depth = 0): { id: number; name: string }[] {
  return nodes.flatMap((node) => [
    { id: node.id, name: `${'　'.repeat(depth)}${node.name}` },
    ...flatten(node.children, depth + 1),
  ])
}
const choices = computed(() => flatten(departments.value))
const dialog = ref<'user' | 'department' | ''>(''),
  editingId = ref<number>()
const form = reactive({
  username: '',
  name: '',
  initialPassword: '',
  departmentId: undefined as number | undefined,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED',
  departmentAdministrator: false,
})
const deptForm = reactive({ name: '', parentId: null as number | null })
const departmentChoices = computed(() => {
  if (dialog.value !== 'department' || !editingId.value) return choices.value
  function find(nodes: Department[]): Department | undefined {
    for (const n of nodes) {
      if (n.id === editingId.value) return n
      const found = find(n.children)
      if (found) return found
    }
  }
  const node = find(departments.value),
    excluded = new Set(node ? flatten([node]).map((n) => n.id) : [])
  return choices.value.filter((n) => !excluded.has(n.id))
})
async function loadRows() {
  rows.value = await api.users({
    page: page.value,
    keyword: keyword.value,
    departmentId: selectedDepartment.value,
    includeDepartmentName: includeDepartmentName.value,
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
  void run(loadRows)
}
function filterDepartment(node?: Department) {
  selectedNode.value = node
  selectedDepartment.value = node?.id
  search()
}
async function openUser(row?: User) {
  await run(async () => {
    const user = row ? await api.user(row.id) : undefined
    editingId.value = user?.id
    Object.assign(form, {
      username: user?.username ?? '',
      name: user?.name ?? '',
      initialPassword: '',
      departmentId:
        user?.department?.id ??
        selectedDepartment.value ??
        session.user?.departmentId ??
        choices.value[0]?.id,
      status: user?.status ?? 'ACTIVE',
      departmentAdministrator: user?.departmentAdministrator ?? false,
    })
    formError.value = ''
    dialog.value = 'user'
  })
}
function openDepartment(edit = false) {
  editingId.value = edit ? selectedNode.value?.id : undefined
  Object.assign(deptForm, {
    name: edit ? (selectedNode.value?.name ?? '') : '',
    parentId: edit ? (selectedNode.value?.parentId ?? null) : (selectedDepartment.value ?? null),
  })
  formError.value = ''
  dialog.value = 'department'
}
async function save() {
  const done = await runForm(async () => {
    if (dialog.value === 'user') {
      const input = { username: form.username, name: form.name, status: form.status } as UserInput
      if (!editingId.value || session.totalAdmin) input.departmentId = form.departmentId!
      if (!editingId.value) input.initialPassword = form.initialPassword
      if (session.totalAdmin) input.departmentAdministrator = form.departmentAdministrator
      await api.saveUser(editingId.value, input)
      form.initialPassword = ''
    } else await api.saveDepartment(editingId.value, deptForm.name, deptForm.parentId)
    dialog.value = ''
    return true
  }, '保存成功')
  if (done) {
    await load()
    await run(() => session.refresh())
  }
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>用户与部门</h1>
      <p>维护组织结构、用户资料与账号状态。</p>
    </div>
    <ActionButton type="primary" permission="user:create" @click="openUser()">
      新增用户
    </ActionButton>
  </div>
  <TaskError :message="error" />
  <el-button v-if="error" @click="load">重新加载</el-button>
  <div class="organization-layout" v-loading="busy">
    <section class="panel department-panel">
      <div class="panel-heading"><h2>组织结构</h2></div>
      <el-button text class="all-departments" @click="filterDepartment()">全部管理范围</el-button>
      <el-tree
        :data="departments"
        node-key="id"
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
        highlight-current
        :expand-on-click-node="false"
        @node-click="filterDepartment"
      />
      <div v-if="session.totalAdmin" class="department-actions">
        <ActionButton permission="department:create" @click="openDepartment()">
          新增部门
        </ActionButton>
        <ActionButton
          permission="department:update"
          :disabled="!selectedNode"
          @click="openDepartment(true)"
        >
          编辑部门
        </ActionButton>
      </div>
      <p class="muted scope-note">
        {{ session.totalAdmin ? '总管理员可维护全部组织结构。' : '仅展示本部门及下级部门。' }}
      </p>
    </section>
    <section class="panel">
      <form class="toolbar" @submit.prevent="search">
        <div class="toolbar-main">
          <el-input
            v-model="keyword"
            placeholder="搜索姓名或用户名"
            maxlength="100"
            aria-label="搜索用户"
            clearable
          />
          <el-button native-type="submit">搜索</el-button>
        </div>
        <label class="inline-check">
          <input type="checkbox" v-model="includeDepartmentName" @change="search" />
          同时搜索部门名
        </label>
      </form>
      <el-table :data="rows.items" empty-text="当前范围内没有匹配的用户。">
        <el-table-column label="用户" min-width="145">
          <template #default="{ row }">
            <strong class="cell-title">{{ row.name }}</strong>
            <span class="cell-subtitle">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属部门" min-width="120">
          <template #default="{ row }">{{ row.department?.name ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="管理身份" min-width="115">
          <template #default="{ row }">
            {{ row.departmentAdministrator ? '部门管理员' : '普通用户' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="plain">
              {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <ActionButton text type="primary" permission="user:update" @click="openUser(row)">
              编辑用户
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
  </div>
  <el-dialog
    :model-value="!!dialog"
    :title="
      dialog === 'user'
        ? editingId
          ? '编辑用户'
          : '新增用户'
        : editingId
          ? '编辑部门'
          : '新增部门'
    "
    width="560px"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :before-close="
      (done: () => void) => {
        if (!saving) {
          dialog = ''
          form.initialPassword = ''
          done()
        }
      }
    "
  >
    <form @submit.prevent="save">
      <TaskError :message="formError" />
      <fieldset class="plain-fieldset form-stack" :disabled="saving">
        <template v-if="dialog === 'user'">
          <label for="user-name">姓名</label>
          <input id="user-name" v-model="form.name" required maxlength="24" />
          <label for="user-username">用户名</label>
          <input
            id="user-username"
            v-model="form.username"
            required
            maxlength="32"
            autocomplete="off"
          />
          <template v-if="!editingId">
            <label for="user-password">初始密码</label>
            <input
              id="user-password"
              v-model="form.initialPassword"
              type="password"
              required
              autocomplete="new-password"
            />
          </template>
          <label for="user-department">所属部门</label>
          <select
            id="user-department"
            v-model="form.departmentId"
            :disabled="!!editingId && !session.totalAdmin"
            required
          >
            <option v-for="department in choices" :key="department.id" :value="department.id">
              {{ department.name }}
            </option>
          </select>
          <p v-if="editingId" class="muted">跨部门调动保留业务角色，仅总管理员可操作。</p>
          <label for="user-status">账号状态</label>
          <select id="user-status" v-model="form.status">
            <option value="ACTIVE">正常</option>
            <option value="DISABLED">停用</option>
          </select>
          <el-alert
            v-if="form.status === 'DISABLED'"
            title="保存后，该用户已有会话立即失效，不能继续登录或访问受保护功能。"
            type="warning"
            :closable="false"
          />
          <label v-if="session.totalAdmin" class="inline-check">
            <input type="checkbox" v-model="form.departmentAdministrator" />
            设为部门管理员
          </label>
        </template>
        <template v-else>
          <label for="department-name">部门名称</label>
          <input id="department-name" v-model="deptForm.name" required maxlength="24" />
          <label for="department-parent">上级部门</label>
          <select id="department-parent" v-model="deptForm.parentId">
            <option :value="null">无（根节点）</option>
            <option
              v-for="department in departmentChoices"
              :key="department.id"
              :value="department.id"
            >
              {{ department.name }}
            </option>
          </select>
        </template>
      </fieldset>
      <div class="dialog-actions">
        <el-button
          :disabled="saving"
          @click="
            () => {
              dialog = ''
              form.initialPassword = ''
            }
          "
        >
          取消
        </el-button>
        <el-button native-type="submit" type="primary" :loading="saving">
          {{ dialog === 'user' ? '保存用户' : '保存部门' }}
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>
