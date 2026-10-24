<script setup lang="ts">
import { ref } from "vue";
import { api, errorText } from "../state";
import { Mutation } from "../api/client";
import type { ChangeRequest, ChangePreview, Role } from "../api/schema";
import type { FormSpec } from "../forms";
import { reason } from "../forms";
import DataTable from "../components/DataTable.vue";
import Panel from "../components/Panel.vue";
import RecordDetails from "../components/RecordDetails.vue";
import ImpactView from "../components/ImpactView.vue";
import ActionForm from "../components/ActionForm.vue";
import ConfigurationEditor from "../components/ConfigurationEditor.vue";
import GrantComposer from "../components/GrantComposer.vue";
import { display } from "../labels";
const table = ref<InstanceType<typeof DataTable>>(),
  detail = ref<ChangeRequest>(),
  preview = ref<ChangePreview>(),
  filter = ref(""),
  error = ref(""),
  message = ref(""),
  busy = ref(false),
  form = ref<FormSpec>(),
  configRole = ref<Role>(),
  editing = ref<ChangeRequest>(),
  grantEdit = ref<ChangeRequest>();
const mutation = new Mutation();
async function open(row: ChangeRequest) {
  error.value = "";
  preview.value = undefined;
  try {
    detail.value = await api.call("getChangeRequest", {
      path: { requestId: row.id },
    });
  } catch (e) {
    error.value = errorText(e);
  }
}
async function readPreview() {
  if (!detail.value) return;
  busy.value = true;
  error.value = "";
  try {
    preview.value = await api.call("changePreview", {
      path: { requestId: detail.value.id },
      body: { expectedDraftVersion: detail.value.draftVersion },
    });
    mutation.clear();
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (!detail.value || !preview.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (Date.parse(preview.value.preview.expiresAt) <= Date.now())
      throw new Error("预览已过期，请重新预览");
    const body = {
      expectedDraftVersion: preview.value.draftVersion,
      previewId: preview.value.preview.id,
    };
    const r = await api.call("submitChange", {
      path: { requestId: detail.value.id },
      body,
      key: mutation.key(body),
    });
    message.value =
      r.outcome === "SUBMITTED"
        ? "已提交 OA，批准执行后才会生效"
        : "没有实际变化，未提交 OA";
    detail.value = undefined;
    preview.value = undefined;
    table.value?.reload();
  } catch (e) {
    error.value = errorText(e);
  } finally {
    busy.value = false;
  }
}
async function edit(d: ChangeRequest) {
  error.value = "";
  try {
    const r = await api.call("getChangeRequest", { path: { requestId: d.id } });
    if (r.content.kind === "ROLE_CONFIGURATION") {
      configRole.value = await api.call("getRole", {
        path: { roleId: r.content.roleId },
      });
      editing.value = r;
    } else if (r.content.kind === "ROLE_GRANT") grantEdit.value = r;
    else
      form.value = {
        title: "编辑撤销草稿",
        description: "保留原固定撤销明细。需变更目标时删除草稿后重新选择。",
        initial: { reason: r.reason },
        fields: [reason],
        save: (v, key) =>
          api.call("updateChangeDraft", {
            path: { requestId: r.id },
            body: {
              expectedDraftVersion: r.draftVersion,
              content: r.content,
              reason: v.reason,
            },
            key,
          }),
        done: () => {
          detail.value = undefined;
        },
      };
  } catch (e) {
    error.value = errorText(e);
  }
}
function remove(d: ChangeRequest) {
  form.value = {
    title: "删除未提交草稿",
    description: "删除后不能恢复该草稿，不会撤回已提交申请。",
    fields: [],
    submitLabel: "确认删除",
    save: (_v, key) =>
      api.call("deleteChangeDraft", {
        path: { requestId: d.id },
        query: { expectedDraftVersion: d.draftVersion },
        key,
      }),
    done: () => {
      detail.value = undefined;
    },
  };
}
function retry(d: ChangeRequest) {
  form.value = {
    title: "重试 OA 投递",
    description: "仍投递同一份固定申请，不重新生成申请。",
    fields: [reason],
    save: (v, key) =>
      api.call("retryOaDelivery", {
        path: { requestId: d.id },
        body: { expectedStatusVersion: d.statusVersion, reason: v.reason },
        key,
      }),
    done: () => {
      detail.value = undefined;
    },
  };
}
async function copy(d: ChangeRequest) {
  if (d.content.kind === "ROLE_REVOCATION") {
    form.value = {
      title: "复制撤销申请",
      description:
        "创建新的草稿，原明细仍需后端预览校验；若关系已变化，请重新从员工授权选择。",
      fields: [reason],
      save: (v, key) =>
        api.call("createChangeDraft", {
          body: {
            content: d.content,
            reason: v.reason,
            copiedFromRequestId: d.id,
          },
          key,
        }),
      done: () => {
        detail.value = undefined;
      },
    };
  } else if (d.content.kind === "ROLE_CONFIGURATION") {
    try {
      configRole.value = await api.call("getRole", {
        path: { roleId: d.content.roleId },
      });
      editing.value = undefined;
    } catch (e) {
      error.value = errorText(e);
    }
  } else {
    grantEdit.value = undefined;
    newGrant.value = true;
  }
}
const newGrant = ref(false);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>变更申请</h1>
      <p>分别核对 OA 投递、审批结果和实际执行。批准不等于已生效。</p>
    </div>
  </div>
  <div class="filters">
    <label class="field"
      >申请状态<select v-model="filter">
        <option value="">全部状态</option>
        <option
          v-for="s in [
            'DRAFT',
            'PENDING',
            'APPROVED',
            'EXECUTED',
            'REJECTED',
            'EXECUTION_FAILED',
            'INVALIDATED',
          ]"
          :key="s"
          :value="s"
        >
          {{ display(s) }}
        </option>
      </select></label
    >
  </div>
  <p v-if="message" class="success" role="status">{{ message }}</p>
  <p v-if="error && !detail" class="error-box" role="alert">{{ error }}</p>
  <DataTable
    ref="table"
    operation="listChangeRequests"
    :query="{ state: filter }"
    :columns="[
      { key: 'content.kind', label: '申请类型' },
      { key: 'reason', label: '办理原因' },
      { key: 'deliveryStatus', label: 'OA 投递' },
      { key: 'approvalResult', label: '审批' },
      { key: 'executionStatus', label: '执行' },
      { key: 'state', label: '状态' },
    ]"
    ><template #actions="{ row }"
      ><button class="link" @click="open(row)">详情</button></template
    ></DataTable
  ><Panel
    v-if="detail"
    title="申请详情"
    wide
    :busy="busy"
    @close="
      detail = undefined;
      preview = undefined;
    "
    ><div class="stack">
      <RecordDetails
        :value="{
          id: detail.id,
          kind: detail.content.kind,
          reason: detail.reason,
          state: detail.state,
          deliveryStatus: detail.deliveryStatus,
          approvalResult: detail.approvalResult,
          executionStatus: detail.executionStatus,
          oaApprovalId: detail.oaApprovalId,
          failureCode: detail.failureCode,
          nextRetryAt: detail.nextRetryAt,
        }"
      />
      <details open>
        <summary>申请固定内容</summary>
        <RecordDetails :value="detail.content" />
      </details>
      <details>
        <summary>提交依据与版本</summary>
        <RecordDetails
          :value="{
            draftVersion: detail.draftVersion,
            submittedVersion: detail.submittedVersion,
            contentDigest: detail.contentDigest,
            originalAuthority: detail.originalAuthority,
          }"
        />
      </details>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <div v-if="detail.state === 'DRAFT'" class="toolbar-actions">
        <button class="button" :disabled="busy" @click="edit(detail)">
          编辑草稿</button
        ><button class="button danger" :disabled="busy" @click="remove(detail)">
          删除草稿</button
        ><button class="button primary" :disabled="busy" @click="readPreview">
          预览并提交
        </button>
      </div>
      <button
        v-if="
          detail.deliveryStatus === 'SEND_FAILED' && detail.state === 'PENDING'
        "
        class="button"
        @click="retry(detail)"
      >
        重试 OA 投递</button
      ><button
        v-if="detail.state === 'REJECTED' || detail.state === 'INVALIDATED'"
        class="button"
        @click="copy(detail)"
      >
        重新发起申请
      </button>
      <section v-if="preview" class="stack">
        <ImpactView :preview="preview.preview" />
        <p v-if="preview.noChangeItems.length" class="warning">
          {{
            preview.noChangeItems.length
          }}
          条无变化明细将被排除；确认以本次有效明细提交。
        </p>
        <details>
          <summary>实际提交内容</summary>
          <RecordDetails
            v-if="preview.effectiveContent"
            :value="preview.effectiveContent"
          />
          <p v-else>全部无变化，不会投递 OA。</p>
        </details>
        <button class="button primary" :disabled="busy" @click="submit">
          确认提交 OA
        </button>
      </section>
      <details>
        <summary>事件时间线</summary>
        <DataTable
          operation="listChangeEvents"
          :params="{ requestId: detail.id }"
          :search="false"
          :columns="[
            { key: 'occurredAt', label: '时间' },
            { key: 'eventType', label: '事件' },
            { key: 'message', label: '说明' },
          ]"
        />
      </details>
      <button class="button" :disabled="busy" @click="open(detail)">
        刷新申请状态
      </button>
    </div></Panel
  ><ActionForm
    v-if="form"
    :spec="form"
    @close="form = undefined"
    @saved="table?.reload()"
  /><ConfigurationEditor
    v-if="configRole"
    :role="configRole"
    :draft="editing"
    @close="configRole = undefined"
    @saved="
      detail = undefined;
      table?.reload();
    "
  /><GrantComposer
    v-if="grantEdit || newGrant"
    :draft="grantEdit"
    @close="
      grantEdit = undefined;
      newGrant = false;
    "
    @saved="
      detail = undefined;
      table?.reload();
    "
  />
</template>
