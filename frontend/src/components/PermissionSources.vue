<script setup lang="ts">
import type { EffectivePermission } from '../api/types'
defineProps<{
  permissions: EffectivePermission[]
  applications?: { applicationId: number; applicationName: string }[]
}>()
const labels = { DIRECT: '直接', INHERITED: '继承', BOTH: '直接 + 继承' }
</script>
<template>
  <el-table :data="permissions" empty-text="尚未配置有效权限" class="source-table">
    <el-table-column v-if="applications" label="所属应用" min-width="130">
      <template #default="{ row }">
        {{ applications.find((app) => app.applicationId === row.applicationId)?.applicationName }}
      </template>
    </el-table-column>
    <el-table-column prop="name" label="权限名称" min-width="140" />
    <el-table-column prop="code" label="权限标识" min-width="155" />
    <el-table-column label="获得方式" min-width="125">
      <template #default="{ row }">
        <el-tag :type="row.acquisition === 'DIRECT' ? 'info' : 'success'" effect="plain">
          {{ labels[row.acquisition as keyof typeof labels] }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="配置来源角色" min-width="180">
      <template #default="{ row }">
        <span v-if="!row.sourceRoles.length">当前申请配置</span>
        <span v-else>{{ row.sourceRoles.map((r: { name: string }) => r.name).join('、') }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>
