<template>
  <div class="login-bg">
    <div class="login-box">
      <h2>AI 考试系统</h2>
      <p class="muted">管理端登录（项目管理部 / 各部门招聘负责人）</p>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    :prefix-icon="Lock" show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>
      <div class="links">
        <router-link to="/candidate-login">考生入口 →</router-link>
      </div>
      <el-alert type="info" :closable="false" style="margin-top:14px">
        演示账号：admin / zhangsan / lisi / wangwu，密码均为 123456
      </el-alert>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function doLogin() {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    const data = await login(form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('loginType', 'admin')
    localStorage.setItem('role', data.user.role)
    localStorage.setItem('userName', data.user.realName)
    localStorage.setItem('deptId', data.user.deptId || '')
    router.push('/admin')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1c3d6e 0%, #2b6cb0 100%); }
.login-box { width: 400px; background: #fff; border-radius: 12px; padding: 36px; }
.login-box h2 { margin: 0 0 6px; text-align: center; }
.login-box .muted { text-align: center; margin-bottom: 22px; }
.links { margin-top: 14px; text-align: right; }
</style>