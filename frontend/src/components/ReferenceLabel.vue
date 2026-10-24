<script setup lang="ts">
import { ref, watch } from "vue";
import { resolveName, type ReferenceKind } from "../references";
const props = defineProps<{ id: string | null; kind: ReferenceKind }>();
const name = ref("");
let sequence = 0;
watch(
  () => [props.id, props.kind],
  async () => {
    const ticket = ++sequence;
    name.value = "";
    if (props.id) {
      const value = await resolveName(props.kind, props.id);
      if (ticket === sequence) name.value = value;
    }
  },
  { immediate: true },
);
</script>
<template>
  <span :title="id || undefined">{{ name || id || "—" }}</span>
</template>
