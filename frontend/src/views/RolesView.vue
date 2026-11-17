<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { api } from '../api'
import type { Page, RoleDetails, RoleInput, RoleSummary } from '../api/types'
import { useSession } from '../stores/session'
import { confirmAction, useTask } from '../composables/useTask'
import ActionButton from '../components/ActionButton.vue'
import TaskError from '../components/TaskError.vue'
import RoleEditor from '../components/RoleEditor.vue'
const session = useSession(),
  { busy, error, run } = useTask()
const rows = ref<Page<RoleSummary>>({ items: [], page: 1, pageSize: 20, total: 0 }),
  page = ref(1),
  keyword = ref(''),
  roleType = ref(session.totalAdmin ? '' : 'BUSINESS')
const editor = ref(false),
  seed = ref<RoleDetails>(),
  readonly = ref(false)
async function load() {
  await run(async () => {
    rows.value = await api.roles({
      page: page.value,
      keyword: keyword.value,
      roleType: roleType.value,
    })
  })
}
function search() {
  page.value = 1
  void load()
}
function resetSearch() {
  keyword.value = ''
  roleType.value = session.totalAdmin ? '' : 'BUSINESS'
  search()
}
async function open(role?: RoleSummary) {
  await run(async () => {
    seed.value = role ? await api.role(role.id) : undefined
    readonly.value =
      !!role && (!session.totalAdmin || role.roleType !== 'BUSINESS' || !session.has('role:update'))
    editor.value = true
  })
}
async function save(input: RoleInput) {
  await api.saveRole(seed.value?.id, input)
}
async function saved() {
  await load()
  await run(() => session.refresh())
}
async function remove(role: RoleSummary) {
  await run(async () => {
    const details = await api.role(role.id)
    if (
      await confirmAction(
        `「${role.name}」关联 ${details.assignedUserCount} 名用户、${details.inheritedRoles.length} 个父角色、${details.inheritedByRoles.length} 个子角色。存在关联时须先解除，是否继续删除？`,
        '删除角色',
      )
    ) {
      await api.deleteRole(role.id)
      if (rows.value.items.length === 1 && page.value > 1) page.value--
      rows.value = await api.roles({
        page: page.value,
        keyword: keyword.value,
        roleType: roleType.value,
      })
    }
  })
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>角色管理</h1>
      <p>配置直接权限与继承角色，查看有效权限和配置来源。</p>
    </div>
    <ActionButton v-if="session.totalAdmin" permission="role:create" type="primary" @click="open()">
      <el-icon><Plus /></el-icon>
      新增角色
    </ActionButton>
  </div>
  <TaskError :message="error" />
  <form class="panel filter-panel" @submit.prevent="search">
    <div class="filter-fields">
      <label for="role-search-name">
        角色名称
        <el-input
          id="role-search-name"
          v-model="keyword"
          maxlength="100"
          placeholder="请输入角色名称"
          clearable
        />
      </label>
      <label v-if="session.totalAdmin" for="role-search-type">
        角色类型
        <select id="role-search-type" v-model="roleType" @change="search">
          <option value="">全部角色</option>
          <option value="BUSINESS">业务角色</option>
          <option value="TOTAL_ADMIN">总管理员</option>
          <option value="DEPARTMENT_ADMIN">部门管理员</option>
        </select>
      </label>
    </div>
    <div class="filter-actions">
      <el-button @click="resetSearch">重置</el-button>
      <el-button type="primary" native-type="submit">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
    </div>
  </form>
  <section class="panel" v-loading="busy">
    <div class="panel-heading"><h2>角色列表</h2></div>
    <el-table :data="rows.items" empty-text="没有匹配的角色，请调整搜索条件或新增角色。">
      <el-table-column label="角色名称" min-width="310">
        <template #default="{ row }">
          <div class="person-cell">
            <span class="avatar square" aria-hidden="true">{{ row.name.slice(0, 1) }}</span>
            <div>
              <strong class="cell-title">{{ row.name }}</strong>
              <span class="cell-subtitle">
                {{ row.description ? row.description + ' · ' : ''
                }}{{ row.directPermissionCount }} 项直接 /
                {{ row.effectivePermissionCount - row.directPermissionCount }} 项仅继承
              </span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="继承关系" min-width="200">
        <template #default="{ row }">
          <span v-if="row.roleType !== 'BUSINESS'" class="muted">预置管理角色，不参与业务继承</span>
          <div v-else class="role-relations">
            <div>
              <span class="muted">父角色</span>
              <div class="tag-list">
                <el-tag v-for="parent in row.inheritedRoles" :key="parent.id" type="info">
                  {{ parent.name }}
                </el-tag>
                <span v-if="!row.inheritedRoles.length" class="muted">无</span>
              </div>
            </div>
            <div>
              <span class="muted">子角色</span>
              <div class="tag-list">
                <el-tag v-for="child in row.inheritedByRoles" :key="child.id" type="info">
                  {{ child.name }}
                </el-tag>
                <span v-if="!row.inheritedByRoles.length" class="muted">无</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="有效权限应用" min-width="170">
        <template #default="{ row }">
          <div class="tag-list">
            <el-tag v-for="a in row.applications" :key="a.applicationId" type="info">
              {{ a.applicationName }}
            </el-tag>
            <span v-if="!row.applications.length" class="muted">尚未授权</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="right" fixed="right">
        <template #default="{ row }">
          <ActionButton
            text
            type="primary"
            :permission="
              session.totalAdmin && row.roleType === 'BUSINESS' ? 'role:update' : undefined
            "
            @click="open(row)"
          >
            {{ session.totalAdmin && row.roleType === 'BUSINESS' ? '配置权限' : '查看权限' }}
          </ActionButton>
          <ActionButton
            v-if="session.totalAdmin && row.roleType === 'BUSINESS'"
            text
            type="danger"
            permission="role:delete"
            @click="remove(row)"
          >
            删除
          </ActionButton>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <span class="muted">共 {{ rows.total }} 个角色</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="rows.total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </section>
  <RoleEditor
    v-if="editor"
    :title="readonly ? '查看角色权限' : seed ? '配置角色权限' : '新增角色'"
    :seed="seed"
    :editing-id="seed?.id"
    :readonly="readonly"
    :save="save"
    @close="editor = false"
    @saved="saved"
  />
</template>
