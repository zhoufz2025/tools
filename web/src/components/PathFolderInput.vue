<template>
  <div class="path-folder-input">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      clearable
      @update:model-value="emit('update:modelValue', $event)"
    >
      <template #append>
        <el-button :disabled="disabled" @click="openDialog">选择</el-button>
      </template>
    </el-input>

    <el-dialog
      v-model="visible"
      :title="pick === 'file' ? '选择本地文件' : '选择本地文件夹'"
      width="640px"
      append-to-body
      destroy-on-close
      @opened="onOpened"
    >
      <div class="toolbar">
        <el-button text :disabled="!parentPath || loading" @click="go(parentPath)">上级</el-button>
        <el-select
          v-model="quickRoot"
          placeholder="常用目录"
          style="width: 160px"
          @change="onQuickRoot"
        >
          <el-option
            v-for="root in roots"
            :key="root.path"
            :label="root.label"
            :value="root.path"
          />
        </el-select>
        <el-input
          v-model="draftPath"
          placeholder="绝对路径，回车跳转"
          clearable
          @keyup.enter="go(draftPath)"
        />
        <el-button :loading="loading" @click="go(draftPath)">跳转</el-button>
      </div>

      <div v-loading="loading" class="browser">
        <div class="current">当前：{{ currentPath || '-' }}</div>
        <el-empty v-if="!loading && !entries.length" description="此目录下没有子文件夹" :image-size="64" />
        <el-scrollbar v-else max-height="320px">
          <div
            v-for="item in entries"
            :key="item.path"
            class="entry"
            :class="{ active: selectedPath === item.path }"
            @click="selectEntry(item)"
            @dblclick="openEntry(item)"
          >
            <el-icon><Folder v-if="item.isDirectory" /><Document v-else /></el-icon>
            <span class="name">{{ item.name }}</span>
          </div>
        </el-scrollbar>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!canConfirm" @click="confirm">
          {{ pick === 'file' ? '选择此文件' : '选择此文件夹' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Folder } from '@element-plus/icons-vue'
import http from '@/api/http'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请选择或输入绝对路径' },
  disabled: { type: Boolean, default: false },
  pick: { type: String, default: 'folder' }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const loading = ref(false)
const roots = ref([])
const quickRoot = ref('')
const currentPath = ref('')
const parentPath = ref('')
const draftPath = ref('')
const selectedPath = ref('')
const selectedIsFile = ref(false)
const entries = ref([])
const currentIsDir = ref(false)

const canConfirm = computed(() => {
  if (props.pick === 'file') {
    return selectedIsFile.value && !!selectedPath.value
  }
  const path = (selectedPath.value || currentPath.value || '').trim()
  return !!path && currentIsDir.value
})

async function openDialog() {
  visible.value = true
}

async function onOpened() {
  await loadRoots()
  const start = (props.modelValue || '').trim() || roots.value[0]?.path || ''
  await go(start)
}

async function loadRoots() {
  try {
    const res = await http.get('/hundsun/fs/roots')
    roots.value = res.data || []
  } catch {
    roots.value = []
  }
}

function onQuickRoot(path) {
  if (path) {
    go(path)
  }
}

async function go(path) {
  const target = (path || '').trim()
  if (!target) {
    ElMessage.warning('请输入路径')
    return
  }
  loading.value = true
  try {
    const res = await http.get('/hundsun/fs/list', {
      params: { path: target, files: props.pick === 'file' }
    })
    const data = res.data || {}
    currentPath.value = data.path || target
    parentPath.value = data.parent || ''
    draftPath.value = currentPath.value
    currentIsDir.value = !!data.isDirectory
    entries.value = data.children || []
    selectedIsFile.value = false
    selectedPath.value = props.pick === 'file' ? '' : (currentIsDir.value ? currentPath.value : '')
    if (!data.exists) {
      ElMessage.warning('路径不存在')
    } else if (!data.isDirectory) {
      ElMessage.warning('不是文件夹')
    }
  } catch (e) {
    entries.value = []
    currentIsDir.value = false
    selectedPath.value = ''
  } finally {
    loading.value = false
  }
}

function selectEntry(item) {
  if (props.pick === 'file' && item.isDirectory) {
    selectedPath.value = ''
    selectedIsFile.value = false
    return
  }
  selectedPath.value = item.path
  selectedIsFile.value = !item.isDirectory
}

function openEntry(item) {
  if (item.isDirectory) {
    go(item.path)
    return
  }
  selectedPath.value = item.path
  selectedIsFile.value = true
  if (props.pick === 'file') {
    confirm()
  }
}

function confirm() {
  const fallback = props.pick === 'file' ? '' : currentPath.value
  const path = (selectedPath.value || fallback || '').trim()
  if (!path) {
    return
  }
  emit('update:modelValue', path)
  visible.value = false
}
</script>

<style scoped>
.path-folder-input {
  width: 100%;
}
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.browser {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  min-height: 200px;
  padding: 8px;
  background: #fafafa;
}
.current {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  word-break: break-all;
}
.entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
}
.entry:hover {
  background: #ecf5ff;
}
.entry.active {
  background: #d9ecff;
}
.name {
  font-size: 13px;
  word-break: break-all;
}
</style>
