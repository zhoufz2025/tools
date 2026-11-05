<template>
  <el-card shadow="never">
    <template #header>Git / SVN 批量更新</template>
    <el-tabs v-model="tab.name">
      <el-tab-pane label="查询分支" name="branches">
        <el-form label-width="100px" style="max-width: 760px" @submit.prevent>
          <el-form-item label="基础路径">
            <PathFolderInput v-model="branchForm.basePath" placeholder="扫描该目录下的仓库" />
          </el-form-item>
          <el-form-item label="仓库类型">
            <el-radio-group v-model="branchForm.vcsType">
              <el-radio-button value="git">仅 Git</el-radio-button>
              <el-radio-button value="svn">仅 SVN</el-radio-button>
              <el-radio-button value="all">Git + SVN</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy" @click="listBranches">查询</el-button>
          </el-form-item>
        </el-form>
        <div v-if="branchRows.length" class="branch-filter">
          <el-input v-model="branchFilter" placeholder="按仓库名/分支过滤" clearable style="width: 260px" />
          <span class="hint">共 {{ filteredBranchRows.length }} / {{ branchRows.length }} 个</span>
        </div>
        <el-table
          v-if="branchRows.length"
          :data="filteredBranchRows"
          stripe
          border
          size="small"
          max-height="480"
          highlight-current-row
          class="branch-table"
          @row-click="openCheckoutDialog"
        >
          <el-table-column prop="type" label="类型" width="60" />
          <el-table-column label="形态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.kind === 'submodule' ? 'warning' : 'info'">
                {{ row.kind === 'submodule' ? '子模块' : '仓库' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="仓库" width="180" show-overflow-tooltip />
          <el-table-column prop="branch" label="当前分支/版本" min-width="220" show-overflow-tooltip />
          <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openCheckoutDialog(row)">切换</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="branchRows.length"
          title="点击行或「切换」可单独切分支。HUI 父仓多为 dev；console-*-vue 子模块常显示 tag/版本-提交。要切某业务前端版本，点对应子模块行即可。"
          type="info"
          show-icon
          :closable="false"
          style="margin-top: 12px"
        />
      </el-tab-pane>

      <el-dialog
        v-model="checkoutDialog.visible"
        :title="`切换分支 — ${checkoutDialog.name || ''}`"
        width="600px"
        append-to-body
        destroy-on-close
      >
        <el-form label-width="90px">
          <el-form-item label="类型">
            <el-tag size="small">{{ checkoutDialog.type }}</el-tag>
            <el-tag
              v-if="checkoutDialog.kind === 'submodule'"
              size="small"
              type="warning"
              style="margin-left: 8px"
            >
              子模块
            </el-tag>
          </el-form-item>
          <el-form-item label="路径">
            <span class="path-text">{{ checkoutDialog.path }}</span>
          </el-form-item>
          <el-form-item label="当前">
            <span>{{ checkoutDialog.currentBranch || '-' }}</span>
          </el-form-item>
          <el-form-item :label="checkoutDialog.type === 'svn' ? '切换目标' : '目标分支'">
            <el-input
              v-model="checkoutDialog.ref"
              :placeholder="checkoutDialog.type === 'svn'
                ? 'SVN URL 或 ^/branches/xxx'
                : (checkoutDialog.kind === 'submodule'
                  ? '业务前端版本：tag/IFMS6.0V… 或 IFMS6.0V…'
                  : '例如 IFMS6.0V202607.00.005 或 hotfix/IFMS6.0V202607.00.005')"
              clearable
              @keyup.enter="confirmCheckoutOne"
            />
          </el-form-item>
          <el-form-item v-if="checkoutDialog.type === 'git'" label="选项">
            <el-checkbox v-model="checkoutOpts.fetchTags">拉取 tags</el-checkbox>
            <el-checkbox v-model="checkoutOpts.pullAfter">切换后再 pull</el-checkbox>
            <el-checkbox v-model="checkoutOpts.updateSubmodules">更新 submodule</el-checkbox>
            <el-checkbox v-model="checkoutOpts.skipIfSame">已在目标则跳过</el-checkbox>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="checkoutDialog.kind === 'submodule'"
          title="仅切换该业务前端 submodule。切发布 tag 请填 tag/版本号（会按需拉单个 tag，默认不拉全量 tags）。"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 8px"
        />
        <template #footer>
          <el-button @click="checkoutDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="busy" @click="confirmCheckoutOne">确认切换</el-button>
        </template>
      </el-dialog>

      <el-tab-pane label="批量更新" name="update">
        <el-form label-width="100px" style="max-width: 860px" @submit.prevent>
          <el-form-item label="基础路径">
            <PathFolderInput
              v-model="updateForm.basePath"
              placeholder="例如 …/lcpt-front/HUI1.0 或 …/git/Sources/"
            />
          </el-form-item>
          <el-form-item label="仓库类型">
            <el-radio-group v-model="updateForm.vcsType">
              <el-radio-button value="git">仅 Git</el-radio-button>
              <el-radio-button value="svn">仅 SVN</el-radio-button>
              <el-radio-button value="all">Git + SVN</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="updateForm.vcsType !== 'svn'" label="选项">
            <el-checkbox v-model="updateForm.updateSubmodules">
              同时更新 submodule（HUI1.0 等前端父仓默认不要勾，会慢很多）
            </el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy" @click="updateAll">批量更新</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          title="默认只 pull 扫描到的父仓，不展开/更新 submodule。勾选后才会把 console-*-vue 等一并 pull。失败则 stash → pull → stash pop（冲突以本地 stash 为准）。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-tab-pane>

      <el-tab-pane label="切换分支" name="checkout">
        <el-form label-width="100px" style="max-width: 860px" @submit.prevent>
          <el-form-item label="基础路径">
            <PathFolderInput v-model="checkoutForm.basePath" placeholder="扫描该目录下的仓库" />
          </el-form-item>
          <el-form-item label="仓库类型">
            <el-radio-group v-model="checkoutForm.vcsType">
              <el-radio-button value="git">仅 Git</el-radio-button>
              <el-radio-button value="svn">仅 SVN</el-radio-button>
              <el-radio-button value="all">Git + SVN</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="checkoutForm.vcsType === 'svn' ? '切换目标' : '分支/标签'">
            <el-input
              v-model="checkoutForm.ref"
              :placeholder="checkoutForm.vcsType === 'svn'
                ? 'SVN URL 或 ^/branches/xxx'
                : '例如 IFMS6.0V202607.00.005 或 hotfix/IFMS6.0V202607.00.005'"
              clearable
            />
          </el-form-item>
          <el-form-item v-if="checkoutForm.vcsType !== 'svn'" label="选项">
            <el-checkbox v-model="checkoutOpts.fetchTags">拉取 tags</el-checkbox>
            <el-checkbox v-model="checkoutOpts.pullAfter">切换后再 pull</el-checkbox>
            <el-checkbox v-model="checkoutOpts.updateSubmodules">更新 submodule</el-checkbox>
            <el-checkbox v-model="checkoutOpts.skipIfSame">已在目标则跳过</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="busy" @click="checkoutAll">切换</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          title="默认轻量：先本地 checkout → 单分支 fetch（不拉全量 tags）→ 不 pull、不 recursive submodule。切 tag 请填 tag/版本 或勾选「拉取 tags」。"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-table
          v-if="checkoutResultRows.length"
          :data="checkoutResultRows"
          stripe
          border
          size="small"
          max-height="420"
          style="margin-top: 12px"
        >
          <el-table-column prop="name" label="仓库" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ok' || row.status === 'skipped' ? 'success' : 'danger'">
                {{ row.status === 'skipped' ? '跳过' : (row.status === 'ok' ? '成功' : '失败') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="branch" label="当前分支" min-width="160" show-overflow-tooltip />
          <el-table-column label="耗时" width="100">
            <template #default="{ row }">{{ formatMs(row.timings?.totalMs) }}</template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="代码替换（废弃）" name="replace">
        <el-alert
          title="已废弃：请改用左侧菜单「大包→小包复制」（源根 A + 目标根 B + 文件列表，结构风险需强制确认）。本页配置文件方式仅保留兼容。"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />
        <el-form label-width="110px" style="max-width: 760px" @submit.prevent>
          <el-form-item label="替换类型">
            <el-radio-group v-model="replaceForm.vcsType">
              <el-radio-button value="git">Git（大包→小包）</el-radio-button>
              <el-radio-button value="svn">SVN（大包→小包）</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="配置文件">
            <PathFolderInput
              v-model="replaceForm.filePath"
              pick="file"
              :placeholder="replaceForm.vcsType === 'svn'
                ? 'svnReplaceFile.txt 的绝对路径，或 classpath 资源名'
                : 'gitReplaceFile.txt 的绝对路径，或 classpath 资源名'"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" :loading="busy" @click="replaceCode">开始替换</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="replaceForm.vcsType === 'git'"
          title="沿用 gitReplaceSqlFile：第1行源前缀=有|无，第2行目标根，第3行 taskId，第4行 verion，其后为源文件绝对路径。SQL 按任务号追加，其余文件覆盖复制到小包对应模块。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else
          title="沿用 svnReplaceSqlFile：依次为源前缀、目标前缀、脚本前缀、VUE 前缀、taskId，其后为相对路径。源前缀含「小包」时按小包路径取值。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-tab-pane>
    </el-tabs>

    <el-alert
      v-if="resultMsg"
      :title="resultMsg"
      :type="resultOk ? 'success' : 'error'"
      show-icon
      :closable="false"
      style="margin-top: 16px"
    />
  </el-card>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import PathFolderInput from '@/components/PathFolderInput.vue'
import { usePersistedState } from '@/composables/usePersistedState'

const tab = usePersistedState('gitUpdate.tab', { name: 'branches' })
const busy = ref(false)
const resultMsg = ref('')
const resultOk = ref(true)
const branchRows = ref([])
const branchFilter = ref('')
const checkoutDialog = reactive({
  visible: false,
  type: 'git',
  kind: 'repo',
  name: '',
  path: '',
  currentBranch: '',
  ref: ''
})
const checkoutOpts = usePersistedState('gitUpdate.checkoutOpts', {
  fetchTags: false,
  pullAfter: false,
  updateSubmodules: false,
  skipIfSame: true
})
const checkoutResultRows = ref([])

const branchForm = usePersistedState('gitUpdate.branchForm', {
  basePath: '/Users/zhoufz/hundsun/lcpt60/git/Sources/lcpt-front/HUI1.0',
  vcsType: 'git'
})
const updateForm = usePersistedState('gitUpdate.updateForm', {
  basePath: '/Users/zhoufz/hundsun/lcpt60/git/Sources/lcpt-front/HUI1.0',
  vcsType: 'git',
  updateSubmodules: false
})
const checkoutForm = usePersistedState('gitUpdate.checkoutForm', {
  basePath: '/Users/zhoufz/hundsun/lcpt60/git/Sources/',
  vcsType: 'git',
  ref: ''
})
const replaceForm = usePersistedState('gitUpdate.replaceForm', {
  vcsType: 'git',
  filePath: ''
})

const filteredBranchRows = computed(() => {
  const keyword = branchFilter.value.trim().toLowerCase()
  if (!keyword) {
    return branchRows.value
  }
  return branchRows.value.filter((row) => {
    return [row.name, row.branch, row.path, row.type]
      .filter(Boolean)
      .some((text) => String(text).toLowerCase().includes(keyword))
  })
})

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

function openCheckoutDialog(row) {
  if (!row?.path) {
    return
  }
  checkoutDialog.type = row.type || 'git'
  checkoutDialog.kind = row.kind || 'repo'
  checkoutDialog.name = row.name || ''
  checkoutDialog.path = row.path
  checkoutDialog.currentBranch = row.branch || ''
  checkoutDialog.ref = ''
  checkoutDialog.visible = true
}

function formatMs(ms) {
  if (ms == null || ms < 0) return '-'
  if (ms < 1000) return ms + ' ms'
  const sec = Math.round(ms / 1000)
  if (sec < 60) return sec + ' s'
  return Math.floor(sec / 60) + ' min ' + (sec % 60) + ' s'
}

function checkoutOptionParams() {
  return {
    fetchTags: !!checkoutOpts.fetchTags,
    pullAfter: !!checkoutOpts.pullAfter,
    updateSubmodules: !!checkoutOpts.updateSubmodules,
    skipIfSame: !!checkoutOpts.skipIfSame
  }
}

async function confirmCheckoutOne() {
  if (!checkoutDialog.path || !checkoutDialog.ref?.trim()) {
    ElMessage.warning(checkoutDialog.type === 'svn' ? '请填写切换目标' : '请填写目标分支')
    return
  }
  const target = checkoutDialog.ref.trim()
  const ok = await confirmDanger(
    `将「${checkoutDialog.name}」从「${checkoutDialog.currentBranch || '?'}」切换到「${target}」。未提交改动可能导致失败。确认继续？`
  )
  if (!ok) return
  busy.value = true
  resultMsg.value = ''
  try {
    const res = await http.get('/hundsun/git/checkoutOne', {
      params: {
        repoPath: checkoutDialog.path,
        ref: target,
        vcsType: checkoutDialog.type || 'auto',
        ...checkoutOptionParams()
      },
      timeout: 10 * 60 * 1000
    })
    resultOk.value = true
    resultMsg.value = res.message || '切换成功'
    checkoutDialog.visible = false
    const nextBranch = res.data?.branch
    const row = branchRows.value.find((item) => item.path === checkoutDialog.path)
    if (row && nextBranch) {
      row.branch = nextBranch
    } else {
      await listBranches()
    }
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '切换失败'
    if (e.data) {
      checkoutResultRows.value = [e.data]
    }
  } finally {
    busy.value = false
  }
}

async function listBranches() {
  if (!branchForm.basePath?.trim()) {
    ElMessage.warning('请填写基础路径')
    return
  }
  busy.value = true
  resultMsg.value = ''
  branchRows.value = []
  try {
    const res = await http.get('/hundsun/git/branches', {
      params: { basePath: branchForm.basePath.trim(), vcsType: branchForm.vcsType }
    })
    branchRows.value = res.data || []
    resultOk.value = true
    resultMsg.value = res.message || `共 ${branchRows.value.length} 个仓库`
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '失败'
  } finally {
    busy.value = false
  }
}

async function updateAll() {
  if (!updateForm.basePath?.trim()) {
    ElMessage.warning('请填写基础路径')
    return
  }
  const withSub = !!updateForm.updateSubmodules
  const tip = withSub
    ? '将展开并更新 submodule（HUI 下各业务前端会一起 pull），可能较慢。确认继续？'
    : '仅更新父仓（不更新 submodule）。确认继续？'
  const ok = await confirmDanger(tip)
  if (!ok) return
  busy.value = true
  resultMsg.value = ''
  try {
    const res = await http.get('/hundsun/git/updateAll', {
      params: {
        basePath: updateForm.basePath.trim(),
        vcsType: updateForm.vcsType,
        updateSubmodules: withSub
      },
      timeout: 30 * 60 * 1000
    })
    resultOk.value = true
    resultMsg.value = res.message || '完成'
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '失败'
  } finally {
    busy.value = false
  }
}

async function checkoutAll() {
  if (!checkoutForm.basePath?.trim() || !checkoutForm.ref?.trim()) {
    ElMessage.warning('请填写基础路径和分支/目标')
    return
  }
  const ok = await confirmDanger(
    `将把「${checkoutForm.basePath.trim()}」下的 ${checkoutForm.vcsType} 仓库切换到「${checkoutForm.ref.trim()}」（默认不拉全量 tags、不 pull）。确认继续？`
  )
  if (!ok) return
  busy.value = true
  resultMsg.value = ''
  checkoutResultRows.value = []
  try {
    const res = await http.get('/hundsun/git/checkoutAll', {
      params: {
        basePath: checkoutForm.basePath.trim(),
        ref: checkoutForm.ref.trim(),
        vcsType: checkoutForm.vcsType,
        ...checkoutOptionParams()
      },
      timeout: 30 * 60 * 1000
    })
    resultOk.value = true
    resultMsg.value = res.message || '完成'
    checkoutResultRows.value = res.data?.items || []
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '失败'
    checkoutResultRows.value = e.data?.items || []
  } finally {
    busy.value = false
  }
}

async function replaceCode() {
  if (!replaceForm.filePath?.trim()) {
    ElMessage.warning('请选择配置文件')
    return
  }
  const ok = await confirmDanger('将按配置把大包文件复制/追加到小包目标路径，已存在文件会被覆盖。确认继续？')
  if (!ok) return
  busy.value = true
  resultMsg.value = ''
  try {
    const res = await http.get('/hundsun/code/replace', {
      params: { filePath: replaceForm.filePath.trim(), vcsType: replaceForm.vcsType }
    })
    resultOk.value = true
    resultMsg.value = res.message || '完成'
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.branch-filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.branch-table {
  cursor: pointer;
}
.path-text {
  word-break: break-all;
  font-size: 13px;
  color: #606266;
}
</style>
