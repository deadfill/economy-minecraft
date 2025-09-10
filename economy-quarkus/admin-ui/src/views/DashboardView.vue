<template>
  <div class="px-4 sm:px-6 lg:px-8">
    <!-- Page header -->
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-2xl font-semibold text-gray-900">📊 Dashboard</h1>
        <p class="mt-2 text-sm text-gray-700">
          Обзор системы управления экономикой
        </p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
        <button
          @click="refreshData"
          :disabled="isLoading"
          class="btn btn-primary"
        >
          <svg v-if="!isLoading" class="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          <div v-else class="animate-spin h-4 w-4 mr-2 border-2 border-white border-t-transparent rounded-full"></div>
          {{ isLoading ? 'Обновление...' : 'Обновить' }}
        </button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="isLoading && !stats" class="mt-8 text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
      <p class="mt-2 text-sm text-gray-600">Загрузка статистики...</p>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="mt-8 rounded-md bg-danger-50 p-4">
      <div class="flex">
        <div class="flex-shrink-0">
          <svg class="h-5 w-5 text-danger-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div class="ml-3">
          <h3 class="text-sm font-medium text-danger-800">Ошибка загрузки данных</h3>
          <div class="mt-2 text-sm text-danger-700">{{ error }}</div>
          <div class="mt-4">
            <button @click="refreshData" class="btn btn-danger text-sm">
              Попробовать снова
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Main content -->
    <div v-else-if="stats" class="mt-8">
      <!-- Stats cards -->
      <div class="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        <!-- Skills card -->
        <div class="card">
          <div class="card-body">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="h-8 w-8 rounded-md bg-primary-500 flex items-center justify-center">
                  <span class="text-white text-lg">🎯</span>
                </div>
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">Skills</dt>
                  <dd class="flex items-baseline">
                    <div class="text-2xl font-semibold text-gray-900">{{ stats.skills?.total || 0 }}</div>
                    <div class="ml-2 flex items-baseline text-sm font-semibold text-success-600">
                      {{ stats.skills?.enabled || 0 }} активных
                    </div>
                  </dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <!-- Recipes card -->
        <div class="card">
          <div class="card-body">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="h-8 w-8 rounded-md bg-success-500 flex items-center justify-center">
                  <span class="text-white text-lg">📋</span>
                </div>
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">Recipes</dt>
                  <dd class="flex items-baseline">
                    <div class="text-2xl font-semibold text-gray-900">{{ stats.recipes?.total || 0 }}</div>
                    <div class="ml-2 flex items-baseline text-sm font-semibold text-success-600">
                      {{ stats.recipes?.categories || 0 }} категорий
                    </div>
                  </dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <!-- Bonuses card -->
        <div class="card">
          <div class="card-body">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="h-8 w-8 rounded-md bg-warning-500 flex items-center justify-center">
                  <span class="text-white text-lg">⚡</span>
                </div>
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">Bonuses</dt>
                  <dd class="flex items-baseline">
                    <div class="text-2xl font-semibold text-gray-900">{{ stats.bonuses?.total || 0 }}</div>
                    <div class="ml-2 flex items-baseline text-sm font-semibold text-success-600">
                      {{ stats.bonuses?.operations || 0 }} типов
                    </div>
                  </dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <!-- System card -->
        <div class="card">
          <div class="card-body">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="h-8 w-8 rounded-md bg-gray-500 flex items-center justify-center">
                  <span class="text-white text-lg">⚙️</span>
                </div>
              </div>
              <div class="ml-5 w-0 flex-1">
                <dl>
                  <dt class="text-sm font-medium text-gray-500 truncate">System</dt>
                  <dd class="flex items-baseline">
                    <div class="text-lg font-semibold text-gray-900">{{ stats.system?.user || 'N/A' }}</div>
                    <div class="ml-2 flex items-baseline text-sm font-semibold text-primary-600">
                      Online
                    </div>
                  </dd>
                </dl>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick actions -->
      <div class="mt-8">
        <h2 class="text-lg font-medium text-gray-900 mb-4">🚀 Быстрые действия</h2>
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <router-link
            to="/skills"
            class="relative rounded-lg border border-gray-300 bg-white px-6 py-5 shadow-sm flex items-center space-x-3 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 transition-colors"
          >
            <div class="flex-shrink-0">
              <span class="text-2xl">🎯</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="absolute inset-0" aria-hidden="true"></span>
              <p class="text-sm font-medium text-gray-900">Управление скиллами</p>
              <p class="text-sm text-gray-500 truncate">Создание и редактирование навыков</p>
            </div>
          </router-link>

          <router-link
            to="/recipes"
            class="relative rounded-lg border border-gray-300 bg-white px-6 py-5 shadow-sm flex items-center space-x-3 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 transition-colors"
          >
            <div class="flex-shrink-0">
              <span class="text-2xl">📋</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="absolute inset-0" aria-hidden="true"></span>
              <p class="text-sm font-medium text-gray-900">Управление рецептами</p>
              <p class="text-sm text-gray-500 truncate">Создание и настройка рецептов</p>
            </div>
          </router-link>

          <router-link
            to="/bonuses"
            class="relative rounded-lg border border-gray-300 bg-white px-6 py-5 shadow-sm flex items-center space-x-3 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 transition-colors"
          >
            <div class="flex-shrink-0">
              <span class="text-2xl">⚡</span>
            </div>
            <div class="flex-1 min-w-0">
              <span class="absolute inset-0" aria-hidden="true"></span>
              <p class="text-sm font-medium text-gray-900">Управление бонусами</p>
              <p class="text-sm text-gray-500 truncate">Настройка бонусов скиллов</p>
            </div>
          </router-link>
        </div>
      </div>

      <!-- System info -->
      <div class="mt-8">
        <div class="card">
          <div class="card-header">
            <h3 class="text-lg font-medium text-gray-900">ℹ️ Информация о системе</h3>
          </div>
          <div class="card-body">
            <dl class="grid grid-cols-1 gap-x-4 gap-y-6 sm:grid-cols-2">
              <div>
                <dt class="text-sm font-medium text-gray-500">Пользователь</dt>
                <dd class="mt-1 text-sm text-gray-900">{{ stats.system?.user }}</dd>
              </div>
              <div>
                <dt class="text-sm font-medium text-gray-500">Роли</dt>
                <dd class="mt-1">
                  <span
                    v-for="role in stats.system?.roles"
                    :key="role"
                    class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium mr-2"
                    :class="{
                      'bg-red-100 text-red-800': role === 'admin',
                      'bg-blue-100 text-blue-800': role === 'developer'
                    }"
                  >
                    {{ role }}
                  </span>
                </dd>
              </div>
              <div>
                <dt class="text-sm font-medium text-gray-500">Последнее обновление</dt>
                <dd class="mt-1 text-sm text-gray-900">{{ formatTimestamp(stats.system?.timestamp) }}</dd>
              </div>
              <div class="sm:col-span-2">
                <dt class="text-sm font-medium text-gray-500">Действия</dt>
                <dd class="mt-1">
                  <button @click="reloadConfigurations" :disabled="isReloading" class="btn btn-secondary mr-2">
                    <svg v-if="!isReloading" class="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    <div v-else class="animate-spin h-4 w-4 mr-1 border-2 border-gray-400 border-t-transparent rounded-full"></div>
                    {{ isReloading ? 'Перезагрузка...' : 'Перезагрузить конфигурации' }}
                  </button>
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

interface SystemStats {
  skills: {
    total: number
    enabled: number
  }
  recipes: {
    total: number
    enabled: number
    categories: number
  }
  bonuses: {
    total: number
    enabled: number
    operations: number
  }
  system: {
    user: string
    roles: string[]
    timestamp: number
  }
}

const stats = ref<SystemStats | null>(null)
const isLoading = ref(false)
const isReloading = ref(false)
const error = ref<string | null>(null)

const refreshData = async () => {
  isLoading.value = true
  error.value = null

  try {
    const response = await axios.get<SystemStats>('/api/admin/config/stats')
    stats.value = response.data
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Не удалось загрузить статистику'
    console.error('Failed to load stats:', err)
  } finally {
    isLoading.value = false
  }
}

const reloadConfigurations = async () => {
  isReloading.value = true

  try {
    await axios.post('/api/admin/config/reload')
    
    // Refresh stats after reload
    await refreshData()
    
    // Show success notification (would need to implement global notification system)
    console.log('✅ Configurations reloaded successfully')
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Не удалось перезагрузить конфигурации'
    console.error('Failed to reload configurations:', err)
  } finally {
    isReloading.value = false
  }
}

const formatTimestamp = (timestamp: number | undefined): string => {
  if (!timestamp) return 'N/A'
  return new Date(timestamp).toLocaleString('ru-RU')
}

onMounted(() => {
  refreshData()
})
</script>
