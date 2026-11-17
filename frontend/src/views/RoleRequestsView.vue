<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Page, RoleInput, RoleRequest } from '../api/types'
import { useSession } from '../stores/session'
import { confirmAction, formatTime, statusLabels, useTask } from '../composables/useTask'
import ActionButton from '../components/ActionButton.vue'
import TaskError from '../components/TaskError.vue'
import RoleEditor from '../components/RoleEditor.vue'
const session = useSession(),
  { busy, error, run } = useTask()
const rows = ref<Page<RoleRequest>>({ items: [], page: 1, pageSize: 20, total: 0 }),
  page = ref(1),
  status = ref('')
const editor = ref(false),
  seed = ref<RoleRequest>(),
  readonly = ref(false)
async function load() {
  await run(async () => {
    rows.value = await api.roleRequests({ page: page.value, status: status.value })
  })
}
async function open(row?: RoleRequest, viewOnly = false) {
  await run(async () => {
    seed.value = row ? await api.roleRequest(row.id) : undefined
    readonly.value = viewOnly
    editor.value = true
  })
}
async function save(input: RoleInput) {
  if (seed.value) await api.reviewRoleRequest(seed.value.id, 'APPROVE', input)
  else await api.submitRoleRequest(input)
}
async function reject(row: RoleRequest) {
  if (!(await confirmAction(`拒绝创建「${row.name}」的申请？`, '拒绝角色创建申请'))) return
  const done = await run(async () => {
    await api.reviewRoleRequest(row.id, 'REJECT')
    return true
  }, '申请已拒绝')
  if (done) await load()
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>角色创建申请</h1>
      <p>
        {{
          session.totalAdmin
            ? '受理后可调整配置，角色创建成功时申请才会通过。'
            : '现有角色无法满足需求时，向总管理员申请创建业务角色。'
        }}
      </p>
    </div>
    <ActionButton
      v-if="!session.totalAdmin"
      type="primary"
      permission="roleRequest:create"
      @click="open()"
    >
      申请创建角色
    </ActionButton>
  </div>
  <TaskError :message="error" />
  <section class="panel" v-loading="busy">
    <div class="toolbar">
      <div class="toolbar-main">
        <label for="role-request-filter">申请状态</label>
        <select
          id="role-request-filter"
          v-model="status"
          @change="
            () => {
              page = 1
              load()
            }
          "
        >
          <option value="">全部状态</option>
          <option v-for="s in ['PENDING', 'APPROVED', 'REJECTED']" :key="s" :value="s">
            {{ statusLabels[s] }}
          </option>
        </select>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows.items" empty-text="暂无匹配的角色创建申请。">
      <el-table-column label="申请角色" min-width="180">
        <template #default="{ row }">
          <strong class="cell-title">{{ row.name }}</strong>
          <span class="cell-subtitle">{{ row.description || '未填写说明' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="applicantName" label="申请人" min-width="100" />
      <el-table-column label="继承角色" min-width="150">
        <template #default="{ row }">
          {{
            row.inheritedRoles
              .map(
                (r: { name: string; exists: boolean }) =>
                  `${r.name}${r.exists ? '' : '（已删除）'}`,
              )
              .join('、') || '无'
          }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="
              row.status === 'PENDING' ? 'warning' : row.status === 'APPROVED' ? 'success' : 'info'
            "
            effect="plain"
          >
            {{ statusLabels[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <template v-if="session.totalAdmin && row.status === 'PENDING'">
            <ActionButton text type="primary" permission="roleRequest:review" @click="open(row)">
              受理申请
            </ActionButton>
            <ActionButton text type="danger" permission="roleRequest:review" @click="reject(row)">
              拒绝
            </ActionButton>
          </template>
          <el-button v-else text type="primary" @click="open(row, true)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <span class="muted">共 {{ rows.total }} 条申请</span>
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
    :title="readonly ? '角色创建申请详情' : seed ? '受理角色创建申请' : '申请创建角色'"
    :seed="seed"
    :readonly="readonly"
    :save="save"
    :submit-label="seed ? '创建角色并通过' : '提交角色申请'"
    @close="editor = false"
    @saved="load"
  />
</template>
