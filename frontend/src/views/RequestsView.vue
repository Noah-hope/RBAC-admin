<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { api } from '../api'
import type { Page, PermissionRequest } from '../api/types'
import { useSession } from '../stores/session'
import { useUi } from '../stores/ui'
import { confirmAction, formatTime, statusLabels, useTask } from '../composables/useTask'
import ActionButton from '../components/ActionButton.vue'
import TaskError from '../components/TaskError.vue'
import PermissionSources from '../components/PermissionSources.vue'
const props = defineProps<{ review: boolean }>()
const session = useSession(),
  ui = useUi(),
  { busy, error, run } = useTask(),
  { busy: saving, error: formError, run: runForm } = useTask()
const rows = ref<Page<PermissionRequest>>({ items: [], page: 1, pageSize: 20, total: 0 }),
  page = ref(1),
  status = ref(props.review ? 'PENDING' : '')
const detail = ref<PermissionRequest>(),
  dialog = ref(false),
  roleId = ref<number>()
async function load() {
  await run(async () => {
    rows.value = await api.requests({
      page: page.value,
      view: props.review ? 'REVIEW' : 'MINE',
      status: status.value,
    })
  })
}
async function open(row: PermissionRequest) {
  await run(async () => {
    detail.value = await api.request(row.id)
    roleId.value = undefined
    formError.value = ''
    dialog.value = true
  })
}
async function submitReview(decision: 'APPROVE' | 'REJECT') {
  const done = await runForm(
    async () => {
      await api.reviewRequest(detail.value!.id, decision, roleId.value)
      dialog.value = false
      return true
    },
    decision === 'APPROVE' ? '已批准并分配角色' : '已拒绝申请',
  )
  if (done) {
    await load()
    await run(() => session.refresh())
  }
}
async function withdraw(row: PermissionRequest) {
  if (!(await confirmAction(`撤回「${row.permission.name}」的待审核申请？`, '撤回申请'))) return
  const done = await run(async () => {
    await api.withdrawRequest(row.id)
    return true
  }, '申请已撤回')
  if (done) await load()
}
watch(() => ui.requestRevision, load)
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>{{ review ? '权限申请审核' : '我的申请' }}</h1>
      <p>
        {{
          review
            ? '选择满足需求且不附带无关权限的业务角色完成授权。'
            : '申请工作所需权限，查看处理进度。'
        }}
      </p>
    </div>
    <el-button v-if="!review" type="primary" @click="ui.askAccess()">申请权限</el-button>
  </div>
  <TaskError :message="error" />
  <section class="panel" v-loading="busy">
    <div class="toolbar">
      <div class="toolbar-main">
        <label for="request-filter">申请状态</label>
        <select
          id="request-filter"
          v-model="status"
          @change="
            () => {
              page = 1
              load()
            }
          "
        >
          <option value="">全部状态</option>
          <option
            v-for="s in ['PENDING', 'APPROVED', 'REJECTED', 'SATISFIED', 'WITHDRAWN']"
            :key="s"
            :value="s"
          >
            {{ statusLabels[s] }}
          </option>
        </select>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows.items" empty-text="暂无匹配的权限申请。">
      <el-table-column v-if="review" prop="applicantName" label="申请人" min-width="100" />
      <el-table-column label="申请权限" min-width="175">
        <template #default="{ row }">
          <strong class="cell-title">{{ row.permission.name }}</strong>
          <span class="cell-subtitle">{{ row.permission.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="
              row.status === 'PENDING'
                ? 'warning'
                : row.status === 'APPROVED' || row.status === 'SATISFIED'
                  ? 'success'
                  : 'info'
            "
            effect="plain"
          >
            {{ statusLabels[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="155">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <ActionButton
            v-if="review && row.status === 'PENDING'"
            permission="request:review"
            text
            type="primary"
            @click="open(row)"
          >
            审核申请
          </ActionButton>
          <el-button v-else text type="primary" @click="open(row)">查看详情</el-button>
          <el-button
            v-if="!review && row.status === 'PENDING'"
            text
            type="danger"
            @click="withdraw(row)"
          >
            撤回
          </el-button>
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
  <el-dialog
    v-model="dialog"
    :title="review ? '审核权限申请' : '申请详情'"
    width="900px"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :before-close="
      (done: () => void) => {
        if (!saving) done()
      }
    "
  >
    <template v-if="detail">
      <TaskError :message="formError" />
      <div class="request-summary">
        <div>
          <span>申请人</span>
          <strong>{{ detail.applicantName }}</strong>
        </div>
        <div>
          <span>申请权限</span>
          <strong>{{ detail.permission.name }}</strong>
        </div>
        <div>
          <span>状态</span>
          <strong>{{ statusLabels[detail.status] }}</strong>
        </div>
        <div>
          <span>申请理由</span>
          <p>{{ detail.reason }}</p>
        </div>
      </div>
      <template v-if="review && detail.status === 'PENDING'">
        <h3>选择要分配的业务角色</h3>
        <p class="muted section-description">
          请核对角色在全部应用中的权限。包含无关权限的角色不可选；可先拆分或创建合适的角色。
        </p>
        <div v-for="option in detail.approvalRoles" :key="option.role.id" class="review-candidate">
          <label class="candidate-heading">
            <input
              v-model="roleId"
              type="radio"
              name="approval-role"
              :value="option.role.id"
              :disabled="!option.selectable || saving"
              :aria-label="`批准角色 ${option.role.name}`"
            />
            <strong>{{ option.role.name }}</strong>
            <el-tag :type="option.selectable ? 'success' : 'warning'" effect="plain">
              {{ option.selectable ? '满足申请' : '包含无关权限' }}
            </el-tag>
          </label>
          <p v-if="!option.selectable" class="muted">
            附带权限：{{
              option.extraPermissionIds
                .map((id) => option.role.effectivePermissions.find((p) => p.id === id)?.name ?? id)
                .join('、')
            }}
          </p>
          <div
            v-for="application in option.role.applications"
            :key="application.applicationId"
            class="candidate-application"
          >
            <h4>{{ application.applicationName }}</h4>
            <PermissionSources
              :permissions="
                option.role.effectivePermissions.filter(
                  (p) => p.applicationId === application.applicationId,
                )
              "
            />
          </div>
        </div>
        <el-empty
          v-if="!detail.approvalRoles?.length"
          description="暂无候选角色。请先创建或调整业务角色，再重新打开申请审核。"
        />
      </template>
      <div class="dialog-actions">
        <el-button :disabled="saving" @click="dialog = false">关闭</el-button>
        <template v-if="review && detail.status === 'PENDING'">
          <ActionButton
            type="danger"
            permission="request:review"
            :loading="saving"
            @click="submitReview('REJECT')"
          >
            拒绝申请
          </ActionButton>
          <ActionButton
            type="primary"
            permission="request:review"
            :disabled="!roleId"
            :loading="saving"
            @click="submitReview('APPROVE')"
          >
            批准并分配角色
          </ActionButton>
        </template>
      </div>
    </template>
  </el-dialog>
</template>
