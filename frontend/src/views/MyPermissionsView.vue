<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api'
import type { MyPermissions } from '../api/types'
import { useSession } from '../stores/session'
import { useUi } from '../stores/ui'
import { useTask } from '../composables/useTask'
import TaskError from '../components/TaskError.vue'
const { busy, error, run } = useTask(),
  session = useSession(),
  ui = useUi()
const data = ref<MyPermissions>({ businessRoles: [], applications: [] })
async function load() {
  await run(async () => {
    data.value = await api.mine()
    await session.refresh()
  })
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>我的权限</h1>
      <p>按应用查看直接与继承获得的权限，同一权限只展示一次。</p>
    </div>
    <el-button @click="ui.askAccess()">申请权限</el-button>
  </div>
  <TaskError :message="error" />
  <section class="panel profile">
    <span class="avatar" aria-hidden="true">{{ session.user?.name.slice(0, 1) }}</span>
    <div>
      <h2>{{ session.user?.name }}</h2>
      <p class="muted">{{ session.user?.no }}</p>
    </div>
    <div class="tag-list">
      <el-tag v-for="role in data.businessRoles" :key="role.id" type="success">
        {{ role.name }}
      </el-tag>
      <span v-if="!data.businessRoles.length" class="muted">暂未分配业务角色</span>
    </div>
    <el-button text @click="load">
      <el-icon><Refresh /></el-icon>
      刷新权限
    </el-button>
  </section>
  <div class="permission-app-grid" v-loading="busy">
    <section
      v-for="app in data.applications"
      :key="app.applicationId"
      class="panel permission-card"
    >
      <div class="panel-heading">
        <span class="app-mark" aria-hidden="true">{{ app.applicationName.slice(0, 1) }}</span>
        <h2>{{ app.applicationName }}</h2>
        <span class="muted app-count">{{ app.menus.length }} 个菜单</span>
      </div>
      <div v-for="menu in app.menus" :key="menu.id" class="owned-menu">
        <div class="owned-menu-heading">
          <h3>{{ menu.name }}</h3>
          <code class="permission-code">{{ menu.code }}</code>
        </div>
        <p class="muted permission-source">
          配置来源：{{ menu.sourceRoles.map((source) => source.name).join('、') || '登录基础能力' }}
        </p>
        <div v-if="menu.buttons.length" class="owned-buttons">
          <div v-for="button in menu.buttons" :key="button.id" class="owned-button">
            <el-tag type="success">{{ button.name }}</el-tag>
            <code class="permission-code">{{ button.code }}</code>
            <span class="muted">
              来源：{{
                button.sourceRoles.map((source) => source.name).join('、') || '登录基础能力'
              }}
            </span>
          </div>
        </div>
        <p v-else class="muted">此菜单下尚无可用按钮</p>
      </div>
      <p v-if="!app.menus.length" class="empty-note">尚未获得此应用的菜单权限。</p>
    </section>
  </div>
  <el-empty
    v-if="!data.applications.length && !busy && !error"
    description="尚未获得业务权限，可提交申请联系管理员处理。"
  />
</template>
