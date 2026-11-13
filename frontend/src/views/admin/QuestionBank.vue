<template>
  <div class="page">
    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="公共题库" name="PUBLIC" />
      <el-tab-pane :label="isPm ? '私有题库(全部)' : '私有题库(本部门)'" name="PRIVATE" />
      <el-tab-pane v-if="isPm" label="贡献审核" name="REVIEW" />
    </el-tabs>

    <div class="toolbar" v-if="tab !== 'REVIEW'">
      <el-select v-model="query.type" placeholder="题型" clearable style="width:110px" @change="load">
        <el-option label="单选" value="SINGLE" /><el-option label="多选" value="MULTI" />
        <el-option label="判断" value="JUDGE" /><el-option label="主观简答" value="ESSAY" />
      </el-select>
      <el-select v-model="query.skill" placeholder="技能" clearable style="width:120px" @change="load">
        <el-option v-for="s in skills" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="query.difficulty" placeholder="难度" clearable style="width:110px" @change="load">
        <el-option v-for="d in 5" :key="d" :label="diffNames[d]" :value="d" />
      </el-select>
      <el-input v-model="query.knowledgePoint" placeholder="知识点" clearable style="width:130px" @change="load" />
      <el-input v-model="query.keyword" placeholder="题干关键词" clearable style="width:180px"
                @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <span class="spacer"></span>
      <el-button v-if="canWrite" type="success" @click="openEdit()">新建试题</el-button>
      <el-button v-if="canWrite" @click="importVisible = true">Excel 导入</el-button>
      <el-button @click="exportExcel">导出</el-button>
    </div>

    <el-table v-if="tab !== 'REVIEW'" :data="rows" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="65" />
      <el-table-column label="题型" width="90">
        <template #default="{ row }"><el-tag size="small">{{ typeNames[row.type] }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="content" label="题干" min-width="260" show-overflow-tooltip />
      <el-table-column prop="skill" label="技能" width="90" />
      <el-table-column label="难度" width="80">
        <template #default="{ row }">{{ diffNames[row.difficulty] }}</template>
      </el-table-column>
      <el-table-column prop="knowledgePoint" label="知识点" width="110" show-overflow-tooltip />
      <el-table-column prop="defaultScore" label="分值" width="70" />
      <el-table-column v-if="tab === 'PRIVATE' && isPm" prop="deptName" label="部门" width="100" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="tab === 'PRIVATE' && !isPm" link type="warning" @click="contribute(row)">贡献公共</el-button>
          <el-button v-if="canWrite" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-else :data="rows" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="65" />
      <el-table-column label="题型" width="90">
        <template #default="{ row }"><el-tag size="small">{{ typeNames[row.type] }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="content" label="题干" min-width="240" show-overflow-tooltip />
      <el-table-column prop="skill" label="技能" width="90" />
      <el-table-column prop="creatorName" label="贡献人" width="90" />
      <el-table-column prop="createdAt" label="贡献时间" width="160" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row, true)">查看</el-button>
          <el-button link type="success" @click="approve(row, true)">通过</el-button>
          <el-button link type="danger" @click="approve(row, false)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top:12px;justify-content:flex-end" layout="total, prev, pager, next, sizes"
                   :total="total" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                   :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑试题' : '新建试题'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="题型" required>
          <el-radio-group v-model="form.type" :disabled="!!form.id" @change="onTypeChange">
            <el-radio-button value="SINGLE">单选</el-radio-button>
            <el-radio-button value="MULTI">多选</el-radio-button>
            <el-radio-button value="JUDGE">判断</el-radio-button>
            <el-radio-button value="ESSAY">主观简答</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干" required>
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'ESSAY'" label="选项">
          <div style="width:100%">
            <div v-for="(opt, i) in options" :key="i" style="display:flex;gap:8px;margin-bottom:6px">
              <span style="line-height:32px;width:20px">{{ letters[i] }}.</span>
              <el-input v-model="options[i]" :disabled="form.type === 'JUDGE'" />
              <el-button v-if="form.type !== 'JUDGE' && options.length > 2" link type="danger"
                         @click="options.splice(i, 1)">删除</el-button>
            </div>
            <el-button v-if="form.type !== 'JUDGE' && options.length < 8" link type="primary"
                       @click="options.push('')">添加选项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="答案" required>
          <el-radio-group v-if="form.type === 'SINGLE' || form.type === 'JUDGE'" v-model="form.answer">
            <el-radio v-for="(o, i) in options" :key="i" :value="letters[i]">{{ letters[i] }}</el-radio>
          </el-radio-group>
          <el-checkbox-group v-else-if="form.type === 'MULTI'" v-model="multiAnswer">
            <el-checkbox v-for="(o, i) in options" :key="i" :value="letters[i]">{{ letters[i] }}</el-checkbox>
          </el-checkbox-group>
          <el-input v-else v-model="form.answer" type="textarea" :rows="3" placeholder="参考答案" />
        </el-form-item>
        <el-form-item v-if="form.type === 'ESSAY'" label="评分要点">
          <el-input v-model="form.scorePoints" type="textarea" :rows="3"
                    placeholder="按要点评分，用分号或换行分隔，如：1.线程安全性；2.null支持；3.性能差异" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="技能标签">
          <el-select v-model="form.skill" filterable allow-create placeholder="选择或输入" style="width:200px">
            <el-option v-for="s in skills" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="form.difficulty" style="width:200px">
            <el-option v-for="d in 5" :key="d" :label="diffNames[d]" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model="form.knowledgePoint" placeholder="如：数组 / 链表 / 排序 / 字符串" style="width:300px" />
        </el-form-item>
        <el-form-item label="默认分值">
          <el-input-number v-model="form.defaultScore" :min="0.5" :max="100" :step="0.5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog v-model="importVisible" title="Excel 导入试题" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        列顺序：题型 / 题干 / 选项(|分隔) / 答案 / 解析 / 评分要点 / 技能 / 难度1-5 / 知识点 / 分值
      </el-alert>
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="f => importFile = f.raw">
        <el-icon style="font-size:40px;color:#c0c4cc"><UploadFilled /></el-icon>
        <div>拖拽或点击选择 Excel 文件</div>
      </el-upload>
      <template #footer>
        <el-button @click="download('/questions/template', '试题导入模板.xlsx')">下载模板</el-button>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageQuestions, createQuestion, updateQuestion, deleteQuestion, contributeQuestion,
  pageContributions, approveContribution, download, upload
} from '../../api'

const isPm = localStorage.getItem('role') === 'PM_ADMIN'
const tab = ref('PUBLIC')
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, type: '', skill: '', difficulty: null, knowledgePoint: '', keyword: '' })
const skills = ['Java', 'C++', '数据库', '数据结构', '前端', '测试', '运维']
const diffNames = ['', '入门', '简单', '中等', '较难', '困难']
const typeNames = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', ESSAY: '主观简答' }
const letters = 'ABCDEFGH'.split('')

const canWrite = computed(() => tab.value === 'PUBLIC' ? isPm : !isPm)

async function load() {
  loading.value = true
  try {
    let data
    if (tab.value === 'REVIEW') {
      data = await pageContributions({ pageNum: query.pageNum, pageSize: query.pageSize })
    } else {
      data = await pageQuestions({ ...query, bankType: tab.value, status: 'NORMAL' })
    }
    rows.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

function onTabChange() { query.pageNum = 1; load() }

// ---------- 编辑 ----------
const editVisible = ref(false)
const saving = ref(false)
const readOnly = ref(false)
const form = reactive({ id: null, type: 'SINGLE', content: '', answer: '', analysis: '', scorePoints: '',
  skill: 'Java', difficulty: 2, knowledgePoint: '', defaultScore: 3 })
const options = ref(['', '', '', ''])
const multiAnswer = ref([])

function onTypeChange() {
  form.answer = ''
  multiAnswer.value = []
  if (form.type === 'JUDGE') options.value = ['正确', '错误']
  else if (options.value.length < 2) options.value = ['', '', '', '']
  form.defaultScore = { SINGLE: 3, MULTI: 4, JUDGE: 2, ESSAY: 10 }[form.type]
}

function openEdit(row, view = false) {
  readOnly.value = view
  if (row) {
    Object.assign(form, row)
    options.value = row.optionsJson ? JSON.parse(row.optionsJson) : ['', '', '', '']
    multiAnswer.value = row.type === 'MULTI' ? (row.answer || '').split('') : []
  } else {
    Object.assign(form, { id: null, type: 'SINGLE', content: '', answer: '', analysis: '', scorePoints: '',
      skill: 'Java', difficulty: 2, knowledgePoint: '', defaultScore: 3 })
    options.value = ['', '', '', '']
    multiAnswer.value = []
  }
  editVisible.value = true
}

async function save() {
  if (readOnly.value) { editVisible.value = false; return }
  if (!form.content) return ElMessage.warning('请填写题干')
  const payload = { ...form }
  if (form.type === 'MULTI') payload.answer = [...multiAnswer.value].sort().join('')
  if (form.type !== 'ESSAY') {
    if (options.value.some(o => !o.trim())) return ElMessage.warning('选项不能为空')
    payload.optionsJson = JSON.stringify(options.value)
    if (!payload.answer) return ElMessage.warning('请设置答案')
  } else {
    payload.optionsJson = null
  }
  payload.bankType = tab.value === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'
  saving.value = true
  try {
    if (form.id) await updateQuestion(form.id, payload)
    else await createQuestion(payload)
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally { saving.value = false }
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该试题？', '提示', { type: 'warning' })
  await deleteQuestion(row.id)
  ElMessage.success('已删除')
  load()
}

async function contribute(row) {
  await ElMessageBox.confirm('将该试题贡献至公共题库？需项目管理部审核后生效。', '贡献确认')
  await contributeQuestion(row.id)
  ElMessage.success('已提交贡献，等待项目管理部审核')
}

async function approve(row, pass) {
  await approveContribution(row.id, pass)
  ElMessage.success(pass ? '已通过' : '已驳回')
  load()
}

// ---------- 导入导出 ----------
const importVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)

async function doImport() {
  if (!importFile.value) return ElMessage.warning('请选择文件')
  importing.value = true
  try {
    const res = await upload('/questions/import', importFile.value, { bankType: tab.value })
    ElMessage.success(`导入完成：成功 ${res.success} 条，失败 ${res.fail} 条`)
    if (res.errors?.length) ElMessageBox.alert(res.errors.join('\n'), '失败明细')
    importVisible.value = false
    importFile.value = null
    load()
  } finally { importing.value = false }
}

function exportExcel() {
  download(`/questions/export?bankType=${tab.value}&type=${query.type || ''}&skill=${query.skill || ''}&difficulty=${query.difficulty || ''}&keyword=${encodeURIComponent(query.keyword || '')}`, '题库导出.xlsx')
}

onMounted(load)
</script>