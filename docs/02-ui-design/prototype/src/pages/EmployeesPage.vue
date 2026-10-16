<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Role } from '../data';
import { departments, type Employee, type Grant } from '../data';
import {
  actor,
  allowedDepts,
  allowedSystems,
  assignRolePairs,
  closeSource,
  companyId,
  companyName,
  currentManagement,
  data,
  deptName,
  effectivePermissions,
  employeeName,
  isGlobal,
  isPending,
  page,
  revokeGrant,
  roleById,
  setEmployeeActive,
  systemName,
  toast,
  tryAction,
  visibleChanges,
  visibleEmployees,
  visibleGrants,
  visibleRoles,
} from '../store';
import UiIcon from '../components/UiIcon.vue';
import { reportError } from '../store';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
import UiPagination from '../components/UiPagination.vue';

const colleagues = computed(
  () =>
    currentManagement.value?.accounts
      .map((id) => employeeName(data.accounts.find((a) => a.id === id)?.employee ?? ''))
      .join('、') ?? '',
);
const query = ref('');
const department = ref('all');
const state = ref('all');
const currentPage = ref(1);
const pageSize = ref(10);
const selected = ref<string[]>([]);
const detail = ref<Employee>();
const detailTab = ref('来源授权');
const filtered = computed(() =>
  visibleEmployees.value.filter(
    (e) =>
      (department.value === 'all' || e.employments.some((j) => j.dept === department.value)) &&
      (state.value === 'all' || (state.value === 'active' ? e.active : !e.active)) &&
      `${e.name} ${e.id}`.toLowerCase().includes(query.value.toLowerCase()),
  ),
);
const rows = computed(() =>
  filtered.value.slice(
    (currentPage.value - 1) * pageSize.value,
    currentPage.value * pageSize.value,
  ),
);
const scopedDepartments = computed(() =>
  departments.filter((d) => allowedDepts.value.includes(d.id)),
);
const activeGrants = (id: string) => visibleGrants(id).filter((g) => g.state === '有效');
const roleNames = (id: string) => [...new Set(activeGrants(id).map((g) => roleById(g.role).name))];
const needsRoles = computed(
  () => visibleEmployees.value.filter((e) => e.active && !activeGrants(e.id).length).length,
);
const dialog = ref<'assign' | 'close' | 'revoke' | 'global' | ''>('');
const reason = ref('');
const source = ref('team-one');
const systems = ref<string[]>([]);
const roleIds = ref<string[]>([]);
const assignSystem = ref('oa');
const preview = ref(false);
const assignmentPreview = ref(false);
const assignmentError = ref('');
const employeeSources = ref<Record<string, string>>({});
const assignmentPairs = computed(() =>
  selected.value.flatMap((employee) =>
    roleIds.value.map((role) => ({
      employee,
      role,
      dept: selected.value.length > 1 ? employeeSources.value[employee] : source.value,
    })),
  ),
);
watch(
  selected,
  (ids) => {
    assignmentPreview.value = false;
    ids.forEach((id) => {
      employeeSources.value[id] ??=
        data.employees
          .find((e) => e.id === id)
          ?.employments.find((j) => j.active && allowedDepts.value.includes(j.dept))?.dept ?? '';
    });
  },
  { deep: true },
);
const roleApplicable = (role: Role) =>
  selected.value.length > 0 &&
  selected.value.every((id) => {
    const e = data.employees.find((e) => e.id === id);
    return e?.active && (!role.dept || e.employments.some((j) => j.dept === role.dept && j.active));
  });
const acknowledged = ref(false);
const targetGrant = ref<Grant>();
const eligibleRoles = computed(() =>
  visibleRoles.value.filter((r) => r.active && r.system === assignSystem.value),
);
const detailSources = computed(
  () => detail.value?.employments.filter((j) => allowedDepts.value.includes(j.dept)) ?? [],
);
const pendingSourceCount = computed(
  () =>
    visibleChanges.value.filter(
      (c) =>
        isPending(c) &&
        c.pairs?.some((p) => p.employee === detail.value?.id && p.dept === source.value) &&
        systems.value.includes(c.system),
    ).length,
);
const impacted = computed(() =>
  detail.value
    ? visibleGrants(detail.value.id).filter(
        (g) =>
          g.dept === source.value &&
          systems.value.includes(roleById(g.role).system) &&
          g.state === '有效',
      )
    : [],
);

function employeeSystems(id: string) {
  return [
    ...new Set(
      activeGrants(id).map(
        (g) => data.systems.find((s) => s.id === roleById(g.role).system)?.short,
      ),
    ),
  ].join(' / ');
}
function employeeOperations(id: string, system: string) {
  return (
    effectivePermissions(id, companyId.value, system)
      .operations.map((id) => data.permissions.find((p) => p.id === id)?.action)
      .join('、') || '无可用功能操作'
  );
}
function open(kind: typeof dialog.value, e?: Employee) {
  if (e) detail.value = e;
  reason.value = '';
  preview.value = false;
  assignmentPreview.value = false;
  assignmentError.value = '';
  acknowledged.value = false;
  roleIds.value = [];
  source.value =
    detail.value?.employments.find((j) => allowedDepts.value.includes(j.dept) && j.active)?.dept ??
    allowedDepts.value[0] ??
    '';
  systems.value = [...allowedSystems.value];
  assignSystem.value = allowedSystems.value[0] ?? 'oa';
  if (kind === 'assign' && e) selected.value = [e.id];
  dialog.value = kind;
}
function previewAssignment() {
  assignmentError.value = !selected.value.length
    ? '请选择员工'
    : !roleIds.value.length
      ? '请选择角色'
      : !reason.value.trim()
        ? '请填写申请原因'
        : assignmentPairs.value.some(
              (p) =>
                !data.employees
                  .find((e) => e.id === p.employee)
                  ?.employments.some((j) => j.dept === p.dept && j.active),
            )
          ? '部分员工不满足来源任职条件'
          : '';
  if (!assignmentError.value) assignmentPreview.value = true;
}
function assign() {
  if (tryAction(() => assignRolePairs(assignmentPairs.value, reason.value))) {
    dialog.value = '';
    selected.value = [];
  }
}
function finish() {
  if (!detail.value) return;
  if (!acknowledged.value) {
    reportError('请先确认本次操作的影响');
    return;
  }
  if (
    tryAction(() => {
      if (dialog.value === 'close')
        closeSource(detail.value!.id, source.value, systems.value, reason.value);
      if (dialog.value === 'revoke' && targetGrant.value)
        revokeGrant(targetGrant.value, reason.value);
      if (dialog.value === 'global')
        setEmployeeActive(detail.value!, !detail.value!.active, reason.value);
    })
  )
    dialog.value = '';
}
function showPreview() {
  if (!reason.value.trim()) {
    reportError('请填写办理原因');
    return;
  }
  if (dialog.value === 'close' && !systems.value.length) {
    reportError('至少选择一个系统');
    return;
  }
  preview.value = true;
}
function toggleAll(checked: boolean) {
  selected.value = checked ? rows.value.filter((e) => e.active).map((e) => e.id) : [];
}
</script>

<template>
  <div class="page-heading">
    <div>
      <h1>员工授权</h1>
    </div>
    <button class="button primary" :disabled="!allowedSystems.length" @click="open('assign')">
      <UiIcon name="Plus" :size="17" />分配角色
    </button>
  </div>
  <div v-if="!allowedSystems.length" class="warning-box mt-4">
    <UiIcon name="Info" />
    <p>当前任命未配置可管理系统，暂时没有可操作系统。请联系总管理员核对范围。</p>
  </div>
  <div class="summary-line">
    <span
      >范围内员工 <b>{{ visibleEmployees.length }}</b></span
    ><span
      >身份有效 <b>{{ visibleEmployees.filter((e) => e.active).length }}</b></span
    ><span
      >待分配角色 <b class="text-amber">{{ needsRoles }}</b></span
    ><button @click="page = 'requests'">
      未结束申请 <b class="text-primary">{{ visibleChanges.filter(isPending).length }}</b
      ><UiIcon name="ArrowUpRight" :size="14" />
    </button>
  </div>
  <div class="roster-layout panel" :class="{ 'single-department': scopedDepartments.length <= 1 }">
    <aside v-if="scopedDepartments.length > 1" class="department-tree">
      <div class="tree-title">部门范围<UiIcon name="Building2" :size="15" /></div>
      <button
        class="tree-row"
        :class="{ selected: department === 'all' }"
        @click="
          department = 'all';
          currentPage = 1;
        "
      >
        <UiIcon name="Layers" :size="16" /><span>全部可管理员工</span
        ><span class="tree-count">{{ visibleEmployees.length }}</span>
      </button>
      <div class="tree-rule" />
      <button
        v-for="d in scopedDepartments"
        :key="d.id"
        class="tree-row"
        :class="{ selected: department === d.id }"
        @click="
          department = d.id;
          currentPage = 1;
        "
      >
        <UiIcon :name="'Folder'" :size="16" /><span>{{ d.name }}</span
        ><span class="tree-count">{{
          visibleEmployees.filter((e) => e.employments.some((j) => j.dept === d.id)).length
        }}</span>
      </button>
    </aside>
    <section class="roster-content">
      <div class="table-toolbar">
        <label class="search-field"
          ><UiIcon name="Search" :size="17" /><input
            v-model="query"
            aria-label="搜索员工"
            placeholder="搜索姓名、员工编号"
            @input="currentPage = 1" /></label
        ><select v-model="state" aria-label="员工状态" @change="currentPage = 1">
          <option value="all">全部员工状态</option>
          <option value="active">身份有效</option>
          <option value="inactive">已停用</option></select
        ><span class="toolbar-spacer" /><span class="muted small"
          >{{ filtered.length }} 名员工</span
        >
      </div>
      <div v-if="selected.length" class="selection-bar">
        <span>已选择 {{ selected.length }} 名员工</span
        ><button @click="open('assign')">批量分配角色</button
        ><button class="muted" @click="selected = []">取消选择</button>
      </div>
      <p class="mobile-table-hint">左右滑动表格，查看角色、状态与操作</p>
      <div class="table-scroll">
        <table class="employee-table">
          <thead>
            <tr>
              <th class="checkbox-cell">
                <input
                  type="checkbox"
                  aria-label="选择当前页身份有效员工"
                  :checked="
                    rows.filter((e) => e.active).length > 0 &&
                    rows.filter((e) => e.active).every((e) => selected.includes(e.id))
                  "
                  @change="toggleAll(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th>员工</th>
              <th>部门 / 岗位</th>
              <th>已获角色</th>
              <th>状态</th>
              <th>最近变更</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in rows" :key="e.id">
              <td class="checkbox-cell">
                <input
                  v-model="selected"
                  type="checkbox"
                  :value="e.id"
                  :disabled="!e.active"
                  :aria-label="`选择${e.name}`"
                />
              </td>
              <td>
                <button
                  class="person-cell"
                  @click="
                    detail = e;
                    detailTab = '来源授权';
                  "
                >
                  <span class="avatar" :class="e.color">{{ e.initials }}</span
                  ><span
                    ><strong>{{ e.name }}</strong
                    ><small>{{ e.id }}</small></span
                  >
                </button>
              </td>
              <td>
                <strong class="table-text"
                  >{{
                    deptName(e.employments.find((j) => allowedDepts.includes(j.dept))?.dept ?? '')
                  }}<span
                    v-if="
                      e.employments.filter((j) => allowedDepts.includes(j.dept) && j.active)
                        .length > 1
                    "
                    class="inline-note"
                    >兼岗</span
                  ></strong
                ><small>{{
                  e.employments.find((j) => allowedDepts.includes(j.dept))?.position
                }}</small>
              </td>
              <td>
                <div class="role-tags">
                  <span v-for="name in roleNames(e.id).slice(0, 1)" :key="name">{{ name }}</span
                  ><span v-if="roleNames(e.id).length > 1" class="count-tag"
                    >+{{ roleNames(e.id).length - 1 }}</span
                  ><span v-if="!roleNames(e.id).length" class="no-role">{{
                    e.active ? '待分配' : '授权已失效'
                  }}</span>
                </div>
                <small v-if="activeGrants(e.id).length">{{ employeeSystems(e.id) }}</small>
              </td>
              <td><StatusBadge :value="e.active ? '身份有效' : '已停用'" dot /></td>
              <td class="muted small nowrap">{{ e.updated }}</td>
              <td class="align-right">
                <button
                  class="text-button"
                  @click="
                    detail = e;
                    detailTab = '来源授权';
                  "
                >
                  管理<UiIcon name="ChevronRight" :size="14" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!rows.length" class="empty-inline">
        <UiIcon name="Search" :size="30" /><strong>没有匹配的员工</strong>
        <p>试试其他姓名，或调整部门与状态筛选。</p>
        <button
          class="button"
          @click="
            query = '';
            state = 'all';
            department = 'all';
            currentPage = 1;
          "
        >
          清除筛选
        </button>
      </div>
      <UiPagination :total="filtered.length" v-model:page="currentPage" v-model:size="pageSize" />
    </section>
  </div>

  <UiDialog
    :open="Boolean(detail)"
    drawer
    :title="detail?.name ?? ''"
    :subtitle="`${detail?.id ?? ''} · 员工授权详情`"
    @close="detail = undefined"
  >
    <template v-if="detail"
      ><div class="person-overview">
        <span class="avatar large" :class="detail.color">{{ detail.initials }}</span>
        <div>
          <h3>{{ detail.name }}</h3>
          <p>
            {{
              detail.employments
                .filter((j) => allowedDepts.includes(j.dept))
                .map((j) => deptName(j.dept))
                .join(' / ')
            }}
          </p>
        </div>
        <StatusBadge :value="detail.active ? '身份有效' : '已停用'" dot />
      </div>
      <div class="tabs">
        <button
          v-for="tab in ['来源授权', '任职信息', '权限预览']"
          :key="tab"
          :class="{ active: detailTab === tab }"
          @click="detailTab = tab"
        >
          {{ tab }}
        </button>
      </div>
      <div v-if="detailTab === '来源授权'" class="stack">
        <div class="info-box">
          <UiIcon name="Info" />
          <p>同一个角色可由不同部门独立授予。关闭一个来源，其他有效来源继续保留。</p>
        </div>
        <article v-for="g in visibleGrants(detail.id)" :key="g.id" class="source-card">
          <div class="flex items-center justify-between gap-3">
            <strong>{{ roleById(g.role).name }}</strong
            ><StatusBadge :value="g.state" />
          </div>
          <p>
            {{ deptName(g.dept)
            }}<span class="muted"> / {{ systemName(roleById(g.role).system) }}</span>
          </p>
          <div class="source-card-footer">
            <span>{{ g.date }} 授予</span
            ><button
              v-if="g.state === '有效'"
              class="text-button"
              @click="
                targetGrant = g;
                open('revoke');
              "
            >
              申请撤销</button
            ><span v-else>已失效，无需再次撤销</span>
          </div>
        </article>
        <div v-if="!visibleGrants(detail.id).length" class="empty-inline">
          <UiIcon name="Shield" :size="28" /><strong>当前范围没有角色来源</strong>
          <p>先确认任职，再申请分配适用角色。</p>
        </div>
      </div>
      <div v-else-if="detailTab === '任职信息'" class="stack">
        <article v-for="j in detailSources" :key="j.dept" class="source-card">
          <div class="flex justify-between">
            <strong>{{ deptName(j.dept) }}</strong
            ><StatusBadge :value="j.active ? '有效' : '已结束'" />
          </div>
          <p>{{ j.position }}</p>
          <small>任职资料由总管理员维护；岗位不自动绑定角色。</small>
        </article>
      </div>
      <div v-else class="stack">
        <article v-for="s in allowedSystems" :key="s" class="source-card">
          <div class="flex justify-between">
            <strong>{{ systemName(s) }}</strong
            ><StatusBadge
              :value="
                effectivePermissions(detail.id, companyId, s).canAccess ? '允许进入' : '不可进入'
              "
            />
          </div>
          <p>{{ employeeOperations(detail.id, s) }}</p>
        </article>
      </div>
      <div class="section-divider" />
      <div class="stack">
        <button
          class="button primary full"
          :disabled="!detail.active"
          @click="open('assign', detail)"
        >
          <UiIcon name="Plus" :size="16" />申请分配角色</button
        ><button class="button full danger-outline" @click="open('close')">
          <UiIcon name="Lock" :size="16" />关闭原部门权限</button
        ><button v-if="isGlobal" class="text-button danger justify-center" @click="open('global')">
          {{ detail.active ? '全局停用员工' : '恢复员工身份' }}
        </button>
        <p class="small muted text-center">
          {{
            isGlobal ? '全局停用影响所有来源，恢复不会自动复权' : '关闭部门来源不会停用员工全局身份'
          }}
        </p>
      </div>
    </template>
  </UiDialog>

  <UiDialog
    :open="dialog === 'assign'"
    class="assignment-dialog"
    title="申请分配角色"
    subtitle="新增授权经 OA 批准并执行后生效"
    wide
    @close="dialog = ''"
    ><div class="stack">
      <label class="field"
        ><span
          >选择员工 <small>已选 {{ selected.length }} 人</small></span
        ><select
          multiple
          size="4"
          v-model="selected"
          :disabled="assignmentPreview"
          aria-label="选择分配员工"
        >
          <option v-for="e in visibleEmployees.filter((e) => e.active)" :key="e.id" :value="e.id">
            {{ e.name }} / {{ e.id }}
          </option></select
        ><small>可按住 Ctrl 多选；每名员工须有本次来源部门的有效任职。</small></label
      >
      <div v-if="!assignmentPreview" class="form-grid">
        <label v-if="selected.length <= 1" class="field"
          ><span>实际来源部门</span
          ><select v-model="source">
            <option v-for="d in scopedDepartments" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select></label
        ><label class="field"
          ><span>业务系统</span
          ><select v-model="assignSystem" @change="roleIds = []">
            <option v-for="s in allowedSystems" :key="s" :value="s">{{ systemName(s) }}</option>
          </select></label
        >
      </div>
      <div v-if="selected.length > 1 && !assignmentPreview" class="table-scroll">
        <table class="mock-pair-table">
          <thead>
            <tr>
              <th>员工</th>
              <th>本次授权来源</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="id in selected" :key="id">
              <td>{{ employeeName(id) }}</td>
              <td>
                <select v-model="employeeSources[id]" :aria-label="`${employeeName(id)}的授权来源`">
                  <option
                    v-for="j in data.employees
                      .find((e) => e.id === id)
                      ?.employments.filter((j) => j.active && allowedDepts.includes(j.dept))"
                    :key="j.dept"
                    :value="j.dept"
                  >
                    {{ deptName(j.dept) }}
                  </option>
                </select>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!assignmentPreview" class="field">
        <span>选择角色</span
        ><label v-for="r in eligibleRoles" :key="r.id" class="choice-row"
          ><input v-model="roleIds" type="checkbox" :value="r.id" :disabled="!roleApplicable(r)" />
          <div>
            <strong>{{ r.name }}</strong
            ><small>{{
              !selected.length
                ? '请先选择员工'
                : !roleApplicable(r)
                  ? '部分所选员工不满足本部门有效任职条件'
                  : r.dept
                    ? `要求在${deptName(r.dept)}部门有效任职`
                    : '公司级共享角色 · 可申请分配'
            }}</small>
          </div>
          <span class="muted small">{{ r.permissions.length }} 项权限</span></label
        >
      </div>
      <label v-if="!assignmentPreview" class="field"
        ><span>申请原因 <i>*</i></span
        ><textarea v-model="reason" rows="3" placeholder="说明工作职责与本次授权需要" />
      </label>
      <p v-if="assignmentError" class="field-error" role="alert">{{ assignmentError }}</p>
      <div v-if="assignmentPreview" class="table-scroll">
        <table class="mock-pair-table">
          <thead>
            <tr>
              <th>员工</th>
              <th>角色</th>
              <th>来源部门</th>
              <th>适用任职</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in assignmentPairs" :key="`${p.employee}/${p.role}/${p.dept}`">
              <td>{{ employeeName(p.employee) }}</td>
              <td>{{ roleById(p.role).name }}</td>
              <td>{{ deptName(p.dept) }}</td>
              <td>
                {{
                  data.employees
                    .find((e) => e.id === p.employee)
                    ?.employments.find((j) => j.dept === p.dept && j.active)?.position
                }}
              </td>
            </tr>
          </tbody>
        </table>
        <p class="readonly-note mt-4">
          原因：{{ reason }}。相同来源已有有效关系会标记无变化并排除；提交不会改变当前权限。
        </p>
      </div>
      <p class="small muted">
        来源部门、员工与角色清单提交后固定。相同来源的已有有效授权作为无变化项排除。
      </p>
    </div>
    <template #footer
      ><button class="button" @click="dialog = ''">取消</button
      ><button v-if="assignmentPreview" class="button" @click="assignmentPreview = false">
        返回修改</button
      ><button v-if="!assignmentPreview" class="button primary" @click="previewAssignment">
        预览分配</button
      ><button v-else class="button primary" @click="assign">
        <UiIcon name="Send" :size="16" />保存并等待 OA 投递
      </button></template
    ></UiDialog
  >

  <UiDialog
    :open="['close', 'revoke', 'global'].includes(dialog)"
    :title="
      dialog === 'close'
        ? '关闭原部门权限'
        : dialog === 'revoke'
          ? '申请撤销指定角色'
          : detail?.active
            ? '全局停用员工'
            : '恢复员工身份'
    "
    :subtitle="detail ? `${detail.name} / ${detail.id}` : ''"
    @close="dialog = ''"
    ><div class="stack">
      <div class="warning-box">
        <UiIcon name="TriangleAlert" />
        <p>
          {{
            dialog === 'close'
              ? '直接生效，不等待 OA。只关闭指定部门和获准系统的授权来源。'
              : dialog === 'revoke'
                ? '一般撤销经 OA。提交后当前角色仍有效，直到批准并执行。'
                : detail?.active
                  ? '将使全部公司、部门的业务来源及管理任命失效。'
                  : '只恢复员工身份，旧授权与管理任命不会恢复。'
          }}
        </p>
      </div>
      <p v-if="dialog === 'close'" class="readonly-note">
        本来源涉及 {{ pendingSourceCount }} 项未结束分配申请；关闭后这些旧申请整批失效。
      </p>
      <template v-if="!preview"
        ><template v-if="dialog === 'close'"
          ><label class="field"
            ><span>原授权来源部门</span
            ><select v-model="source">
              <option v-for="j in detailSources" :key="j.dept" :value="j.dept">
                {{ deptName(j.dept) }}
              </option>
            </select></label
          >
          <div class="field">
            <span>本次关闭系统</span
            ><label v-for="s in allowedSystems" :key="s" class="check-line"
              ><input v-model="systems" type="checkbox" :value="s" />{{ systemName(s) }}</label
            >
          </div></template
        >
        <div v-if="dialog === 'revoke' && targetGrant" class="source-card">
          <strong>{{ roleById(targetGrant.role).name }}</strong>
          <p>
            {{ deptName(targetGrant.dept) }} / {{ systemName(roleById(targetGrant.role).system) }}
          </p>
        </div>
        <label class="field"
          ><span>办理原因 <i>*</i></span
          ><textarea
            v-model="reason"
            rows="3"
            placeholder="例如调离本部门、职责调整或离职"
          /></label></template
      ><template v-else
        ><h3>确认本次影响</h3>
        <div class="impact-row" v-if="dialog === 'close'">
          <span>本次来源</span
          ><strong>{{ deptName(source) }} / {{ systems.map(systemName).join('、') }}</strong>
        </div>
        <div class="impact-row">
          <span>受影响授权</span
          ><strong>{{
            dialog === 'close'
              ? `${impacted.length} 条有效来源`
              : dialog === 'revoke'
                ? '指定的一条角色关联'
                : '员工全局身份'
          }}</strong>
        </div>
        <p class="small muted">
          {{
            dialog === 'close'
              ? '即使当前没有有效关联，也关闭该来源周期，使旧待执行分配不能在以后生效。其他独立来源保留。'
              : dialog === 'revoke'
                ? '仅提交固定撤销申请，不立即改变权限。'
                : '全局状态与部门来源关闭是不同操作。'
          }}
        </p>
        <label class="check-line"
          ><input v-model="acknowledged" type="checkbox" />我已确认影响范围与办理原因</label
        ></template
      >
    </div>
    <template #footer
      ><button class="button" @click="preview ? (preview = false) : (dialog = '')">
        {{ preview ? '上一步' : '取消' }}</button
      ><button v-if="!preview" class="button primary" @click="showPreview">
        预览影响<UiIcon name="ArrowRight" :size="16" /></button
      ><button
        v-else
        class="button"
        :class="dialog === 'revoke' ? 'primary' : 'danger-fill'"
        @click="finish"
      >
        {{
          dialog === 'close'
            ? '确认关闭来源'
            : dialog === 'revoke'
              ? '提交撤销申请'
              : detail?.active
                ? '确认全局停用'
                : '确认恢复身份'
        }}
      </button></template
    ></UiDialog
  >
</template>
