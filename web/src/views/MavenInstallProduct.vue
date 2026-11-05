<template>
  <el-card shadow="never">
    <template #header>产品包 Install（对齐工程包版本）</template>

    <el-alert
      title="直接改 pom 版本（不跑 versions:set）→ mvn install -nsu（对齐 IDEA 关闭 Always update snapshots）→ 再改回原版本。默认不做 clean。用于联调顶替私服时间戳包。"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-form label-width="140px" style="max-width: 960px" @submit.prevent>
      <el-form-item label="产品模块路径" required>
        <PathFolderInput
          v-model="form.modulePath"
          placeholder="模块目录（或手填 …/pom.xml）；选目录则用其下 pom.xml"
        />
      </el-form-item>
      <el-form-item label="目标版本" required>
        <el-input
          v-model="form.targetVersion"
          clearable
          placeholder="工程包正在用的版本，如 7.5.0-SNAPSHOT"
          style="max-width: 360px"
        />
      </el-form-item>
      <el-form-item label="原版本">
        <el-input
          v-model="form.originalVersion"
          clearable
          placeholder="空则从当前 pom 推断，install 后改回"
          style="max-width: 360px"
        />
      </el-form-item>
      <el-form-item label="Maven settings">
        <PathFolderInput
          v-model="form.settingsPath"
          pick="file"
          placeholder="settings_hs.xml"
        />
      </el-form-item>
      <el-form-item label="本地仓库">
        <PathFolderInput v-model="form.localRepo" placeholder="Maven local repository 根目录" />
      </el-form-item>
      <el-form-item label="选项">
        <el-checkbox v-model="form.clearTargetDir">执行前清空目标版本目录</el-checkbox>
        <el-checkbox v-model="form.restoreOriginalVersion">install 成功后改回原版本</el-checkbox>
        <el-checkbox v-model="form.noSnapshotUpdates">不检查 SNAPSHOT 更新 -nsu（推荐，对齐 IDEA）</el-checkbox>
        <el-checkbox v-model="form.runClean">先 clean（更慢，一般不必）</el-checkbox>
        <el-checkbox v-model="form.offline">离线 -o</el-checkbox>
        <el-checkbox v-model="form.allowOtherGroupId">允许其他 groupId</el-checkbox>
      </el-form-item>
      <el-form-item label="额外 Maven 参数">
        <el-input
          v-model="form.extraMavenArgs"
          clearable
          placeholder="默认 -DskipTests；不要默认加 -o"
          style="max-width: 420px"
        />
      </el-form-item>
      <el-form-item>
        <el-space wrap>
          <el-button :loading="busy === 'preview'" :disabled="!!busy" @click="doPreview">预览</el-button>
          <el-button type="primary" :loading="busy === 'execute'" :disabled="!!busy" @click="doExecute">
            执行
          </el-button>
        </el-space>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="busy === 'execute'"
      title="正在执行：请看 IDEA 控制台 [product-install]。默认 install -nsu，本地已有依赖时应接近 IDEA 速度。"
      type="warning"
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

    <el-alert
      v-if="timings?.total"
      :title="`总耗时 ${timings.total}`"
      :description="timingDesc"
      :type="resultOk ? 'success' : 'warning'"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-descriptions v-if="summary" :column="2" border size="small" style="margin-bottom: 12px">
      <el-descriptions-item label="groupId">{{ summary.groupId }}</el-descriptions-item>
      <el-descriptions-item label="artifactId">{{ summary.artifactId }}</el-descriptions-item>
      <el-descriptions-item label="原版本">{{ summary.originalVersion }}</el-descriptions-item>
      <el-descriptions-item label="目标版本">{{ summary.targetVersion }}</el-descriptions-item>
      <el-descriptions-item label="目标目录" :span="2">{{ summary.targetVersionDir }}</el-descriptions-item>
      <el-descriptions-item v-if="summary.clearedDir" label="已清空目录" :span="2">
        {{ summary.clearedDir }}
      </el-descriptions-item>
      <el-descriptions-item v-if="checks" label="SNAPSHOT jar" :span="2">
        <span :class="checks.snapshotJarExists ? 'ok' : 'bad'">
          {{ checks.snapshotJarExists ? '存在' : '不存在' }}
        </span>
        — {{ checks.snapshotJarPath }}
        <span v-if="checks.snapshotJarSize >= 0">（{{ formatSize(checks.snapshotJarSize) }}）</span>
      </el-descriptions-item>
      <el-descriptions-item v-if="checks" label="时间戳包">
        <span :class="checks.noTimestampJar ? 'ok' : 'bad'">
          {{ checks.noTimestampJar ? '无' : (checks.timestampJars || []).join(', ') }}
        </span>
      </el-descriptions-item>
      <el-descriptions-item v-if="checks" label="pom 当前 version">
        <span :class="checks.pomRestored ? 'ok' : 'warn'">{{ checks.pomVersionNow }}</span>
      </el-descriptions-item>
      <el-descriptions-item v-if="exitCodes" label="退出码" :span="2">
        set-target={{ exitCodes.setTarget }}；
        install={{ exitCodes.install }}；
        restore={{ exitCodes.restore }}
      </el-descriptions-item>
    </el-descriptions>

    <el-table v-if="timingRows.length" :data="timingRows" stripe border size="small" style="margin-bottom: 12px; max-width: 520px">
      <el-table-column prop="step" label="步骤" width="180" />
      <el-table-column prop="human" label="耗时" width="140" />
      <el-table-column prop="ms" label="毫秒" width="120" />
    </el-table>

    <div v-if="commands.length" class="block">
      <div class="block-title">将执行的命令</div>
      <pre class="log">{{ commands.join('\n') }}</pre>
    </div>

    <div v-if="logText" class="block">
      <div class="block-title">日志</div>
      <pre class="log">{{ logText }}</pre>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import PathFolderInput from '@/components/PathFolderInput.vue'

const busy = ref('')
const resultMsg = ref('')
const resultOk = ref(true)
const summary = ref(null)
const checks = ref(null)
const commands = ref([])
const logText = ref('')
const exitCodes = ref(null)
const timings = ref(null)

const timingRows = computed(() => {
  const t = timings.value
  if (!t) return []
  const rows = [
    { step: '清空目标目录', human: t.clearDir, ms: t.clearDirMs },
    { step: '改版本→目标', human: t.setTarget, ms: t.setTargetMs },
    { step: 'Maven install', human: t.install, ms: t.installMs },
    { step: '清理时间戳包', human: t.cleanup, ms: t.cleanupMs },
    { step: '改版本→原版', human: t.restore, ms: t.restoreMs },
    { step: '合计', human: t.total, ms: t.totalMs }
  ]
  return rows.filter((r) => r.human != null || r.ms != null)
})

const timingDesc = computed(() => {
  const t = timings.value
  if (!t) return ''
  const parts = []
  if (t.install) parts.push(`install ${t.install}`)
  if (t.clearDir && t.clearDirMs) parts.push(`清空 ${t.clearDir}`)
  if (t.setTarget) parts.push(`改目标版 ${t.setTarget}`)
  if (t.restore) parts.push(`改回 ${t.restore}`)
  return parts.join(' ｜ ')
})

const form = reactive({
  modulePath:
    '/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server/pub/lcpt-jres/lcpt-core/pom.xml',
  targetVersion: '7.5.0-SNAPSHOT',
  originalVersion: '',
  settingsPath: '/opt/homebrew/Cellar/maven/3.9.9/libexec/conf/settings_hs.xml',
  localRepo: '/opt/homebrew/Cellar/maven/3.9.9/libexec/repo',
  clearTargetDir: true,
  restoreOriginalVersion: true,
  noSnapshotUpdates: true,
  runClean: false,
  offline: false,
  allowOtherGroupId: false,
  extraMavenArgs: '-DskipTests'
})

onMounted(async () => {
  try {
    const res = await http.get('/hundsun/maven/product-install/defaults')
    const d = res.data || {}
    if (d.modulePath) form.modulePath = d.modulePath
    if (d.targetVersion) form.targetVersion = d.targetVersion
    if (d.settingsPath) form.settingsPath = d.settingsPath
    if (d.localRepo) form.localRepo = d.localRepo
    if (d.extraMavenArgs) form.extraMavenArgs = d.extraMavenArgs
    if (typeof d.runClean === 'boolean') form.runClean = d.runClean
    if (typeof d.noSnapshotUpdates === 'boolean') form.noSnapshotUpdates = d.noSnapshotUpdates
  } catch {
    // 使用页面默认值
  }
})

function buildBody() {
  return {
    modulePath: form.modulePath.trim(),
    targetVersion: form.targetVersion.trim(),
    originalVersion: (form.originalVersion || '').trim(),
    settingsPath: form.settingsPath.trim(),
    localRepo: form.localRepo.trim(),
    clearTargetDir: !!form.clearTargetDir,
    restoreOriginalVersion: !!form.restoreOriginalVersion,
    noSnapshotUpdates: !!form.noSnapshotUpdates,
    runClean: !!form.runClean,
    offline: !!form.offline,
    allowOtherGroupId: !!form.allowOtherGroupId,
    extraMavenArgs: (form.extraMavenArgs || '-DskipTests').trim()
  }
}

function applyResult(data) {
  summary.value = data
    ? {
        groupId: data.groupId,
        artifactId: data.artifactId,
        originalVersion: data.originalVersion,
        targetVersion: data.targetVersion,
        targetVersionDir: data.targetVersionDir,
        clearedDir: data.clearedDir
      }
    : null
  checks.value = data?.checks || null
  timings.value = data?.timings || null
  commands.value = data?.commands || []
  const lines = data?.log || []
  logText.value = Array.isArray(lines) ? lines.join('\n') : String(lines || '')
  exitCodes.value = {
    setTarget: data?.exitCodeSetTarget ?? '-',
    install: data?.exitCodeInstall ?? '-',
    restore: data?.exitCodeRestore ?? '-'
  }
}

function formatSize(n) {
  if (n == null || n < 0) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

async function doPreview() {
  if (!form.modulePath?.trim() || !form.targetVersion?.trim()) {
    ElMessage.warning('请填写产品模块路径与目标版本')
    return
  }
  busy.value = 'preview'
  resultMsg.value = ''
  try {
    const res = await http.post('/hundsun/maven/product-install/preview', buildBody())
    applyResult(res.data)
    resultOk.value = true
    resultMsg.value = res.message || '预览完成'
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '预览失败'
    if (e.data) applyResult(e.data)
  } finally {
    busy.value = ''
  }
}

async function doExecute() {
  if (!form.modulePath?.trim() || !form.targetVersion?.trim()) {
    ElMessage.warning('请填写产品模块路径与目标版本')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将清空（若勾选）本地 ${form.targetVersion} 目录，versions:set → install → 改回原版本。确认执行？`,
      '确认执行产品包 Install',
      { type: 'warning', confirmButtonText: '开始执行', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = 'execute'
  resultMsg.value = ''
  try {
    // install 可能较久：单独拉长超时（后端默认 30 分钟）
    const res = await http.post('/hundsun/maven/product-install/execute', buildBody(), {
      timeout: 35 * 60 * 1000
    })
    applyResult(res.data)
    resultOk.value = true
    resultMsg.value = res.message || '执行完成'
    ElMessage.success('IDEA 请关闭 Always update snapshots 并 Reload Maven')
  } catch (e) {
    resultOk.value = false
    resultMsg.value = e.message || '执行失败'
    if (e.data) applyResult(e.data)
  } finally {
    busy.value = ''
  }
}
</script>

<style scoped>
.block {
  margin-top: 12px;
}
.block-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.log {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  max-height: 420px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
}
.ok {
  color: #67c23a;
  font-weight: 600;
}
.bad {
  color: #f56c6c;
  font-weight: 600;
}
.warn {
  color: #e6a23c;
  font-weight: 600;
}
</style>
