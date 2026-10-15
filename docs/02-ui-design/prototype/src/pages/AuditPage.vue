<script setup lang="ts">
import { computed, ref } from 'vue';
import type { Audit } from '../data';
import { allowedSystems, deptName, systemName, visibleAudits } from '../store';
import UiIcon from '../components/UiIcon.vue';
import UiDialog from '../components/UiDialog.vue';
import StatusBadge from '../components/StatusBadge.vue';
const query = ref('');
const system = ref('all');
const detail = ref<Audit>();
const rows = computed(() =>
  visibleAudits.value.filter(
    (a) =>
      (system.value === 'all' || a.system === system.value) &&
      `${a.id} ${a.action} ${a.target} ${a.actor} ${a.request ?? ''}`
        .toLowerCase()
        .includes(query.value.toLowerCase()),
  ),
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>审计记录</h1>
      <p>追溯授权来源、申请与实际执行，历史记录只读保留。</p>
    </div>
    <span class="subtle-label"><UiIcon name="Lock" :size="15" />只读记录</span>
  </div>
  <div class="panel">
    <div class="table-toolbar">
      <label class="search-field wide-search"
        ><UiIcon name="Search" :size="17" /><input
          v-model="query"
          placeholder="搜索员工、事件、操作者、申请编号"
          aria-label="搜索审计记录" /></label
      ><select v-model="system" aria-label="审计系统筛选">
        <option value="all">全部获准系统</option>
        <option v-for="s in allowedSystems" :key="s" :value="s">{{ systemName(s) }}</option>
      </select>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>事件</th>
            <th>操作目标</th>
            <th>来源 / 系统</th>
            <th>操作者</th>
            <th>发生时间</th>
            <th>结果</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in rows" :key="a.id">
            <td>
              <strong>{{ a.action }}</strong
              ><small>{{ a.id }}</small>
            </td>
            <td>{{ a.target }}</td>
            <td>
              {{ a.global ? '平台管理事件' : deptName(a.dept)
              }}<small>{{ systemName(a.system) }}</small>
            </td>
            <td>{{ a.actor }}</td>
            <td class="muted small nowrap">{{ a.time }}</td>
            <td><StatusBadge :value="a.result" /></td>
            <td class="align-right">
              <button class="text-button" @click="detail = a">
                详情<UiIcon name="ChevronRight" :size="14" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!rows.length" class="empty-inline">
      <UiIcon name="History" :size="30" /><strong>没有匹配的审计记录</strong>
      <p>调整关键词或系统范围后重试。</p>
    </div>
    <div class="table-footer">
      <span>仅展示获准来源与系统的事件，不按原申请人隔离</span><span>{{ rows.length }} 条记录</span>
    </div>
  </div>
  <UiDialog
    :open="Boolean(detail)"
    title="审计事件详情"
    :subtitle="detail?.id"
    drawer
    @close="detail = undefined"
    ><template v-if="detail"
      ><div class="stack">
        <div class="flex items-center justify-between">
          <h3>{{ detail.action }}</h3>
          <StatusBadge :value="detail.result" />
        </div>
        <dl class="detail-list">
          <div>
            <dt>操作目标</dt>
            <dd>{{ detail.target }}</dd>
          </div>
          <div>
            <dt>操作者</dt>
            <dd>{{ detail.actor }}</dd>
          </div>
          <div>
            <dt>时间</dt>
            <dd>{{ detail.time }}</dd>
          </div>
          <div>
            <dt>来源</dt>
            <dd>{{ detail.global ? '平台管理事件' : deptName(detail.dept) }}</dd>
          </div>
          <div>
            <dt>系统</dt>
            <dd>{{ systemName(detail.system) }}</dd>
          </div>
          <div>
            <dt>办理原因</dt>
            <dd>{{ detail.reason }}</dd>
          </div>
          <div v-if="detail.request">
            <dt>关联申请</dt>
            <dd>{{ detail.request }}</dd>
          </div>
        </dl>
        <h3>事件内容</h3>
        <div class="diff-line"><UiIcon name="FileText" />{{ detail.detail }}</div>
        <div class="info-box">
          <UiIcon name="Lock" />
          <p>历史不代表当前权限；本页面不能修改、重放或删除事件。</p>
        </div>
      </div></template
    ></UiDialog
  >
</template>
