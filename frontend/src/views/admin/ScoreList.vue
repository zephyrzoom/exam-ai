<template>
  <div class="page">
    <div class="toolbar">
      <h3 style="margin:0">成绩列表</h3>
      <el-tag v-if="isGroup" type="warning" size="small">编组模式</el-tag>
      <el-select v-if="isGroup" v-model="groupFilter" placeholder="按组筛选" clearable style="width:160px">
        <el-option v-for="g in groupRows" :key="g.groupName" :label="g.groupName" :value="g.groupName" />
      </el-select>
      <span class="spacer"></span>
      <el-button @click="$router.push(`/admin/projects/${projectId}/analysis`)">成绩分析报告</el-button>
      <el-button type="success" @click="exportScores">导出成绩 Excel</el-button>
    </div>

    <template v-if="isGroup">
      <h4 style="margin:8px 0">组平均成绩</h4>
      <el-table :data="groupRows" border stripe size="small" style="margin-bottom:8px">
        <el-table-column prop="groupName" label="组名" min-width="120" />
        <el-table-column prop="memberCount" label="考生数" width="90" />
        <el-table-column prop="gradedCount" label="已评分" width="90" />
        <el-table-column prop="absentCount" label="缺考" width="80" />
        <el-table-column label="平均分" width="100">
          <template #default="{ row }">{{ row.avgScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="最高分" width="100">
          <template #default="{ row }">{{ row.maxScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="最低分" width="100">
          <template #default="{ row }">{{ row.minScore ?? '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="muted" style="margin-bottom:12px">平均分为组内已评分考生总分的平均值，缺考不计入；未编组考生归入「未分组」。</div>
    </template>

    <el-table :data="filteredRows" border stripe v-loading="loading">
      <el-table-column label="排名" width="70">
        <template #default="{ row }">{{ row.rankNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="candidateName" label="姓名" width="100" />
      <el-table-column v-if="isGroup" label="组" width="110">
        <template #default="{ row }">{{ row.groupName || '未分组' }}</template>
      </el-table-column>
      <el-table-column label="身份证号" width="190">
        <template #default="{ row }">{{ mask(row.idCard) }}</template>
      </el-table-column>
      <el-table-column prop="vendor" label="供应商" width="120" />
      <el-table-column label="交卷时间" width="160">
        <template #default="{ row }">{{ row.submitTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="objectiveScore" label="客观分" width="80" />
      <el-table-column prop="subjectiveScore" label="主观分" width="80" />
      <el-table-column prop="totalScore" label="总分" width="80" />
      <el-table-column label="及格" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.passed === 1" type="success" size="small">及格</el-tag>
          <el-tag v-else-if="row.passed === 0" type="danger" size="small">不及格</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="入围" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.shortlisted === 1" type="success" size="small">入围</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="缺考" width="70">
        <template #default="{ row }">{{ row.absent === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="switchCount" label="切屏" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'GRADED' ? 'success' : 'warning'">
            {{ row.status === 'GRADED' ? '已评分' : '待复核' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/admin/report/${row.id}`)">个人报告</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { projectScores, projectScoreGroups, getProject, download } from '../../api'

const route = useRoute()
const projectId = route.params.id
const rows = ref([])
const groupRows = ref([])
const project = ref(null)
const projectName = ref('')
const groupFilter = ref('')
const loading = ref(false)
const isGroup = computed(() => project.value?.mode === 'GROUP')
const filteredRows = computed(() => groupFilter.value
  ? rows.value.filter(r => (r.groupName || '未分组') === groupFilter.value)
  : rows.value)
const mask = id => id && id.length >= 10 ? id.slice(0, 6) + '********' + id.slice(-4) : id

async function load() {
  loading.value = true
  try {
    const proj = await getProject(projectId)
    project.value = proj
    projectName.value = proj.name
    rows.value = await projectScores(projectId)
    groupRows.value = proj.mode === 'GROUP' ? await projectScoreGroups(projectId) : []
  }
  finally { loading.value = false }
}

function exportScores() {
  download(`/projects/${projectId}/scores/export`, `${projectName.value}_成绩表.xlsx`)
}

onMounted(load)
</script>