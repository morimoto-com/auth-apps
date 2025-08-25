<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { apiGet } from '../api/client'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')

onMounted(async () => {
  try {
    const res = await apiGet('/userinfo')
    username.value = res.username
  } catch (err) {
    console.error('ユーザ情報取得失敗', err)
    router.push('/login')
  }
})

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="flex flex-col items-center justify-center min-h-screen bg-green-50">
    <div class="bg-white shadow-md rounded-lg p-6 w-96 text-center">
      <h1 class="text-2xl font-bold mb-4">ダッシュボード</h1>
      <p class="mb-4">ユーザー名: {{ username }}</p>

      <button
        @click="handleLogout"
        class="bg-red-500 hover:bg-red-600 text-white font-semibold py-2 px-4 rounded"
      >
        ログアウト
      </button>
    </div>
  </div>
</template>
