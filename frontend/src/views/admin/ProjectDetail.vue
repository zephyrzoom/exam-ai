<template>
  <div class="page" v-loading="loading">
    <div class="card" v-if="project">
      <div class="toolbar">
        <h3 style="margin:0">{{ project.name }}</h3>
        <el-tag :type="{ DRAFT: 'info', PUBLISHED: 'success', FINISHED: 'warning' }[project.status]">
          {{ { DRAFT: '草稿', PUBLISHED: '已发布', FINISHED: '已结束' }[project.status] }}
        </el-tag>
        <span class="spacer"></span>
        <el-button @click="$router.push(`/admin/projects/${projectId}/scores`)">成绩列表</el-button>
        <el-button @click="$router.push(`/admin/projects/${projectId}/analysis`)">成绩分析</el-button>
        <el-button v-if="!isPm && project.status === 'DRAFT'" type="primary" @click="publish">发布项目</el-button>
        <el-button v-if="!isPm && project.status === 'PUBLISHED'" type="warning" @click="finish">结束项目</el-button>
      </div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="试卷">{{ project.paperName }}</el-descriptions-item>
        <el-descriptions-item label="时长">{{ project.durationMin }} 分钟</el-descriptions-item>
        <el-descriptions-item label="及格线">{{ project.passScore }} 分</el-descriptions-item>
        <el-descriptions-item label="开考时间">{{ project.startTime }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ project.endTime }}</el-descriptions-item>
        <el-descriptions-item label="批次">{{ project.batch || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ project.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ project.creatorName }}</el-descriptions-item>
        <el-descriptions-item label="考生数">{{ project.candidateCount }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <!-- 考生名单 -->
      <el-tab-pane label="考生名单" name="candidates">
        <div class="toolbar" v-if="!isPm">
          <el-button type="primary" @click="doHrSync" :loading="syncing">从人事系统同步</el-button>
          <el-button @click="importVisible = true">Excel 导入</el-button>
          <el-button @click="candidateVisible = true">手动添加</el-button>
          <span class="spacer"></span>
          <el-button @click="download(`/projects/${projectId}/candidates/export`, '考生名单.xlsx')">导出名单</el-button>
        </div>
        <el-table :data="candidates" border stripe>
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column label="身份证号" width="190">
            <template #default="{ row }">{{ mask(row.idCard) }}</template>
          </el-table-column>
          <el-table-column prop="phone" label="手机" width="130" />
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column prop="vendor" label="供应商" width="120" />
          <el-table-column label="来源" width="100">
            <template #default="{ row }">{{ { HR: '人事系统', EXCEL: 'Excel', MANUAL: '手动' }[row.source] }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="{ NOT_ATTENDED: 'info', ATTENDED: 'warning', SUBMITTED: 'success' }[row.status]">
                {{ { NOT_ATTENDED: '未参加', ATTENDED: '考试中', SUBMITTED: '已交卷' }[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="!isPm" label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeCandidate(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 考试通知 -->
      <el-tab-pane label="考试通知" name="notify">
        <div class="toolbar" v-if="!isPm">
          <el-button type="primary" @click="doNotify" :loading="notifying">发送邮件通知</el-button>
          <span class="muted">收件人：全体考生；抄送：属地招聘管理员与用人部门招聘负责人</span>
        </div>
        <el-table :data="notices" border stripe>
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="subject" label="主题" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="{ SENT: 'success', SIMULATED: 'warning', FAILED: 'danger' }[row.status]" size="small">
                {{ { SENT: '已发送', SIMULATED: '模拟发送', FAILED: '失败' }[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="90" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewNotice(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 入围与回传 -->
      <el-tab-pane label="入围与回传" name="shortlist">
        <el-form :inline="true" v-if="!isPm && (!plan || plan.status !== 'CONFIRMED')">
          <el-form-item label="方案名称"><el-input v-model="planForm.name" style="width:160px" /></el-form-item>
          <el-form-item label="入围规则">
            <el-select v-model="planForm.ruleType" style="width:150px">
              <el-option label="按名额 TOP N" value="TOP_N" />
              <el-option label="按比例" value="RATIO" />
              <el-option label="按分数线" value="SCORE_LINE" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="planForm.ruleType === 'TOP_N'" label="名额">
            <el-input-number v-model="planForm.topN" :min="1" />
          </el-form-item>
          <el-form-item v-if="planForm.ruleType === 'RATIO'" label="比例%">
            <el-input-number v-model="planForm.ratio" :min="1" :max="100" />
          </el-form-item>
          <el-form-item v-if="planForm.ruleType === 'SCORE_LINE'" label="分数线">
            <el-input-number v-model="planForm.minScore" :min="0" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="savePlan">保存方案</el-button>
            <el-button @click="loadPreview" :disabled="!plan">预览入围</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="plan && plan.status === 'CONFIRMED'" type="success" :closable="false" style="margin-bottom:12px">
          方案「{{ plan.name }}」已由 {{ plan.confirmedByName }} 于 {{ plan.confirmedAt }} 确认。
          <el-button v-if="!isPm" link type="warning" @click="revoke">撤销确认</el-button>
        </el-alert>
        <div class="muted" style="margin-bottom:8px">规则说明：按总分排名，TOP_N / RATIO 须达到项目及格线；SCORE_LINE 使用方案分数线。</div>
        <el-table v-if="preview" :data="preview.rows" border stripe max-height="420">
          <el-table-column prop="rank" label="排名" width="70" />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column label="身份证号" width="190">
            <template #default="{ row }">{{ mask(row.idCard) }}</template>
          </el-table-column>
          <el-table-column prop="vendor" label="供应商" width="120" />
          <el-table-column prop="totalScore" label="总分" width="90" />
          <el-table-column label="及格" width="80">
            <template #default="{ row }">{{ row.passed === 1 ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="入围" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.shortlisted" type="success" size="small">入围</el-tag>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="toolbar" style="margin-top:12px" v-if="preview">
          <span>已评分 {{ preview.gradedCount }} 人，拟入围 {{ preview.shortlistCount }} 人，分数线 {{ preview.line }}</span>
          <span class="spacer"></span>
          <el-button v-if="!isPm && plan && plan.status !== 'CONFIRMED'" type="primary" @click="confirmShortlist">确认入围名单</el-button>
          <el-button v-if="!isPm && plan && plan.status === 'CONFIRMED'" type="success" @click="doPushHr" :loading="pushing">回传人事系统</el-button>
        </div>
        <h4>人事系统同步日志</h4>
        <el-table :data="syncLogs" border size="small">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column label="方向" width="90">
            <template #default="{ row }">{{ row.direction === 'PULL' ? '拉取名单' : '回传结果' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="报文" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewPayload(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 手动添加考生 -->
    <el-dialog v-model="candidateVisible" title="手动添加考生" width="520px">
      <el-form label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="candForm.name" /></el-form-item>
        <el-form-item label="身份证号" required><el-input v-model="candForm.idCard" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="candForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="candForm.email" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="candForm.vendor" /></el-form-item>
        <el-form-item label="用人部门"><el-input v-model="candForm.deptName" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="candidateVisible = false">取消</el-button>
        <el-button type="primary" @click="addCandidate">添加</el-button>
      </template>
    </el-dialog>

    <!-- Excel 导入 -->
    <el-dialog v-model="importVisible" title="Excel 导入考生" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">列顺序：姓名 / 身份证号 / 手机 / 邮箱 / 供应商 / 用人部门</el-alert>
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="f => importFile = f.raw">
        <el-icon style="font-size:40px;color:#c0c4cc"><UploadFilled /></el-icon>
        <div>拖拽或点击选择 Excel 文件</div>
      </el-upload>
      <template #footer>
        <el-button @click="download('/projects/candidates/template', '考生导入模板.xlsx')">下载模板</el-button>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 通知/报文查看 -->
    <el-dialog v-model="textVisible" :title="textTitle" width="640px">
      <pre class="notice-text">{{ textContent }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProject, publishProject, finishProject, listCandidates, addCandidate as apiAddCandidate,
  deleteCandidate, hrSync, sendNotify, notifyLogs, getShortlistPlan, saveShortlistPlan,
  shortlistPreview, shortlistConfirm, shortlistRevoke, pushHr, hrSyncLogs, download, upload
} from '../../api'

const route = useRoute()
const projectId = route.params.id
const isPm = localStorage.getItem('role') === 'PM_ADMIN'
const loading = ref(false)
const project = ref(null)
const tab = ref('candidates')

const mask = id => id && id.length >= 10 ? id.slice(0, 6) + '********' + id.slice(-4) : id

async function loadProject() { project.value = await getProject(projectId) }

// ---------- 考生 ----------
const candidates = ref([])
const syncing = ref(false)
async function loadCandidates() { candidates.value = await listCandidates(projectId) }

async function doHrSync() {
  syncing.value = true
  try {
    const res = await hrSync(projectId)
    ElMessage.success(`同步完成：新增 ${res.added} 人，跳过 ${res.skipped} 人${res.mock ? '（演示数据）' : ''}`)
    loadCandidates(); loadProject()
  } finally { syncing.value = false }
}

const candidateVisible = ref(false)
const candForm = reactive({ name: '', idCard: '', phone: '', email: '', vendor: '', deptName: '' })
async function addCandidate() {
  await apiAddCandidate(projectId, candForm)
  ElMessage.success('已添加')
  candidateVisible.value = false
  Object.keys(candForm).forEach(k => candForm[k] = '')
  loadCandidates(); loadProject()
}

async function removeCandidate(row) {
  await ElMessageBox.confirm(`确认移除考生 ${row.name}？`, '提示', { type: 'warning' })
  await deleteCandidate(projectId, row.id)
  ElMessage.success('已移除')
  loadCandidates(); loadProject()
}

const importVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)
async function doImport() {
  if (!importFile.value) return ElMessage.warning('请选择文件')
  importing.value = true
  try {
    const res = await upload(`/projects/${projectId}/candidates/import`, importFile.value)
    ElMessage.success(`导入完成：成功 ${res.success}，失败 ${res.fail}`)
    if (res.errors?.length) ElMessageBox.alert(res.errors.join('\n'), '失败明细')
    importVisible.value = false
    importFile.value = null
    loadCandidates(); loadProject()
  } finally { importing.value = false }
}

// ---------- 通知 ----------
const notices = ref([])
const notifying = ref(false)
async function loadNotices() { notices.value = await notifyLogs(projectId) }
async function doNotify() {
  notifying.value = true
  try {
    const res = await sendNotify(projectId)
    ElMessage.success(res.status === 'SENT' ? '邮件已发送' : '通知已模拟发送并存档（未配置真实邮箱）')
    loadNotices()
  } finally { notifying.value = false }
}

// ---------- 入围 ----------
const plan = ref(null)
const planForm = reactive({ name: '', ruleType: 'TOP_N', topN: 3, ratio: 30, minScore: 60 })
const preview = ref(null)
const syncLogs = ref([])
const pushing = ref(false)

async function loadPlan() {
  plan.value = await getShortlistPlan(projectId)
  if (plan.value) Object.assign(planForm, plan.value)
  syncLogs.value = await hrSyncLogs(projectId)
}
async function savePlan() {
  plan.value = await saveShortlistPlan(projectId, planForm)
  ElMessage.success('方案已保存')
  loadPreview()
}
async function loadPreview() { preview.value = await shortlistPreview(projectId) }
async function confirmShortlist() {
  await ElMessageBox.confirm(`确认入围 ${preview.value.shortlistCount} 人？确认后可回传人事系统。`, '入围确认')
  await shortlistConfirm(projectId)
  ElMessage.success('入围名单已确认')
  loadPlan(); loadPreview()
}
async function revoke() {
  await ElMessageBox.confirm('撤销确认可重新调整方案，确认撤销？', '提示', { type: 'warning' })
  await shortlistRevoke(projectId)
  ElMessage.success('已撤销')
  loadPlan(); loadPreview()
}
async function doPushHr() {
  pushing.value = true
  try {
    const res = await pushHr(projectId)
    ElMessage.success(`已回传 ${res.count} 条测评结果${res.mock ? '（模拟回传，报文已存档）' : ''}`)
    loadPlan()
  } finally { pushing.value = false }
}

// ---------- 项目操作 ----------
async function publish() {
  await ElMessageBox.confirm('发布后考生可登录参加考试，确认发布？', '发布确认')
  await publishProject(projectId)
  ElMessage.success('项目已发布')
  loadProject()
}
async function finish() {
  await ElMessageBox.confirm('结束后未交卷考生将记为缺考，确认结束？', '结束确认', { type: 'warning' })
  await finishProject(projectId)
  ElMessage.success('项目已结束')
  loadProject(); loadCandidates()
}

// ---------- 文本查看 ----------
const textVisible = ref(false)
const textTitle = ref('')
const textContent = ref('')
function viewNotice(row) {
  textTitle.value = row.subject
  textContent.value = `收件人：${row.toList}\n抄送：${row.ccList}\n\n${row.content}`
  textVisible.value = true
}
function viewPayload(row) {
  textTitle.value = `${row.direction === 'PULL' ? '拉取名单' : '回传结果'}报文`
  try { textContent.value = JSON.stringify(JSON.parse(row.payload), null, 2) }
  catch { textContent.value = row.payload || '(空)' }
  textVisible.value = true
}

onMounted(async () => {
  loading.value = true
  try {
    await loadProject()
    await Promise.all([loadCandidates(), loadNotices(), loadPlan()])
  } finally { loading.value = false }
})
</script>

<style scoped>
.notice-text { white-space: pre-wrap; font-family: inherit; line-height: 1.7; max-height: 420px; overflow: auto;
  background: #f8f9fb; padding: 14px; border-radius: 6px; }
</style>