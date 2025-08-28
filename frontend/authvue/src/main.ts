import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

if (import.meta.env.DEV) {
  // モックを使うときはコメント解除＋frontend\authvue\.env.developmentを編集
  // const { worker } = await import('@/mocks/worker')
  // worker.start()
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
