<template>
  <div class="page">
    <div class="toolbar">
      <h3 style="margin:0">成绩列表</h3>
      <span class="spacer"></span>
      <el-button @click="$router.push(`/admin/projects/${projectId}/analysis`)">成绩分析报告</el-button>
      <el-button type="success" @click="exportScores">导出成绩 Excel</el-button>
    </div>
    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column label="排名" width="70">
        <template #default="{ row }">{{ row.rankNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="candidateName" label="姓名" width="100" />
      <el-table-column label="身份证号" width="190">
        <template #default="{ row }">{{ mask(row.idCard) }}</template>
      </el-table-column>
      <el-table-column prop="vendor" label="供应商" width="120" />
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
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { projectScores, download } from '../../api'

const route = useRoute()
const projectId = route.params.id
const rows = ref([])
const loading = ref(false)
const mask = id => id && id.length >= 10 ? id.slice(0, 6) + '********' + id.slice(-4) : id

async function load() {
  loading.value = true
  try { rows.value = await projectScores(projectId) }
  finally { loading.value = false }
}

function exportScores() {
  download(`/projects/${projectId}/scores/export`, '成绩表.xlsx')
}

onMounted(load)
</script>