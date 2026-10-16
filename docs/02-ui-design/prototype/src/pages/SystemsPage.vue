<script setup lang="ts">
import { computed, ref } from 'vue';
import { actionLabels } from '../data';
import { audit, data, toast } from '../store';
import UiIcon from '../components/UiIcon.vue';
import { reportError } from '../store';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
const selected = ref('oa');
const collapsedFeatures = ref<string[]>([]);
const featureKey = (feature: string) => `${selected.value}/${feature}`;
function toggleFeature(feature: string) {
  const key = featureKey(feature);
  collapsedFeatures.value = collapsedFeatures.value.includes(key)
    ? collapsedFeatures.value.filter((x) => x !== key)
    : [...collapsedFeatures.value, key];
}
function setAllExpanded(expanded: boolean) {
  const keys = features.value.map(featureKey);
  collapsedFeatures.value = expanded
    ? collapsedFeatures.value.filter((key) => !keys.includes(key))
    : [...new Set([...collapsedFeatures.value, ...keys])];
}
const system = computed(() => data.systems.find((s) => s.id === selected.value)!);
const features = computed(() => [
  ...new Set(
    data.permissions
      .filter((p) => p.system === selected.value && p.action !== '进入系统')
      .map((p) => p.feature),
  ),
]);
const modal = ref<
  'system' | 'feature' | 'toggle' | 'edit-system' | 'edit-feature' | 'permission' | ''
>('');
const featureTarget = ref('');
const parent = ref('');
const menuPermission = ref('');
const sortOrder = ref(0);
const nodeKind = ref('function');
const editDescription = ref('');
const formError = ref('');
const nodes = computed(() =>
  Object.entries(data.directoryMeta)
    .filter(([key, meta]) => key.startsWith(selected.value + '/') && meta.navigation)
    .map(([key]) => key.slice(selected.value.length + 1)),
);
const parentOptions = computed(() =>
  [...nodes.value, ...features.value].filter((x) => x !== featureTarget.value),
);
const name = ref('');
const code = ref('');
const reason = ref('');
const actionSet = ref<string[]>(['查看', '新增', '编辑']);
const operationName = ref('');
const operationCode = ref('');
const hasCustomOperation = computed(() => Boolean(operationName.value || operationCode.value));
const toggleTarget = ref<{
  label: string;
  type: 'system' | 'feature' | 'permission';
  id: string;
  active: boolean;
}>();
const availableActions = computed(() =>
  actionLabels.filter(
    (a) =>
      modal.value !== 'permission' ||
      !data.permissions.some(
        (p) => p.system === selected.value && p.feature === featureTarget.value && p.action === a,
      ),
  ),
);
function open(kind: typeof modal.value) {
  modal.value = kind;
  formError.value = '';
  name.value = '';
  code.value = '';
  reason.value = '';
}
function editSystem() {
  name.value = system.value.name;
  code.value = system.value.id;
  editDescription.value = system.value.description;
  formError.value = '';
  modal.value = 'edit-system';
}
function editFeature(feature: string) {
  featureTarget.value = feature;
  name.value = feature;
  const meta = data.directoryMeta[`${selected.value}/${feature}`];
  parent.value = meta?.parent ?? '';
  sortOrder.value = meta?.order ?? 0;
  menuPermission.value =
    meta?.menu ??
    data.permissions.find(
      (p) => p.system === selected.value && p.feature === feature && p.action === '查看',
    )?.id ??
    '';
  formError.value = '';
  modal.value = 'edit-feature';
}
function saveMetadata() {
  if (!name.value.trim()) {
    formError.value = '请填写名称';
    return;
  }
  if (modal.value === 'edit-system') {
    system.value.name = name.value.trim();
    system.value.description = editDescription.value;
  } else {
    const old = featureTarget.value;
    const next = name.value.trim();
    if (next !== old && (features.value.includes(next) || nodes.value.includes(next))) {
      formError.value = '此系统已有同名目录';
      return;
    }
    let ancestor = parent.value;
    const visited = new Set([old]);
    while (ancestor) {
      if (visited.has(ancestor)) {
        formError.value = '目录父级形成循环，不能保存';
        return;
      }
      visited.add(ancestor);
      ancestor = data.directoryMeta[`${selected.value}/${ancestor}`]?.parent ?? '';
    }
    const menu = data.permissions.find(
      (p) =>
        p.id === menuPermission.value &&
        p.system === selected.value &&
        p.feature === old &&
        ['查看', '进入'].includes(p.action) &&
        p.active,
    );
    const isNavigation = data.directoryMeta[`${selected.value}/${old}`]?.navigation;
    if (!isNavigation && !menu && data.featureStates[`${selected.value}/${old}`] !== false) {
      formError.value = '启用功能菜单前请选择本功能有效的查看/进入权限';
      return;
    }
    data.permissions
      .filter((p) => p.system === selected.value && p.feature === old)
      .forEach((p) => (p.feature = next));
    Object.entries(data.directoryMeta).forEach(([key, m]) => {
      if (key.startsWith(selected.value + '/') && m.parent === old) m.parent = next;
    });
    const state = data.featureStates[`${selected.value}/${old}`];
    const stableCode = data.directoryMeta[`${selected.value}/${old}`]?.code;
    delete data.directoryMeta[`${selected.value}/${old}`];
    data.directoryMeta[`${selected.value}/${next}`] = {
      parent: parent.value,
      menu: isNavigation ? '' : menuPermission.value,
      order: sortOrder.value,
      navigation: isNavigation,
      code: stableCode,
    };
    if (state !== undefined) {
      delete data.featureStates[`${selected.value}/${old}`];
      data.featureStates[`${selected.value}/${next}`] = state;
    }
  }
  audit(
    '编辑目录资料',
    name.value,
    '',
    selected.value,
    '已生效',
    '核对显示资料',
    '稳定编码及角色授权不变。',
    undefined,
    true,
  );
  modal.value = '';
  toast('资料已更新，稳定编码及已有授权保留');
}
function openPermission(feature: string) {
  featureTarget.value = feature;
  name.value = feature;
  code.value =
    data.permissions
      .find((p) => p.system === selected.value && p.feature === feature)
      ?.id.split(':')[1] ?? '';
  actionSet.value = [];
  operationName.value = '';
  operationCode.value = '';
  formError.value = '';
  modal.value = 'permission';
}
function save() {
  if (modal.value === 'permission') {
    const customName = operationName.value.trim();
    const customCode = operationCode.value.trim();
    if (hasCustomOperation.value && (!customName || !customCode)) {
      formError.value = '请填写操作名称和操作编码';
      return;
    }
    if (hasCustomOperation.value && !/^[a-z][a-z0-9-]*$/.test(customCode)) {
      formError.value = '操作编码须以小写字母开头，可含数字及短横线';
      return;
    }
    if (customName === '进入系统') {
      formError.value = '系统进入权限由系统登记维护';
      return;
    }
    const additions = actionSet.value.map((action) => ({
      action,
      id: `${selected.value}:${code.value}:${['view', 'add', 'edit', 'approve', 'export'][actionLabels.indexOf(action)]}`,
    }));
    if (hasCustomOperation.value) {
      additions.push({ action: customName, id: `${selected.value}:${code.value}:${customCode}` });
    }
    if (!additions.length) {
      formError.value = '请选择尚未登记的独立操作';
      return;
    }
    for (const [index, addition] of additions.entries()) {
      if (
        data.permissions.some((p) => p.id === addition.id) ||
        additions.slice(0, index).some((p) => p.id === addition.id)
      ) {
        formError.value = '操作编码已存在';
        return;
      }
      if (
        data.permissions.some(
          (p) =>
            p.system === selected.value &&
            p.feature === featureTarget.value &&
            p.action === addition.action,
        ) ||
        additions.slice(0, index).some((p) => p.action === addition.action)
      ) {
        formError.value = '操作名称已存在';
        return;
      }
    }
    additions.forEach(({ action, id }) =>
      data.permissions.push({
        id,
        system: selected.value,
        feature: featureTarget.value,
        action,
        active: true,
      }),
    );
    audit(
      '登记独立操作',
      featureTarget.value,
      '',
      selected.value,
      '已生效',
      '目录维护',
      '新增操作不会自动授予角色。',
      undefined,
      true,
    );
    modal.value = '';
    toast('操作已登记，请在角色权限中配置；已有角色授权未改变');
    return;
  }

  if (!name.value.trim() || !/^[a-z][a-z0-9-]*$/.test(code.value)) {
    formError.value = '请填写名称和合法编码（小写字母开头，可含数字及短横线）';
    return;
  }
  if (modal.value === 'system') {
    if (data.systems.some((s) => s.id === code.value)) {
      toast('系统编码已存在');
      return;
    }
    data.systems.push({
      id: code.value,
      name: name.value,
      short: code.value.slice(0, 3).toUpperCase(),
      color: 'violet',
      active: true,
      description: '新登记业务系统，尚无角色授权',
    });
    data.permissions.push({
      id: `${code.value}:access`,
      system: code.value,
      feature: '系统进入',
      action: '进入系统',
      active: true,
    });
    audit(
      '登记系统',
      name.value,
      '',
      code.value,
      '已生效',
      '原型登记',
      '独立系统进入权限已建立，没有自动授权。',
      undefined,
      true,
    );
    selected.value = code.value;
  } else {
    if (
      Object.entries(data.directoryMeta).some(
        ([key, meta]) => key.startsWith(selected.value + '/') && meta.code === code.value,
      ) ||
      data.permissions.some((p) => p.id.startsWith(`${selected.value}:${code.value}:`))
    ) {
      formError.value = '本系统目录编码已存在';
      return;
    }
    if (nodeKind.value === 'navigation') {
      if (nodes.value.includes(name.value) || features.value.includes(name.value)) {
        formError.value = '目录名称已存在';
        return;
      }
      data.directoryMeta[`${selected.value}/${name.value}`] = {
        parent: '',
        menu: '',
        order: 0,
        navigation: true,
        code: code.value,
      };
      audit(
        '新增导航目录',
        name.value,
        '',
        selected.value,
        '已生效',
        '目录整理',
        '导航节点不自动授予权限。',
        undefined,
        true,
      );
      modal.value = '';
      toast('导航目录已登记');
      return;
    }
    if (!actionSet.value.length) {
      reportError('请选择本功能需要登记的操作');
      return;
    }
    if (
      features.value.includes(name.value) ||
      data.permissions.some((p) => p.id.startsWith(`${selected.value}:${code.value}:`))
    ) {
      toast('本系统已有此功能名称或编码');
      return;
    }
    actionSet.value.forEach((a) =>
      data.permissions.push({
        id: `${selected.value}:${code.value}:${['view', 'add', 'edit', 'approve', 'export'][actionLabels.indexOf(a)]}`,
        system: selected.value,
        feature: name.value,
        action: a,
        active: true,
      }),
    );
    data.directoryMeta[`${selected.value}/${name.value}`] = {
      parent: '',
      menu: actionSet.value.includes('查看') ? `${selected.value}:${code.value}:view` : '',
      order: 0,
      code: code.value,
    };
    if (!actionSet.value.includes('查看'))
      data.featureStates[`${selected.value}/${name.value}`] = false;
    audit(
      '新增功能目录',
      name.value,
      '',
      selected.value,
      '已生效',
      '原型登记',
      '新增操作不自动加入任何角色；菜单可见依据本功能查看权限。',
      undefined,
      true,
    );
  }
  modal.value = '';
  toast('目录已登记，未自动授予任何角色');
}
function askToggle(
  label: string,
  type: 'system' | 'feature' | 'permission',
  id: string,
  active: boolean,
) {
  toggleTarget.value = { label, type, id, active };
  reason.value = '';
  modal.value = 'toggle';
}
function toggle() {
  if (!reason.value.trim()) {
    reportError('请填写启停原因');
    return;
  }
  const t = toggleTarget.value!;
  if (t.type === 'system') system.value.active = !t.active;
  if (t.type === 'feature') {
    const meta = data.directoryMeta[t.id];
    if (!t.active && !meta?.navigation) {
      const menu =
        meta?.menu ??
        data.permissions.find(
          (p) => p.system === selected.value && p.feature === t.label && p.action === '查看',
        )?.id;
      if (
        !menu ||
        !data.permissions.find(
          (p) => p.id === menu && p.active && ['查看', '进入'].includes(p.action),
        )
      ) {
        reportError('请先绑定本功能有效的查看/进入权限，再启用菜单');
        return;
      }
    }
    data.featureStates[t.id] = !t.active;
  }
  if (t.type === 'permission') data.permissions.find((p) => p.id === t.id)!.active = !t.active;
  audit(
    `${t.active ? '停用' : '启用'}目录`,
    t.label,
    '',
    selected.value,
    '已生效',
    reason.value,
    '只影响尚有效的授权参与计算，不恢复已失效或已撤销来源。',
    undefined,
    true,
  );
  modal.value = '';
  toast(`${t.label}已${t.active ? '停用' : '启用'}，有效权限演示结果已更新`);
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>系统与权限目录</h1>
      <p>维护系统、功能与独立操作，目录登记不会自动授权。</p>
    </div>
    <button class="button primary" @click="open('system')">
      <UiIcon name="Plus" :size="17" />登记系统
    </button>
  </div>
  <div class="directory-layout">
    <aside class="panel system-picker">
      <h3>
        业务系统 <span class="muted">{{ data.systems.length }}</span>
      </h3>
      <button
        v-for="s in data.systems"
        :key="s.id"
        class="system-choice"
        :class="{ selected: selected === s.id }"
        @click="selected = s.id"
      >
        <span class="app-tile" :class="s.color">{{ s.short }}</span
        ><span
          ><strong>{{ s.name }}</strong
          ><small>{{ s.id }}</small></span
        ><span class="system-state-dot" :class="{ off: !s.active }" />
      </button>
      <p class="small muted px-4 py-3">系统编码保持稳定，名称可以随业务调整。</p>
    </aside>
    <section class="panel directory-content">
      <header class="directory-header">
        <span class="app-tile large" :class="system.color">{{ system.short }}</span>
        <div>
          <h2>{{ system.name }}</h2>
          <button class="text-button" @click="editSystem">
            <UiIcon name="Pencil" :size="20" />编辑系统资料
          </button>
          <p>{{ system.description }}</p>
        </div>
        <StatusBadge :value="system.active ? '已启用' : '已停用'" dot /><button
          class="button small-button ml-auto"
          @click="askToggle(system.name, 'system', system.id, system.active)"
        >
          {{ system.active ? '停用系统' : '启用系统' }}
        </button>
      </header>
      <div class="p-6">
        <div class="access-toggle mb-6">
          <div>
            <strong>系统进入权限</strong>
            <p>{{ selected }}:access <span class="muted"> / 每系统唯一，不绑定功能目录</span></p>
          </div>
          <button
            class="button small-button"
            @click="
              askToggle(
                '系统进入权限',
                'permission',
                `${selected}:access`,
                data.permissions.find((p) => p.id === `${selected}:access`)!.active,
              )
            "
          >
            {{
              data.permissions.find((p) => p.id === `${selected}:access`)?.active ? '停用' : '启用'
            }}
          </button>
        </div>
        <div class="directory-controls mb-4">
          <h3>功能目录</h3>
          <button class="text-button" @click="setAllExpanded(true)" aria-label="展开全部操作">
            展开全部
          </button>
          <button class="text-button" @click="setAllExpanded(false)" aria-label="收起全部操作">
            收起全部
          </button>
          <button class="text-button" @click="open('feature')">
            <UiIcon name="Plus" :size="16" />新增功能
          </button>
        </div>
        <div v-if="nodes.length" class="directory-tree">
          <div v-for="n in nodes" :key="n" class="inline-actions">
            <button @click="editFeature(n)">
              <UiIcon name="Folder" />{{ n }}<small>导航</small></button
            ><button
              class="text-button"
              :aria-label="`${data.featureStates[`${selected}/${n}`] === false ? '启用' : '停用'}导航 ${n}`"
              @click="
                askToggle(
                  n,
                  'feature',
                  `${selected}/${n}`,
                  data.featureStates[`${selected}/${n}`] !== false,
                )
              "
            >
              {{ data.featureStates[`${selected}/${n}`] === false ? '启用' : '停用' }}
            </button>
          </div>
        </div>
        <div v-for="(f, index) in features" :key="f" class="feature-card">
          <div class="feature-heading">
            <strong>{{ f }}</strong>
            <button
              class="text-button"
              :aria-label="`${collapsedFeatures.includes(featureKey(f)) ? '展开' : '收起'}操作 ${f}`"
              :aria-expanded="!collapsedFeatures.includes(featureKey(f))"
              :aria-controls="`${selected}-operations-${index}`"
              @click="toggleFeature(f)"
            >
              <UiIcon
                :name="collapsedFeatures.includes(featureKey(f)) ? 'ChevronRight' : 'ChevronDown'"
                :size="18"
              />{{ collapsedFeatures.includes(featureKey(f)) ? '展开操作' : '收起操作' }}
            </button>
            <button class="text-button" :aria-label="`配置菜单 ${f}`" @click="editFeature(f)">
              <UiIcon name="Pencil" :size="18" />配置目录</button
            ><button class="text-button" :aria-label="`登记操作 ${f}`" @click="openPermission(f)">
              登记操作</button
            ><span class="small muted feature-summary"
              >{{
                data.permissions.filter((p) => p.system === selected && p.feature === f).length
              }}
              项操作 · 上级：{{ data.directoryMeta[`${selected}/${f}`]?.parent || '根目录' }}</span
            ><button
              class="text-button ml-auto"
              @click="
                askToggle(
                  f,
                  'feature',
                  `${selected}/${f}`,
                  data.featureStates[`${selected}/${f}`] !== false,
                )
              "
            >
              {{ data.featureStates[`${selected}/${f}`] === false ? '启用功能' : '停用功能' }}
            </button>
          </div>
          <div
            v-if="!collapsedFeatures.includes(featureKey(f))"
            :id="`${selected}-operations-${index}`"
            class="feature-operations"
          >
            <div
              v-for="p in data.permissions.filter((p) => p.system === selected && p.feature === f)"
              :key="p.id"
            >
              <div>
                <strong>{{ p.action }}</strong
                ><small>{{ p.id }}</small>
              </div>
              <button
                class="switch"
                :aria-label="`${f}${p.action}${p.active ? '停用' : '启用'}`"
                role="switch"
                :aria-checked="p.active"
                @click="askToggle(`${f} / ${p.action}`, 'permission', p.id, p.active)"
              >
                <span :class="{ 'switch-on': p.active }" />
              </button>
            </div>
          </div>
        </div>
        <div v-if="!features.length" class="empty-inline">
          <UiIcon name="Folder" :size="30" /><strong>还没有功能目录</strong>
          <p>新增功能并登记独立操作，再配置角色。</p>
        </div>
        <div class="info-box mt-6">
          <UiIcon name="Info" />
          <p>
            系统或目录重新启用后，尚有效的关联可以重新参与计算；已失效、已撤销授权不会复活。不提供组织启停。
          </p>
        </div>
      </div>
    </section>
  </div>
  <UiDialog
    :open="modal === 'system' || modal === 'feature' || modal === 'permission'"
    :title="
      modal === 'system' ? '登记业务系统' : modal === 'permission' ? '登记操作权限' : '新增功能目录'
    "
    @close="modal = ''"
    ><div class="stack">
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <label v-if="modal === 'feature'" class="field"
        ><span>目录类型</span
        ><select v-model="nodeKind">
          <option value="function">功能菜单</option>
          <option value="navigation">导航目录</option>
        </select></label
      >
      <label class="field"
        ><span
          >{{ modal === 'permission' ? '所属功能' : '名称' }}
          <i v-if="modal !== 'permission'">*</i></span
        ><input
          v-model="name"
          :readonly="modal === 'permission'"
          :placeholder="modal === 'system' ? '例如：档案管理系统' : '例如：项目归档'" /></label
      ><label class="field"
        ><span
          >{{ modal === 'permission' ? '功能编码' : '稳定编码' }}
          <i v-if="modal !== 'permission'">*</i></span
        ><input v-model="code" :readonly="modal === 'permission'" placeholder="例如：archive"
      /></label>
      <div
        v-if="(modal === 'feature' && nodeKind !== 'navigation') || modal === 'permission'"
        class="field"
      >
        <span>{{ modal === 'permission' ? '常用操作（可多选）' : '登记独立操作' }}</span>
        <small v-if="modal === 'permission'"
          >选择尚未登记的常用操作，或在下方填写自定义操作。所属功能固定，无需为操作设置目录。</small
        >
        <small v-if="modal === 'permission' && !availableActions.length"
          >常用操作已全部登记，可在下方新增自定义操作。</small
        >
        <div class="flex gap-4 flex-wrap">
          <label v-for="a in availableActions" :key="a" class="check-line"
            ><input v-model="actionSet" type="checkbox" :value="a" />{{ a }}</label
          >
        </div>
      </div>
      <div v-if="modal === 'permission'" class="stack">
        <h3>自定义操作</h3>
        <div class="form-grid">
          <label class="field"
            ><span>操作名称</span>
            <input v-model="operationName" placeholder="例如：打印" />
          </label>
          <label class="field"
            ><span>操作编码</span>
            <input
              v-model="operationCode"
              placeholder="例如：print"
              aria-describedby="operation-code-help"
            />
          </label>
        </div>
        <p id="operation-code-help" class="small muted break-all">
          小写字母开头，可含数字及短横线；同一功能内名称、编码不能重复。
          <template v-if="operationCode.trim()"
            >完整权限编码：{{ selected }}:{{ code }}:{{ operationCode.trim() }}</template
          >
          编码登记后保持稳定，须与业务系统使用的权限标识一致。
        </p>
      </div>
      <div class="info-box">
        <UiIcon name="Info" />
        <p>
          {{
            modal === 'system'
              ? '系统登记时建立唯一进入权限，不自动授予角色或员工。'
              : '新增功能和操作不会自动加入已有角色；菜单可见依据本功能查看权限。'
          }}
        </p>
      </div>
    </div>
    <template #footer
      ><button class="button" @click="modal = ''">取消</button
      ><button
        class="button primary"
        :disabled="modal === 'permission' && !actionSet.length && !hasCustomOperation"
        @click="save"
      >
        确认登记
      </button></template
    ></UiDialog
  >
  <UiDialog
    :open="modal === 'toggle'"
    :title="`${toggleTarget?.active ? '停用' : '启用'}${toggleTarget?.label ?? ''}`"
    @close="modal = ''"
    ><div class="stack">
      <div class="warning-box">
        <UiIcon name="TriangleAlert" />
        <p>影响当前系统相关有效角色的权限计算。停用保留配置与历史；恢复不复活失效来源。</p>
      </div>
      <label class="field"
        ><span>办理原因 <i>*</i></span
        ><textarea v-model="reason" rows="3" placeholder="说明变更与影响核对情况" />
      </label>
    </div>
    <template #footer
      ><button class="button" @click="modal = ''">取消</button
      ><button class="button primary" @click="toggle">
        确认{{ toggleTarget?.active ? '停用' : '启用' }}
      </button></template
    ></UiDialog
  >
  <UiDialog
    :open="modal === 'edit-system' || modal === 'edit-feature'"
    :title="modal === 'edit-system' ? '编辑系统资料' : '配置功能目录'"
    :subtitle="modal === 'edit-feature' ? '设置外部业务系统菜单的位置与显示条件' : undefined"
    @close="modal = ''"
    ><div class="stack">
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <label class="field"><span>名称</span><input v-model="name" /></label
      ><label v-if="modal === 'edit-system'" class="field"
        ><span>稳定编码</span><input :value="system.id" disabled /></label
      ><label v-if="modal === 'edit-system'" class="field"
        ><span>说明</span><textarea v-model="editDescription" /></label
      ><template v-else
        ><label class="field"
          ><span>父级目录</span
          ><select v-model="parent" aria-label="父级目录" aria-describedby="directory-parent-help">
            <option value="">根目录</option>
            <option v-for="n in parentOptions" :key="n">{{ n }}</option></select
          ><small id="directory-parent-help"
            >功能在外部业务系统菜单中的上级菜单或分组；根目录表示直接放在系统菜单下，与公司、部门层级无关。</small
          ></label
        ><label class="field"
          ><span>显示排序</span><input type="number" v-model.number="sortOrder" /></label
        ><label v-if="!data.directoryMeta[`${selected}/${featureTarget}`]?.navigation" class="field"
          ><span>菜单可见权限</span
          ><select
            v-model="menuPermission"
            aria-label="菜单可见权限"
            aria-describedby="directory-menu-help"
          >
            <option value="">未绑定（仅停用状态可保存）</option>
            <option
              v-for="p in data.permissions.filter(
                (p) =>
                  p.system === selected &&
                  p.feature === featureTarget &&
                  ['查看', '进入'].includes(p.action) &&
                  p.active,
              )"
              :key="p.id"
              :value="p.id"
            >
              {{ p.action }} / {{ p.id }}
            </option></select
          ><small id="directory-menu-help"
            >员工经角色获得所选查看/进入权限，且具备系统进入权限时，业务系统才显示此菜单。编辑权限不会自动带来查看权限。</small
          ></label
        >
        <p class="readonly-note">
          仅本功能的有效查看/进入权限可作为条件；不会因此授予角色权限。编码及授权关联保留。
        </p></template
      >
    </div>
    <template #footer
      ><button class="button" @click="modal = ''">取消</button
      ><button class="button primary" @click="saveMetadata">保存资料</button></template
    ></UiDialog
  >
</template>
