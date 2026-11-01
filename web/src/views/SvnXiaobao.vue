<template>
  <el-card shadow="never">
    <template #header>SVN 小包拉包</template>

    <el-form label-width="110px" style="max-width: 920px" @submit.prevent>
      <el-form-item label="完整分支名" required>
        <el-input
          v-model="form.branch"
          placeholder="例如 IFMS6.0V202405.05.001"
          clearable
          style="max-width: 420px"
          @change="onBranchChange"
        />
        <span v-if="shortName" class="hint">短名目录 → {{ shortName }}</span>
      </el-form-item>

      <el-form-item label="本地根目录">
        <PathFolderInput v-model="form.localRoot" placeholder="小包根，其下再建短名文件夹" />
      </el-form-item>

      <el-form-item label="业务代码">
        <el-checkbox-group v-model="form.modules">
          <el-checkbox v-for="m in saleModules" :key="m.code" :value="m.code">
            {{ m.label }}（{{ m.code }}）
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="脚本版本">
        <div class="spsql-row">
          <el-select
            v-model="form.spsqlVersions"
            multiple
            filterable
            allow-create
            default-first-option
            clearable
            :loading="spsqlLoading"
            placeholder="选短名系列版本，可多选；也可手输"
            style="width: 100%; max-width: 560px"
          >
            <el-option v-for="v in spsqlOptions" :key="v" :label="v" :value="v" />
          </el-select>
          <el-button :loading="spsqlLoading" :disabled="!!busy || !form.branch?.trim()" @click="loadSpsqlVersions">
            刷新脚本
          </el-button>
        </div>
        <div class="hint">
          按分支短名从 SVN spsql 列出（如 202405.05 → IFMS6.0V202405.05.*），新版本在前；整目录含 pub/、trans/
        </div>
      </el-form-item>

      <el-form-item label="管理台前端">
        <el-checkbox-group v-model="form.frontModules">
          <el-checkbox v-for="m in frontModules" :key="m.code" :value="m.code">
            {{ m.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="低柜">
        <el-checkbox v-model="form.includeCounter">ifmcounter</el-checkbox>
      </el-form-item>

      <el-form-item>
        <el-space wrap>
          <el-button :loading="busy === 'preview'" :disabled="!!busy" @click="preview">预览路径</el-button>
          <el-button type="primary" :loading="busy === 'checkout'" :disabled="!!busy" @click="checkout">
            开始拉包
          </el-button>
          <el-button :loading="busy === 'local'" :disabled="!!busy" @click="scanLocal">扫描本地已拉</el-button>
        </el-space>
      </el-form-item>
    </el-form>

    <el-alert
      title="父目录常禁止 list，按叶子 URL 直接 checkout。本地已是同 URL 工作副本则自动 svn update；URL 不一致或非空非 SVN 目录会失败。"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-alert
      v-if="resultMsg"
      :title="resultMsg"
      :type="resultOk ? 'success' : 'error'"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-table v-if="rows.length" :data="rows" stripe border size="small" max-height="480">
      <el-table-column prop="kind" label="类型" width="80" />
      <el-table-column prop="name" label="名称" width="200" show-overflow-tooltip />
      <el-table-column label="本地状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.localStatus || row.success)">
            {{ statusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="localPath" label="本地路径" min-width="240" show-overflow-tooltip />
      <el-table-column prop="url" label="SVN URL" min-width="280" show-overflow-tooltip />
    </el-table>

    <el-table
      v-if="localRows.length"
      :data="localRows"
      stripe
      border
      size="small"
      max-height="360"
      style="margin-top: 16px"
    >
      <el-table-column prop="name" label="已拉相对路径" width="220" show-overflow-tooltip />
      <el-table-column prop="relativeUrl" label="relative-url" min-width="220" show-overflow-tooltip />
      <el-table-column prop="localPath" label="本地路径" min-width="240" show-overflow-tooltip />
      <el-table-column prop="url" label="URL" min-width="280" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import PathFolderInput from '@/components/PathFolderInput.vue'

const busy = ref('')
const spsqlLoading = ref(false)
const resultMsg = ref('')
const resultOk = ref(true)
const rows = ref([])
const localRows = ref([])
const saleModules = ref([])
const frontModules = ref([])
const spsqlOptions = ref([])

const form = reactive({
  branch: 'IFMS6.0V202405.05.001',
  localRoot: '/Users/zhoufz/hundsun/lcpt60/svn/branch',
  modules: ['lcpt-dxasset'],
  spsqlVersions: [],
  frontModules: [],
  includeCounter: false
})

const shortName = computed(() => {
  const m = String(form.branch || '').match(/IFMS6\.0V(\d{6}\.\d{2})/i)
  return m ? m[1] : ''
})

function onBranchChange() {
  rows.value = []
  form.spsqlVersions = []
  loadSpsqlVersions()
}

async function loadSpsqlVersions() {
  if (!form.branch?.trim()) {
    spsqlOptions.value = []
    return
  }
  spsqlLoading.value = true
  try {
    const res = await http.get('/hundsun/svn/xiaobao/spsqlVersions', {
      params: { branch: form.branch.trim() }
    })
    spsqlOptions.value = res.data?.versions || []
    if (!spsqlOptions.value.length) {
      ElMessage.warning(res.message || '未拉到脚本版本，可手输或点刷新')
    }
  } catch (e) {
    spsqlOptions.value = []
    ElMessage.warning(e.message || '列出脚本版本失败')
  } finally {
    spsqlLoading.value = false
  }
}

function statusType(status) {
  if (status === true || status === 'true' || status === 'ready') return 'success'
  if (status === 'missing') return 'info'
  if (status === 'url-mismatch' || status === 'exists-not-svn' || status === false || status === 'false') {
    return 'danger'
  }
  return 'warning'
}

function statusText(row) {
  if (row.success === 'true' || row.success === true) return '成功'
  if (row.success === 'false' || row.success === false) return '失败'
  const map = {
    ready: '已存在',
    missing: '未拉',
    'url-mismatch': 'URL不一致',
    'exists-not-svn': '非SVN目录'
  }
  return map[row.localStatus] || row.localStatus || '-'
}

function buildParams() {
  return {
    branch: form.branch.trim(),
    localRoot: form.localRoot?.trim() || undefined,
    modules: form.modules.join(','),
    spsqlVersions: form.spsqlVersions.join(','),
    frontModules: form.frontModules.join(','),
    includeCounter: form.includeCounter
  }
}

async function loadPresets() {
  try {
    const res = await http.get('/hundsun/svn/xiaobao/presets')
    const data = res.data || {}
    if (data.localRoot) form.localRoot = data.localRoot
    saleModules.value = data.saleModules || []
    frontModules.value = data.frontModules || []
  } catch (e) {
    ElMessage.warning(e.message || '加载预设失败')
  }
}

async function preview() {
  if (!form.branch?.trim()) {
    ElMessage.warning('请填写完整分支名')
    return
  }
  busy.value = 'preview'
  resultMsg.value = ''
  rows.value = []
  try {
    const res = await http.get('/hundsun/svn/xiaobao/preview', { params: buildParams() })
    rows.value = res.data || []
    resultOk.value = true
    resultMsg.value = res.message || `共 ${rows.value.length} 项`
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '预览失败'
  } finally {
    busy.value = ''
  }
}

async function checkout() {
  if (!form.branch?.trim()) {
    ElMessage.warning('请填写完整分支名')
    return
  }
  if (!form.modules.length && !form.spsqlVersions.length && !form.frontModules.length && !form.includeCounter) {
    ElMessage.warning('请至少选择一项要拉的内容')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将 checkout 到 ${form.localRoot}/${shortName.value || '?'} 下，已存在同 URL 工作副本会 update。确认继续？`,
      '确认拉包',
      { type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = 'checkout'
  resultMsg.value = ''
  try {
    const res = await http.get('/hundsun/svn/xiaobao/checkout', { params: buildParams() })
    rows.value = res.data?.items || []
    resultOk.value = true
    resultMsg.value = res.message || '完成'
    await scanLocal(true)
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '拉包失败'
    if (e.data?.items) {
      rows.value = e.data.items
    }
  } finally {
    busy.value = ''
  }
}

async function scanLocal(silent) {
  busy.value = 'local'
  if (!silent) {
    resultMsg.value = ''
  }
  localRows.value = []
  try {
    const res = await http.get('/hundsun/svn/xiaobao/local', {
      params: {
        branch: form.branch?.trim() || undefined,
        localRoot: form.localRoot?.trim() || undefined
      }
    })
    localRows.value = res.data?.items || []
    if (!silent) {
      resultOk.value = true
      resultMsg.value = res.message || `扫描到 ${localRows.value.length} 个`
    }
  } catch (e) {
    if (!silent) {
      resultOk.value = false
      resultMsg.value = e.message || '扫描失败'
    }
  } finally {
    busy.value = ''
  }
}

onMounted(async () => {
  await loadPresets()
  await loadSpsqlVersions()
})
</script>

<style scoped>
.hint {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.spsql-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}
</style>
