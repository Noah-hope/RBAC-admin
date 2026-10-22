<script setup lang="ts">
import { display, fieldLabel, date } from "../labels";
defineProps<{ value: object }>();
</script>
<template>
  <dl class="details">
    <template v-for="(v, k) in value" :key="k"
      ><dt>{{ fieldLabel(String(k)) }}</dt>
      <dd>{{ String(k).endsWith("At") ? date(v) : display(v) }}</dd></template
    >
  </dl>
</template>
