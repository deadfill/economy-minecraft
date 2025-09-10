<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8">
      <div>
        <div class="mx-auto h-12 w-12 flex items-center justify-center rounded-full bg-primary-100">
          <span class="text-2xl">🏭</span>
        </div>
        <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900">
          Economy Admin Panel
        </h2>
        <p class="mt-2 text-center text-sm text-gray-600">
          Войдите в систему управления
        </p>
      </div>

      <form class="mt-8 space-y-6" @submit.prevent="handleLogin">
        <div class="rounded-md shadow-sm -space-y-px">
          <div>
            <label for="username" class="sr-only">Имя пользователя</label>
            <input
              id="username"
              v-model="form.username"
              name="username"
              type="text"
              required
              class="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-t-md focus:outline-none focus:ring-primary-500 focus:border-primary-500 focus:z-10 sm:text-sm"
              placeholder="Имя пользователя"
              :disabled="isLoading"
            />
          </div>
          <div>
            <label for="password" class="sr-only">Пароль</label>
            <input
              id="password"
              v-model="form.password"
              name="password"
              type="password"
              required
              class="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-b-md focus:outline-none focus:ring-primary-500 focus:border-primary-500 focus:z-10 sm:text-sm"
              placeholder="Пароль"
              :disabled="isLoading"
            />
          </div>
        </div>

        <!-- Error message -->
        <div v-if="error" class="rounded-md bg-danger-50 p-4">
          <div class="flex">
            <div class="flex-shrink-0">
              <svg class="h-5 w-5 text-danger-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div class="ml-3">
              <h3 class="text-sm font-medium text-danger-800">
                Ошибка входа
              </h3>
              <div class="mt-2 text-sm text-danger-700">
                {{ error }}
              </div>
            </div>
          </div>
        </div>

        <div>
          <button
            type="submit"
            class="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            :disabled="isLoading || !form.username || !form.password"
          >
            <span class="absolute left-0 inset-y-0 flex items-center pl-3">
              <svg
                v-if="!isLoading"
                class="h-5 w-5 text-primary-500 group-hover:text-primary-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
              </svg>
              <div
                v-else
                class="animate-spin h-5 w-5 border-2 border-white border-t-transparent rounded-full"
              ></div>
            </span>
            {{ isLoading ? 'Вход...' : 'Войти' }}
          </button>
        </div>

        <!-- Demo credentials -->
        <div class="mt-6">
          <div class="relative">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-gray-300" />
            </div>
            <div class="relative flex justify-center text-sm">
              <span class="px-2 bg-gray-50 text-gray-500">Тестовые учетные записи</span>
            </div>
          </div>

          <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <button
              type="button"
              @click="fillCredentials('admin', 'admin123')"
              class="w-full inline-flex justify-center py-2 px-4 border border-gray-300 rounded-md shadow-sm bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 transition-colors"
              :disabled="isLoading"
            >
              👑 Admin
            </button>
            <button
              type="button"
              @click="fillCredentials('developer', 'dev456')"
              class="w-full inline-flex justify-center py-2 px-4 border border-gray-300 rounded-md shadow-sm bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 transition-colors"
              :disabled="isLoading"
            >
              👨‍💻 Developer
            </button>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const form = ref({
  username: '',
  password: ''
})

const isLoading = computed(() => authStore.isLoading)
const error = computed(() => authStore.error)

const handleLogin = async () => {
  try {
    await authStore.login({
      username: form.value.username,
      password: form.value.password
    })
    
    // Redirect to dashboard on success
    router.push('/')
  } catch (err) {
    // Error is handled by the store
    console.error('Login failed:', err)
  }
}

const fillCredentials = (username: string, password: string) => {
  form.value.username = username
  form.value.password = password
}
</script>
