<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Change } from '../data';
import {
  deptName,
  requestedChange,
  data,
  assignRolePairs,
  saveRoleConfig,
  revokeGrant,
  toast,
  roleById,
  isPending,
  retryDelivery,
  simulateApproval,
  simulateExecution,
  systemName,
  tryAction,
  visibleChanges,
} from '../store';
import UiIcon from '../components/UiIcon.vue';
import UiPagination from '../components/UiPagination.vue';
import { usePagination } from '../composables/usePagination';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
const tab = ref('全部申请');
const query = ref('');
const detail = ref<Change>();
watch(
  requestedChange,
  (id) => {
    if (id) {
      detail.value = visibleChanges.value.find((c) => c.id === id);
      requestedChange.value = '';
    }
  },
  { immediate: true },
);
const simulator = ref(false);
const copyDraft = ref<Change>();
const copyReason = ref('');
const copyError = ref('');
const copyPermissions = ref<string[]>([]);
const approval = ref('all');
const execution = ref('all');
const filtered = computed(() =>
  visibleChanges.value.filter(
    (c) =>
      (approval.value === 'all' || c.approval === approval.value) &&
      (execution.value === 'all' || c.execution === execution.value) &&
      `${c.title} ${c.id} ${c.actor}`.toLowerCase().includes(query.value.toLowerCase()) &&
      (tab.value === '全部申请' ||
        (tab.value === '待处理'
          ? isPending(c)
          : tab.value === '已完成'
            ? c.execution === '执行成功'
            : c.execution === '执行失败' || c.delivery === '发送失败' || c.execution === '已失效')),
  ),
);
const { rows, currentPage, pageSize } = usePagination(filtered);
function copyRejected() {
  if (!detail.value || detail.value.approval !== '已驳回') return;
  copyDraft.value = JSON.parse(JSON.stringify(detail.value));
  copyReason.value = detail.value.reason;
  copyPermissions.value = [...(detail.value.permissionIds ?? [])];
  copyError.value = '';
}
function resubmit() {
  const d = copyDraft.value;
  if (!d) return;
  copyError.value = !copyReason.value.trim() ? '请填写新的办理原因' : '';
  if (copyError.value) return;
  try {
    let next: Change | undefined;
    if (d.kind === '分配角色' && d.pairs?.length) {
      next = assignRolePairs(
        d.pairs.map((p) => ({ employee: p.employee, role: p.role, dept: p.dept })),
        copyReason.value,
      );
    } else if (d.kind === '配置角色' && d.roleId) {
      next = saveRoleConfig(roleById(d.roleId), copyPermissions.value, copyReason.value);
    } else if (d.kind === '撤销角色' && d.grantIds?.length === 1) {
      const grant = data.grants.find((g) => g.id === d.grantIds![0]);
      if (!grant) throw Error('原来源关系已不存在');
      next = revokeGrant(grant, copyReason.value);
    } else throw Error('请返回对应业务页，按当前合法来源重新选择明细');
    copyDraft.value = undefined;
    detail.value = next;
    simulator.value = false;
  } catch (e) {
    copyError.value = (e as Error).message;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>变更申请</h1>
      <p>审批通过与执行成功，始终分开记录。</p>
    </div>
    <span class="subtle-label"
      ><UiIcon name="GitPullRequest" :size="16" />{{
        visibleChanges.filter(isPending).length
      }}
      项未结束申请</span
    >
  </div>
  <div class="request-legend">
    <div>
      <span class="legend-number">1</span><strong>OA 投递</strong><span>固定申请送达外部 OA</span>
    </div>
    <UiIcon name="ChevronRight" :size="16" />
    <div>
      <span class="legend-number">2</span><strong>最终审批</strong><span>接收可信的批准或驳回</span>
    </div>
    <UiIcon name="ChevronRight" :size="16" />
    <div>
      <span class="legend-number">3</span><strong>变更执行</strong><span>执行成功后权限才生效</span>
    </div>
  </div>
  <div class="panel">
    <div class="panel-tabs">
      <div class="tabs">
        <button
          v-for="t in ['全部申请', '待处理', '已完成', '需要关注']"
          :key="t"
          :class="{ active: tab === t }"
          @click="tab = t"
        >
          {{ t }}<span v-if="t === '需要关注'" class="tab-dot" />
        </button>
      </div>
    </div>
    <div class="table-toolbar">
      <label class="search-field wide-search"
        ><UiIcon name="Search" :size="17" /><input
          v-model="query"
          placeholder="搜索申请编号、目标、提交人"
          aria-label="搜索申请" /></label
      ><select v-model="approval" aria-label="审批结果筛选">
        <option value="all">全部审批结果</option>
        <option>待审批</option>
        <option>已批准</option>
        <option>已驳回</option></select
      ><select v-model="execution" aria-label="执行结果筛选">
        <option value="all">全部执行结果</option>
        <option>未执行</option>
        <option>待执行</option>
        <option>执行成功</option>
        <option>执行失败</option>
        <option>已失效</option></select
      ><span class="toolbar-spacer" /><span class="small muted">同范围管理员协作可见</span>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>变更事项</th>
            <th>来源 / 系统</th>
            <th>OA 投递</th>
            <th>审批结果</th>
            <th>执行结果</th>
            <th>提交人</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in rows" :key="c.id">
            <td>
              <button
                class="table-link"
                @click="
                  detail = c;
                  simulator = false;
                "
              >
                {{ c.title }}</button
              ><small>{{ c.id }}</small>
            </td>
            <td>
              {{ deptName(c.dept) }}<small>{{ systemName(c.system) }}</small>
            </td>
            <td><StatusBadge :value="c.delivery" /></td>
            <td><StatusBadge :value="c.approval" /></td>
            <td><StatusBadge :value="c.execution" /></td>
            <td>
              {{ c.actor }}<small>{{ c.date }}</small>
            </td>
            <td class="align-right">
              <button
                class="text-button"
                @click="
                  detail = c;
                  simulator = false;
                "
              >
                查看<UiIcon name="ChevronRight" :size="14" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!rows.length" class="empty-inline">
      <UiIcon name="GitPullRequest" :size="30" /><strong>没有匹配的申请</strong>
      <p>试试其他关键词或申请状态。</p>
    </div>
    <div class="table-footer">
      <span>已提交内容保持固定，不提供本地撤回或超时自动取消</span
      ><span>{{ rows.length }} 条申请</span>
    </div>
  </div>
  <UiPagination :total="filtered.length" v-model:page="currentPage" v-model:size="pageSize" />
  <UiDialog
    :open="Boolean(detail)"
    drawer
    title="申请详情"
    :subtitle="detail?.id"
    @close="detail = undefined"
    ><template v-if="detail"
      ><div class="stack">
        <h3 class="detail-title">{{ detail.title }}</h3>
        <div class="status-trio">
          <div><small>OA 投递</small><StatusBadge :value="detail.delivery" /></div>
          <div><small>最终审批</small><StatusBadge :value="detail.approval" /></div>
          <div><small>执行结果</small><StatusBadge :value="detail.execution" /></div>
        </div>
        <div class="info-box">
          <UiIcon name="Info" />
          <p>
            {{
              detail.execution === '已失效'
                ? '原来源或目标条件已变化。迟到批准与恢复员工身份都不能使旧申请重新生效。'
                : detail.execution === '执行失败'
                  ? '申请已批准，执行尚未成功。当前授权未改变，等待服务恢复后重试原固定方案。'
                  : detail.execution === '执行成功'
                    ? '此申请已实际执行，当前授权结果已更新。'
                    : '提交与批准均不代表已生效，请以实际执行结果为准。'
            }}
          </p>
        </div>
        <dl class="detail-list">
          <div>
            <dt>申请类型</dt>
            <dd>{{ detail.kind }}</dd>
          </div>
          <div>
            <dt>提交人</dt>
            <dd>{{ detail.actor }}</dd>
          </div>
          <div>
            <dt>部门 / 系统</dt>
            <dd>{{ deptName(detail.dept) }} / {{ systemName(detail.system) }}</dd>
          </div>
          <div>
            <dt>办理原因</dt>
            <dd>{{ detail.reason }}</dd>
          </div>
        </dl>
        <div>
          <h3 class="section-title">固定变更明细</h3>
          <div v-for="(d, i) in detail.diff" :key="i" class="diff-line">
            <UiIcon name="FileText" :size="16" />{{ d }}
          </div>
        </div>
        <div>
          <h3 class="section-title">处理时间线</h3>
          <ol class="timeline">
            <li v-for="(event, i) in detail.events" :key="i">
              <span class="timeline-node" :class="{ current: i === detail.events.length - 1 }" />
              <p>{{ event }}</p>
            </li>
          </ol>
        </div>
        <button
          v-if="detail.delivery !== '已关联 OA' && detail.execution !== '已失效'"
          class="button primary full"
          @click="tryAction(() => retryDelivery(detail!))"
        >
          <UiIcon name="RefreshCw" :size="16" />{{
            detail.delivery === '发送失败' ? '重试投递同一申请' : '投递至模拟 OA'
          }}
        </button>
        <button
          v-if="detail.approval === '已驳回'"
          class="button primary full"
          @click="copyRejected"
        >
          复制为新草稿
        </button>
        <div class="simulator-panel">
          <button class="simulator-trigger" @click="simulator = !simulator">
            <UiIcon name="Play" :size="15" /><span>原型演示工具</span
            ><UiIcon name="ChevronDown" :size="15" :class="{ 'rotate-180': simulator }" />
          </button>
          <div v-if="simulator" class="simulator-body">
            <p>
              以下模拟外部 OA / 执行服务事件，供原型评审。正式管理页面没有人工批准或强行执行入口。
            </p>
            <div class="flex flex-wrap gap-2">
              <button
                class="button small-button"
                :disabled="detail.delivery === '已关联 OA'"
                @click="tryAction(() => retryDelivery(detail!, true))"
              >
                模拟投递失败</button
              ><button
                class="button small-button"
                :disabled="detail.delivery !== '已关联 OA' || detail.approval !== '待审批'"
                @click="tryAction(() => simulateApproval(detail!, true))"
              >
                模拟 OA 批准</button
              ><button
                class="button small-button"
                :disabled="detail.delivery !== '已关联 OA' || detail.approval !== '待审批'"
                @click="tryAction(() => simulateApproval(detail!, false))"
              >
                模拟 OA 驳回</button
              ><button
                class="button small-button"
                :disabled="
                  detail.approval !== '已批准' || ['已失效', '执行成功'].includes(detail.execution)
                "
                @click="tryAction(() => simulateExecution(detail!, true))"
              >
                模拟执行失败</button
              ><button
                class="button small-button"
                :disabled="detail.approval !== '已批准' || detail.execution === '已失效'"
                @click="tryAction(() => simulateExecution(detail!))"
              >
                {{ detail.execution === '执行失败' ? '模拟服务恢复' : '模拟自动执行' }}
              </button>
            </div>
          </div>
        </div>
      </div></template
    ></UiDialog
  >
  <UiDialog
    :open="Boolean(copyDraft)"
    title="编辑重提草稿"
    subtitle="原驳回记录保持不变，新申请按当前资格重新校验"
    @close="copyDraft = undefined"
    ><div class="stack">
      <div class="info-box">
        <p>
          {{ copyDraft?.title }}。来源和目标沿用原申请；如需更改员工或来源，请回到业务页重新选择。
        </p>
      </div>
      <template v-if="copyDraft?.kind === '配置角色'"
        ><label
          v-for="p in data.permissions.filter((p) => p.system === copyDraft?.system && p.active)"
          :key="p.id"
          class="check-line"
          ><input type="checkbox" v-model="copyPermissions" :value="p.id" />{{ p.feature }} /
          {{ p.action }}</label
        ></template
      ><label class="field"
        ><span>办理原因</span
        ><textarea
          v-model="copyReason"
          :aria-invalid="Boolean(copyError)"
          aria-describedby="resubmit-error"
        />
      </label>
      <p v-if="copyError" id="resubmit-error" class="field-error" role="alert">{{ copyError }}</p>
    </div>
    <template #footer
      ><button class="button" @click="copyDraft = undefined">取消</button
      ><button class="button primary" @click="resubmit">提交新申请</button></template
    ></UiDialog
  >
</template>
