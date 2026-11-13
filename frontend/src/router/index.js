import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/candidate-login', component: () => import('../views/CandidateLogin.vue') },
  {
    path: '/admin',
    component: () => import('../views/admin/Layout.vue'),
    meta: { admin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'questions', component: () => import('../views/admin/QuestionBank.vue') },
      { path: 'papers', component: () => import('../views/admin/PaperList.vue') },
      { path: 'papers/:id', component: () => import('../views/admin/PaperEdit.vue') },
      { path: 'projects', component: () => import('../views/admin/ProjectList.vue') },
      { path: 'projects/:id', component: () => import('../views/admin/ProjectDetail.vue') },
      { path: 'grading', component: () => import('../views/admin/GradingList.vue') },
      { path: 'grading/:id', component: () => import('../views/admin/GradingDetail.vue') },
      { path: 'projects/:id/scores', component: () => import('../views/admin/ScoreList.vue') },
      { path: 'projects/:id/analysis', component: () => import('../views/admin/Analysis.vue') },
      { path: 'report/:id', component: () => import('../views/admin/Report.vue') },
      { path: 'users', component: () => import('../views/admin/UserManage.vue'), meta: { pm: true } }
    ]
  },
  {
    path: '/candidate',
    component: () => import('../views/candidate/ExamList.vue'),
    meta: { candidate: true }
  },
  { path: '/candidate/exam/:projectId', component: () => import('../views/candidate/ExamRoom.vue'), meta: { candidate: true } },
  { path: '/', redirect: '/login' },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const type = localStorage.getItem('loginType')
  if (to.meta.admin) {
    if (!token || type !== 'admin') return next('/login')
    if (to.meta.pm && localStorage.getItem('role') !== 'PM_ADMIN') return next('/admin/dashboard')
  }
  if (to.meta.candidate && (!token || type !== 'candidate')) return next('/candidate-login')
  next()
})

export default router