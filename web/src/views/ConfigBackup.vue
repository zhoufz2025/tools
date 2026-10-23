<template>
  <el-card shadow="never">
    <template #header>配置备份 / 还原</template>

    <el-tabs v-model="tab">
      <el-tab-pane label="备份" name="backup">
        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="源目录">
            <PathFolderInput v-model="backup.sourceRoot" placeholder="要备份的目录" />
          </el-form-item>
          <el-form-item label="备份目录">
            <PathFolderInput v-model="backup.targetRoot" placeholder="备份存放目录" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy" @click="doBackup">开始备份</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="还原" name="restore">
        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="项目目录">
            <PathFolderInput v-model="restore.sourceRoot" placeholder="要被替换的目录" />
          </el-form-item>
          <el-form-item label="备份目录">
            <PathFolderInput v-model="restore.targetRoot" placeholder="备份文件所在目录" />
          </el-form-item>
          <el-form-item label="还原 pom">
            <el-switch v-model="restore.restorePom" active-text="还原 pom.xml" inactive-text="跳过 pom.xml" />
            <div class="hint">默认跳过，避免覆盖依赖并触发 IDEA Maven 导入；需要时再打开。</div>
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="busy" @click="doRestore">开始还原</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="从备份恢复" name="recover">
        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="项目目录">
            <PathFolderInput v-model="recover.projectRoot" placeholder="要被恢复的目录" />
          </el-form-item>
          <el-form-item label="备份目录">
            <PathFolderInput v-model="recover.backupRoot" placeholder="备份文件所在目录" />
          </el-form-item>
          <el-form-item label="还原 pom">
            <el-switch v-model="recover.restorePom" active-text="还原 pom.xml" inactive-text="跳过 pom.xml" />
            <div class="hint">默认跳过，避免覆盖依赖并触发 IDEA Maven 导入；需要时再打开。</div>
          </el-form-item>
          <el-form-item>
            <el-button type="danger" :loading="busy" @click="doRecover">从备份恢复</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-alert v-if="resultMsg" :title="resultMsg" :type="resultOk ? 'success' : 'error'" show-icon :closable="false" />
    <pre v-if="resultData" class="result">{{ resultData }}</pre>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import PathFolderInput from '@/components/PathFolderInput.vue'

const tab = ref('backup')
const busy = ref(false)
const resultMsg = ref('')
const resultOk = ref(true)
const resultData = ref('')

const backup = reactive({ sourceRoot: '', targetRoot: '' })
const restore = reactive({ sourceRoot: '', targetRoot: '', restorePom: false })
const recover = reactive({ projectRoot: '', backupRoot: '', restorePom: false })

function showResult(res) {
  resultOk.value = true
  resultMsg.value = res.message || '完成'
  resultData.value = res.data ? JSON.stringify(res.data, null, 2) : ''
}

function showError(e) {
  resultOk.value = false
  resultMsg.value = e.message || '失败'
  resultData.value = e.data ? JSON.stringify(e.data, null, 2) : ''
}

async function doBackup() {
  if (!backup.sourceRoot || !backup.targetRoot) {
    ElMessage.warning('请填写源目录与备份目录')
    return
  }
  busy.value = true
  try {
    const res = await http.get('/hundsun/config/backup', {
      params: { sourceRoot: backup.sourceRoot, targetRoot: backup.targetRoot }
    })
    showResult(res)
  } catch (e) {
    showError(e)
  } finally {
    busy.value = false
  }
}

async function doRestore() {
  if (!restore.sourceRoot || !restore.targetRoot) {
    ElMessage.warning('请填写项目目录与备份目录')
    return
  }
  busy.value = true
  try {
    const res = await http.get('/hundsun/config/restore', {
      params: {
        sourceRoot: restore.sourceRoot,
        targetRoot: restore.targetRoot,
        restorePom: restore.restorePom
      }
    })
    showResult(res)
  } catch (e) {
    showError(e)
  } finally {
    busy.value = false
  }
}

async function doRecover() {
  if (!recover.projectRoot || !recover.backupRoot) {
    ElMessage.warning('请填写项目目录与备份目录')
    return
  }
  busy.value = true
  try {
    const res = await http.get('/hundsun/config/recover', {
      params: {
        projectRoot: recover.projectRoot,
        backupRoot: recover.backupRoot,
        restorePom: recover.restorePom
      }
    })
    showResult(res)
  } catch (e) {
    showError(e)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.result {
  margin-top: 16px;
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  overflow: auto;
  max-height: 420px;
  font-size: 12px;
}
.hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
