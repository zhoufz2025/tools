import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/db',
    children: [
      {
        path: 'db',
        name: 'db',
        component: () => import('@/views/DbInit.vue'),
        meta: { title: '本地库初始化' }
      },
      {
        path: 'git',
        name: 'git',
        component: () => import('@/views/GitUpdate.vue'),
        meta: { title: 'Git/SVN 批量更新' }
      },
      {
        path: 'config',
        name: 'config',
        component: () => import('@/views/ConfigBackup.vue'),
        meta: { title: '配置备份/还原' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
