<template>
  <div class="px-4 sm:px-6 lg:px-8">
    <!-- Page header -->
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-2xl font-semibold text-gray-900">⚡ Skill Bonuses</h1>
        <p class="mt-2 text-sm text-gray-700">
          Управление бонусами навыков и их эффектами
        </p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none space-x-2">
        <button
          @click="refreshBonuses"
          :disabled="isLoading"
          class="btn btn-secondary"
        >
          <svg v-if="!isLoading" class="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          <div v-else class="animate-spin h-4 w-4 mr-2 border-2 border-gray-400 border-t-transparent rounded-full"></div>
          Обновить
        </button>
        <button
          @click="showCreateModal = true"
          class="btn btn-primary"
          v-if="authStore.canManageSystem"
        >
          <svg class="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          Создать бонус
        </button>
      </div>
    </div>

    <!-- Bonus types info -->
    <div class="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-blue-500 flex items-center justify-center">
                <span class="text-white text-sm">💰</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Cost Reduction</dt>
                <dd class="text-lg font-medium text-gray-900">
                  {{ bonuses.filter(b => b.operation === 'INPUT_COST_MULTIPLIER').length }}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-green-500 flex items-center justify-center">
                <span class="text-white text-sm">⏱️</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Time Reduction</dt>
                <dd class="text-lg font-medium text-gray-900">
                  {{ bonuses.filter(b => b.operation === 'DURATION_MULTIPLIER').length }}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-purple-500 flex items-center justify-center">
                <span class="text-white text-sm">🎲</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Extra Output</dt>
                <dd class="text-lg font-medium text-gray-900">
                  {{ bonuses.filter(b => b.operation === 'EXTRA_OUTPUT_CHANCE').length }}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-orange-500 flex items-center justify-center">
                <span class="text-white text-sm">🔓</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Unlocks</dt>
                <dd class="text-lg font-medium text-gray-900">
                  {{ bonuses.filter(b => b.operation === 'UNLOCK_RECIPE').length }}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="isLoading && bonuses.length === 0" class="mt-8 text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
      <p class="mt-2 text-sm text-gray-600">Загрузка бонусов...</p>
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
          <h3 class="text-sm font-medium text-danger-800">Ошибка загрузки</h3>
          <div class="mt-2 text-sm text-danger-700">{{ error }}</div>
          <div class="mt-4">
            <button @click="refreshBonuses" class="btn btn-danger text-sm">
              Попробовать снова
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Bonuses table -->
    <div v-else class="mt-8">
      <div class="card">
        <div class="card-header">
          <h3 class="text-lg font-medium text-gray-900">Список бонусов навыков</h3>
        </div>
        <div class="card-body p-0">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5">
            <table class="table">
              <thead>
                <tr>
                  <th>Навык</th>
                  <th>Тип</th>
                  <th>Цель</th>
                  <th>Операция</th>
                  <th>За уровень</th>
                  <th>Максимум</th>
                  <th>Статус</th>
                  <th class="relative px-6 py-3">
                    <span class="sr-only">Действия</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="bonus in bonuses" :key="`${bonus.skillId}-${bonus.operation}-${bonus.target}`">
                  <td class="font-mono text-sm">{{ bonus.skillId }}</td>
                  <td>
                    <span
                      class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium"
                      :class="{
                        'bg-blue-100 text-blue-800': bonus.kind === 'recipe',
                        'bg-green-100 text-green-800': bonus.kind === 'tag',
                        'bg-purple-100 text-purple-800': bonus.kind === 'all'
                      }"
                    >
                      {{ bonus.kind }}
                    </span>
                  </td>
                  <td class="font-mono text-sm">{{ bonus.target || 'all' }}</td>
                  <td>
                    <span
                      class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium"
                      :class="getOperationColor(bonus.operation)"
                    >
                      {{ getOperationName(bonus.operation) }}
                    </span>
                  </td>
                  <td class="text-sm">{{ formatBps(bonus.perLevelBps) }}</td>
                  <td class="text-sm">{{ bonus.capBps > 0 ? formatBps(bonus.capBps) : 'Без ограничений' }}</td>
                  <td>
                    <span
                      class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                      :class="{
                        'bg-success-100 text-success-800': bonus.enabled,
                        'bg-gray-100 text-gray-800': !bonus.enabled
                      }"
                    >
                      {{ bonus.enabled ? 'Активен' : 'Отключен' }}
                    </span>
                  </td>
                  <td class="text-right text-sm font-medium">
                    <button
                      @click="viewBonusDetails(bonus)"
                      class="text-primary-600 hover:text-primary-900 mr-4"
                    >
                      Подробнее
                    </button>
                    <button
                      @click="editBonus(bonus)"
                      class="text-gray-600 hover:text-gray-900"
                      v-if="authStore.canEditContent"
                    >
                      Редактировать
                    </button>
                  </td>
                </tr>
                <tr v-if="bonuses.length === 0">
                  <td colspan="8" class="text-center py-12 text-gray-500">
                    <div class="text-center">
                      <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      <h3 class="mt-2 text-sm font-medium text-gray-900">Нет бонусов</h3>
                      <p class="mt-1 text-sm text-gray-500">Начните с создания первого бонуса навыка.</p>
                      <div class="mt-6" v-if="authStore.canManageSystem">
                        <button @click="showCreateModal = true" class="btn btn-primary">
                          <svg class="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                          </svg>
                          Создать бонус
                        </button>
                      </div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- Bonus Details Modal -->
    <div
      v-if="showDetailsModal && selectedBonus"
      class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50"
      @click.self="showDetailsModal = false"
    >
      <div class="relative top-20 mx-auto p-5 border w-11/12 max-w-2xl shadow-lg rounded-md bg-white">
        <div class="mt-3">
          <div class="flex justify-between items-start mb-4">
            <h3 class="text-lg font-medium text-gray-900">
              ⚡ Бонус: {{ selectedBonus.skillId }} → {{ getOperationName(selectedBonus.operation) }}
            </h3>
            <button
              @click="showDetailsModal = false"
              class="text-gray-400 hover:text-gray-600"
            >
              <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="space-y-6">
            <div>
              <h4 class="text-sm font-medium text-gray-900 mb-2">Описание</h4>
              <p class="text-sm text-gray-700">{{ selectedBonus.description || 'Нет описания' }}</p>
            </div>

            <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
              <div>
                <h4 class="text-sm font-medium text-gray-900 mb-2">Параметры</h4>
                <dl class="text-sm space-y-2">
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Навык:</dt>
                    <dd class="text-gray-900 font-mono">{{ selectedBonus.skillId }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Тип применения:</dt>
                    <dd class="text-gray-900">{{ selectedBonus.kind }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Цель:</dt>
                    <dd class="text-gray-900 font-mono">{{ selectedBonus.target || 'все' }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Операция:</dt>
                    <dd class="text-gray-900">{{ getOperationName(selectedBonus.operation) }}</dd>
                  </div>
                </dl>
              </div>

              <div>
                <h4 class="text-sm font-medium text-gray-900 mb-2">Эффекты</h4>
                <dl class="text-sm space-y-2">
                  <div class="flex justify-between">
                    <dt class="text-gray-500">За уровень:</dt>
                    <dd class="text-gray-900 font-medium">{{ formatBps(selectedBonus.perLevelBps) }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Максимум:</dt>
                    <dd class="text-gray-900 font-medium">
                      {{ selectedBonus.capBps > 0 ? formatBps(selectedBonus.capBps) : 'Без ограничений' }}
                    </dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Статус:</dt>
                    <dd>
                      <span
                        class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                        :class="{
                          'bg-success-100 text-success-800': selectedBonus.enabled,
                          'bg-gray-100 text-gray-800': !selectedBonus.enabled
                        }"
                      >
                        {{ selectedBonus.enabled ? 'Активен' : 'Отключен' }}
                      </span>
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

            <!-- Example calculation -->
            <div class="bg-gray-50 p-4 rounded-lg">
              <h4 class="text-sm font-medium text-gray-900 mb-2">Пример расчета</h4>
              <div class="text-sm text-gray-600 space-y-1">
                <p>При уровне навыка 5:</p>
                <p class="font-mono">
                  Бонус = {{ formatBps(Math.min(selectedBonus.perLevelBps * 5, selectedBonus.capBps || selectedBonus.perLevelBps * 5)) }}
                </p>
                <p class="text-xs text-gray-500">
                  ({{ selectedBonus.perLevelBps }} × 5 = {{ selectedBonus.perLevelBps * 5 }} bps, 
                  ограничено {{ selectedBonus.capBps > 0 ? selectedBonus.capBps + ' bps' : 'не ограничено' }})
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Placeholder modals -->
    <div v-if="showCreateModal" class="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50">
      <div class="bg-white p-6 rounded-lg shadow-lg max-w-md w-full mx-4">
        <h3 class="text-lg font-medium text-gray-900 mb-4">🚧 В разработке</h3>
        <p class="text-sm text-gray-600 mb-4">
          Функция создания бонусов будет доступна в следующей версии.
        </p>
        <button @click="showCreateModal = false" class="btn btn-primary">
          Понятно
        </button>
      </div>
    </div>

    <div v-if="showEditModal" class="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50">
      <div class="bg-white p-6 rounded-lg shadow-lg max-w-md w-full mx-4">
        <h3 class="text-lg font-medium text-gray-900 mb-4">🚧 В разработке</h3>
        <p class="text-sm text-gray-600 mb-4">
          Функция редактирования бонусов будет доступна в следующей версии.
        </p>
        <button @click="showEditModal = false" class="btn btn-primary">
          Понятно
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import axios from 'axios'

interface SkillBonus {
  skillId: string
  kind: string // 'recipe' | 'tag' | 'all'
  target: string
  operation: string // 'INPUT_COST_MULTIPLIER' | 'DURATION_MULTIPLIER' | 'UNLOCK_RECIPE' | 'EXTRA_OUTPUT_CHANCE'
  perLevelBps: number
  capBps: number
  enabled: boolean
  description: string
}

const authStore = useAuthStore()

const bonuses = ref<SkillBonus[]>([])
const isLoading = ref(false)
const error = ref<string | null>(null)

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showDetailsModal = ref(false)
const selectedBonus = ref<SkillBonus | null>(null)

const refreshBonuses = async () => {
  isLoading.value = true
  error.value = null

  try {
    const response = await axios.get<{ bonuses: SkillBonus[] }>('/api/admin/config/bonuses')
    bonuses.value = response.data.bonuses || []
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Не удалось загрузить бонусы'
    console.error('Failed to load bonuses:', err)
  } finally {
    isLoading.value = false
  }
}

const viewBonusDetails = (bonus: SkillBonus) => {
  selectedBonus.value = bonus
  showDetailsModal.value = true
}

const editBonus = (bonus: SkillBonus) => {
  selectedBonus.value = bonus
  showEditModal.value = true
}

const getOperationName = (operation: string): string => {
  const names: Record<string, string> = {
    'INPUT_COST_MULTIPLIER': 'Снижение стоимости',
    'DURATION_MULTIPLIER': 'Ускорение времени',
    'EXTRA_OUTPUT_CHANCE': 'Доп. выход',
    'UNLOCK_RECIPE': 'Открытие рецептов'
  }
  return names[operation] || operation
}

const getOperationColor = (operation: string): string => {
  const colors: Record<string, string> = {
    'INPUT_COST_MULTIPLIER': 'bg-blue-100 text-blue-800',
    'DURATION_MULTIPLIER': 'bg-green-100 text-green-800',
    'EXTRA_OUTPUT_CHANCE': 'bg-purple-100 text-purple-800',
    'UNLOCK_RECIPE': 'bg-orange-100 text-orange-800'
  }
  return colors[operation] || 'bg-gray-100 text-gray-800'
}

const formatBps = (bps: number): string => {
  const percent = bps / 100
  return `${percent}%`
}

onMounted(() => {
  refreshBonuses()
})
</script>
