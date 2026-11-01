<template>
  <el-card shadow="never">
    <template #header>大包→小包复制（A/B 相对路径）</template>

    <el-form label-width="100px" style="max-width: 960px" @submit.prevent>
      <el-form-item label="源根 A" required>
        <PathFolderInput
          v-model="form.sourceRoot"
          placeholder="大包模块根，如 …/sale/lcpt-dxasset 或 …/spsql/spsql-dxasset/IFMS6.0V…"
        />
      </el-form-item>
      <el-form-item label="目标根 B" required>
        <PathFolderInput
          v-model="form.targetRoot"
          placeholder="小包对应根，如 …/svn/branch/202405.05/lcpt-dxasset"
        />
      </el-form-item>
      <el-form-item label="taskId">
        <el-input
          v-model="form.taskId"
          clearable
          placeholder="SQL 追加用，如 T202605144282；空则脚本也整文件覆盖"
          style="max-width: 360px"
        />
      </el-form-item>
      <el-form-item label="源文件列表" required>
        <el-input
          v-model="form.filesText"
          type="textarea"
          :rows="10"
          placeholder="粘贴源文件绝对路径，每行一个（须在源根 A 下）"
        />
      </el-form-item>
      <el-form-item>
        <el-space wrap>
          <el-button :loading="busy === 'preview'" :disabled="!!busy" @click="doPreview">预览</el-button>
          <el-button type="primary" :loading="busy === 'execute'" :disabled="!!busy" @click="doExecute">
            执行复制
          </el-button>
        </el-space>
      </el-form-item>
    </el-form>

    <el-alert
      title="相对路径 = 文件相对 A；目标 = B + 相对路径。结构差太大（第一级目录对不上）标为风险，默认跳过，需勾选强制确认。"
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

    <div v-if="rows.length" class="toolbar">
      <el-button size="small" @click="selectAllRisk">全选风险项</el-button>
      <el-button size="small" @click="clearForce">清空强制</el-button>
      <span class="hint">
        可复制 {{ summary.okCount }}，风险 {{ summary.riskCount }}，错误 {{ summary.errorCount }}；
        已强制 {{ forceSet.size }}
      </span>
    </div>

    <el-table v-if="rows.length" :data="rows" stripe border size="small" max-height="520">
      <el-table-column label="强制" width="64" align="center">
        <template #default="{ row }">
          <el-checkbox
            v-if="row.status === 'risk'"
            :model-value="forceSet.has(row.source)"
            @change="(v) => toggleForce(row.source, v)"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="80" prop="action" />
      <el-table-column label="相对路径" min-width="180" prop="rel" show-overflow-tooltip />
      <el-table-column label="原因" min-width="220" prop="reason" show-overflow-tooltip />
      <el-table-column label="源" min-width="200" prop="source" show-overflow-tooltip />
      <el-table-column label="目标" min-width="200" prop="target" show-overflow-tooltip />
      <el-table-column v-if="hasResult" label="结果" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.result" size="small" :type="resultTag(row.result)">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="hasResult" label="结果说明" min-width="140" prop="resultMsg" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import PathFolderInput from '@/components/PathFolderInput.vue'

const busy = ref('')
const resultMsg = ref('')
const resultOk = ref(true)
const rows = ref([])
const summary = reactive({ okCount: 0, riskCount: 0, errorCount: 0 })
const forceSet = ref(new Set())

const form = reactive({
  sourceRoot: '/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server/sale/lcpt-dxasset',
  targetRoot: '/Users/zhoufz/hundsun/lcpt60/svn/branch/202405.05/lcpt-dxasset',
  taskId: '',
  filesText: ''
})

const hasResult = computed(() => rows.value.some((r) => r.result))

function parseFiles() {
  return form.filesText
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean)
}

function buildBody(withForce) {
  const body = {
    sourceRoot: form.sourceRoot.trim(),
    targetRoot: form.targetRoot.trim(),
    taskId: form.taskId.trim(),
    files: parseFiles()
  }
  if (withForce) {
    body.forcePaths = Array.from(forceSet.value)
  }
  return body
}

function applySummary(data) {
  summary.okCount = data?.okCount || 0
  summary.riskCount = data?.riskCount || 0
  summary.errorCount = data?.errorCount || 0
}

function statusTag(status) {
  if (status === 'ok') return 'success'
  if (status === 'risk') return 'warning'
  return 'danger'
}

function statusLabel(status) {
  if (status === 'ok') return '可复制'
  if (status === 'risk') return '风险'
  return '错误'
}

function resultTag(result) {
  if (result === 'success') return 'success'
  if (result === 'failed') return 'danger'
  return 'info'
}

function toggleForce(source, checked) {
  const next = new Set(forceSet.value)
  if (checked) {
    next.add(source)
  } else {
    next.delete(source)
  }
  forceSet.value = next
}

function selectAllRisk() {
  const next = new Set(forceSet.value)
  rows.value.filter((r) => r.status === 'risk').forEach((r) => next.add(r.source))
  forceSet.value = next
}

function clearForce() {
  forceSet.value = new Set()
}

async function doPreview() {
  if (!form.sourceRoot?.trim() || !form.targetRoot?.trim()) {
    ElMessage.warning('请填写源根 A 与目标根 B')
    return
  }
  if (!parseFiles().length) {
    ElMessage.warning('请粘贴至少一个源文件路径')
    return
  }
  busy.value = 'preview'
  resultMsg.value = ''
  rows.value = []
  forceSet.value = new Set()
  try {
    const res = await http.post('/hundsun/package/copy/preview', buildBody(false))
    rows.value = res.data?.items || []
    applySummary(res.data)
    resultOk.value = true
    resultMsg.value = res.message || '预览完成'
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '预览失败'
  } finally {
    busy.value = ''
  }
}

async function doExecute() {
  if (!form.sourceRoot?.trim() || !form.targetRoot?.trim()) {
    ElMessage.warning('请填写源根 A 与目标根 B')
    return
  }
  if (!parseFiles().length) {
    ElMessage.warning('请粘贴至少一个源文件路径')
    return
  }
  if (!rows.value.length) {
    await doPreview()
    if (!rows.value.length) return
  }
  const forceCount = forceSet.value.size
  const riskWithoutForce = rows.value.filter((r) => r.status === 'risk' && !forceSet.value.has(r.source)).length
  let tip = '将复制结构 OK 的文件；风险项仅复制已勾选强制的。'
  if (forceCount) {
    tip += `\n已强制 ${forceCount} 个风险项。`
  }
  if (riskWithoutForce) {
    tip += `\n另有 ${riskWithoutForce} 个风险项将跳过。`
  }
  try {
    await ElMessageBox.confirm(tip, '确认执行复制', {
      type: 'warning',
      confirmButtonText: '开始复制',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  busy.value = 'execute'
  resultMsg.value = ''
  try {
    const res = await http.post('/hundsun/package/copy/execute', buildBody(true))
    rows.value = res.data?.items || []
    applySummary(res.data)
    resultOk.value = true
    resultMsg.value = res.message || '完成'
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '复制失败'
    if (e.data?.items) {
      rows.value = e.data.items
      applySummary(e.data)
    }
  } finally {
    busy.value = ''
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.muted {
  color: #c0c4cc;
}
</style>
