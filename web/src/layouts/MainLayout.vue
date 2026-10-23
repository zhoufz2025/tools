<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">恒生本地工具台</div>
      <el-menu
        :default-active="active"
        router
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/db">
          <el-icon><Coin /></el-icon>
          <span>本地库初始化</span>
        </el-menu-item>
        <el-menu-item index="/git">
          <el-icon><Refresh /></el-icon>
          <span>Git/SVN 批量更新</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><FolderOpened /></el-icon>
          <span>配置备份/还原</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ currentTitle }}</span>
        <span class="hint">API 代理 → http://127.0.0.1:18080</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Coin, Refresh, FolderOpened } from '@element-plus/icons-vue'

const route = useRoute()
const active = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '工具台')
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #1f2d3d;
  color: #fff;
}
.brand {
  height: 56px;
  line-height: 56px;
  padding: 0 16px;
  font-weight: 600;
  font-size: 15px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.main {
  background: #f5f7fa;
}
</style>
