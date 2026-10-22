<script setup lang="ts">
import { computed } from "vue";
import { materialSymbols } from "../material-symbols";
const props = withDefaults(defineProps<{ name: string; size?: number }>(), {
  size: 24,
});
const symbol = computed(() => materialSymbols[props.name] ?? "circle");
</script>
<template>
  <span
    class="ui-icon material-symbol shrink-0"
    aria-hidden="true"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      '--symbol': `url(/assets/symbols/${symbol}.svg)`,
    }"
  />
</template>
