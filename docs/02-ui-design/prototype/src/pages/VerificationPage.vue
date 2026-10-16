<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  companyId,
  companyName,
  allowedSystems,
  data,
  effectivePermissions,
  permissionLabel,
  systemName,
  visibleEmployees,
} from '../store';
import UiIcon from '../components/UiIcon.vue';
import StatusBadge from '../components/StatusBadge.vue';
const queryError = ref('');
const callScene = ref('normal');
const employee = ref(visibleEmployees.value[0]?.id ?? '');
const system = ref(allowedSystems.value[0] ?? 'oa');
const checked = ref(false);
const code = ref('');
const single = ref<boolean | null>(null);
const result = computed(() => effectivePermissions(employee.value, companyId.value, system.value));
const permissions = computed(() => data.permissions.filter((p) => p.system === system.value));
watch([employee, system, callScene], () => {
  checked.value = false;
  single.value = null;
  code.value = '';
  queryError.value = '';
});
function queryPermissions() {
  queryError.value =
    callScene.value === 'service-error'
      ? '服务暂不可用，请稍后重试；未返回成功空权限，也未默认放行'
      : callScene.value === 'cross-system'
        ? '模拟调用身份绑定另一系统，拒绝越系统查询'
        : data.systems.find((s) => s.id === system.value)?.active
          ? ''
          : '调用系统已停用，拒绝本次查询；这不是空权限结果';
  checked.value = !queryError.value;
}
function checkPermission() {
  queryPermissions();
  single.value = queryError.value
    ? null
    : code.value.endsWith(':access')
      ? result.value.canAccess
      : result.value.operations.includes(code.value);
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>权限调用演示</h1>
      <p>验证进入资格、可见菜单与独立功能操作。</p>
    </div>
    <span class="demo-chip"><UiIcon name="Play" :size="13" />本地模拟</span>
  </div>
  <div class="info-box mb-6">
    <UiIcon name="Info" />
    <p>
      模拟受信业务后端查询，未接入真实外部系统。下方响应仅表达界面含义，不是已定稿 API
      契约；普通员工不登录本平台。
    </p>
  </div>
  <div class="verification-grid">
    <section class="panel p-6">
      <h2 class="section-title">查询上下文</h2>
      <div class="stack">
        <label class="field"
          ><span>模拟调用场景</span
          ><select v-model="callScene">
            <option value="normal">正常受信调用</option>
            <option value="cross-system">越系统调用</option>
            <option value="service-error">服务故障</option>
          </select></label
        >
        <label class="field"><span>当前公司</span><input :value="companyName" readonly /></label
        ><label class="field"
          ><span>目标员工</span
          ><select v-model="employee">
            <option v-for="e in visibleEmployees" :key="e.id" :value="e.id">
              {{ e.name }} / {{ e.id }}{{ !e.active ? '（已停用）' : '' }}
            </option>
          </select></label
        ><label class="field"
          ><span>调用方绑定系统</span
          ><select v-model="system">
            <option v-for="s in allowedSystems" :key="s" :value="s">
              {{ systemName(s) }}
            </option></select
          ><small>仅在当前公司、当前系统中计算有效授权。</small></label
        ><button class="button primary full" @click="queryPermissions">
          <UiIcon name="Fingerprint" :size="17" />查询有效权限
        </button>
        <div class="section-divider" />
        <h3>指定功能检查</h3>
        <label class="field"
          ><span>权限编码</span
          ><select v-model="code">
            <option value="" disabled>选择独立权限</option>
            <option v-for="p in permissions" :key="p.id" :value="p.id">
              {{ permissionLabel(p.id) }}
            </option>
          </select></label
        ><button class="button full" :disabled="!code" @click="checkPermission">
          检查是否拥有权限
        </button>
        <div v-if="single !== null" class="single-result" :class="single ? 'allowed' : 'denied'">
          <UiIcon :name="single ? 'CircleCheck' : 'Lock'" />
          <div>
            <strong>{{ single ? '允许此功能' : '无此功能权限' }}</strong
            ><small>{{ code }}</small>
          </div>
        </div>
      </div>
    </section>
    <section class="panel p-6">
      <div class="flex justify-between items-center mb-5">
        <h2 class="section-title mb-0">有效权限结果</h2>
        <span class="muted small">UC-05 / UC-06</span>
      </div>
      <div v-if="queryError" role="alert" class="error-box">
        <UiIcon name="TriangleAlert" />
        <p>{{ queryError }}</p>
      </div>
      <template v-else-if="checked"
        ><div class="access-result" :class="{ granted: result.canAccess }">
          <span class="object-icon" :class="result.canAccess ? 'teal' : 'slate'"
            ><UiIcon :name="result.canAccess ? 'ShieldCheck' : 'Lock'" :size="26"
          /></span>
          <div>
            <strong>{{ result.canAccess ? '具备系统进入资格' : '不具备系统进入资格' }}</strong>
            <p>
              {{
                result.canAccess
                  ? '系统进入与功能操作按当前公司及系统独立判断'
                  : '菜单与操作返回空集合，其他系统不受影响'
              }}
            </p>
          </div>
          <StatusBadge :value="result.canAccess ? '允许进入' : '不可进入'" />
        </div>
        <h3 class="section-title mt-6">
          可见菜单 <span class="muted">{{ result.menus.length }}</span>
        </h3>
        <div v-if="result.menus.length" class="menu-preview">
          <div v-for="m in result.menus" :key="m">
            <UiIcon name="Folder" :size="17" />{{ m }}<UiIcon name="ChevronRight" :size="15" />
          </div>
        </div>
        <p v-else class="inline-placeholder">没有可见菜单。编辑不自动推导查看。</p>
        <h3 class="section-title mt-6">
          允许的功能操作 <span class="muted">{{ result.operations.length }}</span>
        </h3>
        <div class="operation-grid">
          <div v-for="id in result.operations" :key="id" class="operation-item">
            <UiIcon name="Check" :size="16" />
            <div>
              <strong>{{ permissionLabel(id) }}</strong
              ><small>{{ id }}</small>
            </div>
          </div>
        </div>
        <p v-if="!result.operations.length" class="inline-placeholder">当前没有允许的功能操作。</p>
        <details class="response-details">
          <summary>查看模拟响应</summary>
          <pre>{{
            JSON.stringify(
              {
                company: companyId,
                system,
                canAccessSystem: result.canAccess,
                menus: result.menus,
                operations: result.operations,
              },
              null,
              2,
            )
          }}</pre>
        </details></template
      >
      <div v-else class="empty-result">
        <span class="empty-orbit"><UiIcon name="Fingerprint" :size="42" /></span>
        <h3>选择上下文，查看权限结果</h3>
        <p>角色来源取并集，功能操作去重。<br />系统进入资格与菜单可见性分别判断。</p>
      </div>
    </section>
  </div>
</template>
