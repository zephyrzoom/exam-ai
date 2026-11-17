<template>
  <div class="candidate-page">
    <div class="header">
      <h2>我的考试</h2>
      <span class="spacer"></span>
      <span>{{ userName }}</span>
      <el-button link type="danger" @click="logout">退出</el-button>
    </div>
    <div class="list">
      <el-empty v-if="!projects.length" description="暂无考试安排，请联系招聘管理员" />
      <div class="exam-card" v-for="p in projects" :key="p.projectId">
        <div class="exam-title">{{ p.projectName }}</div>
        <div class="muted">
          时间：{{ p.startTime }} ~ {{ p.endTime }}　时长：{{ p.durationMin }} 分钟<br />
          地点：{{ p.location || '线上' }}　批次：{{ p.batch || '-' }}
        </div>
        <div style="margin-top:12px">
          <el-tag v-if="isEnded(p)" type="warning">已结束</el-tag>
          <el-button v-else-if="isOpen(p)" type="primary" @click="enter(p)">
            {{ p.candidateStatus === 'ATTENDED' ? '继续作答' : '进入考试' }}
          </el-button>
          <el-button v-else-if="secondsToStart(p) <= 60" type="primary" disabled>
            距开始 {{ countdown(p) }}
          </el-button>
          <el-tag v-else type="info">未开始</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { myProjects } from '../../api'

const router = useRouter()
const userName = localStorage.getItem('userName')
const projects = ref([])
const now = ref(Date.now())
let timer = null

function enter(p) { router.push(`/candidate/exam/${p.projectId}`) }

function ts(s) { return new Date(s.replace(' ', 'T')).getTime() }
function secondsToStart(p) { return Math.max(0, Math.ceil((ts(p.startTime) - now.value) / 1000)) }
function isOpen(p) { return now.value >= ts(p.startTime) && now.value < ts(p.endTime) }
function isEnded(p) { return now.value >= ts(p.endTime) }
function countdown(p) {
  const s = secondsToStart(p)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function logout() {
  localStorage.clear()
  router.push('/candidate-login')
}

onMounted(async () => {
  const list = await myProjects()
  projects.value = list.filter(p => p.candidateStatus !== 'SUBMITTED')
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.candidate-page { min-height: 100vh; background: #f5f7fa; }
.header { background: #fff; padding: 14px 24px; display: flex; align-items: center; gap: 12px;
  border-bottom: 1px solid #ebeef5; }
.header h2 { margin: 0; font-size: 18px; }
.spacer { flex: 1; }
.list { max-width: 760px; margin: 24px auto; padding: 0 16px; }
.exam-card { background: #fff; border-radius: 10px; padding: 20px; margin-bottom: 14px;
  border: 1px solid #ebeef5; }
.exam-title { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
</style>