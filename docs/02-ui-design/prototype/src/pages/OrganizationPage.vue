<script setup lang="ts">
import { computed, ref } from 'vue';
import { departments, type Employee } from '../data';
import {
  allowedDepts,
  allowedSystems,
  audit,
  closeSource,
  companyId,
  companyName,
  data,
  deptName,
  setEmployeeActive,
  toast,
  visibleEmployees,
} from '../store';
import UiIcon from '../components/UiIcon.vue';
import { reportError } from '../store';
import UiDialog from '../components/UiDialog.vue';
import OrganizationPreflight from '../components/OrganizationPreflight.vue';
const organizationOpen = ref(false);
const importing = ref(false);
const step = ref(1);
const filename = ref('');
const text = ref('');
const confirmed = ref(false);
const committed = ref(false);
const reason = ref('');
const errors = ref<string[]>([]);
type Row = {
  id: string;
  name: string;
  company: string;
  dept: string;
  position: string;
  action: string;
};
const batch = ref<Row[]>([]);
const orgs = computed(() => departments.filter((d) => allowedDepts.value.includes(d.id)));
const header = '员工编号,姓名,公司编码,部门编码,岗位,动作';
const demo = () =>
  `${header}\nEMP-1011,叶初晴,municipal,team-one,项目助理,新增任职\nEMP-1005,周晚宁,municipal,team-one,资料专员,结束任职\nEMP-1005,周晚宁,municipal,team-two,资料专员,新增任职`;
function downloadTemplate() {
  const blob = new Blob(['\ufeff' + demo()], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = '员工变更_演示模板.csv';
  a.click();
  URL.revokeObjectURL(url);
}
async function loadFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.csv') || file.size > 128 * 1024) {
    reportError('请选择 128KB 以内、按演示模板填写的 CSV 文件');
    return;
  }
  filename.value = file.name;
  text.value = await file.text();
  errors.value = [];
}
function preflight() {
  errors.value = [];
  const lines = text.value
    .replace(/^\uFEFF/, '')
    .trim()
    .split(/\r?\n/);
  if (lines[0] !== header || lines.length < 2) {
    errors.value = ['文件表头不符合演示模板，或没有变更明细'];
    return;
  }
  batch.value = [];
  const seen = new Set<string>();
  const names = new Map<string, string>();
  lines.slice(1).forEach((line, i) => {
    const fields = line.split(',').map((v) => v.trim());
    const [id, name, company, dept, position, action] = fields;
    if (fields.length !== 6 || fields.some((f) => !f)) {
      errors.value.push(`第 ${i + 2} 行：须完整填写六个字段，不支持字段内逗号`);
      return;
    }
    const d = departments.find((d) => d.id === dept && d.company === company);
    const e = data.employees.find((e) => e.id === id);
    const key = `${id}/${dept}/${action}`;
    if (!d || company !== companyId.value)
      errors.value.push(`第 ${i + 2} 行：未知部门或不是当前公司`);
    if (!['新增任职', '结束任职', '停用', '恢复'].includes(action))
      errors.value.push(`第 ${i + 2} 行：动作无法识别`);
    if (e && e.name !== name) errors.value.push(`第 ${i + 2} 行：员工编号与姓名冲突`);
    if (!e && action !== '新增任职') errors.value.push(`第 ${i + 2} 行：员工不存在`);
    if (action === '结束任职' && !e?.employments.some((j) => j.dept === dept && j.active))
      errors.value.push(`第 ${i + 2} 行：不存在此部门有效任职`);
    if (seen.has(key)) errors.value.push(`第 ${i + 2} 行：重复明细`);
    if (names.has(id) && names.get(id) !== name)
      errors.value.push(`第 ${i + 2} 行：同一编号的员工属性冲突`);
    names.set(id, name);
    if (
      (action === '停用' && seen.has(`${id}/${dept}/恢复`)) ||
      (action === '恢复' && seen.has(`${id}/${dept}/停用`))
    )
      errors.value.push(`第 ${i + 2} 行：同一员工启停动作冲突`);
    seen.add(key);
    batch.value.push({ id, name, company, dept, position, action });
  });
  if (!errors.value.length) step.value = 2;
}
function apply() {
  if (!confirmed.value || !reason.value.trim()) {
    reportError('请填写导入原因，并确认预检影响');
    return;
  }
  if (committed.value) {
    toast('此演示批次已执行，返回原结果');
    return;
  }
  // Apply representative, prevalidated fixtures in memory; no file is sent to a server.
  for (const row of batch.value) {
    let e = data.employees.find((e) => e.id === row.id);
    if (!e) {
      e = {
        id: row.id,
        name: row.name,
        initials: row.name.slice(-2),
        color: 'violet',
        active: true,
        employments: [],
        updated: '刚刚',
      } as Employee;
      data.employees.push(e);
    }
    if (row.action === '新增任职' && !e.employments.some((j) => j.dept === row.dept && j.active))
      e.employments.push({ dept: row.dept, position: row.position, active: true });
    if (row.action === '结束任职') {
      closeSource(e.id, row.dept, [...allowedSystems.value], reason.value);
      e.employments.filter((j) => j.dept === row.dept).forEach((j) => (j.active = false));
    }
    if (row.action === '停用' || row.action === '恢复')
      setEmployeeActive(e, row.action === '恢复', reason.value);
    e.updated = '刚刚';
  }
  audit(
    '员工模拟批次完成',
    filename.value,
    '',
    '',
    '已生效',
    reason.value,
    `${batch.value.length} 条演示明细；没有自动分配角色、建立账号或任命。`,
    undefined,
    true,
  );
  committed.value = true;
  step.value = 3;
  toast('模拟批次已应用，新任职未自动获得授权');
}
function open() {
  importing.value = true;
  step.value = 1;
  filename.value = '';
  text.value = '';
  batch.value = [];
  confirmed.value = false;
  committed.value = false;
  reason.value = '';
  errors.value = [];
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>员工组织</h1>
      <p>维护人员与任职，岗位和组织本身不自动授予权限。</p>
    </div>
    <div class="flex gap-2">
      <button class="button" @click="downloadTemplate">
        <UiIcon name="Download" :size="16" />演示模板</button
      ><button class="button" @click="organizationOpen = true">导入组织资料</button
      ><button class="button primary" @click="open">
        <UiIcon name="Upload" :size="16" />导入员工变更
      </button>
    </div>
  </div>
  <div class="info-box mb-6">
    <UiIcon name="Building2" />
    <p>
      组织用于管理边界与任职，不设计公司、部门启停。员工离岗可直接关闭来源，员工全局停用由总管理员办理。
    </p>
  </div>
  <div class="panel">
    <div class="organization-header">
      <span class="object-icon violet"><UiIcon name="Building2" :size="24" /></span>
      <div>
        <h2>{{ companyName }}</h2>
        <p>{{ orgs.length }} 个平级部门 / {{ visibleEmployees.length }} 名示例员工</p>
      </div>
      <span class="subtle-label ml-auto">公司 / 部门 / 岗位</span>
    </div>
    <div class="org-list">
      <article v-for="d in orgs" :key="d.id">
        <UiIcon name="Folder" :size="20" />
        <div>
          <strong>{{ d.name }}</strong
          ><small>{{ d.id }} / {{ companyName }}</small>
        </div>
        <span class="muted small ml-auto"
          >{{
            data.employees.filter((e) => e.employments.some((j) => j.dept === d.id && j.active))
              .length
          }}
          名有效任职员工</span
        >
      </article>
    </div>
  </div>
  <UiDialog
    :open="importing"
    title="导入员工变更"
    subtitle="文件仅在浏览器读取；不连接真实 HR 或数据库"
    wide
    @close="importing = false"
    ><div class="stack">
      <div class="step-track">
        <span
          v-for="(title, i) in ['选择文件', '预检与影响', '执行报告']"
          :key="title"
          :class="{ current: step === i + 1, complete: step > i + 1 }"
          ><b>{{ i + 1 }}</b
          >{{ title }}</span
        >
      </div>
      <template v-if="step === 1"
        ><label class="upload-zone"
          ><UiIcon name="Upload" :size="32" /><strong>{{
            filename || '选择员工变更 CSV 文件'
          }}</strong
          ><span>当前公司代表性模板，最大 128KB</span
          ><input
            type="file"
            accept=".csv,text/csv"
            aria-label="选择员工变更文件"
            @change="loadFile"
        /></label>
        <div class="flex gap-3">
          <button
            class="text-button"
            @click="
              text = demo();
              filename = '员工变更_演示批次.csv';
              errors = [];
            "
          >
            载入演示批次</button
          ><button class="text-button" @click="downloadTemplate">下载模板</button>
        </div>
        <div v-if="errors.length" class="error-box">
          <UiIcon name="TriangleAlert" />
          <div>
            <strong>预检未通过，未写入数据</strong>
            <p v-for="error in errors" :key="error">{{ error }}</p>
          </div>
        </div>
        <div class="info-box">
          <UiIcon name="Info" />
          <p>
            明确填写新增任职、结束任职、停用或恢复。文件缺行不视为离职，不建立后台账号或自动授予角色。
          </p>
        </div></template
      ><template v-else-if="step === 2"
        ><div class="success-inline">
          <UiIcon name="CircleCheck" />预检通过 · {{ batch.length }} 条模拟明细
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>员工</th>
                <th>目标部门</th>
                <th>动作</th>
                <th>影响</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in batch" :key="i">
                <td>
                  {{ row.name }}<small>{{ row.id }}</small>
                </td>
                <td>{{ deptName(row.dept) }}</td>
                <td>{{ row.action }}</td>
                <td class="small">
                  {{
                    row.action === '结束任职'
                      ? '原来源失效，其他来源保留'
                      : row.action === '新增任职'
                        ? '仅维护任职，不自动授权'
                        : row.action === '停用'
                          ? '全部来源和任命失效'
                          : '恢复身份，不复权'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <label class="field"
          ><span>导入原因 <i>*</i></span
          ><textarea v-model="reason" rows="2" placeholder="说明本批人员或任职变更依据" /></label
        ><label class="check-line"
          ><input v-model="confirmed" type="checkbox" />已核对变更影响，确认应用此模拟批次</label
        ></template
      ><template v-else
        ><div class="result-success">
          <span><UiIcon name="CheckCheck" :size="36" /></span>
          <h3>模拟批次已完成</h3>
          <p>{{ batch.length }} 条明细已应用到本次原型内存</p>
        </div>
        <div class="info-box">
          <UiIcon name="Info" />
          <p>
            新增任职未自动获得任何角色。请到员工授权重新申请；已关闭来源不会随调岗或身份恢复自动复活。
          </p>
        </div></template
      >
    </div>
    <template #footer
      ><button class="button" @click="step === 2 ? (step = 1) : (importing = false)">
        {{ step === 2 ? '上一步' : '关闭' }}</button
      ><button v-if="step === 1" class="button primary" :disabled="!text" @click="preflight">
        开始预检</button
      ><button v-if="step === 2" class="button primary" @click="apply">确认应用模拟批次</button
      ><button v-if="step === 3" class="button primary" @click="importing = false">
        完成
      </button></template
    ></UiDialog
  >
  <OrganizationPreflight :open="organizationOpen" @close="organizationOpen = false" />
</template>
