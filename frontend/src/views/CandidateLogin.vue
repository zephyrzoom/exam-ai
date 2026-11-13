<template>
  <div class="login-bg">
    <div class="login-box">
      <h2>考生登录</h2>
      <p class="muted">请使用本人身份证号与姓名登录考试系统</p>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="form.idCard" placeholder="身份证号" size="large" :prefix-icon="Postcard" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.name" placeholder="姓名" size="large" :prefix-icon="User" @keyup.enter="doLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
          进入考试
        </el-button>
      </el-form>
      <div class="links">
        <router-link to="/login">← 管理端入口</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Postcard } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { candidateLogin } from '../api'

const router = useRouter()
const form = reactive({ idCard: '', name: '' })
const loading = ref(false)

async function doLogin() {
  if (!form.idCard || !form.name) return ElMessage.warning('请输入身份证号和姓名')
  loading.value = true
  try {
    const data = await candidateLogin(form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('loginType', 'candidate')
    localStorage.setItem('userName', data.candidate.name)
    router.push('/candidate')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #134e5e 0%, #71b280 100%); }
.login-box { width: 400px; background: #fff; border-radius: 12px; padding: 36px; }
.login-box h2 { margin: 0 0 6px; text-align: center; }
.login-box .muted { text-align: center; margin-bottom: 22px; }
.links { margin-top: 14px; }
</style>