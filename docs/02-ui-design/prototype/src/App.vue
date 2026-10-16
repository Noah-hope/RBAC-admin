<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { companies, type Page } from './data';
import {
  actor,
  companyId,
  companyName,
  currentManagement,
  data,
  deptName,
  identity,
  isGlobal,
  isPending,
  loggedIn,
  notice,
  page,
  pageAllowed,
  resetDemo,
  scene,
  selectedManagement,
  sessionUsable,
  switchIdentity,
  toast,
  visibleChanges,
} from './store';
import UiIcon from './components/UiIcon.vue';
import UiDialog from './components/UiDialog.vue';
import TerminologyGuide from './components/TerminologyGuide.vue';
import ManagementContext from './components/ManagementContext.vue';
import EmployeesPage from './pages/EmployeesPage.vue';
import RolesPage from './pages/RolesPage.vue';
import RequestsPage from './pages/RequestsPage.vue';
import SystemsPage from './pages/SystemsPage.vue';
import AdministratorsPage from './pages/AdministratorsPage.vue';
import OrganizationPage from './pages/OrganizationPage.vue';
import VerificationPage from './pages/VerificationPage.vue';
import AuditPage from './pages/AuditPage.vue';

const navigation: { id: Page; title: string; icon: string; group: string; global?: boolean }[] = [
  { id: 'employees', title: '员工授权', icon: 'Users', group: '授权管理' },
  { id: 'roles', title: '角色权限', icon: 'ShieldCheck', group: '授权管理' },
  { id: 'requests', title: '变更申请', icon: 'GitPullRequest', group: '授权管理' },
  { id: 'verification', title: '权限调用演示', icon: 'Fingerprint', group: '演示工具' },
  { id: 'audit', title: '审计记录', icon: 'History', group: '操作记录' },
  { id: 'systems', title: '系统与权限目录', icon: 'Grid2X2', group: '平台管理', global: true },
  { id: 'administrators', title: '管理员与范围', icon: 'UserCog', group: '平台管理', global: true },
  { id: 'organization', title: '员工组织', icon: 'Building2', group: '平台管理', global: true },
];
const views = {
  employees: EmployeesPage,
  roles: RolesPage,
  requests: RequestsPage,
  systems: SystemsPage,
  administrators: AdministratorsPage,
  organization: OrganizationPage,
  verification: VerificationPage,
  audit: AuditPage,
};
const current = computed(() => navigation.find((n) => n.id === page.value)!);
const mobileNav = ref(false);
const railCollapsed = ref(window.matchMedia('(min-width: 840px) and (max-width: 1199px)').matches);
const help = ref(false);
const terminology = ref(false);
const accountMenu = ref(false);
const passwordOpen = ref(false);
const password = ref('');
const confirm = ref('');
const oldPassword = ref('');
const loginName = ref('li.qiao.demo');
const loginPassword = ref('');
const loginError = ref('');
const loginKind = ref<'department' | 'global'>('department');
const temporary = computed(
  () => identity.value === 'department' && data.accounts.find((a) => a.id === 'A-01')?.temporary,
);
const memberships = computed(() => data.management.filter((m) => m.accounts.includes('A-01')));
watch(
  memberships,
  (items) => {
    if (!isGlobal.value && items.length && !items.some((m) => m.id === selectedManagement.value))
      selectedManagement.value = items[0].id;
  },
  { deep: true },
);
watch(
  () => currentManagement.value?.company,
  (company) => {
    if (!isGlobal.value && company) companyId.value = company;
  },
);
const viewKey = computed(
  () => `${identity.value}/${companyId.value}/${selectedManagement.value}/${page.value}`,
);
watch(page, () => {
  scene.value = 'normal';
  mobileNav.value = false;
  accountMenu.value = false;
});
watch(companyId, () => (scene.value = 'normal'));
function go(id: Page) {
  page.value = id;
  mobileNav.value = false;
}
function login() {
  if (
    !loginPassword.value ||
    loginName.value !== (loginKind.value === 'global' ? 'platform.admin' : 'li.qiao.demo')
  ) {
    loginError.value = '登录未成功，请核对演示账号和非空密码输入';
    return;
  }
  switchIdentity(loginKind.value);
  loginPassword.value = '';
  loginError.value = '';
  if (!sessionUsable.value) {
    loggedIn.value = false;
    loginError.value = '演示账号、员工状态或管理任命不可用，请重置演示数据';
    return;
  }
  toast('已进入本地交互原型');
}
function changePassword() {
  if (!oldPassword.value || password.value.length < 8 || password.value !== confirm.value) {
    toast('请填写当前密码，新密码至少 8 位且两次一致');
    return;
  }
  if (identity.value === 'department')
    data.accounts.find((a) => a.id === 'A-01')!.temporary = false;
  password.value = '';
  oldPassword.value = '';
  confirm.value = '';
  passwordOpen.value = false;
  loggedIn.value = false;
  toast('模拟改密完成，旧会话失效，请重新登录。所填密码未保存');
}
function openPassword() {
  accountMenu.value = false;
  oldPassword.value = '';
  password.value = '';
  confirm.value = '';
  passwordOpen.value = true;
}
</script>

<template>
  <div v-if="!loggedIn" class="login-page">
    <section class="login-story">
      <div class="brand">
        <span class="brand-mark"><UiIcon name="ShieldCheck" :size="26" /></span>
        <div><strong>权限中心</strong><small>功能权限管理</small></div>
      </div>
      <div class="login-story-copy">
        <span class="story-line" />
        <h1>让权限有序，<br />让协作有界。</h1>
        <p>从部门到系统，从申请到生效，<br />每一份功能权限都有清晰的来源。</p>
        <div class="login-source-map">
          <span>公司 / 部门</span><UiIcon name="ChevronRight" /><span>角色</span
          ><UiIcon name="ChevronRight" /><span>功能权限</span>
        </div>
      </div>
      <p class="login-story-footer">集中权限管理系统 · 界面原型</p>
    </section>
    <section class="login-main">
      <div class="login-card">
        <span class="demo-chip mb-6">交互原型 / 虚构数据</span>
        <h2>登录权限中心</h2>
        <p class="muted mb-7">仅总管理员和权限管理员登录</p>
        <div class="segmented mb-6">
          <button
            :class="{ active: loginKind === 'department' }"
            @click="
              loginKind = 'department';
              loginName = 'li.qiao.demo';
              loginError = '';
            "
          >
            部门权限管理员</button
          ><button
            :class="{ active: loginKind === 'global' }"
            @click="
              loginKind = 'global';
              loginName = 'platform.admin';
              loginError = '';
            "
          >
            总管理员
          </button>
        </div>
        <form class="stack" @submit.prevent="login">
          <label class="field"
            ><span>演示登录名</span><input v-model="loginName" autocomplete="username" /></label
          ><label class="field"
            ><span>演示密码</span
            ><input
              v-model="loginPassword"
              type="password"
              autocomplete="current-password"
              placeholder="输入任意非空内容，不会保存"
          /></label>
          <p v-if="loginError" role="alert" class="field-error">{{ loginError }}</p>
          <button class="button primary full" type="submit">
            登录演示工作区<UiIcon name="ArrowRight" :size="17" />
          </button>
        </form>
        <p class="small muted mt-5">本原型未接入真实认证或 OA / HR；请勿输入真实密码。</p>
        <button
          class="text-button mt-5"
          @click="
            resetDemo();
            loggedIn = false;
            loginError = '';
          "
        >
          <UiIcon name="RefreshCw" :size="14" />重置演示账号与数据
        </button>
      </div>
    </section>
  </div>
  <div v-else class="app-shell" :class="{ 'rail-collapsed': railCollapsed }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <div v-if="mobileNav" class="mobile-scrim" @click="mobileNav = false" />
    <aside class="sidebar" :class="{ 'mobile-open': mobileNav }">
      <div class="brand">
        <span class="brand-mark"><UiIcon name="ShieldCheck" :size="26" /></span>
        <div><strong>权限中心</strong><small>功能权限管理</small></div>
        <button
          class="icon-button mobile-only ml-auto"
          aria-label="关闭导航"
          @click="mobileNav = false"
        >
          <UiIcon name="X" />
        </button>
      </div>
      <div class="workspace-name">
        <span class="workspace-icon"><UiIcon name="Building2" :size="17" /></span>
        <div>
          <strong>{{ isGlobal ? '总管理员工作区' : '部门授权工作区' }}</strong
          ><small>{{ companyName }}</small>
        </div>
      </div>
      <nav id="main-navigation" aria-label="主导航">
        <template
          v-for="group in ['授权管理', '操作记录', ...(isGlobal ? ['平台管理'] : [])]"
          :key="group"
          ><div class="nav-group-label">
            {{ group }}<span v-if="group === '平台管理' && !isGlobal">仅总管理员</span>
          </div>
          <button
            v-for="n in navigation.filter((n) => n.group === group)"
            :key="n.id"
            :aria-label="n.title"
            :title="n.title"
            class="nav-item"
            :class="{ active: page === n.id, restricted: n.global && !isGlobal }"
            :aria-current="page === n.id ? 'page' : undefined"
            @click="go(n.id)"
          >
            <UiIcon :name="n.icon" :size="19" /><span>{{ n.title }}</span
            ><span
              v-if="n.id === 'requests' && visibleChanges.filter(isPending).length"
              class="nav-badge"
              >{{ visibleChanges.filter(isPending).length }}</span
            ><UiIcon v-if="n.global && !isGlobal" name="Lock" :size="13" /></button
        ></template>
      </nav>
      <div class="sidebar-bottom">
        <button
          class="sidebar-support"
          aria-label="术语说明"
          title="术语说明"
          @click="terminology = true"
        >
          <UiIcon name="BookOpen" :size="17" /><span>术语说明</span
          ><UiIcon name="ChevronRight" :size="14" />
        </button>
        <button
          class="sidebar-support"
          aria-label="原型使用说明"
          title="原型使用说明"
          @click="help = true"
        >
          <UiIcon name="HelpCircle" :size="17" /><span>原型使用说明</span
          ><UiIcon name="ChevronRight" :size="14" />
        </button>
        <div class="sidebar-version">
          <span class="live-dot" />交互原型 <span>V1.3</span
          ><button
            class="icon-button"
            aria-label="重置演示数据"
            title="重置所有演示数据"
            @click="resetDemo"
          >
            <UiIcon name="RefreshCw" :size="14" />
          </button>
        </div>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <button
          class="icon-button mobile-only"
          aria-label="打开导航"
          aria-controls="main-navigation"
          :aria-expanded="mobileNav"
          @click="mobileNav = true"
        >
          <UiIcon name="Menu" />
        </button>
        <button
          class="icon-button desktop-nav-toggle"
          :aria-label="railCollapsed ? '展开侧栏' : '收起侧栏'"
          aria-controls="main-navigation"
          :aria-expanded="!railCollapsed"
          @click="railCollapsed = !railCollapsed"
        >
          <UiIcon name="Menu" />
        </button>
        <div class="breadcrumb">
          <span>权限中心</span><UiIcon name="ChevronRight" :size="14" /><strong>{{
            current.title
          }}</strong>
        </div>
        <div class="topbar-right">
          <label v-if="!['systems', 'administrators'].includes(page)" class="company-select"
            ><UiIcon name="Building2" :size="15" /><select
              v-model="companyId"
              aria-label="当前公司"
              :disabled="!isGlobal"
            >
              <option
                v-for="c in isGlobal ? companies : companies.filter((c) => c.id === companyId)"
                :key="c.id"
                :value="c.id"
              >
                {{ c.name }}
              </option>
            </select></label
          ><span class="topbar-separator" /><button
            class="icon-button help-top"
            aria-label="原型使用说明"
            @click="help = true"
          >
            <UiIcon name="HelpCircle" :size="20" />
          </button>
          <div class="profile-wrapper">
            <button
              class="profile-button"
              :aria-expanded="accountMenu"
              @click="accountMenu = !accountMenu"
            >
              <span class="avatar tiny">{{ isGlobal ? '总管' : '李乔' }}</span>
              <div>
                <strong>{{ actor }}</strong
                ><small>{{ isGlobal ? '平台总管理员' : '部门权限管理员' }}</small>
              </div>
              <UiIcon name="ChevronDown" :size="14" />
            </button>
            <div v-if="accountMenu" class="profile-menu">
              <button @click="openPassword">
                <UiIcon name="KeyRound" :size="16" />修改本人密码</button
              ><button
                @click="
                  loggedIn = false;
                  accountMenu = false;
                  loginPassword = '';
                "
              >
                <UiIcon name="LogOut" :size="16" />退出当前会话
              </button>
            </div>
          </div>
        </div>
      </header>
      <div class="prototype-controls">
        <details class="prototype-tools">
          <summary>
            <span class="prototype-label">界面原型</span><span>模拟数据 · 未连接真实业务系统</span
            ><span class="tools-caption">原型演示工具</span
            ><UiIcon name="SlidersHorizontal" :size="18" />
          </summary>
          <div class="prototype-toolbar">
            <button class="text-button" @click="go('verification')">
              <UiIcon name="Fingerprint" />权限调用演示
            </button>
            <div>
              <label
                >演示身份<select
                  :value="identity"
                  aria-label="演示身份"
                  @change="
                    switchIdentity(
                      ($event.target as HTMLSelectElement).value as 'department' | 'global',
                    )
                  "
                >
                  <option value="department">部门权限管理员</option>
                  <option value="global">总管理员</option>
                </select></label
              ><label
                >页面状态<select v-model="scene" aria-label="演示页面状态">
                  <option value="normal">正常</option>
                  <option value="loading">加载中</option>
                  <option value="empty">空结果</option>
                  <option value="error">服务故障</option>
                  <option value="forbidden">无权限</option>
                  <option value="expired">会话失效</option>
                </select></label
              >
            </div>
          </div>
        </details>
        <button
          class="text-button demo-identity-switch"
          @click="
            accountMenu = false;
            switchIdentity(isGlobal ? 'department' : 'global');
          "
        >
          <UiIcon name="UserCog" :size="16" />
          {{ isGlobal ? '切换为部门权限管理员（演示）' : '切换为总管理员（演示）' }}
        </button>
      </div>
      <main id="main-content" class="main-content" tabindex="-1">
        <section v-if="temporary" class="state-panel">
          <span class="empty-orbit"><UiIcon name="KeyRound" :size="38" /></span>
          <h1>先修改临时密码</h1>
          <p>首次或重置后只能修改密码，完成后重新登录。</p>
          <button class="button primary" @click="openPassword">修改临时密码</button>
        </section>
        <section v-else-if="!sessionUsable || scene === 'expired'" class="state-panel">
          <span class="empty-orbit"><UiIcon name="KeyRound" :size="38" /></span>
          <h1>当前会话已不可用</h1>
          <p>账号、员工状态或管理资格可能已变化。请重新登录。</p>
          <button class="button primary" @click="loggedIn = false">返回登录</button>
        </section>
        <section v-else-if="!pageAllowed(page) || scene === 'forbidden'" class="state-panel">
          <span class="empty-orbit"><UiIcon name="Lock" :size="38" /></span>
          <h1>{{ current.global ? '此页面需要总管理员资格' : '当前身份无权访问此页面' }}</h1>
          <p>你的范围仍可办理员工授权、部门角色与申请。<br />演示身份切换仅用于原型评审。</p>
          <button
            class="button primary"
            @click="
              go('employees');
              scene = 'normal';
            "
          >
            返回员工授权
          </button>
        </section>
        <section
          v-else-if="scene === 'loading'"
          aria-busy="true"
          aria-label="正在加载页面"
          class="loading-scene"
        >
          <div class="skeleton h-8 w-48" />
          <div class="skeleton h-4 w-80 mt-4" />
          <div class="skeleton h-20 w-full mt-8" />
          <div class="panel mt-6 p-6">
            <div v-for="i in 6" :key="i" class="skeleton h-10 my-5" />
          </div>
          <p>正在读取当前范围内的数据…</p>
        </section>
        <section v-else-if="scene === 'error'" class="state-panel">
          <span class="empty-orbit warning"><UiIcon name="TriangleAlert" :size="38" /></span>
          <h1>暂时无法读取数据</h1>
          <p>服务出现临时故障，数据没有被清空。<br />稍后重试，或联系平台管理员。</p>
          <button class="button primary" @click="scene = 'normal'">
            <UiIcon name="RefreshCw" :size="16" />重新加载
          </button>
        </section>
        <section v-else-if="scene === 'empty'" class="state-panel">
          <span class="empty-orbit"><UiIcon name="Search" :size="38" /></span>
          <h1>当前没有匹配的记录</h1>
          <p>试试调整筛选条件，或确认当前管理范围。</p>
          <button class="button" @click="scene = 'normal'">恢复正常演示</button>
        </section>
        <template v-else
          ><ManagementContext /><component :is="views[page]" :key="viewKey"
        /></template>
      </main>
      <footer class="workspace-footer">
        <span>集中权限管理系统</span><span>{{ companyName }} / 功能权限管理</span>
      </footer>
    </div>
  </div>
  <Transition name="toast"
    ><div v-if="notice" class="toast-message" role="status">
      <UiIcon name="Info" :size="18" /><span>{{ notice }}</span
      ><button aria-label="关闭提示" @click="notice = ''">
        <UiIcon name="X" :size="15" />
      </button></div
  ></Transition>
  <TerminologyGuide :open="terminology" @close="terminology = false" />
  <UiDialog :open="help" title="原型使用说明" @close="help = false"
    ><div class="stack">
      <div class="info-box">
        <UiIcon name="Play" />
        <p>这是可交互的界面原型，数据、账号与事件均为虚构。刷新页面或重置演示会恢复初始数据。</p>
      </div>
      <h3>建议体验路径</h3>
      <ol class="help-steps">
        <li>查看林予安的授权来源，切换第一、第二项目经理部任命，分别查看独立来源。</li>
        <li>关闭第一项目经理部来源，再切换第二项目经理部任命，确认其来源仍有效。</li>
        <li>提交角色分配，到变更申请展开演示工具，分别模拟 OA 结果和执行。</li>
        <li>切换总管理员，体验系统目录、后台账号、范围配置和人员预检。</li>
        <li>使用页面状态切换加载、空结果、故障、无权限和会话失效。</li>
      </ol>
      <p class="small muted">
        原型未实现真实登录认证、后端权限防护、数据库事务和 OA/HR 接入；演示响应不是正式 API 契约。
      </p>
    </div>
    <template #footer
      ><button class="button primary" @click="help = false">开始体验</button></template
    ></UiDialog
  >
  <UiDialog
    :open="passwordOpen"
    title="修改本人密码"
    subtitle="模拟改密会结束当前演示会话；不会保存输入的密码"
    @close="
      passwordOpen = false;
      password = '';
      confirm = '';
      oldPassword = '';
    "
    ><form id="password-form" class="stack" @submit.prevent="changePassword">
      <label class="field"
        ><span>当前密码</span
        ><input
          v-model="oldPassword"
          type="password"
          autocomplete="current-password"
          placeholder="演示填写任意非空内容" /></label
      ><label class="field"
        ><span>新密码</span
        ><input
          v-model="password"
          type="password"
          autocomplete="new-password"
          placeholder="原型校验至少 8 位" /></label
      ><label class="field"
        ><span>确认新密码</span
        ><input v-model="confirm" type="password" autocomplete="new-password"
      /></label>
    </form>
    <template #footer
      ><button
        class="button"
        @click="
          passwordOpen = false;
          password = '';
          confirm = '';
          oldPassword = '';
        "
      >
        取消</button
      ><button class="button primary" type="submit" form="password-form">
        修改并重新登录
      </button></template
    ></UiDialog
  >
</template>
