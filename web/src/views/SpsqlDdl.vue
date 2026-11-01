<template>
  <el-card v-loading="busy" element-loading-text="正在执行 spsql DDL，可能需要数分钟…" shadow="never">
    <template #header>
      <div class="card-header">
        <span>spsql 发包 DDL 刷新</span>
        <el-button text type="primary" :loading="loadingMeta" :disabled="busy" @click="reloadMeta">
          刷新模块/版本
        </el-button>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon class="tip">
      先执行同版本标准 <code>*.mysql.proc.DDL.sql</code>（如 <code>proc_modpk</code>），再执行
      <code>*.mysql.DDL.sql</code>（排除 BANK / taclear）；自动去 UTF-8 BOM；表/列/索引已存在则忽略。
      基线缺 <code>proc_addindex</code> 等时会从 <code>000create_proc_mysql.sql</code> 补齐。
      账本：lcptpub.tb_tools_spsql_ddl_applied。错误看下方列表。
    </el-alert>

    <el-form label-width="110px" class="form">
      <el-form-item label="spsql 根目录">
        <span class="mono">{{ spsqlRoot || '-' }}</span>
      </el-form-item>

      <el-form-item label="系统模块">
        <el-checkbox-group v-model="selected" :disabled="busy">
          <el-checkbox v-for="m in modules" :key="m.code" :value="m.code">
            {{ m.label }}（{{ m.code }}）
          </el-checkbox>
        </el-checkbox-group>
        <div class="ops">
          <el-button link type="primary" :disabled="busy" @click="selectAll">全选</el-button>
          <el-button link :disabled="busy" @click="selected = []">清空</el-button>
        </div>
        <div v-if="!loadingMeta && !modules.length" class="empty-hint">
          未取到模块。请确认后端已启动，再点「刷新模块/版本」。
        </div>
      </el-form-item>

      <el-form-item label="发包版本">
        <el-select
          v-model="version"
          filterable
          clearable
          :disabled="busy"
          placeholder="选择 IFMS6.0V… 版本（所选模块并集）"
          style="width: 420px"
        >
          <el-option v-for="v in versions" :key="v" :label="v" :value="v" />
        </el-select>
        <el-button
          class="ml"
          :loading="loadingVersions"
          :disabled="busy || !selected.length"
          @click="loadVersions"
        >
          按所选模块刷新版本
        </el-button>
      </el-form-item>

      <el-form-item label="使用账本">
        <el-switch v-model="useLedger" :disabled="busy" />
        <span class="hint">开启后跳过已成功语句，并写入账本（推荐）</span>
      </el-form-item>

      <el-form-item label="操作">
        <el-space wrap>
          <el-button :loading="busy && activeAction === 'preview'" :disabled="busy" @click="doPreview">
            预览脚本
          </el-button>
          <el-button
            type="primary"
            :loading="busy && activeAction === 'apply'"
            :disabled="busy"
            @click="doApply"
          >
            执行刷新
          </el-button>
        </el-space>
      </el-form-item>
    </el-form>

    <el-table
      v-if="previewRows.length"
      :data="previewRows"
      border
      stripe
      size="small"
      max-height="360"
      class="preview"
    >
      <el-table-column prop="module" label="模块" width="88" />
      <el-table-column label="片区" width="110">
        <template #default="{ row }">
          <el-tag :type="row.area === 'trans' ? 'warning' : 'success'" size="small" effect="plain">
            {{ row.areaLabel || (row.area === 'trans' ? '交易片区' : '公共片区') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="database" label="目标库" width="130" />
      <el-table-column label="脚本相对路径" min-width="360" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="mono">{{ row.relPath || row.path || row.file }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      v-if="result"
      :title="result.message || (result.ok ? '成功' : '失败')"
      :type="result.ok ? 'success' : 'error'"
      show-icon
      :closable="false"
      class="summary-alert"
    >
      <template #default>
        <span>接口 {{ result.api }}，code={{ result.code }}</span>
      </template>
    </el-alert>

    <el-descriptions v-if="result && result.fields.length" :column="2" border class="summary">
      <el-descriptions-item v-for="field in result.fields" :key="field.label" :label="field.label">
        {{ field.value }}
      </el-descriptions-item>
    </el-descriptions>

    <el-collapse v-if="result && result.lists.length" class="lists">
      <el-collapse-item
        v-for="list in result.lists"
        :key="list.label"
        :title="`${list.label}（${list.items.length}）`"
        :name="list.label"
      >
        <div v-if="!list.items.length" class="empty-hint">无</div>
        <div v-for="(line, index) in list.items" :key="index" class="line">{{ line }}</div>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const modules = ref([])
const selected = ref([])
const versions = ref([])
const version = ref('')
const spsqlRoot = ref('')
const useLedger = ref(true)
const previewRows = ref([])

const loadingMeta = ref(false)
const loadingVersions = ref(false)
const busy = ref(false)
const activeAction = ref('')
const result = ref(null)

function modulesParam() {
  return selected.value.join(',')
}

function selectAll() {
  selected.value = modules.value.map((m) => m.code)
}

async function loadModules() {
  const res = await http.get('/hundsun/db/spsql/modules')
  modules.value = res.data || []
  const codes = new Set(modules.value.map((m) => m.code))
  selected.value = selected.value.filter((c) => codes.has(c))
  if (!selected.value.length) {
    selected.value = modules.value.map((m) => m.code)
  }
}

async function loadVersions() {
  if (!selected.value.length) {
    ElMessage.warning('请至少选择一个系统模块')
    return
  }
  loadingVersions.value = true
  try {
    const res = await http.get('/hundsun/db/spsql/versions', {
      params: { modules: modulesParam() }
    })
    const data = res.data || {}
    spsqlRoot.value = data.spsqlRoot || ''
    versions.value = data.versions || []
    if (version.value && !versions.value.includes(version.value)) {
      version.value = ''
    }
    if (!version.value && versions.value.length) {
      version.value = versions.value[0]
    }
  } finally {
    loadingVersions.value = false
  }
}

async function reloadMeta() {
  loadingMeta.value = true
  result.value = null
  previewRows.value = []
  try {
    await loadModules()
    await loadVersions()
  } catch (e) {
    result.value = failResult('GET /hundsun/db/spsql/modules|versions', e)
  } finally {
    loadingMeta.value = false
  }
}

function ensureReady() {
  if (!selected.value.length) {
    ElMessage.warning('请至少选择一个系统模块')
    return false
  }
  if (!version.value) {
    ElMessage.warning('请选择发包版本')
    return false
  }
  return true
}

async function doPreview() {
  if (!ensureReady()) return
  const api = 'GET /hundsun/db/spsql/preview'
  busy.value = true
  activeAction.value = 'preview'
  result.value = null
  previewRows.value = []
  try {
    const res = await http.get('/hundsun/db/spsql/preview', {
      params: { version: version.value, modules: modulesParam() }
    })
    previewRows.value = res.data || []
    result.value = {
      ok: true,
      api,
      code: res?.code ?? 200,
      message: res?.message || `预览 ${previewRows.value.length} 个脚本任务`,
      fields: [
        { label: '版本', value: version.value },
        { label: '模块', value: modulesParam() },
        { label: '任务数', value: String(previewRows.value.length) }
      ],
      lists: []
    }
  } catch (e) {
    result.value = failResult(api, e)
  } finally {
    busy.value = false
    activeAction.value = ''
  }
}

async function doApply() {
  if (!ensureReady()) return
  try {
    await ElMessageBox.confirm(
      `将对模块 [${modulesParam()}] 执行版本 ${version.value} 的标准 mysql.DDL（排除 BANK）。账本=${useLedger.value ? '开' : '关'}。确认继续？`,
      '确认刷新',
      { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const api = 'POST /hundsun/db/spsql/apply'
  busy.value = true
  activeAction.value = 'apply'
  result.value = null
  try {
    const res = await http.post('/hundsun/db/spsql/apply', null, {
      params: {
        version: version.value,
        modules: modulesParam(),
        useLedger: useLedger.value
      }
    })
    result.value = okResult(api, res)
  } catch (e) {
    result.value = failResult(api, e, summarizeApply)
  } finally {
    busy.value = false
    activeAction.value = ''
  }
}

function okResult(api, res) {
  const view = summarizeApply(res?.data)
  return {
    ok: true,
    api,
    code: res?.code ?? 200,
    message: res?.message || '成功',
    fields: view.fields,
    lists: view.lists
  }
}

function failResult(api, error, summarize) {
  const body = error?.code ? error : error?.response?.data
  const data = body?.data ?? error?.data
  const view = summarize ? summarize(data) : { fields: [], lists: [] }
  return {
    ok: false,
    api,
    code: body?.code ?? error?.response?.status ?? '',
    message: body?.message || error?.message || '失败',
    fields: view.fields,
    lists: view.lists
  }
}

function summarizeApply(data) {
  if (!data || typeof data !== 'object') {
    return { fields: [], lists: [] }
  }
  return {
    fields: [
      { label: '版本', value: text(data.packageVersion) },
      { label: '模块', value: text(data.modules) },
      { label: '账本', value: data.useLedger ? '开' : '关' },
      { label: '耗时(ms)', value: text(data.elapsedMs) },
      { label: '成功语句', value: text(data.successStatements) },
      { label: '忽略已存在', value: text(data.ignoredCount) },
      { label: '账本跳过', value: text(data.ledgerSkipCount) },
      { label: '错误', value: count(data.errors) }
    ],
    lists: [
      { label: '已执行文件', items: asList(data.executedFiles) },
      { label: '跳过文件', items: asList(data.skippedFiles) },
      { label: '忽略语句', items: asList(data.ignoredStatements) },
      { label: '账本跳过', items: asList(data.ledgerSkipped) },
      { label: '错误', items: asList(data.errors) }
    ]
  }
}

function text(value) {
  if (value === 0) return '0'
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function asList(value) {
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

function count(value) {
  return String(asList(value).length)
}

watch(selected, () => {
  // 模块变更后版本并集可能变化，清空预览
  previewRows.value = []
})

onMounted(reloadMeta)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tip {
  margin-bottom: 16px;
}
.form {
  margin-top: 8px;
}
.ops {
  margin-top: 4px;
}
.ml {
  margin-left: 8px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 13px;
}
.preview,
.summary,
.lists,
.summary-alert {
  margin-top: 16px;
}
.empty-hint,
.line {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.line {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  word-break: break-all;
}
</style>
