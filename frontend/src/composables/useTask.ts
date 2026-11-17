import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useTask() {
  const busy = ref(false)
  const error = ref('')
  async function run<T>(action: () => Promise<T>, success?: string): Promise<T | undefined> {
    busy.value = true
    error.value = ''
    try {
      const result = await action()
      if (success) ElMessage.success(success)
      return result
    } catch (failure) {
      error.value = (failure as Error).message
    } finally {
      busy.value = false
    }
  }
  return { busy, error, run }
}
export async function confirmAction(message: string, title: string) {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}
export const formatTime = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '—'
export const statusLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  SATISFIED: '已满足',
  WITHDRAWN: '已撤回',
  ACTIVE: '正常',
  DISABLED: '已停用',
}
