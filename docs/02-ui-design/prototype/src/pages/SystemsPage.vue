<script setup lang="ts">
import { computed, ref } from 'vue';
import { actionLabels } from '../data';
import { audit, data, toast } from '../store';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
const selected = ref('oa');
const system = computed(() => data.systems.find((s) => s.id === selected.value)!);
const features = computed(() => [
  ...new Set(
    data.permissions
      .filter((p) => p.system === selected.value && p.action !== '进入系统')
      .map((p) => p.feature),
  ),
]);
const modal = ref<'system' | 'feature' | 'toggle' | ''>('');
const name = ref('');
const code = ref('');
const reason = ref('');
const actionSet = ref<string[]>(['查看', '新增', '编辑']);
const toggleTarget = ref<{
  label: string;
  type: 'system' | 'feature' | 'permission';
  id: string;
  active: boolean;
}>();
function open(kind: typeof modal.value) {
  modal.value = kind;
  name.value = '';
  code.value = '';
  reason.value = '';
}
function save() {
  if (!name.value.trim() || !/^[a-z][a-z0-9-]*$/.test(code.value)) {
    toast('请填写名称和合法编码（小写字母开头，可含数字及短横线）');
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
    if (!actionSet.value.length) {
      toast('请选择本功能需要登记的操作');
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
    toast('请填写启停原因');
    return;
  }
  const t = toggleTarget.value!;
  if (t.type === 'system') system.value.active = !t.active;
  if (t.type === 'feature') data.featureStates[t.id] = !t.active;
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
        <div class="flex items-center justify-between mb-4">
          <h3>功能目录</h3>
          <button class="text-button" @click="open('feature')">
            <UiIcon name="Plus" :size="16" />新增功能
          </button>
        </div>
        <div v-for="f in features" :key="f" class="feature-card">
          <div class="feature-heading">
            <UiIcon name="Folder" :size="19" /><strong>{{ f }}</strong
            ><span class="small muted">菜单依据“查看”权限可见</span
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
          <div class="feature-operations">
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
    :open="modal === 'system' || modal === 'feature'"
    :title="modal === 'system' ? '登记业务系统' : '新增功能目录'"
    @close="modal = ''"
    ><div class="stack">
      <label class="field"
        ><span>名称 <i>*</i></span
        ><input
          v-model="name"
          :placeholder="modal === 'system' ? '例如：档案管理系统' : '例如：项目归档'" /></label
      ><label class="field"
        ><span>稳定编码 <i>*</i></span
        ><input v-model="code" placeholder="例如：archive"
      /></label>
      <div v-if="modal === 'feature'" class="field">
        <span>登记独立操作</span>
        <div class="flex gap-4 flex-wrap">
          <label v-for="a in actionLabels" :key="a" class="check-line"
            ><input v-model="actionSet" type="checkbox" :value="a" />{{ a }}</label
          >
        </div>
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
      ><button class="button primary" @click="save">确认登记</button></template
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
</template>
