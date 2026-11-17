<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { OperationLog, Page } from '../api/types'
import { formatTime, useTask } from '../composables/useTask'
import TaskError from '../components/TaskError.vue'
const { busy, error, run } = useTask(),
  page = ref(1),
  keyword = ref('')
const rows = ref<Page<OperationLog>>({ items: [], page: 1, pageSize: 20, total: 0 })
async function load() {
  await run(async () => {
    rows.value = await api.logs({ page: page.value, keyword: keyword.value })
  })
}
onMounted(load)
</script>
<template>
  <div class="page-head">
    <div>
      <h1>操作记录</h1>
      <p>查看管理范围内的重要操作。记录范围按操作发生时的部门确定。</p>
    </div>
    <el-button @click="load">刷新</el-button>
  </div>
  <TaskError :message="error" />
  <section class="panel" v-loading="busy">
    <form
      class="toolbar"
      @submit.prevent="
        () => {
          page = 1
          load()
        }
      "
    >
      <div class="toolbar-main">
        <el-input
          v-model="keyword"
          maxlength="100"
          placeholder="搜索操作人、对象或内容"
          aria-label="搜索操作记录"
          clearable
        />
        <el-button native-type="submit">搜索</el-button>
      </div>
      <span class="muted">共 {{ rows.total }} 条记录</span>
    </form>
    <el-table :data="rows.items" empty-text="暂无匹配的操作记录。">
      <el-table-column label="操作时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column prop="actorName" label="操作人" min-width="110" />
      <el-table-column prop="action" label="操作" min-width="130" />
      <el-table-column label="操作对象" min-width="150">
        <template #default="{ row }">{{ row.targetNames.join('、') }}</template>
      </el-table-column>
      <el-table-column prop="detail" label="内容" min-width="250" />
      <el-table-column label="结果" min-width="100">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">
            {{ row.result === 'SUCCESS' ? '成功' : row.result }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="rows.total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </section>
</template>
