<script setup lang="ts">
import type { Preview } from "../api/schema";
import { display, fieldLabel, date } from "../labels";
import DataTable from "./DataTable.vue";
defineProps<{ preview: Preview }>();
</script>
<template>
  <section class="impact">
    <h3>影响预览</h3>
    <p>有效期至 {{ date(preview.expiresAt) }}。确认后按这份内容办理。</p>
    <p v-if="preview.impact.hasUndisclosedImpact" class="warning">
      当前仅展示可见范围，另有不可见影响，不代表全量统计。
    </p>
    <dl class="impact-grid">
      <template v-for="(v, k) in preview.impact" :key="k"
        ><template v-if="typeof v === 'number'"
          ><dt>{{ fieldLabel(k) }}</dt>
          <dd>{{ v }}</dd></template
        ></template
      >
    </dl>
    <p v-if="preview.impact.warning" class="warning">
      {{ preview.impact.warning }}
    </p>
    <p v-for="w in preview.warnings" :key="w" class="warning">{{ w }}</p>
    <details>
      <summary>受影响员工</summary>
      <DataTable
        operation="listPreviewEmployees"
        :params="{ previewId: preview.id }"
        :search="false"
        :columns="[
          { key: 'employeeId', label: '员工' },
          { key: 'canAccessBefore', label: '变更前可进入' },
          { key: 'canAccessAfter', label: '变更后可进入' },
          { key: 'resultCompleteness', label: '可见性' },
        ]"
      />
    </details>
    <details>
      <summary>其他受影响对象</summary>
      <DataTable
        operation="listPreviewTargets"
        :params="{ previewId: preview.id }"
        :search="false"
        :columns="[
          { key: 'targetType', label: '类型' },
          { key: 'summary', label: '说明' },
          { key: 'effect', label: '影响' },
        ]"
      />
    </details>
  </section>
</template>
