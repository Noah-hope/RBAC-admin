<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import {
  api,
  state,
  isSuper,
  establish,
  clearSession,
  errorText,
  chooseAppointment,
  loadAppointments,
} from "./state";
import UiIcon from "./components/UiIcon.vue";
import Panel from "./components/Panel.vue";
import PasswordForm from "./components/PasswordForm.vue";
import EmployeesPage from "./pages/EmployeesPage.vue";
import RolesPage from "./pages/RolesPage.vue";
import RequestsPage from "./pages/RequestsPage.vue";
import AuditPage from "./pages/AuditPage.vue";
import SystemsPage from "./pages/SystemsPage.vue";
import AdministratorsPage from "./pages/AdministratorsPage.vue";
import OrganizationPage from "./pages/OrganizationPage.vue";
const nav = [
  { id: "employees", title: "员工授权", icon: "Users", view: EmployeesPage },
  { id: "roles", title: "角色权限", icon: "ShieldCheck", view: RolesPage },
  {
    id: "requests",
    title: "变更申请",
    icon: "GitPullRequest",
    view: RequestsPage,
  },
  { id: "audit", title: "审计记录", icon: "History", view: AuditPage },
  {
    id: "systems",
    title: "系统与权限目录",
    icon: "Grid2X2",
    view: SystemsPage,
    platform: true,
  },
  {
    id: "administrators",
    title: "管理员与范围",
    icon: "UserCog",
    view: AdministratorsPage,
    platform: true,
  },
  {
    id: "organization",
    title: "员工组织",
    icon: "Building2",
    view: OrganizationPage,
    platform: true,
  },
];
const page = ref(location.hash.replace("#/", "").split("?")[0] || "employees"),
  mobile = ref(false),
  passwordOpen = ref(false),
  help = ref(false),
  loginName = ref(""),
  password = ref(""),
  error = ref(""),
  busy = ref(false),
  ready = ref(false);
const current = computed(() => nav.find((n) => n.id === page.value));
const permitted = computed(() => !current.value?.platform || isSuper.value);
const contextNames = ref({ company: "", department: "" });
function hash() {
  page.value = location.hash.replace("#/", "").split("?")[0] || "employees";
  mobile.value = false;
}
onMounted(() => window.addEventListener("hashchange", hash));
onUnmounted(() => window.removeEventListener("hashchange", hash));
watch(
  () => state.appointment,
  async (a) => {
    contextNames.value = { company: "", department: "" };
    if (!a) return;
    try {
      const [c, d] = await Promise.all([
        api.call("listCompanies", { query: { page: 1, pageSize: 100 } }),
        api.call("listDepartments", {
          query: { companyId: a.companyId, page: 1, pageSize: 100 },
        }),
      ]);
      contextNames.value = {
        company: c.items.find((x) => x.id === a.companyId)?.name || a.companyId,
        department:
          d.items.find((x) => x.id === a.departmentId)?.name || a.departmentId,
      };
    } catch (e) {
      state.contextError = errorText(e);
    }
  },
);
watch(
  () => state.session,
  (s) => {
    if (!s) {
      ready.value = false;
      passwordOpen.value = false;
    }
  },
);
async function login() {
  busy.value = true;
  error.value = "";
  try {
    await establish(
      await api.call("login", {
        body: { loginName: loginName.value, password: password.value },
      }),
    );
    password.value = "";
    ready.value = true;
  } catch (e) {
    error.value = errorText(e);
    if (state.session) {
      state.contextError = error.value;
      ready.value = true;
    }
  } finally {
    busy.value = false;
  }
}
async function logout() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await api.call("logout");
    clearSession("已退出");
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function refreshContext() {
  try {
    await loadAppointments();
    chooseAppointment("");
  } catch (e) {
    state.contextError = errorText(e);
  }
}
</script>
<template>
  <div v-if="!state.session" class="login-page">
    <section class="login-story">
      <div class="brand">
        <UiIcon name="ShieldCheck" :size="32" /><strong>权限中心</strong>
      </div>
      <div>
        <h1>让权限有序，<br />让协作有界。</h1>
        <p>从部门到系统，从申请到生效，<br />每一份功能权限都有清晰的来源。</p>
        <div class="source-map">
          公司 / 部门 <UiIcon name="ChevronRight" /> 角色
          <UiIcon name="ChevronRight" /> 功能权限
        </div>
      </div>
      <small>集中权限管理系统</small>
    </section>
    <main class="login-main">
      <form class="login-card stack" @submit.prevent="login">
        <h2>登录权限中心</h2>
        <p class="hint">仅总管理员和权限管理员登录</p>
        <label class="field"
          >登录名<input
            v-model="loginName"
            required
            maxlength="100"
            autocomplete="username"
            autofocus /></label
        ><label class="field"
          >密码<input
            v-model="password"
            type="password"
            required
            maxlength="128"
            autocomplete="current-password"
        /></label>
        <p v-if="error || state.notice" role="alert" class="error-box">
          {{ error || state.notice }}
        </p>
        <button class="button primary" :disabled="busy">
          {{ busy ? "正在登录…" : "登录" }}
        </button>
        <p class="hint small">账号由总管理员提供。首次登录需要修改临时密码。</p>
      </form>
    </main>
  </div>
  <main
    v-else-if="state.session.mode === 'PASSWORD_CHANGE_ONLY'"
    class="password-page"
  >
    <section class="surface stack">
      <h1>首次登录，请修改密码</h1>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <PasswordForm /><button class="button" @click="logout">退出登录</button>
    </section>
  </main>
  <div v-else class="workspace">
    <div v-if="mobile" class="nav-scrim" @click="mobile = false" />
    <aside class="sidebar" :class="{ open: mobile }">
      <div class="brand">
        <UiIcon name="ShieldCheck" :size="30" />
        <div><strong>权限中心</strong><small>功能权限管理</small></div>
      </div>
      <nav aria-label="主导航">
        <template v-for="(n, i) in nav" :key="n.id"
          ><p v-if="i === 0" class="nav-section">授权管理</p>
          <p v-if="i === 3" class="nav-section">操作记录</p>
          <p v-if="i === 4 && isSuper" class="nav-section">平台管理</p>
          <a
            v-if="!n.platform || isSuper"
            :href="'#/' + n.id"
            :class="{ active: page === n.id }"
            :aria-current="page === n.id ? 'page' : undefined"
            ><UiIcon :name="n.icon" />{{ n.title }}</a
          ></template
        >
      </nav>
      <button class="sidebar-help" @click="help = true">
        <UiIcon name="HelpCircle" />使用说明
      </button>
    </aside>
    <div class="work-main">
      <header class="topbar">
        <button
          class="icon-button mobile-toggle"
          aria-label="打开导航"
          @click="mobile = !mobile"
        >
          <UiIcon name="Menu" /></button
        ><span>{{ current?.title || "页面不存在" }}</span>
        <div class="topbar-right">
          <span class="identity">{{ isSuper ? "总管理员" : "权限管理员" }}</span
          ><button class="button" @click="passwordOpen = true">修改密码</button
          ><button class="icon-button" aria-label="退出登录" @click="logout">
            <UiIcon name="LogOut" />
          </button>
        </div>
      </header>
      <main class="page-content">
        <p v-if="error" class="error-box" role="alert">{{ error }}</p>
        <section v-if="!isSuper" class="context-bar">
          <UiIcon name="Shield" />
          <div>
            <strong>当前管理范围</strong>
            <p v-if="state.appointment">
              {{ contextNames.company }} / {{ contextNames.department }} ·
              {{ state.appointment.systemIds.length }} 个系统
            </p>
          </div>
          <label
            >管理任命<select
              :value="state.appointment?.id || ''"
              @change="
                chooseAppointment(($event.target as HTMLSelectElement).value)
              "
            >
              <option value="">请选择任命</option>
              <option v-for="a in state.appointments" :key="a.id" :value="a.id">
                {{ a.companyId }} / {{ a.departmentId }} ·
                {{ a.systemIds.length }} 个系统
              </option>
            </select></label
          ><button class="button" @click="refreshContext">刷新任命</button>
        </section>
        <p v-if="state.contextError" class="error-box" role="alert">
          {{ state.contextError }}
        </p>
        <p v-if="!permitted" class="empty">此页面仅总管理员可使用</p>
        <p v-else-if="!current" class="empty">页面不存在，请选择左侧导航。</p>
        <p v-else-if="!isSuper && !state.appointment" class="empty">
          请选择有效管理任命后开始办理。
        </p>
        <component
          v-else-if="ready"
          :is="current.view"
          :key="page + ':' + state.epoch"
        />
        <p v-else class="empty">正在读取会话…</p>
      </main>
    </div>
  </div>
  <Panel
    v-if="passwordOpen && state.session"
    title="修改本人密码"
    @close="passwordOpen = false"
    ><PasswordForm
  /></Panel>
  <Panel v-if="help" title="使用说明" @close="help = false"
    ><div class="stack">
      <h3>先选范围，再办理授权</h3>
      <p>
        权限管理员每次选择一项管理任命，不能拼接不同任命的权限。公司级共享角色由总管理员维护，授予员工时仍有明确来源部门。
      </p>
      <h3>提交申请与生效</h3>
      <p>
        角色配置、分配和一般撤销需要先保存草稿、预览并提交
        OA。批准后由服务端执行；请在变更申请中核对执行结果。
      </p>
      <h3>关闭来源与全局停用</h3>
      <p>
        关闭部门来源直接关权；停用员工同时使其授权和后台资格失效，恢复员工不会恢复旧权限。
      </p>
      <h3>会话与业务系统</h3>
      <p>
        刷新页面后需重新登录。普通员工通过业务系统使用权限，权限中心不提供员工登录及服务令牌输入入口。
      </p>
    </div></Panel
  >
</template>
