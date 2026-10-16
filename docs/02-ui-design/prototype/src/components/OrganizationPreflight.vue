<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { departments } from '../data';
import { audit, companyId, data, toast } from '../store';
import UiDialog from './UiDialog.vue';
import UiIcon from './UiIcon.vue';
defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();
const type = ref('organization');
const scenario = ref('rename');
const target = ref('engineering');
const value = ref('工程管理中心');
const sourceField = ref('部门编码');
const error = ref('');
const checked = ref(false);
const confirmed = ref(false);
const version = ref(0);
const reason = ref('');
const options = computed(() => departments.filter((d) => d.company === companyId.value));
const jobs = computed(() => [
  ...new Set(
    data.employees.flatMap((e) =>
      e.employments.filter((j) => j.dept === target.value).map((j) => j.position),
    ),
  ),
]);
const oldJob = ref('');
watch([type, scenario, target, value, sourceField, oldJob], () => {
  checked.value = false;
  error.value = '';
  confirmed.value = false;
});
watch(
  options,
  (rows) => {
    if (!rows.some((d) => d.id === target.value)) target.value = rows[0]?.id ?? '';
  },
  { immediate: true },
);
function preflight() {
  error.value = '';
  if (!sourceField.value.trim() || !value.value.trim())
    error.value = '请核对来源标识映射及目标名称';
  else if (scenario.value === 'migration')
    error.value =
      '第 2 行：部门及子树已有角色、任职或授权历史引用，禁止直接跨公司迁移。请在目标公司新建组织并明确变更任职。';
  else if (scenario.value === 'duplicate')
    error.value = '第 3 行：同一稳定编码存在冲突的名称或父级，请修正后重新预检。';
  else if (type.value === 'position' && !oldJob.value) error.value = '请选择目标部门内的原岗位';
  version.value = data.organizationVersion;
  checked.value = !error.value;
  confirmed.value = false;
}
function apply() {
  if (!checked.value || !confirmed.value || !reason.value.trim()) {
    error.value = '请完成预检，填写原因并确认影响';
    return;
  }
  if (version.value !== data.organizationVersion) {
    error.value = '预检后组织资料已变化，请重新预检';
    checked.value = false;
    return;
  }
  if (type.value === 'organization') {
    const department = departments.find((d) => d.id === target.value)!;
    department.name = value.value.trim();
  } else
    data.employees.forEach((e) =>
      e.employments.forEach((j) => {
        if (j.dept === target.value && j.position === oldJob.value) j.position = value.value.trim();
      }),
    );
  data.organizationVersion++;
  audit(
    '组织资料模拟批次',
    value.value,
    target.value,
    '',
    '已生效',
    reason.value,
    '按稳定标识更新名称；未创建账号、任命或业务授权。',
    undefined,
    true,
  );
  checked.value = false;
  emit('close');
  toast('模拟批次已应用，稳定标识及既有授权保留');
}
</script>
<template>
  <UiDialog
    :open="open"
    title="组织资料预检"
    subtitle="固定演示资料，模拟映射、预检及确认；未连接 HR"
    wide
    @close="emit('close')"
  >
    <div class="stack">
      <div class="form-grid">
        <label class="field"
          ><span>资料类型</span
          ><select v-model="type">
            <option value="organization">组织资料</option>
            <option value="position">岗位资料</option>
          </select></label
        >
        <label class="field"
          ><span>演示场景</span
          ><select v-model="scenario">
            <option value="rename">按稳定标识更名</option>
            <option value="migration">已有引用的跨公司迁移</option>
            <option value="duplicate">重复编码冲突</option>
          </select></label
        >
      </div>
      <div class="info-box">
        <UiIcon name="Info" />
        <p>
          组织 → 岗位 →
          员工及任职依次预检。这里使用虚构批次演示；员工新增/结束任职、停用和恢复请使用“导入人员变更”。文件缺行不视为离职。
        </p>
      </div>
      <div class="form-grid">
        <label class="field"
          ><span>来源标识字段映射</span><input v-model="sourceField" /><small
            >来源字段 → 稳定内部编码；不按姓名或相似名称匹配。</small
          ></label
        ><label class="field"
          ><span>目标部门</span
          ><select v-model="target">
            <option v-for="d in options" :key="d.id" :value="d.id">
              {{ d.name }} / {{ d.id }}
            </option>
          </select></label
        >
      </div>
      <label v-if="type === 'position'" class="field"
        ><span>原岗位</span
        ><select v-model="oldJob">
          <option value="">选择原岗位</option>
          <option v-for="p in jobs" :key="p">{{ p }}</option>
        </select></label
      >
      <label class="field"><span>目标名称</span><input v-model="value" /></label>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <div v-if="checked" class="source-card">
        <strong>预检通过 · 计划更新 1 个{{ type === 'organization' ? '部门' : '岗位' }}</strong>
        <p>稳定标识保留；不更换角色，不自动授权。计划数量不代表已经提交。</p>
        <small>预检版本 {{ version }} · 当前版本 {{ data.organizationVersion }}</small>
      </div>
      <label class="field"><span>办理原因</span><textarea v-model="reason" /></label>
      <label class="check-line"
        ><input
          type="checkbox"
          v-model="confirmed"
          :disabled="!checked"
        />已核对映射及本批次影响</label
      >
    </div>
    <template #footer
      ><button class="button" @click="emit('close')">取消</button
      ><button class="button" @click="preflight">运行预检</button
      ><button class="button primary" :disabled="!checked || !confirmed" @click="apply">
        应用模拟批次
      </button></template
    >
  </UiDialog>
</template>
