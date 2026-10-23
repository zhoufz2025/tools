<template>
  <el-card shadow="never">
    <template #header>Git / SVN 批量更新</template>
    <el-tabs v-model="tab">
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
        width="560px"
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
                  ? '业务前端版本，如 IFMS6.0V202607.04.000 或 tag/IFMS6.0V202607.04.000'
                  : '例如 IFMS6.0V202607.00.005 或 hotfix/IFMS6.0V202607.00.005')"
              clearable
              @keyup.enter="confirmCheckoutOne"
            />
          </el-form-item>
        </el-form>
        <el-alert
          v-if="checkoutDialog.kind === 'submodule'"
          title="仅切换该业务前端 submodule（如 console-dxfund-vue），不会改 HUI1.0 父仓分支。"
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
        <el-form label-width="100px" style="max-width: 760px" @submit.prevent>
          <el-form-item label="基础路径">
            <PathFolderInput v-model="updateForm.basePath" placeholder="例如 /Users/zhoufz/hundsun/lcpt60/git/Sources/" />
          </el-form-item>
          <el-form-item label="仓库类型">
            <el-radio-group v-model="updateForm.vcsType">
              <el-radio-button value="git">仅 Git</el-radio-button>
              <el-radio-button value="svn">仅 SVN</el-radio-button>
              <el-radio-button value="all">Git + SVN</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy" @click="updateAll">批量更新</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          title="递归扫描后并发更新：Git 执行 git pull，SVN 执行 svn update。进度看后端控制台。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-tab-pane>

      <el-tab-pane label="切换分支" name="checkout">
        <el-form label-width="100px" style="max-width: 760px" @submit.prevent>
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
          <el-form-item>
            <el-button type="warning" :loading="busy" @click="checkoutAll">切换</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          title="Git：fetch 后 checkout（可匹配 hotfix/版本、release/版本），命名分支上会再 pull。SVN：svn switch，目标须是 URL 或 ^/ 相对路径。"
          type="warning"
          show-icon
          :closable="false"
        />
      </el-tab-pane>

      <el-tab-pane label="代码替换" name="replace">
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

const tab = ref('branches')
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

const branchForm = reactive({
  basePath: '/Users/zhoufz/hundsun/lcpt60/git/Sources/',
  vcsType: 'git'
})
const updateForm = reactive({
  basePath: '/Users/zhoufz/hundsun/lcpt60/git/Sources/',
  vcsType: 'git'
})
const checkoutForm = reactive({
  basePath: '/Users/zhoufz/hundsun/lcpt60/git/Sources/',
  vcsType: 'git',
  ref: ''
})
const replaceForm = reactive({
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
        vcsType: checkoutDialog.type || 'auto'
      }
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
  busy.value = true
  resultMsg.value = ''
  try {
    const res = await http.get('/hundsun/git/updateAll', {
      params: { basePath: updateForm.basePath.trim(), vcsType: updateForm.vcsType }
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
    `将把「${checkoutForm.basePath.trim()}」下的 ${checkoutForm.vcsType} 仓库切换到「${checkoutForm.ref.trim()}」。未提交改动可能导致 checkout/switch 失败。确认继续？`
  )
  if (!ok) return
  busy.value = true
  resultMsg.value = ''
  try {
    const res = await http.get('/hundsun/git/checkoutAll', {
      params: {
        basePath: checkoutForm.basePath.trim(),
        ref: checkoutForm.ref.trim(),
        vcsType: checkoutForm.vcsType
      }
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
