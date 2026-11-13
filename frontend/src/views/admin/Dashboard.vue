<template>
  <div class="page">
    <div class="stat-grid">
      <div class="stat-item"><div class="num">{{ stats.questions }}</div><div class="label">题库试题</div></div>
      <div class="stat-item"><div class="num">{{ stats.papers }}</div><div class="label">试卷</div></div>
      <div class="stat-item"><div class="num">{{ stats.projects }}</div><div class="label">笔试项目</div></div>
      <div class="stat-item"><div class="num">{{ stats.pendingReview }}</div><div class="label">待复核答卷</div></div>
    </div>
    <div class="card" style="margin-top:14px">
      <h3>业务流程</h3>
      <el-steps :active="5" align-center finish-status="success">
        <el-step title="确认考生名单" description="对接人事系统 / Excel 导入" />
        <el-step title="确认试卷" description="AI 组卷 + 人工调整 + 保密承诺" />
        <el-step title="考试通知" description="邮件通知考生并抄送管理员" />
        <el-step title="在线考试" description="身份证登录 / 自动保存 / 防作弊" />
        <el-step title="判分" description="客观自动 + AI 初评 + 人工复核" />
        <el-step title="入围回传" description="入围规则 + 结果回传人事系统" />
      </el-steps>
    </div>
    <div class="card">
      <h3>快捷入口</h3>
      <el-space wrap>
        <el-button type="primary" @click="$router.push('/admin/questions')">维护题库</el-button>
        <el-button type="success" @click="$router.push('/admin/papers')">AI 组卷</el-button>
        <el-button type="warning" @click="$router.push('/admin/projects')">创建笔试项目</el-button>
        <el-button @click="$router.push('/admin/grading')">进入阅卷</el-button>
      </el-space>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { pageQuestions, pagePapers, pageProjects, gradingRecords } from '../../api'

const stats = reactive({ questions: 0, papers: 0, projects: 0, pendingReview: 0 })

onMounted(async () => {
  const [q, p, pr, g] = await Promise.all([
    pageQuestions({ pageNum: 1, pageSize: 1 }),
    pagePapers({ pageNum: 1, pageSize: 1 }),
    pageProjects({ pageNum: 1, pageSize: 1 }),
    gradingRecords({ pageNum: 1, pageSize: 1, state: 'pending' })
  ])
  stats.questions = q.total
  stats.papers = p.total
  stats.projects = pr.total
  stats.pendingReview = g.total
})
</script>