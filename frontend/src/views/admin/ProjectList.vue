<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="项目名称" clearable style="width:200px" @keyup.enter="load" @clear="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:130px" @change="load">
        <el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" />
        <el-option label="已结束" value="FINISHED" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <span class="spacer"></span>
      <el-button v-if="!isPm" type="success" @click="openEdit()">创建笔试项目</el-button>
    </div>

    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="paperName" label="试卷" min-width="150" show-overflow-tooltip />
      <el-table-column prop="batch" label="批次" width="90" />
      <el-table-column prop="location" label="地点" width="100" show-overflow-tooltip />
      <el-table-column label="考试时间" width="300">
        <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
      </el-table-column>
      <el-table-column prop="durationMin" label="时长(分)" width="85" />
      <el-table-column prop="candidateCount" label="考生" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="{ DRAFT: 'info', PUBLISHED: 'success', FINISHED: 'warning' }[row.status]">
            {{ { DRAFT: '草稿', PUBLISHED: '已发布', FINISHED: '已结束' }[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/admin/projects/${row.id}`)">管理</el-button>
          <el-button link type="success" @click="$router.push(`/admin/projects/${row.id}/scores`)">成绩</el-button>
          <el-button v-if="!isPm" link type="warning" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="!isPm" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px;justify-content:flex-end" layout="total, prev, pager, next"
                   :total="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" @current-change="load" />

    <el-dialog v-model="editVisible" :title="form.id ? '编辑笔试项目' : '创建笔试项目'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="关联试卷" required>
          <el-select v-model="form.paperId" filterable placeholder="选择本部门已发布试卷" style="width:100%">
            <el-option v-for="p in papers" :key="p.id"
                       :label="`${p.name}（${p.questionCount}题 / ${p.totalScore}分）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试时长" required>
          <el-input-number v-model="form.durationMin" :min="5" :max="480" /> 分钟
        </el-form-item>
        <el-form-item label="开考时间" required>
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="截止时间" required>
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="考试地点"><el-input v-model="form.location" placeholder="线上 / 具体考场" /></el-form-item>
        <el-form-item label="批次"><el-input v-model="form.batch" placeholder="如：第一批" /></el-form-item>
        <el-form-item label="及格线" required>
          <el-input-number v-model="form.passScore" :min="0" :step="1" /> 分
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageProjects, createProject, updateProject, deleteProject, pagePapers } from '../../api'

const isPm = localStorage.getItem('role') === 'PM_ADMIN'
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, name: '', status: '' })
const papers = ref([])

async function load() {
  loading.value = true
  try {
    const data = await pageProjects(query)
    rows.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

const editVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, name: '', paperId: null, durationMin: 60, location: '线上',
  batch: '', startTime: '', endTime: '', passScore: 60 })

async function openEdit(row) {
  const data = await pagePapers({ pageNum: 1, pageSize: 100, status: 'PUBLISHED' })
  papers.value = data.records
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, name: '', paperId: null, durationMin: 60, location: '线上',
    batch: '', startTime: '', endTime: '', passScore: 60 })
  editVisible.value = true
}

async function save() {
  if (!form.name || !form.paperId || !form.startTime || !form.endTime)
    return ElMessage.warning('请完整填写项目信息')
  saving.value = true
  try {
    if (form.id) await updateProject(form.id, form)
    else await createProject(form)
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally { saving.value = false }
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该项目？', '提示', { type: 'warning' })
  await deleteProject(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>