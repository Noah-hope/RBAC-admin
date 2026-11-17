<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Key } from '@element-plus/icons-vue'
import { api } from '../api'
import type { MyPermissions } from '../api/types'
import { useSession } from '../stores/session'
import { useUi } from '../stores/ui'
import { useTask } from '../composables/useTask'
import TaskError from '../components/TaskError.vue'
const { busy, error, run } = useTask(),
  session = useSession(),
  ui = useUi()
const data = ref<MyPermissions>({ businessRoles: [], applications: [] }),
  selectedApp = ref<number>()
async function load() {
  await run(async () => {
    data.value = await api.mine()
    await session.refresh()
    selectedApp.value ??= data.value.applications[0]?.applicationId
  })
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>我的权限</h1>
      <p>查看业务角色，以及各应用中可使用的菜单和按钮。</p>
    </div>
    <el-button @click="ui.askAccess()">申请权限</el-button>
  </div>
  <TaskError :message="error" />
  <section class="panel role-overview">
    <div>
      <span class="overview-icon">
        <el-icon><Key /></el-icon>
      </span>
      <div>
        <h2>我的业务角色</h2>
        <p class="muted">有效权限由全部业务角色及其继承角色合并获得。</p>
      </div>
    </div>
    <div class="tag-list">
      <el-tag v-for="role in data.businessRoles" :key="role.id" type="success" effect="plain">
        {{ role.name }}
      </el-tag>
      <span v-if="!data.businessRoles.length" class="muted">暂未分配业务角色</span>
    </div>
  </section>
  <section class="panel" v-loading="busy">
    <div class="panel-heading">
      <h2>按应用查看权限</h2>
      <el-button text @click="load">
        <el-icon><Refresh /></el-icon>
        刷新权限
      </el-button>
    </div>
    <div
      v-if="data.applications.length"
      class="permission-app-tabs"
      role="tablist"
      aria-label="我的应用"
    >
      <button
        v-for="app in data.applications"
        :key="app.applicationId"
        type="button"
        role="tab"
        :aria-selected="selectedApp === app.applicationId"
        :class="{ active: selectedApp === app.applicationId }"
        @click="selectedApp = app.applicationId"
      >
        {{ app.applicationName }}
        <span>{{ app.menus.length }} 个菜单</span>
      </button>
    </div>
    <template
      v-for="app in data.applications.filter((a) => a.applicationId === selectedApp)"
      :key="app.applicationId"
    >
      <div v-for="menu in app.menus" :key="menu.id" class="my-menu">
        <div class="section-heading">
          <div>
            <h3>{{ menu.name }}</h3>
            <p class="muted">{{ menu.code }}</p>
          </div>
          <span class="muted">来源：{{ menu.sourceRoles.map((s) => s.name).join('、') }}</span>
        </div>
        <el-table :data="menu.buttons" empty-text="此菜单下尚无可用按钮">
          <el-table-column prop="name" label="可用按钮" min-width="130" />
          <el-table-column prop="code" label="权限标识" min-width="180" />
          <el-table-column label="配置来源角色" min-width="180">
            <template #default="{ row }">
              {{ row.sourceRoles.map((r: { name: string }) => r.name).join('、') }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
    <el-empty
      v-if="!data.applications.length && !busy && !error"
      description="尚未获得业务权限，可提交申请联系管理员处理。"
    />
  </section>
</template>
