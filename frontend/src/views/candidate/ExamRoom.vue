<template>
  <div class="exam-room" @copy.prevent="onCopy" @cut.prevent="onCopy" @paste.prevent="onPaste"
       @contextmenu.prevent="onContextMenu">
    <!-- 顶部栏 -->
    <div class="topbar">
      <b>{{ exam?.projectName }}</b>
      <span class="spacer"></span>
      <el-tag v-if="saveState === 'saving'" type="warning" size="small">保存中…</el-tag>
      <el-tag v-else-if="saveState === 'saved'" type="success" size="small">已自动保存 {{ lastSaveTime }}</el-tag>
      <el-tag v-else-if="saveState === 'error'" type="danger" size="small">保存失败，将重试</el-tag>
      <el-tag :type="remain < 300 ? 'danger' : 'primary'" effect="dark" size="large" class="timer">
        ⏱ {{ formatTime(remain) }}
      </el-tag>
      <el-button size="small" @click="toggleFullscreen">{{ isFullscreen ? '退出全屏' : '全屏作答' }}</el-button>
      <el-button type="danger" size="small" @click="confirmSubmit">交卷</el-button>
    </div>

    <el-alert v-if="switchCount > 0" type="warning" :closable="false" class="cheat-warn">
      防作弊提示：已检测到 {{ switchCount }} 次切屏/失焦行为，均已被记录。请专注作答，禁止复制粘贴与查阅资料。
    </el-alert>

    <div class="body" v-if="exam">
      <!-- 左侧答题卡 -->
      <div class="nav-panel">
        <div class="nav-title">答题卡</div>
        <div class="nav-grid">
          <div v-for="(q, i) in exam.questions" :key="q.questionId"
               class="nav-item" :class="{ active: i === current, answered: !!answers[q.questionId] }"
               @click="goto(i)">{{ i + 1 }}</div>
        </div>
        <div class="muted" style="margin-top:10px">
          已答 {{ answeredCount }} / {{ exam.questions.length }} 题
        </div>
        <el-progress :percentage="Math.round(answeredCount / exam.questions.length * 100)" style="margin-top:6px" />
      </div>

      <!-- 题目区 -->
      <div class="question-panel" v-if="currentQuestion">
        <div class="q-header">
          <el-tag>{{ typeNames[currentQuestion.type] }}</el-tag>
          <span class="muted">第 {{ current + 1 }} / {{ exam.questions.length }} 题（{{ currentQuestion.score }} 分）</span>
        </div>
        <div class="q-content q-text">{{ currentQuestion.content }}</div>

        <template v-if="currentQuestion.type === 'SINGLE' || currentQuestion.type === 'JUDGE'">
          <el-radio-group v-model="answers[currentQuestion.questionId]" class="opt-group" @change="onAnswerChange">
            <el-radio v-for="(opt, i) in currentQuestion.options" :key="i" :value="letters[i]" class="opt-item">
              {{ letters[i] }}. {{ opt }}
            </el-radio>
          </el-radio-group>
        </template>

        <template v-else-if="currentQuestion.type === 'MULTI'">
          <el-checkbox-group :model-value="(answers[currentQuestion.questionId] || '').split('')"
                             @update:model-value="v => setMulti(v)" class="opt-group">
            <el-checkbox v-for="(opt, i) in currentQuestion.options" :key="i" :value="letters[i]" class="opt-item">
              {{ letters[i] }}. {{ opt }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="muted">多选题：全对满分，漏选半分，错选不得分</div>
        </template>

        <template v-else>
          <el-input v-model="answers[currentQuestion.questionId]" type="textarea" :rows="10"
                    placeholder="请在此作答（禁止粘贴外部内容）" @input="onAnswerChange" />
        </template>

        <div class="q-footer">
          <el-button :disabled="current === 0" @click="goto(current - 1)">上一题</el-button>
          <el-button v-if="current < exam.questions.length - 1" type="primary" @click="goto(current + 1)">下一题</el-button>
          <el-button v-else type="danger" @click="confirmSubmit">交卷</el-button>
        </div>
      </div>
    </div>

    <!-- 交卷成功 -->
    <div v-if="submitted" class="done-box">
      <el-result icon="success" title="交卷成功"
                 :sub-title="`您的答卷已提交${autoSubmitted ? '（超时自动交卷）' : ''}，客观题由系统自动判分，主观题由 AI 初评后人工复核。成绩由招聘管理员通知。`">
        <template #extra>
          <el-button type="primary" @click="$router.push('/candidate')">返回考试列表</el-button>
        </template>
      </el-result>
    </div>

    <div v-if="enterError" class="done-box">
      <el-result icon="warning" title="无法进入考试" :sub-title="enterError">
        <template #extra>
          <el-button type="primary" @click="$router.push('/candidate')">返回考试列表</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { enterExam, saveAnswers, submitExam, reportCheat } from '../../api'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId
const letters = 'ABCDEFGH'.split('')
const typeNames = { SINGLE: '单选题', MULTI: '多选题', JUDGE: '判断题', ESSAY: '主观简答题' }

const exam = ref(null)
const answers = reactive({})
const current = ref(0)
const remain = ref(0)
const saveState = ref('')
const lastSaveTime = ref('')
const submitted = ref(false)
const autoSubmitted = ref(false)
const enterError = ref('')
const switchCount = ref(0)
const isFullscreen = ref(false)

let timer = null
let saver = null

const currentQuestion = computed(() => exam.value?.questions[current.value])
const answeredCount = computed(() =>
  exam.value ? exam.value.questions.filter(q => (answers[q.questionId] || '').trim()).length : 0)

function formatTime(s) {
  if (s < 0) s = 0
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function collectAnswers() {
  return exam.value.questions.map(q => ({ questionId: q.questionId, answer: answers[q.questionId] || '' }))
}

async function save() {
  if (!exam.value || submitted.value) return
  saveState.value = 'saving'
  try {
    const res = await saveAnswers(exam.value.recordId, collectAnswers())
    if (res.autoSubmitted) {
      autoSubmitted.value = true
      finishExam()
      return
    }
    if (res.remainSeconds != null) remain.value = res.remainSeconds
    saveState.value = 'saved'
    lastSaveTime.value = new Date().toLocaleTimeString()
  } catch {
    saveState.value = 'error'
  }
}

function onAnswerChange() {
  clearTimeout(window.__saveDebounce)
  window.__saveDebounce = setTimeout(save, 1200)
}

function setMulti(v) {
  answers[currentQuestion.value.questionId] = [...v].sort().join('')
  onAnswerChange()
}

async function goto(i) {
  await save()
  current.value = i
}

async function confirmSubmit() {
  const unanswered = exam.value.questions.length - answeredCount.value
  await ElMessageBox.confirm(
    unanswered > 0 ? `还有 ${unanswered} 题未作答，确认交卷？交卷后不可修改。` : '确认交卷？交卷后不可修改。',
    '交卷确认', { type: 'warning', confirmButtonText: '确认交卷', cancelButtonText: '继续作答' })
  await save()
  await submitExam(exam.value.recordId)
  finishExam()
}

function finishExam() {
  submitted.value = true
  clearInterval(timer)
  clearInterval(saver)
  removeAntiCheat()
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {})
}

// ---------- 防作弊 ----------
function cheat(type, detail) {
  if (!exam.value || submitted.value) return
  reportCheat(exam.value.recordId, type, detail).catch(() => {})
  if (type === 'BLUR' || type === 'VISIBILITY') {
    switchCount.value++
    ElMessage.warning(`防作弊警告：切屏/失焦行为已记录（第 ${switchCount.value} 次）`)
  }
}
const onVisibility = () => { if (document.hidden) cheat('VISIBILITY', '页面隐藏') }
const onBlur = () => cheat('BLUR', '窗口失焦')
const onCopy = () => { cheat('COPY', '尝试复制'); ElMessage.warning('考试期间禁止复制') }
const onPaste = () => { cheat('PASTE', '尝试粘贴'); ElMessage.warning('考试期间禁止粘贴') }
const onContextMenu = () => cheat('CONTEXT_MENU', '右键菜单')
const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
  if (!document.fullscreenElement && exam.value && !submitted.value) cheat('FULLSCREEN_EXIT', '退出全屏')
}

function installAntiCheat() {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('blur', onBlur)
  document.addEventListener('fullscreenchange', onFullscreenChange)
}
function removeAntiCheat() {
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('blur', onBlur)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
}

async function toggleFullscreen() {
  if (document.fullscreenElement) await document.exitFullscreen()
  else await document.documentElement.requestFullscreen().catch(() => ElMessage.warning('浏览器拒绝全屏请求'))
}

onMounted(async () => {
  try {
    const data = await enterExam(projectId)
    exam.value = data
    remain.value = data.remainSeconds
    switchCount.value = data.switchCount || 0
    data.questions.forEach(q => { answers[q.questionId] = data.savedAnswers[q.questionId] || '' })
    installAntiCheat()
    timer = setInterval(() => {
      remain.value--
      if (remain.value <= 0) {
        autoSubmitted.value = true
        saveAnswers(exam.value.recordId, collectAnswers())
          .then(() => submitExam(exam.value.recordId))
          .catch(() => {})
          .finally(finishExam)
      }
    }, 1000)
    saver = setInterval(save, 15000)
  } catch (e) {
    enterError.value = e.message || '进入考试失败'
  }
})

onBeforeUnmount(() => {
  clearInterval(timer)
  clearInterval(saver)
  removeAntiCheat()
})
</script>

<style scoped>
.exam-room { min-height: 100vh; background: #f5f7fa; user-select: none; }
.exam-room textarea { user-select: text; }
.topbar { position: sticky; top: 0; z-index: 10; background: #fff; padding: 10px 20px;
  display: flex; align-items: center; gap: 12px; border-bottom: 1px solid #ebeef5; flex-wrap: wrap; }
.topbar b { white-space: nowrap; }
.spacer { flex: 1; }
.timer { font-size: 16px; font-variant-numeric: tabular-nums; }
.cheat-warn { margin: 10px 20px 0; width: auto; }
.body { display: flex; gap: 16px; padding: 16px 20px; max-width: 1200px; margin: 0 auto; }
.nav-panel { width: 220px; flex-shrink: 0; background: #fff; border-radius: 8px; padding: 14px;
  align-self: flex-start; position: sticky; top: 70px; }
.nav-title { font-weight: 600; margin-bottom: 10px; }
.nav-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.nav-item { width: 34px; height: 34px; border-radius: 6px; border: 1px solid #dcdfe6;
  display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 13px; }
.nav-item.answered { background: #e8f5e9; border-color: #67c23a; color: #67c23a; }
.nav-item.active { background: #409eff; border-color: #409eff; color: #fff; }
.question-panel { flex: 1; background: #fff; border-radius: 8px; padding: 22px; }
.q-header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.q-text { font-size: 16px; line-height: 1.8; margin-bottom: 18px; }
.opt-group { display: flex; flex-direction: column; gap: 12px; }
.opt-item { font-size: 15px; height: auto; white-space: normal; line-height: 1.6; }
.q-footer { margin-top: 26px; display: flex; gap: 10px; }
.done-box { max-width: 640px; margin: 60px auto; background: #fff; border-radius: 10px; padding: 20px; }
</style>