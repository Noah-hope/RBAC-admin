<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Grid, Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import type { Application, ApiRecord, PermissionTree } from '../api/types'
import { useTask, confirmAction } from '../composables/useTask'
import { useSession } from '../stores/session'
import { useUi } from '../stores/ui'
import ActionButton from '../components/ActionButton.vue'
import TaskError from '../components/TaskError.vue'
const session = useSession(),
  ui = useUi(),
  { busy, error, run } = useTask()
const { busy: formBusy, error: formError, run: runForm } = useTask()
const apps = ref<Application[]>([]),
  appId = ref<number>(),
  tab = ref('permissions')
const tree = ref<PermissionTree>(),
  registry = ref<ApiRecord[]>([]),
  keyword = ref('')
const activeApp = computed(() => apps.value.find((a) => a.id === appId.value))
const permissions = computed(() =>
  (tree.value?.menus ?? []).flatMap((menu) => [
    { ...menu, type: 'MENU', parentId: null },
    ...menu.buttons.map((p) => ({ ...p, type: 'BUTTON', parentId: menu.id })),
  ]),
)
const filteredApis = computed(() =>
  registry.value.filter((a) =>
    `${a.operationId} ${a.httpMethod} ${a.path}`
      .toLowerCase()
      .includes(keyword.value.toLowerCase()),
  ),
)
const dialog = ref<'app' | 'permission' | 'api' | ''>(''),
  editingId = ref<number>()
const appForm = reactive({ name: '', code: '' })
const permissionForm = reactive<{
  name: string
  code: string
  type: 'MENU' | 'BUTTON'
  parentId: number | null
  apiIds: number[]
}>({ name: '', code: '', type: 'MENU', parentId: null, apiIds: [] })
const apiForm = reactive({ operationId: '', httpMethod: 'GET', path: '' })
const apiOptions = ref<ApiRecord[]>([])
async function selectedData() {
  tree.value = undefined
  registry.value = []
  if (!appId.value) return
  const id = appId.value
  const [loaded, apis] = await Promise.all([
    api.permissionTree(id),
    session.has('api:manage') ? api.allApis(id) : Promise.resolve([]),
  ])
  if (id === appId.value) {
    tree.value = loaded
    registry.value = apis
  }
}
async function load() {
  await run(async () => {
    apps.value = await api.allApplications()
    appId.value = apps.value.some((a) => a.id === appId.value) ? appId.value : apps.value[0]?.id
    await selectedData()
  })
}
async function selectApp(id: number) {
  appId.value = id
  await run(selectedData)
}
function openApp(edit = false) {
  editingId.value = edit ? appId.value : undefined
  Object.assign(
    appForm,
    edit ? { name: activeApp.value!.name, code: activeApp.value!.code } : { name: '', code: '' },
  )
  formError.value = ''
  dialog.value = 'app'
}
async function openPermission(id?: number) {
  await run(async () => {
    editingId.value = id
    const details = id ? await api.permission(appId.value!, id) : undefined
    Object.assign(
      permissionForm,
      details
        ? {
            name: details.name,
            code: details.code,
            type: details.type,
            parentId: details.parentId,
            apiIds: details.apis.map((a) => a.id),
          }
        : { name: '', code: '', type: 'MENU', parentId: null, apiIds: [] },
    )
    apiOptions.value = session.has('api:manage')
      ? await api.allApis(appId.value!)
      : (details?.apis ?? [])
    formError.value = ''
    dialog.value = 'permission'
  })
}
function openApi(record?: ApiRecord) {
  editingId.value = record?.id
  Object.assign(
    apiForm,
    record
      ? { operationId: record.operationId, httpMethod: record.httpMethod, path: record.path }
      : { operationId: '', httpMethod: 'GET', path: '' },
  )
  formError.value = ''
  dialog.value = 'api'
}
async function save() {
  const result = await runForm(async () => {
    if (dialog.value === 'app') {
      const saved = await api.saveApplication(
        editingId.value,
        appForm.name.trim(),
        appForm.code || undefined,
      )
      appId.value = saved.id
    } else if (dialog.value === 'permission')
      await api.savePermission(appId.value!, editingId.value, {
        ...permissionForm,
        parentId: permissionForm.type === 'MENU' ? null : permissionForm.parentId,
      })
    else await api.saveApi(appId.value!, editingId.value, { ...apiForm })
    dialog.value = ''
    return true
  }, '保存成功')
  if (result) {
    await load()
    await run(() => session.refresh())
  }
}
async function removeApi(record: ApiRecord) {
  if (
    !(await confirmAction(
      `删除「${record.operationId}」登记，并解除 ${record.permissionIds.length} 项权限关联。后续该接口权限检查将返回拒绝。`,
      '删除接口登记',
    ))
  )
    return
  await run(async () => {
    await api.deleteApi(appId.value!, record.id)
    await selectedData()
  }, '接口登记已删除')
}
function selectTab(value: string) {
  if (value === 'apis' && !session.has('api:manage')) ui.askAccess('api:manage')
  else tab.value = value
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>应用与权限</h1>
      <p>按应用维护菜单、按钮及关联接口。</p>
    </div>
    <ActionButton permission="application:create" type="primary" @click="openApp()">
      <el-icon><Plus /></el-icon>
      新增应用
    </ActionButton>
  </div>
  <TaskError :message="error" />
  <el-button v-if="error" @click="load">重新加载</el-button>
  <div class="catalog-layout" v-loading="busy">
    <section class="panel app-list">
      <div class="panel-heading">
        <h2>业务应用</h2>
        <span class="muted">{{ apps.length }}</span>
      </div>
      <button
        v-for="app in apps"
        :key="app.id"
        class="app-choice"
        :class="{ selected: app.id === appId }"
        @click="selectApp(app.id)"
      >
        <span class="app-mark">
          <el-icon><Grid /></el-icon>
        </span>
        <span>
          <strong>{{ app.name }}</strong>
          <small>{{ app.code }}</small>
        </span>
      </button>
      <p v-if="!apps.length && !busy" class="empty-note">尚未登记应用，请新增应用。</p>
    </section>
    <section class="panel" v-if="activeApp">
      <div class="panel-heading">
        <div>
          <h2>{{ activeApp.name }}</h2>
          <p class="muted">接入标识 {{ activeApp.code }}</p>
        </div>
        <ActionButton permission="application:update" @click="openApp(true)">编辑应用</ActionButton>
      </div>
      <div class="permission-app-tabs" role="tablist" aria-label="应用配置">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'permissions'"
          :class="{ active: tab === 'permissions' }"
          @click="selectTab('permissions')"
        >
          菜单与按钮
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'apis'"
          :class="{ active: tab === 'apis' }"
          @click="selectTab('apis')"
        >
          API 登记
        </button>
      </div>
      <template v-if="tab === 'permissions'">
        <div class="toolbar">
          <p class="muted">按钮归属菜单，角色按菜单和按钮授权。</p>
          <ActionButton permission="permission:create" @click="openPermission()">
            新增权限
          </ActionButton>
        </div>
        <el-table :data="permissions" empty-text="此应用尚无权限，请先登记菜单。">
          <el-table-column label="权限名称" min-width="160">
            <template #default="{ row }">
              <span :class="{ 'child-name': row.type === 'BUTTON' }">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="85">
            <template #default="{ row }">
              <el-tag :type="row.type === 'MENU' ? 'info' : 'success'" effect="plain">
                {{ row.type === 'MENU' ? '菜单' : '按钮' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="code" label="权限标识" min-width="160" />
          <el-table-column label="关联接口" width="110">
            <template #default="{ row }">
              <el-button text type="primary" @click="openPermission(row.id)">
                {{
                  session.has('api:manage')
                    ? registry.filter((a) => a.permissionIds.includes(row.id)).length
                    : '查看关联'
                }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="95">
            <template #default="{ row }">
              <ActionButton
                permission="permission:update"
                type="primary"
                text
                @click="openPermission(row.id)"
              >
                编辑
              </ActionButton>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template v-else>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索接口标识、方法或路径"
            aria-label="搜索接口"
            clearable
            maxlength="100"
          />
          <ActionButton permission="api:manage" @click="openApi()">登记接口</ActionButton>
        </div>
        <el-table :data="filteredApis" empty-text="没有匹配的接口登记。">
          <el-table-column prop="operationId" label="接口标识" min-width="160" />
          <el-table-column prop="httpMethod" label="方法" width="90" />
          <el-table-column prop="path" label="请求路径" min-width="180" />
          <el-table-column label="关联权限" width="100">
            <template #default="{ row }">{{ row.permissionIds.length }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <ActionButton text permission="api:manage" type="primary" @click="openApi(row)">
                编辑
              </ActionButton>
              <ActionButton text permission="api:manage" type="danger" @click="removeApi(row)">
                删除
              </ActionButton>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </section>
  </div>
  <el-dialog
    :model-value="!!dialog"
    :title="
      dialog === 'app'
        ? editingId
          ? '编辑应用'
          : '新增应用'
        : dialog === 'permission'
          ? editingId
            ? '编辑权限'
            : '新增权限'
          : editingId
            ? '编辑接口'
            : '登记接口'
    "
    width="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="!formBusy"
    :before-close="
      (done: () => void) => {
        if (!formBusy) {
          dialog = ''
          done()
        }
      }
    "
  >
    <form @submit.prevent="save">
      <TaskError :message="formError" />
      <fieldset :disabled="formBusy" class="plain-fieldset form-stack">
        <template v-if="dialog === 'app'">
          <label for="app-name">应用名称</label>
          <input id="app-name" v-model="appForm.name" required maxlength="24" />
          <label for="app-code">接入标识</label>
          <input
            id="app-code"
            v-model="appForm.code"
            :disabled="!!editingId"
            maxlength="64"
            pattern="[a-z][a-z0-9_-]*"
            placeholder="省略时由系统生成"
          />
          <p class="muted">接入标识创建后不能修改。</p>
        </template>
        <template v-else-if="dialog === 'permission'">
          <label for="permission-name">权限名称</label>
          <input id="permission-name" v-model="permissionForm.name" required maxlength="24" />
          <label for="permission-code">权限标识</label>
          <input
            id="permission-code"
            v-model="permissionForm.code"
            required
            maxlength="64"
            pattern="[^:\s]+:[^:\s]+"
            placeholder="例如 course:create"
          />
          <div class="form-grid">
            <div>
              <label for="permission-type">权限类型</label>
              <select id="permission-type" v-model="permissionForm.type" :disabled="!!editingId">
                <option value="MENU">菜单</option>
                <option value="BUTTON">按钮</option>
              </select>
            </div>
            <div v-if="permissionForm.type === 'BUTTON'">
              <label for="permission-parent">所属菜单</label>
              <select
                id="permission-parent"
                v-model="permissionForm.parentId"
                :disabled="!!editingId"
                required
              >
                <option v-for="menu in tree?.menus" :key="menu.id" :value="menu.id">
                  {{ menu.name }}
                </option>
              </select>
            </div>
          </div>
          <h3>关联接口</h3>
          <p class="muted">移除关联保留 API 登记。新接口请先在「API 登记」中创建。</p>
          <div class="checkbox-list">
            <label v-for="record in apiOptions" :key="record.id">
              <input v-model="permissionForm.apiIds" type="checkbox" :value="record.id" />
              <span>
                {{ record.operationId }}
                <small>{{ record.httpMethod }} {{ record.path }}</small>
              </span>
            </label>
            <p v-if="!apiOptions.length" class="muted">没有可选择的接口。</p>
          </div>
        </template>
        <template v-else-if="dialog === 'api'">
          <label for="api-operation">接口标识</label>
          <input
            id="api-operation"
            v-model="apiForm.operationId"
            required
            maxlength="128"
            pattern="\S+"
            placeholder="operationId，例如 createCourse"
          />
          <label for="api-method">请求方法</label>
          <select id="api-method" v-model="apiForm.httpMethod">
            <option
              v-for="method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']"
              :key="method"
            >
              {{ method }}
            </option>
          </select>
          <label for="api-path">请求路径</label>
          <input
            id="api-path"
            v-model="apiForm.path"
            required
            maxlength="255"
            pattern="/[^?#\s]*"
            placeholder="例如 /courses/{courseId}"
          />
        </template>
      </fieldset>
      <div class="dialog-actions">
        <el-button :disabled="formBusy" @click="dialog = ''">取消</el-button>
        <el-button type="primary" native-type="submit" :loading="formBusy">
          {{ dialog === 'app' ? '保存应用' : dialog === 'permission' ? '保存权限' : '保存接口' }}
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>
