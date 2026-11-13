<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="试卷名称" clearable style="width:200px" @keyup.enter="load" @clear="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:130px" @change="load">
        <el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" />
      </el-select>
      <el-date-picker v-model="createdRange" type="daterange" range-separator="至"
                      start-placeholder="创建开始" end-placeholder="创建结束"
                      value-format="YYYY-MM-DD" style="width:260px" @change="onRangeChange" />
      <el-button type="primary" @click="load">查询</el-button>
      <span class="spacer"></span>
      <el-button v-if="!isPm" type="success" @click="aiVisible = true">AI 智能组卷</el-button>
      <el-button v-if="!isPm" @click="createVisible = true">手动创建</el-button>
    </div>

    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="65" />
      <el-table-column prop="name" label="试卷名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="questionCount" label="题量" width="70" />
      <el-table-column prop="totalScore" label="总分" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="保密承诺" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.pledgeUserId" type="success" size="small">{{ row.pledgeUserName }} 已签</el-tag>
          <el-tag v-else type="danger" size="small">未签署</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="90" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/admin/papers/${row.id}`)">
            {{ !isPm ? '编辑' : '查看' }}
          </el-button>
          <el-button v-if="!isPm" link type="warning" @click="copy(row)">复制</el-button>
          <el-button link type="success" @click="exportPaper(row, 'word')">Word</el-button>
          <el-button link type="success" @click="exportPaper(row, 'pdf')">PDF</el-button>
          <el-button v-if="!isPm" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px;justify-content:flex-end" layout="total, prev, pager, next"
                   :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />

    <!-- AI 组卷 -->
    <el-dialog v-model="aiVisible" title="AI 智能组卷" width="760px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        根据岗位要求与题型构成，从公共题库按难度比例智能随机抽题。生成后可人工增删调整。
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="试卷名称"><el-input v-model="ai.name" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="岗位要求"><el-input v-model="ai.jobRequirement" placeholder="如：Java 初级开发工程师" /></el-form-item>
        <el-form-item label="测评目标"><el-input v-model="ai.assessTarget" placeholder="如：考察 Java 基础与编码能力" /></el-form-item>
        <el-form-item label="技能筛选">
          <el-select v-model="ai.skill" clearable placeholder="不限" style="width:200px">
            <el-option v-for="s in skills" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型构成">
          <div style="width:100%">
            <div v-for="rule in ai.rules" :key="rule.type" class="rule-row">
              <el-checkbox v-model="rule.enabled">{{ typeNames[rule.type] }}</el-checkbox>
              <template v-if="rule.enabled">
                <span>数量</span>
                <el-input-number v-model="rule.count" :min="1" :max="50" size="small" />
                <span>难度比例(入门/简单/中等/较难/困难)</span>
                <el-input v-for="d in 5" :key="d" v-model.number="rule.ratio[d - 1]" size="small"
                          style="width:52px" type="number" />
              </template>
            </div>
            <div class="muted">比例留空或全 0 表示不限难度随机抽取</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" :loading="composing" @click="doCompose">开始组卷</el-button>
      </template>
    </el-dialog>

    <!-- 手动创建 -->
    <el-dialog v-model="createVisible" title="手动创建试卷" width="520px">
      <el-form label-width="90px">
        <el-form-item label="试卷名称" required><el-input v-model="manual.name" /></el-form-item>
        <el-form-item label="岗位要求"><el-input v-model="manual.jobRequirement" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="manual.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePapers, aiCompose, createPaper, copyPaper, deletePaper, download } from '../../api'

const router = useRouter()
const isPm = localStorage.getItem('role') === 'PM_ADMIN'
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, name: '', status: '', createdBegin: '', createdEnd: '' })
const createdRange = ref(null)

function onRangeChange(v) {
  query.createdBegin = v ? v[0] + ' 00:00:00' : ''
  query.createdEnd = v ? v[1] + ' 23:59:59' : ''
  load()
}
const skills = ['Java', 'C++', '数据库', '数据结构', '前端', '测试', '运维']
const typeNames = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', ESSAY: '主观简答' }

async function load() {
  loading.value = true
  try {
    const data = await pagePapers(query)
    rows.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

// ---------- AI 组卷 ----------
const aiVisible = ref(false)
const composing = ref(false)
const ai = reactive({
  name: '', jobRequirement: '', assessTarget: '', skill: 'Java',
  rules: [
    { type: 'SINGLE', enabled: true, count: 5, ratio: [20, 30, 30, 15, 5] },
    { type: 'MULTI', enabled: true, count: 2, ratio: [0, 50, 50, 0, 0] },
    { type: 'JUDGE', enabled: true, count: 2, ratio: [0, 0, 0, 0, 0] },
    { type: 'ESSAY', enabled: true, count: 1, ratio: [0, 0, 0, 0, 0] }
  ]
})

async function doCompose() {
  const rules = ai.rules.filter(r => r.enabled).map(r => {
    const ratio = {}
    r.ratio.forEach((v, i) => { if (v > 0) ratio[i + 1] = v })
    return { type: r.type, count: r.count, difficultyRatio: ratio }
  })
  if (!rules.length) return ElMessage.warning('请至少选择一种题型')
  composing.value = true
  try {
    const paper = await aiCompose({ name: ai.name, jobRequirement: ai.jobRequirement,
      assessTarget: ai.assessTarget, skill: ai.skill, rules })
    ElMessage.success(`组卷完成：${paper.questionCount} 题，总分 ${paper.totalScore}`)
    aiVisible.value = false
    router.push(`/admin/papers/${paper.id}`)
  } finally { composing.value = false }
}

// ---------- 手动创建 ----------
const createVisible = ref(false)
const manual = reactive({ name: '', jobRequirement: '', description: '' })

async function doCreate() {
  if (!manual.name) return ElMessage.warning('请填写试卷名称')
  const paper = await createPaper(manual)
  createVisible.value = false
  router.push(`/admin/papers/${paper.id}`)
}

async function copy(row) {
  const p = await copyPaper(row.id)
  ElMessage.success('已复制')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该试卷？', '提示', { type: 'warning' })
  await deletePaper(row.id)
  ElMessage.success('已删除')
  load()
}

function exportPaper(row, format) {
  download(`/papers/${row.id}/export?format=${format}`, `${row.name}.${format === 'pdf' ? 'pdf' : 'docx'}`)
}

onMounted(load)
</script>

<style scoped>
.rule-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
</style>