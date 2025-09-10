<template>
  <div id="app" class="min-h-screen bg-gray-50">
    <!-- Loading overlay -->
    <div v-if="isInitializing" class="fixed inset-0 bg-white bg-opacity-75 flex items-center justify-center z-50">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
        <p class="mt-2 text-sm text-gray-600">Загрузка...</p>
      </div>
    </div>

    <!-- Main content -->
    <div v-else>
      <!-- Navigation bar (only for authenticated users) -->
      <nav v-if="authStore.isAuthenticated" class="bg-white shadow-sm border-b border-gray-200">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div class="flex justify-between h-16">
            <!-- Logo and main navigation -->
            <div class="flex">
              <div class="flex-shrink-0 flex items-center">
                <h1 class="text-xl font-bold text-gray-900">🏭 Economy Admin</h1>
              </div>
              <div class="hidden sm:ml-6 sm:flex sm:space-x-8">
                <router-link
                  to="/"
                  class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm transition-colors"
                  active-class="border-primary-500 text-primary-600"
                >
                  📊 Dashboard
                </router-link>
                <router-link
                  to="/skills"
                  class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm transition-colors"
                  active-class="border-primary-500 text-primary-600"
                >
                  🎯 Skills
                </router-link>
                <router-link
                  to="/recipes"
                  class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm transition-colors"
                  active-class="border-primary-500 text-primary-600"
                >
                  📋 Recipes
                </router-link>
                <router-link
                  to="/bonuses"
                  class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm transition-colors"
                  active-class="border-primary-500 text-primary-600"
                >
                  ⚡ Bonuses
                </router-link>
              </div>
            </div>

            <!-- User menu -->
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="relative">
                  <button
                    @click="showUserMenu = !showUserMenu"
                    class="bg-white rounded-full flex text-sm focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
                  >
                    <span class="sr-only">Open user menu</span>
                    <div class="h-8 w-8 rounded-full bg-primary-100 flex items-center justify-center">
                      <span class="text-sm font-medium text-primary-700">
                        {{ authStore.user?.username?.charAt(0).toUpperCase() }}
                      </span>
                    </div>
                  </button>

                  <!-- User dropdown menu -->
                  <div
                    v-if="showUserMenu"
                    class="origin-top-right absolute right-0 mt-2 w-48 rounded-md shadow-lg bg-white ring-1 ring-black ring-opacity-5 focus:outline-none z-50"
                    @click="showUserMenu = false"
                  >
                    <div class="py-1">
                      <div class="px-4 py-2 text-xs text-gray-500">
                        Вошли как <strong>{{ authStore.user?.username }}</strong>
                      </div>
                      <div class="px-4 py-1">
                        <span
                          v-for="role in authStore.user?.roles"
                          :key="role"
                          class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium mr-1"
                          :class="{
                            'bg-red-100 text-red-800': role === 'admin',
                            'bg-blue-100 text-blue-800': role === 'developer'
                          }"
                        >
                          {{ role }}
                        </span>
                      </div>
                      <div class="border-t border-gray-100"></div>
                      <button
                        @click="handleLogout"
                        class="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                      >
                        Выйти
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Mobile menu (optional) -->
        <div class="sm:hidden">
          <div class="pt-2 pb-3 space-y-1">
            <router-link
              to="/"
              class="bg-primary-50 border-primary-500 text-primary-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium"
              active-class="bg-primary-50 border-primary-500 text-primary-700"
            >
              📊 Dashboard
            </router-link>
            <router-link
              to="/skills"
              class="border-transparent text-gray-600 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-800 block pl-3 pr-4 py-2 border-l-4 text-base font-medium"
              active-class="bg-primary-50 border-primary-500 text-primary-700"
            >
              🎯 Skills
            </router-link>
            <router-link
              to="/recipes"
              class="border-transparent text-gray-600 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-800 block pl-3 pr-4 py-2 border-l-4 text-base font-medium"
              active-class="bg-primary-50 border-primary-500 text-primary-700"
            >
              📋 Recipes
            </router-link>
            <router-link
              to="/bonuses"
              class="border-transparent text-gray-600 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-800 block pl-3 pr-4 py-2 border-l-4 text-base font-medium"
              active-class="bg-primary-50 border-primary-500 text-primary-700"
            >
              ⚡ Bonuses
            </router-link>
          </div>
        </div>
      </nav>

      <!-- Main content area -->
      <main class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <router-view />
      </main>
    </div>

    <!-- Global notifications -->
    <div
      v-if="notification"
      class="fixed bottom-4 right-4 max-w-sm w-full bg-white shadow-lg rounded-lg pointer-events-auto ring-1 ring-black ring-opacity-5 z-50"
    >
      <div class="p-4">
        <div class="flex items-start">
          <div class="flex-shrink-0">
            <div
              class="h-6 w-6 rounded-full flex items-center justify-center text-white text-sm"
              :class="{
                'bg-success-500': notification.type === 'success',
                'bg-danger-500': notification.type === 'error',
                'bg-warning-500': notification.type === 'warning',
                'bg-primary-500': notification.type === 'info'
              }"
            >
              <span v-if="notification.type === 'success'">✓</span>
              <span v-else-if="notification.type === 'error'">✗</span>
              <span v-else-if="notification.type === 'warning'">!</span>
              <span v-else>i</span>
            </div>
          </div>
          <div class="ml-3 w-0 flex-1">
            <p class="text-sm font-medium text-gray-900">{{ notification.title }}</p>
            <p v-if="notification.message" class="mt-1 text-sm text-gray-500">{{ notification.message }}</p>
          </div>
          <div class="ml-4 flex-shrink-0 flex">
            <button
              @click="notification = null"
              class="rounded-md inline-flex text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
            >
              <span class="sr-only">Close</span>
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const isInitializing = ref(true)
const showUserMenu = ref(false)
const notification = ref<{
  type: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
} | null>(null)

onMounted(async () => {
  try {
    await authStore.init()
  } catch (error) {
    console.error('Failed to initialize auth:', error)
  } finally {
    isInitializing.value = false
  }
})

const handleLogout = async () => {
  try {
    await authStore.logout()
    router.push('/login')
    showNotification('success', 'Выход выполнен', 'Вы успешно вышли из системы')
  } catch (error) {
    console.error('Logout failed:', error)
    showNotification('error', 'Ошибка', 'Не удалось выйти из системы')
  }
}

const showNotification = (type: 'success' | 'error' | 'warning' | 'info', title: string, message?: string) => {
  notification.value = { type, title, message }
  setTimeout(() => {
    notification.value = null
  }, 5000)
}

// Close user menu when clicking outside
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target.closest('.relative')) {
    showUserMenu.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.router-link-active {
  @apply border-primary-500 text-primary-600;
}
</style>
