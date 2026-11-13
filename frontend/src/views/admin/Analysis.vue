<template>
  <div class="page" v-loading="loading">
    <div class="toolbar">
      <h3 style="margin:0">整体成绩分析 · {{ data?.project?.name }}</h3>
      <span class="spacer"></span>
      <el-button @click="$router.push(`/admin/projects/${projectId}/scores`)">成绩列表</el-button>
    </div>

    <template v-if="data">
      <div class="stat-grid">
        <div class="stat-item"><div class="num">{{ b.candidateCount }}</div><div class="label">考生总数</div></div>
        <div class="stat-item"><div class="num">{{ b.attendedCount }}</div><div class="label">实考人数</div></div>
        <div class="stat-item"><div class="num">{{ b.absentCount }}</div><div class="label">缺考人数</div></div>
        <div class="stat-item"><div class="num">{{ b.attendanceRate }}%</div><div class="label">到场率</div></div>
        <div class="stat-item"><div class="num">{{ b.avgScore ?? '-' }}</div><div class="label">平均分</div></div>
        <div class="stat-item"><div class="num">{{ b.maxScore ?? '-' }}</div><div class="label">最高分</div></div>
        <div class="stat-item"><div class="num">{{ b.minScore ?? '-' }}</div><div class="label">最低分</div></div>
        <div class="stat-item"><div class="num">{{ b.stdDev ?? '-' }}</div><div class="label">标准差</div></div>
        <div class="stat-item"><div class="num">{{ b.passCount }}</div><div class="label">及格人数</div></div>
        <div class="stat-item"><div class="num">{{ b.passRate }}%</div><div class="label">及格率</div></div>
        <div class="stat-item"><div class="num">{{ b.shortlistedCount }}</div><div class="label">入围人数</div></div>
        <div class="stat-item"><div class="num">{{ b.passScore }}</div><div class="label">及格线</div></div>
      </div>

      <el-row :gutter="14" style="margin-top:14px">
        <el-col :span="12">
          <div class="card"><h4>分数分布（按得分率）</h4><div ref="distChart" style="height:300px"></div></div>
        </el-col>
        <el-col :span="12">
          <div class="card"><h4>及格情况</h4><div ref="passChart" style="height:300px"></div></div>
        </el-col>
      </el-row>

      <div class="card">
        <h4>逐题难度与区分度</h4>
        <div class="muted" style="margin-bottom:8px">
          难度 = 平均得分率（越低越难）；区分度 = 高分组(前27%)与低分组(后27%)得分率之差（越高越能区分考生水平，≥0.4 优秀，0.3~0.39 良好，&lt;0.2 需改进）
        </div>
        <el-table :data="data.questionStats" border stripe size="small">
          <el-table-column prop="questionId" label="题号" width="70" />
          <el-table-column prop="type" label="题型" width="90" />
          <el-table-column prop="content" label="题目" min-width="260" show-overflow-tooltip />
          <el-table-column prop="fullScore" label="满分" width="70" />
          <el-table-column label="难度(得分率)" width="130">
            <template #default="{ row }">
              <el-progress v-if="row.difficulty != null" :percentage="Math.round(row.difficulty * 100)"
                           :stroke-width="10" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="区分度" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.discrimination != null" size="small"
                      :type="row.discrimination >= 0.4 ? 'success' : row.discrimination >= 0.3 ? 'warning' : 'danger'">
                {{ row.discrimination }}
              </el-tag>
              <span v-else class="muted">样本不足</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { projectAnalysis } from '../../api'

const route = useRoute()
const projectId = route.params.id
const loading = ref(false)
const data = ref(null)
const b = computed(() => data.value?.basic || {})
const distChart = ref(null)
const passChart = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    data.value = await projectAnalysis(projectId)
    await nextTick()
    const dist = echarts.init(distChart.value)
    dist.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.distribution.map(d => d.label) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ type: 'bar', data: data.value.distribution.map(d => d.count),
        itemStyle: { color: '#409eff' }, barWidth: '45%', label: { show: true, position: 'top' } }]
    })
    const pass = echarts.init(passChart.value)
    const passCount = data.value.basic.passCount || 0
    const failCount = (data.value.basic.gradedCount || 0) - passCount
    pass.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['40%', '65%'],
        data: [
          { name: '及格', value: passCount, itemStyle: { color: '#67c23a' } },
          { name: '不及格', value: failCount, itemStyle: { color: '#f56c6c' } }
        ],
        label: { formatter: '{b}: {c}人 ({d}%)' } }]
    })
    window.addEventListener('resize', () => { dist.resize(); pass.resize() })
  } finally { loading.value = false }
})
</script>