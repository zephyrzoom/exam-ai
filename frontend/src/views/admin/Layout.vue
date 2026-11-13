<template>
  <el-container style="min-height:100vh">
    <el-aside width="210px" class="aside">
      <div class="logo">AI 考试系统</div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#bfcbd9"
               active-text-color="#409eff">
        <el-menu-item index="/admin/dashboard"><el-icon><DataBoard /></el-icon>工作台</el-menu-item>
        <el-menu-item index="/admin/questions"><el-icon><Collection /></el-icon>试题管理</el-menu-item>
        <el-menu-item index="/admin/papers"><el-icon><Document /></el-icon>组卷管理</el-menu-item>
        <el-menu-item index="/admin/projects"><el-icon><Calendar /></el-icon>考试管理</el-menu-item>
        <el-menu-item index="/admin/grading"><el-icon><EditPen /></el-icon>阅卷工作台</el-menu-item>
        <el-menu-item v-if="isPm" index="/admin/users"><el-icon><UserFilled /></el-icon>用户与部门</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="muted">{{ isPm ? '项目管理部招聘负责人' : '部门招聘负责人' }}</span>
        <span class="spacer"></span>
        <span>{{ userName }}</span>
        <el-button link type="danger" @click="logout">退出</el-button>
      </el-header>
      <el-main style="padding:0"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const userName = localStorage.getItem('userName') || ''
const isPm = computed(() => localStorage.getItem('role') === 'PM_ADMIN')

function logout() {
  localStorage.clear()
  router.push('/login')
}
</script>

<style scoped>
.aside { background: #1f2d3d; }
.logo { color: #fff; font-size: 17px; font-weight: 700; text-align: center; padding: 18px 0; }
.el-menu { border-right: none; }
.header { background: #fff; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid #ebeef5; }
.spacer { flex: 1; }
</style>