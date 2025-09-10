import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'
import SkillsView from '@/views/SkillsView.vue'
import RecipesView from '@/views/RecipesView.vue'
import BonusesView from '@/views/BonusesView.vue'

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { requiresGuest: true }
    },
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: { requiresAuth: true }
    },
    {
      path: '/skills',
      name: 'skills',
      component: SkillsView,
      meta: { requiresAuth: true, requiresRole: ['admin', 'developer'] }
    },
    {
      path: '/recipes',
      name: 'recipes',
      component: RecipesView,
      meta: { requiresAuth: true, requiresRole: ['admin', 'developer'] }
    },
    {
      path: '/bonuses',
      name: 'bonuses',
      component: BonusesView,
      meta: { requiresAuth: true, requiresRole: ['admin', 'developer'] }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// Navigation guards
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // Initialize auth store if not done yet
  if (!authStore.user && authStore.token) {
    await authStore.init()
  }

  // Check if route requires authentication
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login')
    return
  }

  // Check if route requires guest (not authenticated)
  if (to.meta.requiresGuest && authStore.isAuthenticated) {
    next('/')
    return
  }

  // Check role requirements
  if (to.meta.requiresRole && authStore.user) {
    const requiredRoles = Array.isArray(to.meta.requiresRole) 
      ? to.meta.requiresRole as string[]
      : [to.meta.requiresRole as string]
    
    const hasRequiredRole = requiredRoles.some(role => 
      authStore.user?.roles.includes(role)
    )

    if (!hasRequiredRole) {
      console.warn('Access denied: insufficient permissions')
      next('/')
      return
    }
  }

  next()
})

export default router
