<template>
  <div class="px-4 sm:px-6 lg:px-8">
    <!-- Page header -->
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-2xl font-semibold text-gray-900">📋 Recipes Management</h1>
        <p class="mt-2 text-sm text-gray-700">
          Управление рецептами производства и их компонентами
        </p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none space-x-2">
        <button
          @click="refreshRecipes"
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
          Создать рецепт
        </button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="isLoading && recipes.length === 0" class="mt-8 text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
      <p class="mt-2 text-sm text-gray-600">Загрузка рецептов...</p>
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
            <button @click="refreshRecipes" class="btn btn-danger text-sm">
              Попробовать снова
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Recipes table -->
    <div v-else class="mt-8">
      <div class="card">
        <div class="card-header">
          <h3 class="text-lg font-medium text-gray-900">Список рецептов</h3>
        </div>
        <div class="card-body p-0">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5">
            <table class="table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Название</th>
                  <th>Категория</th>
                  <th>Тег</th>
                  <th>Длительность</th>
                  <th>Входы</th>
                  <th>Выходы</th>
                  <th>Статус</th>
                  <th class="relative px-6 py-3">
                    <span class="sr-only">Действия</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="recipe in recipes" :key="recipe.id">
                  <td class="font-mono text-sm">{{ recipe.id }}</td>
                  <td class="font-medium">{{ recipe.name }}</td>
                  <td>
                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                      {{ recipe.category || 'N/A' }}
                    </span>
                  </td>
                  <td>
                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-primary-100 text-primary-800">
                      {{ recipe.tag }}
                    </span>
                  </td>
                  <td class="text-sm">{{ formatDuration(recipe.baseDurationMs) }}</td>
                  <td class="text-sm">{{ recipe.inputs?.length || 0 }}</td>
                  <td class="text-sm">{{ recipe.outputs?.length || 0 }}</td>
                  <td>
                    <span
                      class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                      :class="{
                        'bg-success-100 text-success-800': recipe.enabled,
                        'bg-gray-100 text-gray-800': !recipe.enabled
                      }"
                    >
                      {{ recipe.enabled ? 'Активен' : 'Отключен' }}
                    </span>
                  </td>
                  <td class="text-right text-sm font-medium">
                    <button
                      @click="viewRecipeDetails(recipe)"
                      class="text-primary-600 hover:text-primary-900 mr-4"
                    >
                      Подробнее
                    </button>
                    <button
                      @click="editRecipe(recipe)"
                      class="text-gray-600 hover:text-gray-900"
                      v-if="authStore.canEditContent"
                    >
                      Редактировать
                    </button>
                  </td>
                </tr>
                <tr v-if="recipes.length === 0">
                  <td colspan="9" class="text-center py-12 text-gray-500">
                    <div class="text-center">
                      <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                      </svg>
                      <h3 class="mt-2 text-sm font-medium text-gray-900">Нет рецептов</h3>
                      <p class="mt-1 text-sm text-gray-500">Начните с создания первого рецепта.</p>
                      <div class="mt-6" v-if="authStore.canManageSystem">
                        <button @click="showCreateModal = true" class="btn btn-primary">
                          <svg class="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                          </svg>
                          Создать рецепт
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

    <!-- Recipe Details Modal -->
    <div
      v-if="showDetailsModal && selectedRecipe"
      class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50"
      @click.self="showDetailsModal = false"
    >
      <div class="relative top-20 mx-auto p-5 border w-11/12 max-w-4xl shadow-lg rounded-md bg-white">
        <div class="mt-3">
          <div class="flex justify-between items-start mb-4">
            <h3 class="text-lg font-medium text-gray-900">
              📋 {{ selectedRecipe.name }} ({{ selectedRecipe.id }})
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

          <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <!-- Basic Info -->
            <div class="space-y-4">
              <div>
                <h4 class="text-sm font-medium text-gray-900 mb-2">Основная информация</h4>
                <dl class="text-sm space-y-2">
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Описание:</dt>
                    <dd class="text-gray-900 text-right max-w-xs">{{ selectedRecipe.description || 'Нет описания' }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Категория:</dt>
                    <dd class="text-gray-900">{{ selectedRecipe.category || 'N/A' }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Тег:</dt>
                    <dd class="text-gray-900">{{ selectedRecipe.tag }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Длительность:</dt>
                    <dd class="text-gray-900">{{ formatDuration(selectedRecipe.baseDurationMs) }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-500">Статус:</dt>
                    <dd>
                      <span
                        class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                        :class="{
                          'bg-success-100 text-success-800': selectedRecipe.enabled,
                          'bg-gray-100 text-gray-800': !selectedRecipe.enabled
                        }"
                      >
                        {{ selectedRecipe.enabled ? 'Активен' : 'Отключен' }}
                      </span>
                    </dd>
                  </div>
                </dl>
              </div>

              <!-- Inputs -->
              <div v-if="selectedRecipe.inputs?.length">
                <h4 class="text-sm font-medium text-gray-900 mb-2">Входные материалы</h4>
                <div class="space-y-2">
                  <div
                    v-for="input in selectedRecipe.inputs"
                    :key="input.itemId"
                    class="flex justify-between items-center p-2 bg-gray-50 rounded"
                  >
                    <span class="font-mono text-sm">{{ input.itemId }}</span>
                    <span class="font-medium">{{ input.quantity }}x</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Outputs and Requirements -->
            <div class="space-y-4">
              <!-- Outputs -->
              <div v-if="selectedRecipe.outputs?.length">
                <h4 class="text-sm font-medium text-gray-900 mb-2">Выходные материалы</h4>
                <div class="space-y-2">
                  <div
                    v-for="output in selectedRecipe.outputs"
                    :key="output.itemId"
                    class="flex justify-between items-center p-2 bg-gray-50 rounded"
                  >
                    <span class="font-mono text-sm">{{ output.itemId }}</span>
                    <div class="text-right">
                      <span class="font-medium">{{ output.quantity }}x</span>
                      <span v-if="output.chance < 1" class="text-xs text-gray-500 ml-1">
                        ({{ Math.round(output.chance * 100) }}%)
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Requirements -->
              <div v-if="selectedRecipe.requirements?.length">
                <h4 class="text-sm font-medium text-gray-900 mb-2">Требования</h4>
                <div class="space-y-2">
                  <div
                    v-for="req in selectedRecipe.requirements"
                    :key="req.target"
                    class="flex justify-between items-center p-2 bg-yellow-50 rounded"
                  >
                    <span class="text-sm">{{ req.description || `${req.type}: ${req.target}` }}</span>
                    <span class="text-sm font-medium">{{ req.value }}</span>
                  </div>
                </div>
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
          Функция создания рецептов будет доступна в следующей версии.
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
          Функция редактирования рецептов будет доступна в следующей версии.
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

interface RecipeInput {
  itemId: string
  quantity: number
  description?: string
  sortOrder: number
}

interface RecipeOutput {
  itemId: string
  quantity: number
  chance: number
  description?: string
  sortOrder: number
}

interface RecipeRequirement {
  type: string
  target: string
  value: string
  description?: string
}

interface Recipe {
  id: string
  name: string
  description?: string
  tag: string
  baseDurationMs: number
  enabled: boolean
  category?: string
  inputs: RecipeInput[]
  outputs: RecipeOutput[]
  requirements: RecipeRequirement[]
}

const authStore = useAuthStore()

const recipes = ref<Recipe[]>([])
const isLoading = ref(false)
const error = ref<string | null>(null)

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showDetailsModal = ref(false)
const selectedRecipe = ref<Recipe | null>(null)

const refreshRecipes = async () => {
  isLoading.value = true
  error.value = null

  try {
    const response = await axios.get<{ recipes: Recipe[] }>('/api/admin/config/recipes')
    recipes.value = response.data.recipes || []
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Не удалось загрузить рецепты'
    console.error('Failed to load recipes:', err)
  } finally {
    isLoading.value = false
  }
}

const viewRecipeDetails = (recipe: Recipe) => {
  selectedRecipe.value = recipe
  showDetailsModal.value = true
}

const editRecipe = (recipe: Recipe) => {
  selectedRecipe.value = recipe
  showEditModal.value = true
}

const formatDuration = (durationMs: number): string => {
  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return `${hours}ч ${minutes % 60}м`
  } else if (minutes > 0) {
    return `${minutes}м ${seconds % 60}с`
  } else {
    return `${seconds}с`
  }
}

onMounted(() => {
  refreshRecipes()
})
</script>
