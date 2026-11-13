<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <span class="spacer"></span>
          <el-button type="success" @click="openUser()">新建用户</el-button>
        </div>
        <el-table :data="users" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="realName" label="姓名" width="100" />
          <el-table-column label="角色" width="180">
            <template #default="{ row }">
              <el-tag :type="row.role === 'PM_ADMIN' ? 'danger' : 'primary'" size="small">
                {{ row.role === 'PM_ADMIN' ? '项目管理部招聘负责人' : '部门招聘负责人' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deptName" label="部门" width="120" />
          <el-table-column prop="email" label="邮箱" min-width="170" />
          <el-table-column prop="phone" label="手机" width="130" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openUser(row)">编辑</el-button>
              <el-button link type="danger" @click="removeUser(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="部门管理" name="depts">
        <div class="toolbar">
          <span class="spacer"></span>
          <el-button type="success" @click="deptVisible = true">新建部门</el-button>
        </div>
        <el-table :data="depts" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="部门名称" min-width="180" />
          <el-table-column prop="code" label="编码" width="140" />
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="userVisible" :title="userForm.id ? '编辑用户' : '新建用户'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" :disabled="!!userForm.id" />
        </el-form-item>
        <el-form-item label="姓名" required><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="userForm.role" style="width:100%">
            <el-option label="项目管理部招聘负责人" value="PM_ADMIN" />
            <el-option label="部门招聘负责人" value="DEPT_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" :required="userForm.role === 'DEPT_ADMIN'">
          <el-select v-model="userForm.deptId" clearable style="width:100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="userForm.phone" /></el-form-item>
        <el-form-item :label="userForm.id ? '重置密码' : '初始密码'">
          <el-input v-model="userForm.password" :placeholder="userForm.id ? '留空则不修改' : '默认 123456'" />
        </el-form-item>
        <el-form-item v-if="userForm.id" label="状态">
          <el-switch v-model="userForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deptVisible" title="新建部门" width="420px">
      <el-form label-width="90px">
        <el-form-item label="部门名称" required><el-input v-model="deptForm.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="deptForm.code" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, deleteUser, listDepts, createDept } from '../../api'

const tab = ref('users')
const users = ref([])
const depts = ref([])

async function load() {
  users.value = await listUsers()
  depts.value = await listDepts()
}

const userVisible = ref(false)
const userForm = reactive({ id: null, username: '', realName: '', role: 'DEPT_ADMIN', deptId: null,
  email: '', phone: '', password: '', status: 1 })

function openUser(row) {
  if (row) Object.assign(userForm, row, { password: '' })
  else Object.assign(userForm, { id: null, username: '', realName: '', role: 'DEPT_ADMIN',
    deptId: null, email: '', phone: '', password: '', status: 1 })
  userVisible.value = true
}

async function saveUser() {
  if (!userForm.username || !userForm.realName) return ElMessage.warning('请填写用户名和姓名')
  if (userForm.id) await updateUser(userForm.id, userForm)
  else await createUser(userForm)
  ElMessage.success('已保存')
  userVisible.value = false
  load()
}

async function removeUser(row) {
  await ElMessageBox.confirm(`确认删除用户 ${row.realName}？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

const deptVisible = ref(false)
const deptForm = reactive({ name: '', code: '' })
async function saveDept() {
  if (!deptForm.name) return ElMessage.warning('请填写部门名称')
  await createDept(deptForm)
  ElMessage.success('已创建')
  deptVisible.value = false
  deptForm.name = ''; deptForm.code = ''
  load()
}

onMounted(load)
</script>