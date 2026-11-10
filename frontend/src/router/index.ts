import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/spaces' },
    { path: '/spaces', component: () => import('../views/SpaceListView.vue') },
    { path: '/spaces/new', component: () => import('../views/SpaceCreateView.vue') },
    { path: '/spaces/:id', component: () => import('../views/SpaceDetailView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
})

export default router
