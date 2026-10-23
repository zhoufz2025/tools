<template>
  <el-card v-loading="busy" element-loading-text="正在执行，全量初始化可能需要数分钟…" shadow="never">
    <template #header>
      <div class="card-header">
        <span>本地 MySQL 初始化</span>
        <el-button text type="primary" :loading="loadingCats" :disabled="busy" @click="loadCategories">
          刷新种类
        </el-button>
      </div>
    </template>

    <el-form label-width="110px">
      <el-form-item label="数据库种类">
        <el-checkbox-group v-model="selected" :disabled="busy">
          <el-checkbox v-for="c in categories" :key="c.code" :value="c.code">
            {{ c.label }}（{{ c.code }}：{{ c.databases }}）
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="!loadingCats && !categories.length" class="empty-hint">
          未取到种类。请确认后端已启动，再点「刷新种类」。
        </div>
      </el-form-item>

      <el-form-item label="初始化">
        <el-space wrap>
          <el-button
            v-for="item in actions"
            :key="item.action"
            :type="item.type"
            :loading="busy && activeAction === item.action"
            :disabled="busy && activeAction !== item.action"
            @click="run(item)"
          >
            {{ item.label }}
          </el-button>
        </el-space>
      </el-form-item>

      <el-form-item label="分表视图">
        <el-input
          v-model="viewDatabases"
          :disabled="busy"
          placeholder="databases，如 lcptpub；留空则用上方种类"
          style="width: 280px; margin-right: 8px"
        />
        <el-input
          v-model="viewTables"
          :disabled="busy"
          placeholder="tables 可选，如 tbclient"
          style="width: 220px; margin-right: 8px"
        />
        <el-button
          :loading="busy && activeAction === 'createShardingViews'"
          :disabled="busy && activeAction !== 'createShardingViews'"
          @click="createViews"
        >
          生成分表视图
        </el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="result"
      :title="result.message || (result.ok ? '成功' : '失败')"
      :type="result.ok ? 'success' : 'error'"
      show-icon
      :closable="false"
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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const actions = [
  { action: 'createDatabases', label: '仅建库', type: 'primary' },
  {
    action: 'initSchema',
    label: '建库+DDL',
    type: 'warning',
    confirm: '建库+DDL 可能按配置 DROP 并重建所选库，确认继续？'
  },
  {
    action: 'initData',
    label: '仅 initdata',
    type: 'warning',
    confirm: '将向已有表写入 initdata，确认继续？'
  },
  {
    action: 'initAll',
    label: '全量 initAll',
    type: 'danger',
    confirm: '全量初始化会执行建库、DDL 与 initdata，并可能 DROP 重建库，确认继续？'
  },
  {
    action: 'init',
    label: '全量 init',
    type: 'danger',
    confirm: '将调用 /hundsun/db/init，效果与全量初始化相同，可能 DROP 重建库，确认继续？'
  }
]

const categories = ref([])
const selected = ref([])
const loadingCats = ref(false)
const busy = ref(false)
const activeAction = ref('')
const result = ref(null)
const viewDatabases = ref('lcptpub')
const viewTables = ref('')

function categoriesParam() {
  return selected.value.join(',')
}

async function loadCategories() {
  loadingCats.value = true
  try {
    const res = await http.get('/hundsun/db/categories')
    categories.value = res.data || []
    const codes = new Set(categories.value.map((c) => c.code))
    selected.value = selected.value.filter((code) => codes.has(code))
    if (!selected.value.length) {
      selected.value = categories.value.map((c) => c.code)
    }
  } catch (e) {
    result.value = failResult('GET /hundsun/db/categories', e)
  } finally {
    loadingCats.value = false
  }
}

async function confirmDanger(message) {
  try {
    await ElMessageBox.confirm(message, '确认操作', {
      type: 'warning',
      confirmButtonText: '继续',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}

function ensureCategories() {
  if (selected.value.length) {
    return true
  }
  ElMessage.warning('请至少选择一种数据库')
  return false
}

async function run(item) {
  if (!ensureCategories()) {
    return
  }
  if (item.confirm && !(await confirmDanger(item.confirm))) {
    return
  }
  const api = `POST /hundsun/db/${item.action}`
  busy.value = true
  activeAction.value = item.action
  result.value = null
  try {
    const res = await http.post(`/hundsun/db/${item.action}`, null, {
      params: { categories: categoriesParam() }
    })
    result.value = okResult(api, res, summarizeInit)
  } catch (e) {
    result.value = failResult(api, e, summarizeInit)
  } finally {
    busy.value = false
    activeAction.value = ''
  }
}

async function createViews() {
  const databases = viewDatabases.value.trim()
  const tables = viewTables.value.trim()
  if (!databases && !ensureCategories()) {
    return
  }
  if (!(await confirmDanger('将 CREATE OR REPLACE 分表视图，确认继续？'))) {
    return
  }
  const api = 'POST /hundsun/db/createShardingViews'
  const params = {}
  if (databases) {
    params.databases = databases
  } else {
    params.categories = categoriesParam()
  }
  if (tables) {
    params.tables = tables
  }
  busy.value = true
  activeAction.value = 'createShardingViews'
  result.value = null
  try {
    const res = await http.post('/hundsun/db/createShardingViews', null, { params })
    result.value = okResult(api, res, summarizeViews)
  } catch (e) {
    result.value = failResult(api, e, summarizeViews)
  } finally {
    busy.value = false
    activeAction.value = ''
  }
}

function okResult(api, res, summarize) {
  const data = res?.data
  const view = summarize(data)
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

function summarizeInit(data) {
  if (!data || typeof data !== 'object') {
    return { fields: [], lists: [] }
  }
  return {
    fields: [
      { label: '种类', value: text(data.selectedCategories) },
      { label: '耗时(ms)', value: text(data.elapsedMs) },
      { label: '新建库', value: count(data.createdDatabases) },
      { label: '已执行脚本', value: count(data.executedScripts) },
      { label: '跳过脚本', value: count(data.skippedScripts) },
      { label: '错误', value: count(data.errors) }
    ],
    lists: [
      { label: '新建库', items: asList(data.createdDatabases) },
      { label: '已执行脚本', items: asList(data.executedScripts) },
      { label: '跳过脚本', items: asList(data.skippedScripts) },
      { label: '错误', items: asList(data.errors) }
    ]
  }
}

function summarizeViews(data) {
  if (!data || typeof data !== 'object') {
    return { fields: [], lists: [] }
  }
  return {
    fields: [
      { label: '库', value: text(data.databases) },
      { label: '分表数', value: text(data.shardCount) },
      { label: '耗时(ms)', value: text(data.elapsedMs) },
      { label: '新建/更新视图', value: count(data.createdViews) },
      { label: '跳过视图', value: count(data.skippedViews) },
      { label: '错误', value: count(data.errors) }
    ],
    lists: [
      { label: '新建/更新视图', items: asList(data.createdViews) },
      { label: '跳过视图', items: asList(data.skippedViews) },
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

onMounted(loadCategories)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.summary,
.lists {
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
