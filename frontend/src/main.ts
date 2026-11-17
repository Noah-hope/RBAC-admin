import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElIcon,
  ElInput,
  ElLoading,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
} from 'element-plus'
import 'element-plus/dist/index.css'
import './styles.css'
import App from './App.vue'
import { router } from './router'
import { useSession } from './stores/session'

const app = createApp(App)
app.use(createPinia())
for (const component of [
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElIcon,
  ElInput,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
])
  app.use(component)
app.use(ElLoading)
const session = useSession()
watch(
  () => session.user,
  (user, old) => {
    if (old && !user && router.currentRoute.value.path !== '/login')
      void router.replace({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath },
      })
  },
)
app.use(router)
app.mount('#app')
