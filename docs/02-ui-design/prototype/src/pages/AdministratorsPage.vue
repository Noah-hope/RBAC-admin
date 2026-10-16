<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { companies, departments, type Account, type Management } from '../data';
import { audit, data, deptName, employeeName, systemName, toast } from '../store';
import UiIcon from '../components/UiIcon.vue';
import { reportError } from '../store';
import UiPagination from '../components/UiPagination.vue';
import { usePagination } from '../composables/usePagination';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
const tab = ref('后台账号');
const query = ref('');
const modal = ref<'create' | 'toggle' | 'password' | 'scope' | ''>('');
const target = ref<Account>();
const editing = ref<Management>();
const employee = ref('');
const login = ref('');
const password = ref('');
const confirm = ref('');
const reason = ref('');
const company = ref('municipal');
const dept = ref('team-one');
const systems = ref<string[]>([]);
const members = ref<string[]>([]);
const filtered = computed(() =>
  data.accounts.filter((a) =>
    `${employeeName(a.employee)} ${a.login}`.toLowerCase().includes(query.value.toLowerCase()),
  ),
);
const candidates = computed(() =>
  data.accounts.filter(
    (a) =>
      !a.global &&
      a.active &&
      data.employees.some(
        (e) =>
          e.id === a.employee &&
          e.active &&
          e.employments.some(
            (j) =>
              j.active &&
              j.dept === dept.value &&
              departments.some((d) => d.id === j.dept && d.company === company.value),
          ),
      ),
  ),
);
const unlinkedEmployees = computed(() =>
  data.employees.filter((e) => e.active && !data.accounts.some((a) => a.employee === e.id)),
);
function memberNames(m: Management) {
  return (
    m.accounts
      .map((id) => employeeName(data.accounts.find((a) => a.id === id)?.employee ?? ''))
      .join('、') || '当前暂无管理员'
  );
}
function open(kind: typeof modal.value, a?: Account) {
  modal.value = kind;
  target.value = a;
  password.value = '';
  confirm.value = '';
  reason.value = '';
  employee.value = '';
  login.value = '';
}
function saveAccount() {
  if (!employee.value || !login.value.trim() || !reason.value.trim()) {
    reportError('请选择员工并填写登录名、原因');
    return;
  }
  if (data.accounts.some((a) => a.employee === employee.value || a.login === login.value.trim())) {
    toast('该员工或登录名已有账号，请复用已有账号');
    return;
  }
  if (password.value.length < 8 || password.value !== confirm.value) {
    reportError('临时密码至少 8 位，且两次输入须一致');
    return;
  }
  data.accounts.push({
    id: `A-${++data.serial}`,
    employee: employee.value,
    login: login.value.trim(),
    active: true,
    temporary: true,
  });
  audit(
    '建立后台账号',
    employeeName(employee.value),
    '',
    '',
    '待任命',
    reason.value,
    '仅保存原型账号元数据；临时密码未保存。创建账号不授予任命或业务角色。',
    undefined,
    true,
  );
  password.value = '';
  confirm.value = '';
  modal.value = '';
  toast('演示账号已创建，尚需单独配置管理任命');
}
function saveAccountAction() {
  if (!target.value || target.value.global) {
    toast('不能通过此入口停用或重置当前总管理员');
    return;
  }
  if (!reason.value.trim()) {
    reportError('请填写办理原因');
    return;
  }
  if (modal.value === 'password') {
    if (password.value.length < 8 || password.value !== confirm.value) {
      reportError('临时密码至少 8 位，且两次输入须一致');
      return;
    }
    target.value.temporary = true;
  } else target.value.active = !target.value.active;
  audit(
    modal.value === 'password' ? '重置后台密码' : '变更账号状态',
    target.value.login,
    '',
    '',
    '已生效',
    reason.value,
    '旧会话应失效；不授予任命、不修改业务角色。原型不存储密码或真实会话。',
    undefined,
    true,
  );
  password.value = '';
  confirm.value = '';
  modal.value = '';
  toast('账号状态已更新，业务角色保持独立');
}
function openScope(m?: Management) {
  editing.value = m;
  company.value = m?.company ?? 'municipal';
  dept.value = m?.dept ?? 'team-one';
  systems.value = [...(m?.systems ?? [])];
  members.value = [...(m?.accounts ?? [])];
  reason.value = '';
  modal.value = 'scope';
}
function saveScope() {
  if (!reason.value.trim()) {
    reportError('请填写配置原因');
    return;
  }
  if (members.value.some((id) => !candidates.value.some((a) => a.id === id))) {
    toast('所选账号不满足当前部门范围的有效任职条件，请重新选择');
    return;
  }
  if (editing.value)
    Object.assign(editing.value, {
      company: company.value,
      dept: dept.value,
      systems: [...systems.value],
      accounts: [...members.value],
      version: editing.value.version + 1,
    });
  else
    data.management.push({
      id: `M-${++data.serial}`,
      company: company.value,
      dept: dept.value,
      systems: [...systems.value],
      accounts: [...members.value],
      version: 1,
    });
  audit(
    '配置管理范围',
    deptName(dept.value),
    dept.value,
    '',
    '已生效',
    reason.value,
    '仅管理指定公司与部门；同范围协作，不自动授予业务角色。',
    undefined,
    true,
  );
  modal.value = '';
  toast('管理范围已保存，后续操作按当前范围展示');
}
const { rows, currentPage, pageSize } = usePagination(filtered);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>管理员与范围</h1>
    </div>
    <button class="button primary" @click="tab === '后台账号' ? open('create') : openScope()">
      <UiIcon name="Plus" :size="17" />{{ tab === '后台账号' ? '建立后台账号' : '新增管理范围' }}
    </button>
  </div>
  <div class="panel">
    <div class="panel-tabs">
      <div class="tabs">
        <button
          v-for="t in ['后台账号', '管理范围']"
          :key="t"
          :class="{ active: tab === t }"
          @click="tab = t"
        >
          {{ t }}
        </button>
      </div>
    </div>
    <template v-if="tab === '后台账号'"
      ><div class="table-toolbar">
        <label class="search-field"
          ><UiIcon name="Search" :size="17" /><input
            v-model="query"
            placeholder="搜索员工、登录名"
            aria-label="搜索后台账号" /></label
        ><span class="toolbar-spacer" /><span class="small muted">普通员工不登录本平台</span>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>后台账号</th>
              <th>关联员工</th>
              <th>管理资格</th>
              <th>账号状态</th>
              <th>密码状态</th>
              <th class="align-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in rows" :key="a.id">
              <td>
                <strong>{{ a.login }}</strong
                ><small>{{ a.id }}</small>
              </td>
              <td>{{ a.employee ? employeeName(a.employee) : '部署初始化身份' }}</td>
              <td>
                {{
                  a.global
                    ? '总管理员'
                    : data.management.some((m) => m.accounts.includes(a.id))
                      ? '权限管理员'
                      : '未任命'
                }}
              </td>
              <td><StatusBadge :value="a.active ? '已启用' : '已停用'" dot /></td>
              <td class="muted small">{{ a.temporary ? '下次登录需修改' : '正常' }}</td>
              <td class="align-right">
                <div v-if="!a.global" class="flex gap-4 justify-end">
                  <button class="text-button" @click="open('password', a)">重置密码</button
                  ><button
                    class="text-button"
                    :class="{ danger: a.active }"
                    @click="open('toggle', a)"
                  >
                    {{ a.active ? '停用' : '启用' }}
                  </button>
                </div>
                <span v-else class="muted small"
                  ><UiIcon name="Lock" :size="13" />当前身份受保护</span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <UiPagination :total="filtered.length" v-model:page="currentPage" v-model:size="pageSize"
    /></template>
    <div v-else class="scope-card-grid">
      <article v-for="m in data.management" :key="m.id" class="management-card">
        <div class="flex items-center justify-between">
          <span class="object-icon violet"><UiIcon name="ShieldCheck" /></span
          ><button class="text-button" @click="openScope(m)">
            编辑范围<UiIcon name="ChevronRight" :size="14" />
          </button>
        </div>
        <h3>{{ deptName(m.dept) }}</h3>
        <p>{{ companies.find((c) => c.id === m.company)?.name }}</p>
        <div class="flex gap-2 flex-wrap my-4">
          <span v-for="s in m.systems" :key="s" class="small-tag">{{ systemName(s) }}</span
          ><span v-if="!m.systems.length" class="muted small">未配置可管理系统</span>
        </div>
        <div class="management-members">
          <UiIcon name="Users" :size="16" /><span>{{ memberNames(m) }}</span
          ><small>{{ m.id }} / v{{ m.version }}</small>
        </div>
      </article>
    </div>
  </div>
  <UiDialog
    :open="['create', 'password', 'toggle'].includes(modal)"
    :title="
      modal === 'create'
        ? '建立后台账号'
        : modal === 'password'
          ? '重置临时密码'
          : `${target?.active ? '停用' : '启用'}后台账号`
    "
    :subtitle="target?.login"
    @close="
      modal = '';
      password = '';
      confirm = '';
    "
    ><div class="stack">
      <template v-if="modal === 'create'"
        ><label class="field"
          ><span>关联有效员工</span
          ><select v-model="employee">
            <option value="" disabled>选择尚未建立后台账号的员工</option>
            <option v-for="e in unlinkedEmployees" :key="e.id" :value="e.id">
              {{ e.name }} / {{ e.id }}
            </option>
          </select></label
        ><label class="field"
          ><span>平台登录名</span
          ><input
            v-model="login"
            placeholder="例如：staff.demo"
            autocomplete="off" /></label></template
      ><template v-if="modal !== 'toggle'"
        ><div class="form-grid">
          <label class="field"
            ><span>临时密码</span
            ><input
              v-model="password"
              type="password"
              autocomplete="new-password"
              placeholder="至少 8 位" /></label
          ><label class="field"
            ><span>确认临时密码</span
            ><input v-model="confirm" type="password" autocomplete="new-password"
          /></label></div
      ></template>
      <div class="warning-box">
        <UiIcon name="Info" />
        <p>
          {{
            modal === 'create'
              ? '创建账号不授予管理资格，需单独任命。原型只保存账号元数据，不保存所填密码。'
              : modal === 'password'
                ? '仅变更后台凭据；不启用账号、不恢复任命、不修改业务授权。'
                : '账号状态与员工全局身份、业务角色分别判断；启用不会复活旧会话。'
          }}
        </p>
      </div>
      <label class="field"
        ><span>办理原因 <i>*</i></span
        ><textarea v-model="reason" rows="3" />
      </label>
    </div>
    <template #footer
      ><button
        class="button"
        @click="
          modal = '';
          password = '';
          confirm = '';
        "
      >
        取消</button
      ><button
        class="button primary"
        @click="modal === 'create' ? saveAccount() : saveAccountAction()"
      >
        {{ modal === 'create' ? '创建演示账号' : modal === 'password' ? '确认重置' : '确认变更' }}
      </button></template
    ></UiDialog
  >
  <UiDialog
    :open="modal === 'scope'"
    title="配置管理范围"
    subtitle="指定公司、指定部门与系统，同范围内协作"
    wide
    @close="modal = ''"
    ><div class="stack">
      <div class="form-grid">
        <label class="field"
          ><span>公司</span
          ><select
            v-model="company"
            @change="
              dept = departments.find((d) => d.company === company)?.id ?? '';
              members = [];
            "
          >
            <option v-for="c in companies" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select></label
        ><label class="field"
          ><span>指定部门</span
          ><select v-model="dept" aria-label="指定部门" @change="members = []">
            <option
              v-for="d in departments.filter((d) => d.company === company)"
              :key="d.id"
              :value="d.id"
            >
              {{ d.name }}
            </option></select
          ><small>仅管理所选部门；管理其他部门需另行任命。</small></label
        >
      </div>
      <div class="field">
        <span>可管理系统</span
        ><label v-for="s in data.systems" :key="s.id" class="check-line"
          ><input v-model="systems" type="checkbox" :value="s.id" />{{ s.name }}</label
        >
      </div>
      <div class="field">
        <span>协作管理员</span
        ><label v-for="a in candidates" :key="a.id" class="choice-row"
          ><input v-model="members" type="checkbox" :value="a.id" />
          <div>
            <strong>{{ employeeName(a.employee) }}</strong
            ><small>{{ a.login }}</small>
          </div></label
        >
        <p v-if="!candidates.length" class="muted small">
          此部门范围暂无符合任职且账号有效的候选人，可先保留空名单。
        </p>
      </div>
      <label class="field"
        ><span>配置原因 <i>*</i></span
        ><textarea v-model="reason" rows="2" />
      </label>
      <div class="info-box">
        <UiIcon name="Info" />
        <p>
          空名单或空系统允许保存，但无对应管理能力。不改变员工业务角色，原申请人离任本身不使合法申请失效。
        </p>
      </div>
    </div>
    <template #footer
      ><button class="button" @click="modal = ''">取消</button
      ><button class="button primary" @click="saveScope">确认保存范围</button></template
    ></UiDialog
  >
</template>
