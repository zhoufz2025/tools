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
        path: 'spsql',
        name: 'spsql',
        component: () => import('@/views/SpsqlDdl.vue'),
        meta: { title: 'spsql DDL 刷新' }
      },
      {
        path: 'git',
        name: 'git',
        component: () => import('@/views/GitUpdate.vue'),
        meta: { title: 'Git/SVN 批量更新' }
      },
      {
        path: 'svn-xiaobao',
        name: 'svnXiaobao',
        component: () => import('@/views/SvnXiaobao.vue'),
        meta: { title: 'SVN 小包拉包' }
      },
      {
        path: 'package-copy',
        name: 'packageCopy',
        component: () => import('@/views/PackageCopy.vue'),
        meta: { title: '大包→小包复制' }
      },
      {
        path: 'maven-install-product',
        name: 'mavenInstallProduct',
        component: () => import('@/views/MavenInstallProduct.vue'),
        meta: { title: '产品包 Install（对齐工程包版本）' }
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
