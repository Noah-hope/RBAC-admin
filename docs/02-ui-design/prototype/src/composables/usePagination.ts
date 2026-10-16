import { computed, ref, watch, type Ref } from 'vue';
export function usePagination<T>(items: Ref<T[]>) {
  const currentPage = ref(1);
  const pageSize = ref(10);
  watch(items, () => (currentPage.value = 1));
  const rows = computed(() =>
    items.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value),
  );
  return { currentPage, pageSize, rows };
}
