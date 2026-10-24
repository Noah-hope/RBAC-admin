<script setup lang="ts">
import { ref } from "vue";
import { api, errorText } from "../state";
import type { AuditDetail } from "../api/schema";
import DataTable from "../components/DataTable.vue";
import Panel from "../components/Panel.vue";
import RecordDetails from "../components/RecordDetails.vue";
const eventType = ref(""),
  requestId = ref(""),
  from = ref(""),
  to = ref(""),
  query = ref<Record<string, unknown>>({}),
  detail = ref<AuditDetail>(),
  error = ref("");
function search() {
  error.value = "";
  if (from.value && to.value && from.value >= to.value) {
    error.value = "结束时间必须晚于开始时间";
    return;
  }
  query.value = {
    eventType: eventType.value,
    requestId: requestId.value,
    from: from.value ? new Date(from.value).toISOString() : "",
    to: to.value ? new Date(to.value).toISOString() : "",
  };
}
async function open(id: string) {
  try {
    detail.value = await api.call("getAudit", { path: { auditEventId: id } });
  } catch (e) {
    error.value = errorText(e);
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>审计记录</h1>
      <p>只读核对操作、固定申请与实际执行结果。</p>
    </div>
  </div>
  <form class="filters" @submit.prevent="search">
    <label class="field"
      >事件类型<input
        v-model="eventType"
        maxlength="100"
        placeholder="如 ROLE_CREATED" /></label
    ><label class="field"
      >请求号<input v-model="requestId" maxlength="64" /></label
    ><label class="field"
      >开始时间<input v-model="from" type="datetime-local" /></label
    ><label class="field"
      >结束时间（不包含）<input v-model="to" type="datetime-local" /></label
    ><button class="button">筛选</button>
  </form>
  <p v-if="error" class="error-box" role="alert">{{ error }}</p>
  <DataTable
    operation="listAudit"
    :query="query"
    :search="false"
    :columns="[
      { key: 'occurredAt', label: '发生时间' },
      { key: 'eventType', label: '事件' },
      { key: 'actorId', label: '操作者' },
      { key: 'reason', label: '原因' },
      { key: 'result', label: '结果' },
    ]"
    ><template #actions="{ row }"
      ><button class="link" @click="open(row.id)">详情</button></template
    ></DataTable
  ><Panel v-if="detail" title="审计详情" wide @close="detail = undefined"
    ><div class="stack">
      <RecordDetails :value="detail.event" />
      <h3>字段差异</h3>
      <div v-for="(diff, i) in detail.differences" :key="i" class="line-card">
        <RecordDetails :value="diff" />
      </div>
      <RecordDetails
        :value="{
          originalSubmitterId: detail.originalSubmitterId,
          oaApprovalId: detail.oaApprovalId,
          submittedVersion: detail.submittedVersion,
          failureCode: detail.failureCode,
        }"
      /></div
  ></Panel>
</template>
