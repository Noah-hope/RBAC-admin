import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUi = defineStore('ui', () => {
  const requestOpen = ref(false)
  const requestedCode = ref('')
  const requestRevision = ref(0)
  function askAccess(code = '') {
    requestedCode.value = code
    requestOpen.value = true
  }
  return { requestOpen, requestedCode, requestRevision, askAccess }
})
