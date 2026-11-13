<template>
  <div class="page" v-loading="loading">
    <div class="card" v-if="record">
      <div class="toolbar">
        <h3 style="margin:0">{{ record.candidateName }} · {{ record.projectName }}</h3>
        <el-tag>{{ record.status === 'GRADED' ? '已评分' : '待复核' }}</el-tag>
        <span class="muted">交卷：{{ record.submitTime }}（{{ record.submitType === 'AUTO' ? '自动' : '主动' }}） 切屏 {{ record.switchCount }} 次</span>
        <span class="spacer"></span>
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" :disabled="!record.reviewDone || record.status === 'GRADED'" @click="finalize">
          合成总分
        </el-button>
      </div>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="客观题得分">{{ record.objectiveScore }}</el-descriptions-item>
        <el-descriptions-item label="主观题得分">{{ record.subjectiveScore }}</el-descriptions-item>
        <el-descriptions-item label="总分">{{ record.totalScore }}</el-descriptions-item>
        <el-descriptions-item label="排名 / 及格">{{ record.rankNo || '-' }} / {{ record.passed === 1 ? '及格' : record.passed === 0 ? '不及格' : '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="card" v-for="(a, idx) in answers" :key="a.id">
      <div class="toolbar">
        <b>第 {{ idx + 1 }} 题（{{ typeNames[a.type] }}，满分 {{ a.fullScore }} 分）</b>
        <el-tag v-if="a.type !== 'ESSAY'" :type="a.score > 0 ? 'success' : 'danger'" size="small">
          自动判分：{{ a.score }} 分
        </el-tag>
        <template v-else>
          <el-tag v-if="a.status === 'REVIEWED'" type="success" size="small">已复核：{{ a.reviewScore }} 分（{{ a.reviewerName }}）</el-tag>
          <el-tag v-else-if="a.status === 'AI_DONE'" type="warning" size="small">AI初评：{{ a.aiScore }} 分，待复核</el-tag>
          <el-tag v-else type="info" size="small">AI初评中…</el-tag>
        </template>
      </div>
      <div class="q-content">{{ a.question?.content }}</div>
      <div v-if="a.question?.optionsJson" style="margin:8px 0">
        <div v-for="(opt, i) in JSON.parse(a.question.optionsJson)" :key="i" class="muted">
          {{ 'ABCDEFGH'[i] }}. {{ opt }}
        </div>
      </div>
      <el-row :gutter="14" style="margin-top:10px">
        <el-col :span="12">
          <div class="answer-box">
            <div class="box-title">考生答案</div>
            <div class="q-content">{{ a.answer || '（未作答）' }}</div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="answer-box ref">
            <div class="box-title">参考答案</div>
            <div class="q-content">{{ a.question?.answer }}</div>
            <div v-if="a.question?.scorePoints" style="margin-top:6px">
              <div class="box-title">评分要点</div>
              <div class="q-content muted">{{ a.question.scorePoints }}</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <template v-if="a.type === 'ESSAY'">
        <el-alert v-if="a.aiComment" type="info" :closable="false" style="margin-top:10px">
          <b>AI 评分说明：</b>{{ a.aiComment }}
        </el-alert>
        <div v-if="a.status === 'REVIEWED'" class="muted" style="margin-top:8px">
          阅卷人：{{ a.reviewerName }}　复核时间：{{ a.reviewTime }}　意见：{{ a.reviewComment || '无' }}
        </div>
        <div v-else-if="a.status === 'AI_DONE' && record.status !== 'GRADED'" class="toolbar" style="margin-top:10px">
          <span>复核给分</span>
          <el-input-number v-model="reviewScores[a.id]" :min="0" :max="Number(a.fullScore)" :step="0.5" />
          <el-input v-model="reviewComments[a.id]" placeholder="复核意见（可选）" style="width:280px" />
          <el-button type="success" @click="confirmAi(a)">确认AI评分</el-button>
          <el-button type="primary" @click="submitReview(a)">提交复核分</el-button>
        </div>
      </template>
    </div>

    <div class="card" v-if="reviewLogs.length">
      <h4>评分追溯日志</h4>
      <el-timeline>
        <el-timeline-item v-for="log in reviewLogs" :key="log.id" :timestamp="log.createdAt" placement="top">
          <b>{{ log.reviewerName || 'AI 系统' }}</b>
          <el-tag size="small" style="margin:0 6px">{{ { AI_SCORE: 'AI初评', CONFIRM: '确认', ADJUST: '修正' }[log.action] }}</el-tag>
          <span>{{ log.oldScore != null ? log.oldScore + ' → ' : '' }}{{ log.newScore }} 分</span>
          <div class="muted">{{ log.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { gradingDetail, reviewAnswer, finalizeRecord } from '../../api'

const route = useRoute()
const recordId = route.params.id
const loading = ref(false)
const record = ref(null)
const answers = ref([])
const reviewLogs = ref([])
const reviewScores = reactive({})
const reviewComments = reactive({})
const typeNames = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', ESSAY: '主观简答' }

async function load() {
  loading.value = true
  try {
    const data = await gradingDetail(recordId)
    record.value = data.record
    answers.value = data.answers
    reviewLogs.value = data.reviewLogs
    answers.value.forEach(a => {
      if (a.type === 'ESSAY' && reviewScores[a.id] === undefined) {
        reviewScores[a.id] = Number(a.aiScore ?? 0)
      }
    })
  } finally { loading.value = false }
}

async function confirmAi(a) {
  await reviewAnswer(a.id, Number(a.aiScore ?? 0), '确认 AI 初评分')
  ElMessage.success('已确认')
  load()
}

async function submitReview(a) {
  await reviewAnswer(a.id, reviewScores[a.id], reviewComments[a.id] || '')
  ElMessage.success('复核已提交')
  load()
}

async function finalize() {
  const r = await finalizeRecord(recordId)
  ElMessage.success(`总分已合成：${r.totalScore} 分，排名 ${r.rankNo}`)
  load()
}

onMounted(load)
</script>

<style scoped>
.answer-box { background: #f8f9fb; border-radius: 6px; padding: 12px; min-height: 90px; }
.answer-box.ref { background: #f0f9eb; }
.box-title { font-weight: 600; margin-bottom: 6px; font-size: 13px; }
</style>