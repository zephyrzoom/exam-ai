<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width:260px" @change="load">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-radio-group v-model="query.state" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="pending">待复核</el-radio-button>
        <el-radio-button value="done">已完成</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column prop="id" label="记录ID" width="80" />
      <el-table-column prop="candidateName" label="考生" width="100" />
      <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
      <el-table-column prop="submitTime" label="交卷时间" width="160" />
      <el-table-column label="交卷方式" width="95">
        <template #default="{ row }">{{ row.submitType === 'AUTO' ? '自动交卷' : '主动交卷' }}</template>
      </el-table-column>
      <el-table-column prop="objectiveScore" label="客观分" width="80" />
      <el-table-column label="AI初评" width="90">
        <template #default="{ row }">
          <el-tag :type="row.aiDone ? 'success' : 'warning'" size="small">{{ row.aiDone ? '已完成' : '进行中' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="复核" width="90">
        <template #default="{ row }">
          <el-tag :type="row.reviewDone ? 'success' : 'danger'" size="small">{{ row.reviewDone ? '已复核' : '待复核' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalScore" label="总分" width="80" />
      <el-table-column prop="switchCount" label="切屏" width="70" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/admin/grading/${row.id}`)">
            {{ row.reviewDone ? '查看' : '复核' }}
          </el-button>
          <el-button v-if="row.reviewDone && row.status !== 'GRADED'" link type="success" @click="finalize(row)">合成总分</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px;justify-content:flex-end" layout="total, prev, pager, next"
                   :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { gradingRecords, pageProjects, finalizeRecord } from '../../api'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const projects = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, projectId: null, state: 'pending' })

async function load() {
  loading.value = true
  try {
    const data = await gradingRecords(query)
    rows.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

async function finalize(row) {
  await finalizeRecord(row.id)
  ElMessage.success('总分已合成')
  load()
}

onMounted(async () => {
  const data = await pageProjects({ pageNum: 1, pageSize: 100 })
  projects.value = data.records
  load()
})
</script>