<template>
  <div class="page" v-loading="loading">
    <div class="card" v-if="paper">
      <div class="toolbar">
        <h3 style="margin:0">{{ paper.name }}</h3>
        <el-tag :type="paper.status === 'PUBLISHED' ? 'success' : 'info'">
          {{ paper.status === 'PUBLISHED' ? '已发布' : '草稿' }}
        </el-tag>
        <span class="muted">共 {{ paper.questionCount }} 题 / 总分 {{ paper.totalScore }} 分</span>
        <span class="spacer"></span>
        <el-button v-if="!isPm" @click="infoVisible = true">编辑信息</el-button>
        <el-button @click="logsVisible = true">调整日志</el-button>
        <el-button @click="exportPaper('word')">导出 Word</el-button>
        <el-button @click="exportPaper('pdf')">导出 PDF</el-button>
        <el-button v-if="!isPm && !paper.pledgeUserId" type="warning" @click="openPledge">签署保密承诺书</el-button>
        <el-tag v-if="paper.pledgeUserId" type="success">{{ paper.pledgeUserName }} 已签署承诺</el-tag>
        <el-button v-if="!isPm && paper.status !== 'PUBLISHED'" type="primary" @click="publish">发布试卷</el-button>
      </div>
      <div class="muted" v-if="paper.jobRequirement">岗位要求：{{ paper.jobRequirement }}　测评目标：{{ paper.assessTarget }}</div>
      <el-alert v-if="paper.status === 'PUBLISHED'" type="warning" :closable="false" style="margin-top:8px">
        已发布试卷仍可编辑调整，所有调整将记录日志；发布前须签署保密承诺书。
      </el-alert>
    </div>

    <div class="card">
      <div class="toolbar">
        <h4 style="margin:0">试卷题目</h4>
        <span class="spacer"></span>
        <el-button v-if="!isPm" type="success" @click="openAdd">从题库补充试题</el-button>
      </div>
      <el-table :data="questions" border stripe row-key="questionId" ref="tableRef">
        <el-table-column label="序" width="76">
          <template #default="{ $index }">
            <span class="seq-cell">
              <el-icon v-if="!isPm" class="drag-handle" title="拖拽调整顺序"><Rank /></el-icon>
              {{ $index + 1 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="90">
          <template #default="{ row }"><el-tag size="small">{{ typeNames[row.question?.type] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="题干" min-width="280">
          <template #default="{ row }"><span class="q-content">{{ row.question?.content }}</span></template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            {{ row.question?.bankType === 'PUBLIC' ? '公共题库' : '私有题库' }}
          </template>
        </el-table-column>
        <el-table-column label="分值" width="130">
          <template #default="{ row }">
            <el-input-number v-if="!isPm" v-model="row.score" :min="0.5" :max="100" :step="0.5" size="small"
                             @change="v => changeScore(row, v)" />
            <span v-else>{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isPm" label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeQuestion(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 补充试题 -->
    <el-dialog v-model="addVisible" title="从题库选择试题" width="900px" destroy-on-close>
      <div class="toolbar">
        <el-radio-group v-model="addQuery.bankType" @change="loadBank">
          <el-radio-button value="PUBLIC">公共题库</el-radio-button>
          <el-radio-button value="PRIVATE">本部门私有题库</el-radio-button>
        </el-radio-group>
        <el-select v-model="addQuery.type" placeholder="题型" clearable style="width:110px" @change="loadBank">
          <el-option v-for="(n, k) in typeNames" :key="k" :label="n" :value="k" />
        </el-select>
        <el-select v-model="addQuery.skill" placeholder="技能" clearable style="width:110px" @change="loadBank">
          <el-option v-for="s in skills" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="addQuery.difficulty" placeholder="难度" clearable style="width:100px" @change="loadBank">
          <el-option v-for="d in 5" :key="d" :label="diffNames[d]" :value="d" />
        </el-select>
        <el-input v-model="addQuery.keyword" placeholder="关键词" clearable style="width:150px" @keyup.enter="loadBank" />
        <el-button type="primary" @click="loadBank">筛选</el-button>
      </div>
      <el-table :data="bankRows" border height="360" @selection-change="s => selected = s" ref="bankTable">
        <el-table-column type="selection" width="45" :selectable="r => !inPaper(r.id)" />
        <el-table-column label="题型" width="85">
          <template #default="{ row }">{{ typeNames[row.type] }}</template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="260" show-overflow-tooltip />
        <el-table-column label="难度" width="75">
          <template #default="{ row }">{{ diffNames[row.difficulty] }}</template>
        </el-table-column>
        <el-table-column prop="skill" label="技能" width="85" />
        <el-table-column prop="defaultScore" label="默认分" width="75" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="inPaper(row.id)" type="info" size="small">已选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:10px" layout="total, prev, pager, next" :total="bankTotal"
                     v-model:current-page="addQuery.pageNum" :page-size="10" @current-change="loadBank" />
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selected.length" @click="addSelected">
          添加所选 {{ selected.length }} 题
        </el-button>
      </template>
    </el-dialog>

    <!-- 保密承诺 -->
    <el-dialog v-model="pledgeVisible" title="签署保密承诺书" width="600px">
      <pre class="pledge-text">{{ pledgeText }}</pre>
      <el-form label-width="110px" style="margin-top:12px">
        <el-form-item :label="`输入姓名签署`">
          <el-input v-model="signerName" :placeholder="`请输入本人姓名：${userName}`" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pledgeVisible = false">取消</el-button>
        <el-button type="primary" @click="doPledge">确认签署</el-button>
      </template>
    </el-dialog>

    <!-- 基本信息 -->
    <el-dialog v-model="infoVisible" title="编辑试卷信息" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="info.name" /></el-form-item>
        <el-form-item label="岗位要求"><el-input v-model="info.jobRequirement" /></el-form-item>
        <el-form-item label="测评目标"><el-input v-model="info.assessTarget" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="info.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="infoVisible = false">取消</el-button>
        <el-button type="primary" @click="saveInfo">保存</el-button>
      </template>
    </el-dialog>

    <!-- 日志 -->
    <el-drawer v-model="logsVisible" title="试卷调整日志" size="480px" @open="loadLogs">
      <el-timeline>
        <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="log.createdAt" placement="top">
          <b>{{ log.operatorName }}</b>
          <el-tag size="small" style="margin:0 6px">{{ actionNames[log.action] || log.action }}</el-tag>
          <div class="muted">{{ log.detail }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Sortable from 'sortablejs'
import {
  getPaper, updatePaper, addPaperQuestions, removePaperQuestion, updatePaperQuestionScore,
  reorderPaper, paperLogs, pledgeTemplate, signPledge, publishPaper, pageQuestions, download
} from '../../api'

const route = useRoute()
const paperId = route.params.id
const isPm = localStorage.getItem('role') === 'PM_ADMIN'
const userName = localStorage.getItem('userName')
const loading = ref(false)
const paper = ref(null)
const questions = ref([])
const typeNames = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', ESSAY: '主观简答' }
const diffNames = ['', '入门', '简单', '中等', '较难', '困难']
const skills = ['Java', 'C++', '数据库', '数据结构', '前端', '测试', '运维']
const actionNames = { CREATE: '创建', ADD: '补充试题', REMOVE: '移除试题', UPDATE: '调整分值',
  REORDER: '调整顺序', PUBLISH: '发布', COPY: '复制', AI_COMPOSE: 'AI组卷', PLEDGE: '签署承诺', EDIT: '编辑信息' }

async function load() {
  loading.value = true
  try {
    const data = await getPaper(paperId)
    paper.value = data.paper
    questions.value = data.questions
  } finally { loading.value = false }
}

function inPaper(qid) { return questions.value.some(q => q.questionId === qid) }

async function changeScore(row, v) {
  await updatePaperQuestionScore(paperId, row.questionId, v)
  await load()
}

async function removeQuestion(row) {
  await ElMessageBox.confirm('确认从试卷移除该题？', '提示', { type: 'warning' })
  await removePaperQuestion(paperId, row.questionId)
  ElMessage.success('已移除')
  load()
}

// ---------- 拖拽排序 ----------
const tableRef = ref(null)
let sortable = null

function initSortable() {
  if (isPm || sortable) return
  const tbody = tableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
  if (!tbody) return
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 150,
    ghostClass: 'drag-ghost',
    chosenClass: 'drag-chosen',
    async onEnd(evt) {
      const { oldIndex, newIndex, item, from } = evt
      if (oldIndex === newIndex) return
      // 先还原 Sortable 对 DOM 的移动，交由 Vue 按数据渲染新顺序，避免 keyed diff 错位
      from.removeChild(item)
      from.insertBefore(item, from.children[oldIndex] || null)
      const arr = [...questions.value]
      const [moved] = arr.splice(oldIndex, 1)
      arr.splice(newIndex, 0, moved)
      questions.value = arr
      try {
        await reorderPaper(paperId, arr.map(q => q.questionId))
        ElMessage.success(`已将第 ${oldIndex + 1} 题移动到第 ${newIndex + 1} 位`)
      } finally {
        load()
      }
    }
  })
}

onBeforeUnmount(() => { sortable?.destroy(); sortable = null })

// ---------- 补充试题 ----------
const addVisible = ref(false)
const bankRows = ref([])
const bankTotal = ref(0)
const selected = ref([])
const addQuery = reactive({ pageNum: 1, pageSize: 10, bankType: 'PUBLIC', type: '', skill: '', difficulty: null, keyword: '' })

function openAdd() { addVisible.value = true; loadBank() }

async function loadBank() {
  const data = await pageQuestions({ ...addQuery, status: 'NORMAL' })
  bankRows.value = data.records
  bankTotal.value = data.total
}

async function addSelected() {
  await addPaperQuestions(paperId, selected.value.map(q => ({ questionId: q.id, score: q.defaultScore })))
  ElMessage.success('已添加')
  addVisible.value = false
  load()
}

// ---------- 承诺与发布 ----------
const pledgeVisible = ref(false)
const pledgeText = ref('')
const signerName = ref('')

async function openPledge() {
  pledgeText.value = await pledgeTemplate()
  signerName.value = ''
  pledgeVisible.value = true
}

async function doPledge() {
  await signPledge(paperId, signerName.value)
  ElMessage.success('签署成功')
  pledgeVisible.value = false
  load()
}

async function publish() {
  await ElMessageBox.confirm('发布后试卷可用于创建笔试项目，确认发布？', '发布确认')
  await publishPaper(paperId)
  ElMessage.success('发布成功')
  load()
}

// ---------- 信息与日志 ----------
const infoVisible = ref(false)
const info = reactive({ name: '', jobRequirement: '', assessTarget: '', description: '' })

async function saveInfo() {
  await updatePaper(paperId, info)
  ElMessage.success('已保存')
  infoVisible.value = false
  load()
}

const logsVisible = ref(false)
const logs = ref([])

async function loadLogs() { logs.value = await paperLogs(paperId) }

function exportPaper(format) {
  download(`/papers/${paperId}/export?format=${format}`, `${paper.value.name}.${format === 'pdf' ? 'pdf' : 'docx'}`)
}

onMounted(async () => {
  await load()
  Object.assign(info, { name: paper.value.name, jobRequirement: paper.value.jobRequirement,
    assessTarget: paper.value.assessTarget, description: paper.value.description })
  loadLogs()
  await nextTick()
  initSortable()
})
</script>

<style scoped>
.pledge-text { white-space: pre-wrap; background: #f8f9fb; padding: 14px; border-radius: 6px;
  font-family: inherit; line-height: 1.8; max-height: 300px; overflow: auto; }
.seq-cell { display: inline-flex; align-items: center; gap: 6px; }
.drag-handle { cursor: grab; color: #a8abb2; font-size: 16px; }
.drag-handle:hover { color: var(--el-color-primary); }
.drag-handle:active { cursor: grabbing; }
:deep(.drag-ghost) td { background: var(--el-color-primary-light-9) !important; }
:deep(.drag-ghost) { opacity: 0.7; }
:deep(.drag-chosen) td { background: var(--el-color-primary-light-9) !important; }
</style>