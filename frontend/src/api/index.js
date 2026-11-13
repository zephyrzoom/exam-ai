import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  return cfg
})

http.interceptors.response.use(
  res => {
    if (res.config.responseType === 'blob') return res
    const body = res.data
    if (body.code !== 0) {
      ElMessage.error(body.msg || '请求失败')
      if (body.code === 401) {
        localStorage.clear()
        router.push(localStorage.getItem('loginType') === 'candidate' ? '/candidate-login' : '/login')
      }
      return Promise.reject(new Error(body.msg))
    }
    return body.data
  },
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.msg || err.message
    if (status === 401) {
      localStorage.clear()
      router.push('/login')
    }
    ElMessage.error(msg || '网络错误')
    return Promise.reject(err)
  }
)

export default http

// ---------- 认证 ----------
export const login = d => http.post('/auth/login', d)
export const candidateLogin = d => http.post('/auth/candidate-login', d)
export const getMe = () => http.get('/auth/me')
export const myProjects = () => http.get('/auth/my-projects')

// ---------- 用户/部门 ----------
export const listDepts = () => http.get('/depts')
export const createDept = d => http.post('/depts', d)
export const listUsers = () => http.get('/users')
export const createUser = d => http.post('/users', d)
export const updateUser = (id, d) => http.put(`/users/${id}`, d)
export const deleteUser = id => http.delete(`/users/${id}`)

// ---------- 题库 ----------
export const pageQuestions = p => http.get('/questions', { params: p })
export const getQuestion = id => http.get(`/questions/${id}`)
export const createQuestion = d => http.post('/questions', d)
export const updateQuestion = (id, d) => http.put(`/questions/${id}`, d)
export const deleteQuestion = id => http.delete(`/questions/${id}`)
export const contributeQuestion = id => http.post(`/questions/${id}/contribute`)
export const pageContributions = p => http.get('/questions/contributions', { params: p })
export const approveContribution = (id, pass) => http.post(`/questions/${id}/approve`, { pass })

// ---------- 试卷 ----------
export const pagePapers = p => http.get('/papers', { params: p })
export const getPaper = id => http.get(`/papers/${id}`)
export const createPaper = d => http.post('/papers', d)
export const aiCompose = d => http.post('/papers/ai-compose', d)
export const updatePaper = (id, d) => http.put(`/papers/${id}`, d)
export const deletePaper = id => http.delete(`/papers/${id}`)
export const copyPaper = id => http.post(`/papers/${id}/copy`)
export const addPaperQuestions = (id, items) => http.post(`/papers/${id}/questions`, items)
export const removePaperQuestion = (id, qid) => http.delete(`/papers/${id}/questions/${qid}`)
export const updatePaperQuestionScore = (id, qid, score) => http.put(`/papers/${id}/questions/${qid}`, { score })
export const reorderPaper = (id, qids) => http.post(`/papers/${id}/reorder`, qids)
export const paperLogs = id => http.get(`/papers/${id}/logs`)
export const pledgeTemplate = () => http.get('/papers/pledge-template')
export const signPledge = (id, signerName) => http.post(`/papers/${id}/pledge`, { signerName })
export const publishPaper = id => http.post(`/papers/${id}/publish`)

// ---------- 笔试项目 ----------
export const pageProjects = p => http.get('/projects', { params: p })
export const getProject = id => http.get(`/projects/${id}`)
export const createProject = d => http.post('/projects', d)
export const updateProject = (id, d) => http.put(`/projects/${id}`, d)
export const deleteProject = id => http.delete(`/projects/${id}`)
export const publishProject = id => http.post(`/projects/${id}/publish`)
export const finishProject = id => http.post(`/projects/${id}/finish`)
export const listCandidates = id => http.get(`/projects/${id}/candidates`)
export const addCandidate = (id, d) => http.post(`/projects/${id}/candidates`, d)
export const deleteCandidate = (id, cid) => http.delete(`/projects/${id}/candidates/${cid}`)
export const hrSync = id => http.post(`/projects/${id}/candidates/hr-sync`)
export const sendNotify = id => http.post(`/projects/${id}/notify`)
export const notifyLogs = id => http.get(`/projects/${id}/notify-logs`)

// ---------- 在线考试 ----------
export const enterExam = projectId => http.post('/exam/enter', { projectId })
export const saveAnswers = (recordId, answers) => http.post('/exam/save', { recordId, answers })
export const submitExam = recordId => http.post('/exam/submit', { recordId })
export const examStatus = recordId => http.get(`/exam/status/${recordId}`)
export const reportCheat = (recordId, eventType, detail) => http.post('/exam/cheat', { recordId, eventType, detail })

// ---------- 阅卷 ----------
export const gradingRecords = p => http.get('/grading/records', { params: p })
export const gradingDetail = id => http.get(`/grading/records/${id}`)
export const reviewAnswer = (id, score, comment) => http.post(`/grading/answers/${id}/review`, { score, comment })
export const finalizeRecord = id => http.post(`/grading/records/${id}/finalize`)

// ---------- 成绩 ----------
export const projectScores = id => http.get(`/projects/${id}/scores`)
export const projectAnalysis = id => http.get(`/projects/${id}/analysis`)
export const personalReport = id => http.get(`/records/${id}/report`)

// ---------- 入围 ----------
export const getShortlistPlan = id => http.get(`/projects/${id}/shortlist-plan`)
export const saveShortlistPlan = (id, d) => http.post(`/projects/${id}/shortlist-plan`, d)
export const shortlistPreview = id => http.get(`/projects/${id}/shortlist/preview`)
export const shortlistConfirm = id => http.post(`/projects/${id}/shortlist/confirm`)
export const shortlistRevoke = id => http.post(`/projects/${id}/shortlist/revoke`)
export const pushHr = id => http.post(`/projects/${id}/shortlist/push-hr`)
export const hrSyncLogs = id => http.get(`/projects/${id}/hr-sync-logs`)

// ---------- 下载 ----------
export function download(url, filename) {
  return http.get(url, { responseType: 'blob' }).then(res => {
    const blob = new Blob([res.data])
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    a.click()
    URL.revokeObjectURL(a.href)
  })
}
export function upload(url, file, extra = {}) {
  const fd = new FormData()
  fd.append('file', file)
  Object.entries(extra).forEach(([k, v]) => fd.append(k, v))
  return http.post(url, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}