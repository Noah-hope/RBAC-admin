<script setup lang="ts">
import { computed, ref } from 'vue';
import { actionLabels, departments, type Role } from '../data';
import {
  allowedDepts,
  allowedSystems,
  audit,
  canEditRole,
  companyId,
  data,
  deptName,
  isGlobal,
  isPending,
  page,
  permissionLabel,
  saveRoleConfig,
  systemName,
  toast,
  tryAction,
  visibleRoles,
} from '../store';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
const query = ref('');
const tab = ref('全部角色');
const system = ref('all');
const rows = computed(() =>
  visibleRoles.value.filter(
    (r) =>
      (!query.value || `${r.name} ${r.code}`.toLowerCase().includes(query.value.toLowerCase())) &&
      (system.value === 'all' || r.system === system.value) &&
      (tab.value === '全部角色' ||
        (tab.value === '公司共享'
          ? !r.dept
          : tab.value === '部门角色'
            ? Boolean(r.dept)
            : !r.active)),
  ),
);
const detail = ref<Role>();
const selectedPermissions = ref<string[]>([]);
const reason = ref('');
const preview = ref(false);
const features = computed(() => [
  ...new Set(
    data.permissions
      .filter((p) => p.system === detail.value?.system && p.action !== '进入系统')
      .map((p) => p.feature),
  ),
]);
const add = computed(() =>
  selectedPermissions.value.filter((p) => !detail.value?.permissions.includes(p)),
);
const remove = computed(
  () => detail.value?.permissions.filter((p) => !selectedPermissions.value.includes(p)) ?? [],
);
const occupied = computed(() =>
  data.changes.some((c) => c.roleId === detail.value?.id && isPending(c)),
);
const create = ref(false);
const name = ref('');
const code = ref('');
const dept = ref('team-one');
const newSystem = ref('oa');
function open(r: Role) {
  detail.value = r;
  selectedPermissions.value = [...r.permissions];
  reason.value = '';
  preview.value = false;
}
function save() {
  if (
    detail.value &&
    tryAction(() => saveRoleConfig(detail.value!, selectedPermissions.value, reason.value))
  )
    detail.value = undefined;
}
function createRole() {
  if (!allowedSystems.value.includes(newSystem.value)) {
    toast('当前范围没有可管理系统，请联系总管理员核对任命');
    return;
  }
  if (!name.value.trim() || !code.value.trim()) {
    toast('请填写角色名称与唯一编码');
    return;
  }
  if (data.roles.some((r) => r.code === code.value.trim())) {
    toast('角色编码已存在，请使用其他编码');
    return;
  }
  if (!dept.value && !isGlobal.value) {
    toast('只有总管理员可新建公司级共享角色');
    return;
  }
  const r: Role = {
    id: `R-${++data.serial}`,
    code: code.value.trim(),
    name: name.value.trim(),
    company: companyId.value,
    dept: dept.value || undefined,
    system: newSystem.value,
    active: false,
    permissions: [],
    version: 0,
    description: '新建角色草稿，尚未生效',
  };
  data.roles.unshift(r);
  audit(
    '保存角色草稿',
    r.name,
    r.dept ?? '',
    r.system,
    '待配置',
    '原型新建角色',
    '草稿不可分配，不授予任何员工权限。',
    undefined,
    !r.dept,
  );
  create.value = false;
  toast('角色草稿已保存，配置并审批执行后才可分配');
  open(r);
}
function permissionId(feature: string, action: string) {
  return (
    data.permissions.find(
      (p) => p.system === detail.value?.system && p.feature === feature && p.action === action,
    )?.id ?? ''
  );
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>角色权限</h1>
      <p>组合独立功能操作，通过角色授予员工。</p>
    </div>
    <button
      class="button primary"
      @click="
        create = true;
        name = '';
        code = '';
        dept = allowedDepts[0] ?? '';
        newSystem = allowedSystems[0] ?? '';
      "
    >
      <UiIcon name="Plus" :size="17" />新建角色
    </button>
  </div>
  <div class="info-box mb-6">
    <UiIcon name="ShieldCheck" />
    <p>
      公司级共享角色由总管理员配置；权限管理员可维护范围内部门角色，并为合格员工申请分配两类角色。
    </p>
  </div>
  <div class="panel">
    <div class="panel-tabs">
      <div class="tabs">
        <button
          v-for="t in ['全部角色', '部门角色', '公司共享', '待配置']"
          :key="t"
          :class="{ active: tab === t }"
          @click="tab = t"
        >
          {{ t === '公司共享' ? '公司级共享角色' : t }}
        </button>
      </div>
      <span class="small muted">{{ visibleRoles.length }} 个范围内角色</span>
    </div>
    <div class="table-toolbar">
      <label class="search-field"
        ><UiIcon name="Search" :size="17" /><input
          v-model="query"
          placeholder="搜索角色名称、编码"
          aria-label="搜索角色" /></label
      ><select v-model="system" aria-label="筛选角色系统">
        <option value="all">全部获准系统</option>
        <option v-for="id in allowedSystems" :key="id" :value="id">{{ systemName(id) }}</option>
      </select>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>角色名称</th>
            <th>业务系统</th>
            <th>角色归属</th>
            <th>功能配置</th>
            <th>状态</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td>
              <div class="flex items-center gap-3">
                <span class="object-icon" :class="r.dept ? 'violet' : 'blue'"
                  ><UiIcon name="ShieldCheck" :size="20"
                /></span>
                <div>
                  <strong>{{ r.name }}</strong
                  ><small>{{ r.code }}</small>
                </div>
              </div>
            </td>
            <td>{{ systemName(r.system) }}</td>
            <td>
              <span class="role-type" :class="{ shared: !r.dept }">{{
                r.dept ? deptName(r.dept) : '公司级共享角色'
              }}</span>
            </td>
            <td>
              <strong>{{ r.permissions.filter((p) => !p.endsWith(':access')).length }}</strong
              ><span class="muted small"> 项操作</span
              ><small>{{
                r.permissions.includes(`${r.system}:access`) ? '已配置系统进入' : '未配置系统进入'
              }}</small>
            </td>
            <td><StatusBadge :value="r.active ? '已生效' : '待配置'" dot /></td>
            <td class="align-right">
              <button class="text-button" @click="open(r)">
                {{ canEditRole(r) ? '配置权限' : '查看配置'
                }}<UiIcon :name="canEditRole(r) ? 'ChevronRight' : 'Lock'" :size="14" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!rows.length" class="empty-inline">
      <UiIcon name="ShieldCheck" :size="30" /><strong>没有匹配的角色</strong>
      <p>调整筛选，或新建当前范围内的部门角色。</p>
    </div>
    <div class="table-footer"><span>角色归属与员工授权来源分别记录；岗位不自动绑定角色</span></div>
  </div>
  <UiDialog
    :open="Boolean(detail)"
    :title="detail?.name ?? ''"
    :subtitle="`${detail?.code ?? ''} / ${detail ? systemName(detail.system) : ''}`"
    wide
    @close="detail = undefined"
    ><template v-if="detail"
      ><div class="stack">
        <div class="info-box" v-if="!canEditRole(detail)">
          <UiIcon name="Lock" />
          <p>
            此公司级共享角色仅总管理员可修改。你可在员工授权中申请分配。查看、编辑等操作独立授予。
          </p>
        </div>
        <div v-if="occupied" class="warning-box">
          <UiIcon name="Clock" />
          <p>
            此角色已有未结束配置申请。有效配置继续使用，不能重复提交。<button
              class="text-button"
              @click="
                detail = undefined;
                page = 'requests';
              "
            >
              查看变更申请
            </button>
          </p>
        </div>
        <template v-if="!preview"
          ><div class="access-toggle">
            <div>
              <strong>系统进入权限</strong>
              <p>员工使用本系统功能的必要前提，独立于菜单查看。</p>
            </div>
            <label class="switch"
              ><input
                v-model="selectedPermissions"
                type="checkbox"
                :value="`${detail.system}:access`"
                :disabled="!canEditRole(detail)"
                aria-label="系统进入权限" /><span
            /></label>
          </div>
          <div class="table-scroll">
            <table class="permission-matrix">
              <thead>
                <tr>
                  <th>功能目录</th>
                  <th v-for="a in actionLabels" :key="a">{{ a }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="feature in features" :key="feature">
                  <td><UiIcon name="Folder" :size="16" />{{ feature }}</td>
                  <td v-for="a in actionLabels" :key="a">
                    <input
                      v-model="selectedPermissions"
                      type="checkbox"
                      :value="permissionId(feature, a)"
                      :disabled="
                        !canEditRole(detail) ||
                        (!data.permissions.find((p) => p.id === permissionId(feature, a))?.active &&
                          !detail.permissions.includes(permissionId(feature, a)))
                      "
                      :aria-label="`${feature}${a}`"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="small muted">
            编辑不会自动勾选查看；未配置查看时，功能操作可独立存在，菜单不会因此出现。
          </p>
          <label v-if="canEditRole(detail)" class="field"
            ><span>变更原因 <i>*</i></span
            ><textarea
              v-model="reason"
              placeholder="说明本次新增或移除操作的原因"
              rows="3"
            /></label></template
        ><template v-else
          ><h3>本次配置差异</h3>
          <div v-for="id in add" :key="id" class="diff-line added">
            <UiIcon name="Plus" :size="16" />{{ permissionLabel(id) }}
          </div>
          <div v-for="id in remove" :key="id" class="diff-line removed">
            <UiIcon name="X" :size="16" />{{ permissionLabel(id) }}
          </div>
          <div class="warning-box">
            <UiIcon name="Info" />
            <p>提交后不立即生效；审批期间仍使用当前有效配置。</p>
          </div></template
        >
      </div></template
    ><template #footer
      ><button class="button" @click="preview ? (preview = false) : (detail = undefined)">
        {{ preview ? '上一步' : '关闭' }}</button
      ><template v-if="detail && canEditRole(detail)"
        ><button
          v-if="!preview"
          class="button primary"
          :disabled="occupied"
          @click="
            reason.trim() && (add.length || remove.length)
              ? (preview = true)
              : toast('请填写原因并至少修改一项权限')
          "
        >
          预览变更</button
        ><button v-else class="button primary" @click="save">保存并等待 OA 投递</button></template
      ></template
    ></UiDialog
  >
  <UiDialog
    :open="create"
    title="新建角色草稿"
    subtitle="保存草稿不生效，也不能分配给员工"
    @close="create = false"
    ><div class="stack">
      <label class="field"
        ><span>角色名称 <i>*</i></span
        ><input v-model="name" placeholder="例如：工程一组资料归档员" /></label
      ><label class="field"
        ><span>唯一编码 <i>*</i></span
        ><input v-model="code" placeholder="例如：ENG1_ARCHIVE" /><small
          >编码创建后保持稳定，不随角色更名变化。</small
        ></label
      >
      <div class="form-grid">
        <label class="field"
          ><span>角色归属</span
          ><select v-model="dept">
            <option v-if="isGlobal" value="">公司级共享角色</option>
            <option
              v-for="d in departments.filter((d) => allowedDepts.includes(d.id))"
              :key="d.id"
              :value="d.id"
            >
              {{ d.name }}
            </option>
          </select></label
        ><label class="field"
          ><span>业务系统</span
          ><select v-model="newSystem">
            <option v-for="s in allowedSystems" :key="s" :value="s">{{ systemName(s) }}</option>
          </select></label
        >
      </div>
    </div>
    <template #footer
      ><button class="button" @click="create = false">取消</button
      ><button class="button primary" @click="createRole">保存角色草稿</button></template
    ></UiDialog
  >
</template>
