<template>
  <div class="page" v-loading="loading">
    <template v-if="data">
      <div class="card">
        <div class="toolbar">
          <h3 style="margin:0">个人成绩报告</h3>
          <span class="spacer"></span>
          <el-button @click="$router.back()">返回</el-button>
          <el-button type="primary" @click="print">打印 / 另存 PDF</el-button>
        </div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="考生">{{ r.candidateName }}</el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ mask(r.idCard) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ r.vendor || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ data.project.name }}</el-descriptions-item>
          <el-descriptions-item label="试卷">{{ data.paper.name }}</el-descriptions-item>
          <el-descriptions-item label="交卷时间">{{ r.submitTime }}（{{ r.submitType === 'AUTO' ? '自动' : '主动' }}）</el-descriptions-item>
          <el-descriptions-item label="客观题得分">{{ r.objectiveScore }}</el-descriptions-item>
          <el-descriptions-item label="主观题得分">{{ r.subjectiveScore }}</el-descriptions-item>
          <el-descriptions-item label="总分">
            <b style="font-size:18px;color:#409eff">{{ r.totalScore }}</b> / {{ data.paper.totalScore }}
          </el-descriptions-item>
          <el-descriptions-item label="及格线">{{ data.project.passScore }}</el-descriptions-item>
          <el-descriptions-item label="是否及格">
            <el-tag :type="r.passed === 1 ? 'success' : 'danger'">{{ r.passed === 1 ? '及格' : '不及格' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="排名">{{ r.rankNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="入围结果">
            <el-tag v-if="r.shortlisted === 1" type="success">入围</el-tag>
            <span v-else>未入围</span>
          </el-descriptions-item>
          <el-descriptions-item label="缺考">{{ r.absent === 1 ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="切屏次数">{{ r.switchCount }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="card" v-for="(a, idx) in data.answers" :key="a.id">
        <div class="toolbar">
          <b>{{ idx + 1 }}. （{{ typeNames[a.type] }}，{{ a.fullScore }} 分）</b>
          <el-tag :type="a.score > 0 ? 'success' : 'danger'" size="small">得分 {{ a.score ?? '-' }}</el-tag>
          <span v-if="a.type === 'ESSAY' && a.reviewerName" class="muted">阅卷人：{{ a.reviewerName }}</span>
        </div>
        <div class="q-content">{{ a.question?.content }}</div>
        <div v-if="a.question?.optionsJson" style="margin:6px 0">
          <div v-for="(opt, i) in JSON.parse(a.question.optionsJson)" :key="i" class="muted">
            {{ 'ABCDEFGH'[i] }}. {{ opt }}
          </div>
        </div>
        <el-row :gutter="12" style="margin-top:8px">
          <el-col :span="12">
            <div class="box"><b>考生答案：</b><span class="q-content">{{ a.answer || '（未作答）' }}</span></div>
          </el-col>
          <el-col :span="12">
            <div class="box ref"><b>参考答案：</b><span class="q-content">{{ a.question?.answer }}</span></div>
          </el-col>
        </el-row>
        <div v-if="a.type === 'ESSAY'" style="margin-top:8px">
          <div class="muted">AI 初评：{{ a.aiScore }} 分 — {{ a.aiComment }}</div>
          <div class="muted" v-if="a.reviewScore != null">人工复核：{{ a.reviewScore }} 分（{{ a.reviewerName }}，{{ a.reviewTime }}）{{ a.reviewComment }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { personalReport } from '../../api'

const route = useRoute()
const loading = ref(false)
const data = ref(null)
const r = computed(() => data.value?.record || {})
const typeNames = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', ESSAY: '主观简答' }
const mask = id => id && id.length >= 10 ? id.slice(0, 6) + '********' + id.slice(-4) : id

function print() { window.print() }

onMounted(async () => {
  loading.value = true
  try { data.value = await personalReport(route.params.id) }
  finally { loading.value = false }
})
</script>

<style scoped>
.box { background: #f8f9fb; border-radius: 6px; padding: 10px; }
.box.ref { background: #f0f9eb; }
@media print {
  .toolbar .el-button { display: none; }
}
</style>