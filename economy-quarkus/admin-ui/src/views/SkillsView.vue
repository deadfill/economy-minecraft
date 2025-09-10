<template>
  <div class="px-4 sm:px-6 lg:px-8">
    <!-- Page header -->
    <div class="sm:flex sm:items-center">
      <div class="sm:flex-auto">
        <h1 class="text-2xl font-semibold text-gray-900">🎯 Skills Management</h1>
        <p class="mt-2 text-sm text-gray-700">
          Управление навыками и их характеристиками
        </p>
      </div>
      <div class="mt-4 sm:mt-0 sm:ml-16 sm:flex-none space-x-2">
        <button
          @click="refreshSkills"
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
          Создать скилл
        </button>
      </div>
    </div>

    <!-- Stats cards -->
    <div class="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-3">
      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-primary-500 flex items-center justify-center">
                <span class="text-white font-bold">{{ skills.length }}</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Всего скиллов</dt>
                <dd class="text-lg font-medium text-gray-900">{{ skills.length }}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-success-500 flex items-center justify-center">
                <span class="text-white font-bold">{{ enabledSkills }}</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Активных</dt>
                <dd class="text-lg font-medium text-gray-900">{{ enabledSkills }}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <div class="flex items-center">
            <div class="flex-shrink-0">
              <div class="h-8 w-8 rounded-md bg-warning-500 flex items-center justify-center">
                <span class="text-white font-bold">{{ maxLevelSum }}</span>
              </div>
            </div>
            <div class="ml-5 w-0 flex-1">
              <dl>
                <dt class="text-sm font-medium text-gray-500 truncate">Общий макс. уровень</dt>
                <dd class="text-lg font-medium text-gray-900">{{ maxLevelSum }}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="isLoading && skills.length === 0" class="mt-8 text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
      <p class="mt-2 text-sm text-gray-600">Загрузка скиллов...</p>
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
            <button @click="refreshSkills" class="btn btn-danger text-sm">
              Попробовать снова
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Skills table -->
    <div v-else class="mt-8">
      <div class="card">
        <div class="card-header">
          <h3 class="text-lg font-medium text-gray-900">Список скиллов</h3>
        </div>
        <div class="card-body p-0">
          <div class="overflow-hidden shadow ring-1 ring-black ring-opacity-5">
            <table class="table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Название</th>
                  <th>Описание</th>
                  <th>Макс. уровень</th>
                  <th>Статус</th>
                  <th>Версия</th>
                  <th class="relative px-6 py-3">
                    <span class="sr-only">Действия</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="skill in skills" :key="skill.id">
                  <td class="font-mono text-sm">{{ skill.id }}</td>
                  <td class="font-medium">{{ skill.title }}</td>
                  <td class="max-w-xs truncate">{{ skill.description }}</td>
                  <td>
                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary-100 text-primary-800">
                      {{ skill.maxLevel }}
                    </span>
                  </td>
                  <td>
                    <span
                      class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                      :class="{
                        'bg-success-100 text-success-800': skill.enabled,
                        'bg-gray-100 text-gray-800': !skill.enabled
                      }"
                    >
                      {{ skill.enabled ? 'Активен' : 'Отключен' }}
                    </span>
                  </td>
                  <td class="text-sm text-gray-500">v{{ skill.version }}</td>
                  <td class="text-right text-sm font-medium">
                    <button
                      @click="editSkill(skill)"
                      class="text-primary-600 hover:text-primary-900 mr-4"
                      v-if="authStore.canEditContent"
                    >
                      Редактировать
                    </button>
                    <button
                      @click="viewSkillDetails(skill)"
                      class="text-gray-600 hover:text-gray-900"
                    >
                      Подробнее
                    </button>
                  </td>
                </tr>
                <tr v-if="skills.length === 0">
                  <td colspan="7" class="text-center py-12 text-gray-500">
                    <div class="text-center">
                      <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                      </svg>
                      <h3 class="mt-2 text-sm font-medium text-gray-900">Нет скиллов</h3>
                      <p class="mt-1 text-sm text-gray-500">Начните с создания первого скилла.</p>
                      <div class="mt-6" v-if="authStore.canManageSystem">
                        <button @click="showCreateModal = true" class="btn btn-primary">
                          <svg class="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                          </svg>
                          Создать скилл
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

    <!-- Create/Edit Modal -->
    <div
      v-if="showCreateModal || showEditModal"
      class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50"
      @click.self="closeModals"
    >
      <div class="relative top-20 mx-auto p-5 border w-11/12 max-w-2xl shadow-lg rounded-md bg-white">
        <div class="mt-3">
          <h3 class="text-lg font-medium text-gray-900 mb-4">
            {{ showCreateModal ? '✨ Создать новый скилл' : '✏️ Редактировать скилл' }}
          </h3>

          <form @submit.prevent="saveSkill" class="space-y-6">
            <!-- Basic info -->
            <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
              <div class="form-group">
                <label class="form-label">ID скилла *</label>
                <input
                  v-model="skillForm.id"
                  type="text"
                  class="form-input"
                  placeholder="industry"
                  :disabled="showEditModal"
                  required
                />
                <p class="mt-1 text-sm text-gray-500">Уникальный идентификатор (только латиница и подчеркивания)</p>
              </div>

              <div class="form-group">
                <label class="form-label">Название *</label>
                <input
                  v-model="skillForm.title"
                  type="text"
                  class="form-input"
                  placeholder="Industry"
                  required
                />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Описание</label>
              <textarea
                v-model="skillForm.description"
                class="form-textarea"
                rows="3"
                placeholder="Описание навыка и его влияния на игровой процесс"
              ></textarea>
            </div>

            <div class="grid grid-cols-1 gap-6 sm:grid-cols-3">
              <div class="form-group">
                <label class="form-label">Максимальный уровень *</label>
                <input
                  v-model.number="skillForm.maxLevel"
                  type="number"
                  class="form-input"
                  min="1"
                  max="50"
                  required
                />
              </div>

              <div class="form-group">
                <label class="form-label">Категория</label>
                <select v-model="skillForm.category" class="form-select">
                  <option value="">Без категории</option>
                  <option value="production">Производство</option>
                  <option value="gathering">Добыча</option>
                  <option value="crafting">Крафт</option>
                  <option value="combat">Бой</option>
                  <option value="magic">Магия</option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">Статус</label>
                <select v-model="skillForm.enabled" class="form-select">
                  <option :value="true">Активен</option>
                  <option :value="false">Отключен</option>
                </select>
              </div>
            </div>

            <!-- Durations -->
            <div class="form-group">
              <label class="form-label">Длительности тренировок (мс)</label>
              <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
                <div v-for="(duration, index) in skillForm.durations" :key="index" class="relative">
                  <label class="block text-xs font-medium text-gray-700 mb-1">
                    Уровень {{ index + 1 }}
                  </label>
                  <input
                    v-model.number="skillForm.durations[index].durationMs"
                    type="number"
                    class="form-input text-sm"
                    min="1000"
                    step="1000"
                  />
                  <p class="mt-1 text-xs text-gray-500">
                    {{ formatDuration(skillForm.durations[index].durationMs) }}
                  </p>
                </div>
              </div>
              <button
                type="button"
                @click="generateDefaultDurations"
                class="mt-2 text-sm text-primary-600 hover:text-primary-900"
              >
                🎲 Сгенерировать стандартные длительности
              </button>
            </div>

            <!-- Actions -->
            <div class="flex justify-end space-x-3 pt-6 border-t border-gray-200">
              <button
                type="button"
                @click="closeModals"
                class="btn btn-secondary"
              >
                Отмена
              </button>
              <button
                type="submit"
                :disabled="isSaving"
                class="btn btn-primary"
              >
                <div v-if="isSaving" class="animate-spin h-4 w-4 mr-2 border-2 border-white border-t-transparent rounded-full"></div>
                {{ isSaving ? 'Сохранение...' : (showCreateModal ? 'Создать' : 'Сохранить') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Skill Details Modal -->
    <div
      v-if="showDetailsModal && selectedSkill"
      class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50"
      @click.self="showDetailsModal = false"
    >
      <div class="relative top-20 mx-auto p-5 border w-11/12 max-w-3xl shadow-lg rounded-md bg-white">
        <div class="mt-3">
          <div class="flex justify-between items-start mb-4">
            <h3 class="text-lg font-medium text-gray-900">
              🎯 {{ selectedSkill.title }} ({{ selectedSkill.id }})
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
              <p class="text-sm text-gray-700">{{ selectedSkill.description || 'Нет описания' }}</p>
            </div>

            <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
              <div>
                <h4 class="text-sm font-medium text-gray-900 mb-2">Характеристики</h4>
                <dl class="text-sm">
                  <div class="flex justify-between py-1">
                    <dt class="text-gray-500">Максимальный уровень:</dt>
                    <dd class="text-gray-900">{{ selectedSkill.maxLevel }}</dd>
                  </div>
                  <div class="flex justify-between py-1">
                    <dt class="text-gray-500">Статус:</dt>
                    <dd>
                      <span
                        class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                        :class="{
                          'bg-success-100 text-success-800': selectedSkill.enabled,
                          'bg-gray-100 text-gray-800': !selectedSkill.enabled
                        }"
                      >
                        {{ selectedSkill.enabled ? 'Активен' : 'Отключен' }}
                      </span>
                    </dd>
                  </div>
                  <div class="flex justify-between py-1">
                    <dt class="text-gray-500">Версия:</dt>
                    <dd class="text-gray-900">v{{ selectedSkill.version }}</dd>
                  </div>
                </dl>
              </div>

              <div>
                <h4 class="text-sm font-medium text-gray-900 mb-2">Длительности тренировок</h4>
                <div class="text-sm space-y-1">
                  <div
                    v-for="(duration, index) in selectedSkill.durationsMs"
                    :key="index"
                    class="flex justify-between py-1"
                  >
                    <dt class="text-gray-500">Уровень {{ index + 1 }}:</dt>
                    <dd class="text-gray-900">{{ formatDuration(duration) }}</dd>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import axios from 'axios'

interface Skill {
  id: string
  title: string
  description: string
  maxLevel: number
  enabled: boolean
  durationsMs: number[]
  version: number
}

interface DurationLevel {
  level: number
  durationMs: number
  description: string
}

interface SkillForm {
  id: string
  title: string
  description: string
  maxLevel: number
  enabled: boolean
  category: string
  durations: DurationLevel[]
}

const authStore = useAuthStore()

const skills = ref<Skill[]>([])
const isLoading = ref(false)
const isSaving = ref(false)
const error = ref<string | null>(null)

const showCreateModal = ref(false)
const showEditModal = ref(false)
const showDetailsModal = ref(false)
const selectedSkill = ref<Skill | null>(null)

const skillForm = reactive<SkillForm>({
  id: '',
  title: '',
  description: '',
  maxLevel: 5,
  enabled: true,
  category: '',
  durations: []
})

// Computed properties
const enabledSkills = computed(() => skills.value.filter(s => s.enabled).length)
const maxLevelSum = computed(() => skills.value.reduce((sum, s) => sum + s.maxLevel, 0))

// Methods
const refreshSkills = async () => {
  isLoading.value = true
  error.value = null

  try {
    const response = await axios.get<{ skills: Skill[] }>('/api/admin/config/skills')
    skills.value = response.data.skills || []
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Не удалось загрузить скиллы'
    console.error('Failed to load skills:', err)
  } finally {
    isLoading.value = false
  }
}

const resetSkillForm = () => {
  skillForm.id = ''
  skillForm.title = ''
  skillForm.description = ''
  skillForm.maxLevel = 5
  skillForm.enabled = true
  skillForm.category = ''
  skillForm.durations = []
  generateDefaultDurations()
}

const generateDefaultDurations = () => {
  skillForm.durations = []
  for (let level = 1; level <= skillForm.maxLevel; level++) {
    const durationMs = Math.floor(60000 * Math.pow(5, level - 1)) // 1 min, 5 min, 25 min, etc.
    skillForm.durations.push({
      level,
      durationMs,
      description: formatDuration(durationMs)
    })
  }
}

const editSkill = (skill: Skill) => {
  selectedSkill.value = skill
  skillForm.id = skill.id
  skillForm.title = skill.title
  skillForm.description = skill.description
  skillForm.maxLevel = skill.maxLevel
  skillForm.enabled = skill.enabled
  skillForm.category = ''
  
  // Convert durationsMs to DurationLevel array
  skillForm.durations = skill.durationsMs.map((durationMs, index) => ({
    level: index + 1,
    durationMs,
    description: formatDuration(durationMs)
  }))
  
  showEditModal.value = true
}

const viewSkillDetails = (skill: Skill) => {
  selectedSkill.value = skill
  showDetailsModal.value = true
}

const saveSkill = async () => {
  isSaving.value = true

  try {
    const skillData = {
      id: skillForm.id,
      title: skillForm.title,
      description: skillForm.description,
      maxLevel: skillForm.maxLevel,
      enabled: skillForm.enabled,
      category: skillForm.category,
      durations: skillForm.durations
    }

    if (showCreateModal.value) {
      await axios.post('/api/admin/config/skills', skillData)
    } else {
      await axios.put(`/api/admin/config/skills/${skillForm.id}`, skillData)
    }

    await refreshSkills()
    closeModals()
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Не удалось сохранить скилл'
    console.error('Failed to save skill:', err)
  } finally {
    isSaving.value = false
  }
}

const closeModals = () => {
  showCreateModal.value = false
  showEditModal.value = false
  showDetailsModal.value = false
  selectedSkill.value = null
  resetSkillForm()
}

const formatDuration = (durationMs: number): string => {
  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) {
    return `${days}д ${hours % 24}ч`
  } else if (hours > 0) {
    return `${hours}ч ${minutes % 60}м`
  } else if (minutes > 0) {
    return `${minutes}м ${seconds % 60}с`
  } else {
    return `${seconds}с`
  }
}

// Watch maxLevel changes to update durations
import { watch } from 'vue'
watch(() => skillForm.maxLevel, (newValue, oldValue) => {
  if (newValue !== oldValue && newValue > 0) {
    generateDefaultDurations()
  }
})

onMounted(() => {
  refreshSkills()
  resetSkillForm()
})
</script>
