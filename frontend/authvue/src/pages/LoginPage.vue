<template>
  <div class="flex flex-col items-center justify-center min-h-screen bg-gray-100">
    <div class="bg-white shadow-md rounded-lg p-6 w-80">
      <h1 class="text-xl font-bold mb-4">login</h1>
      <form @submit.prevent="handleLogin">
        <label class="block mb-2">
          id
          <input v-model="loginId" type="text" class="w-full border px-2 py-1 rounded" />
        </label>

        <label class="block mb-4">
          pass
          <input v-model="password" type="password" class="w-full border px-2 py-1 rounded" />
        </label>

        <button
          type="submit"
          class="w-full bg-blue-500 hover:bg-blue-600 text-white font-semibold py-2 px-4 rounded"
        >
          submit
        </button>
      </form>
      <p v-if="errorMessage" class="text-red-500 text-sm mt-2">{{ errorMessage }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiPost } from '../api/client'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loginId = ref('test')
const password = ref('pass')
const errorMessage = ref('')

async function handleLogin() {
  try {
    const response = await apiPost('/login', {
      loginId: loginId.value,
      password: password.value,
    })

    auth.login(response.token)
    router.push('/dashboard')
  } catch (err: unknown) {
    errorMessage.value = 'error login'
    console.error(err)
  }
}
</script>
