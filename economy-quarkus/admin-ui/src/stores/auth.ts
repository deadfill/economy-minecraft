import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'

export interface User {
  username: string
  roles: string[]
  isAdmin: boolean
  isDeveloper: boolean
}

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  refreshToken: string
  username: string
  roles: string[]
  expiresIn: number
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('auth_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refresh_token'))
  const user = ref<User | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Computed properties
  const isAuthenticated = computed(() => !!token.value && !!user.value)
  const isAdmin = computed(() => user.value?.isAdmin || false)
  const isDeveloper = computed(() => user.value?.isDeveloper || false)
  const canManageSystem = computed(() => isAdmin.value)
  const canEditContent = computed(() => isAdmin.value || isDeveloper.value)

  // Setup axios interceptors
  const setupAxiosInterceptors = () => {
    // Request interceptor to add token
    axios.interceptors.request.use(
      (config) => {
        if (token.value) {
          config.headers.Authorization = `Bearer ${token.value}`
        }
        return config
      },
      (error) => Promise.reject(error)
    )

    // Response interceptor to handle auth errors
    axios.interceptors.response.use(
      (response) => response,
      async (error) => {
        if (error.response?.status === 401 && isAuthenticated.value) {
          await logout()
        }
        return Promise.reject(error)
      }
    )
  }

  // Actions
  const login = async (credentials: LoginCredentials): Promise<void> => {
    isLoading.value = true
    error.value = null

    try {
      const response = await axios.post<LoginResponse>('/api/auth/login', credentials)
      const data = response.data

      // Store tokens
      token.value = data.token
      refreshToken.value = data.refreshToken
      localStorage.setItem('auth_token', data.token)
      localStorage.setItem('refresh_token', data.refreshToken)

      // Set user data
      user.value = {
        username: data.username,
        roles: data.roles,
        isAdmin: data.roles.includes('admin'),
        isDeveloper: data.roles.includes('developer')
      }

      setupAxiosInterceptors()

      console.log('✅ Login successful:', user.value)
    } catch (err: any) {
      error.value = err.response?.data?.error || 'Login failed'
      console.error('❌ Login failed:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const logout = async (): Promise<void> => {
    try {
      if (token.value) {
        await axios.post('/api/auth/logout')
      }
    } catch (err) {
      console.warn('Logout request failed:', err)
    } finally {
      // Clear all auth data
      token.value = null
      refreshToken.value = null
      user.value = null
      localStorage.removeItem('auth_token')
      localStorage.removeItem('refresh_token')
      
      // Clear axios default headers
      delete axios.defaults.headers.common['Authorization']
      
      console.log('✅ Logged out successfully')
    }
  }

  const getCurrentUser = async (): Promise<void> => {
    if (!token.value) return

    try {
      const response = await axios.get<User>('/api/auth/me')
      user.value = response.data
      setupAxiosInterceptors()
    } catch (err) {
      console.error('Failed to get current user:', err)
      await logout()
    }
  }

  const validateToken = async (): Promise<boolean> => {
    if (!token.value) return false

    try {
      const response = await axios.get('/api/auth/validate')
      return response.data.valid
    } catch (err) {
      console.error('Token validation failed:', err)
      return false
    }
  }

  // Initialize store
  const init = async (): Promise<void> => {
    if (token.value) {
      const isValid = await validateToken()
      if (isValid) {
        await getCurrentUser()
      } else {
        await logout()
      }
    }
  }

  return {
    // State
    token,
    refreshToken,
    user,
    isLoading,
    error,

    // Computed
    isAuthenticated,
    isAdmin,
    isDeveloper,
    canManageSystem,
    canEditContent,

    // Actions
    login,
    logout,
    getCurrentUser,
    validateToken,
    init,
    setupAxiosInterceptors
  }
})
