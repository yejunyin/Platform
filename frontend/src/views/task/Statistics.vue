<template>
  <div class="page-container">
    <!-- 页面头 -->
    <div class="page-header">
      <div class="ph-left">
        <h2 class="page-title">TASK&nbsp;ANALYTICS · 任务统计驾驶舱</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>DATA-SRC <b>REAL-TIME</b></span>
          <span class="li"><i class="led-dot led-run"></i>REPORT <b>DAY</b></span>
          <span class="li"><i class="led-dot led-on"></i>INDEX <b>READY</b></span>
          <span class="li"><i class="led-dot led-warn"></i>ALERT <b>{{ stats.overdueCount + stats.urgentCount }}</b></span>
        </div>
      </div>
      <div class="ph-right">
        <el-tag type="primary" effect="dark" class="mono">RANGE · {{ trendRange === '7' ? '7-DAY' : '30-DAY' }}</el-tag>
      </div>
    </div>

    <!-- 工业状态工具栏 -->
    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><DataAnalysis /></el-icon><span>单元</span><b>U-TASK</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>ETL</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-run"></i><span>IDX</span><b>RUN</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>吞吐</span><b>{{ throughput }}</b></div>
      <div class="tb-item"><span>效率</span><b>{{ stats.onTimeRate }}%</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>用户</span><b>{{ displayName }}</b></div>
      <div class="tb-item"><span>刷新</span><b>{{ lastRefresh }}</b></div>
      <el-button size="small" :icon="Refresh" @click="handleRefresh" style="margin-left:auto">
        <span class="mono">REFRESH</span>
      </el-button>
    </div>

    <!-- KPI 数据卡 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :span="6">
        <div class="stat-card card-primary">
          <div class="stat-label">PENDING / 待办</div>
          <div class="stat-value">{{ stats.pendingCount }}</div>
          <div class="stat-footer"><i class="led-dot led-run"></i>处理中 <b>{{ stats.processingCount }}</b></div>
          <el-icon class="stat-icon"><Tickets /></el-icon>
          <div class="sc-corner sc-tl">KP-01</div>
          <div class="sc-corner sc-br">CH-A</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-danger">
          <div class="stat-label">OVERDUE / 超时</div>
          <div class="stat-value">{{ stats.overdueCount }}</div>
          <div class="stat-footer"><i class="led-dot led-off"></i>紧急 <b>{{ stats.urgentCount }}</b></div>
          <el-icon class="stat-icon"><Warning /></el-icon>
          <div class="sc-corner sc-tl">KP-02</div>
          <div class="sc-corner sc-br">CH-B</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-success">
          <div class="stat-label">COMPLETED / 累计完成</div>
          <div class="stat-value">{{ stats.completedCount }}</div>
          <div class="stat-footer"><i class="led-dot led-on"></i>今日 <b>{{ stats.todayCompletedCount }}</b></div>
          <el-icon class="stat-icon"><Finished /></el-icon>
          <div class="sc-corner sc-tl">KP-03</div>
          <div class="sc-corner sc-br">CH-C</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-warn">
          <div class="stat-label">ONTIME / 按期完成率</div>
          <div class="stat-value">{{ stats.onTimeRate }}<small>%</small></div>
          <div class="stat-footer"><i class="led-dot led-warn"></i>均值 <b>{{ formatDuration(stats.avgHandleDuration) }}</b></div>
          <el-icon class="stat-icon"><Trophy /></el-icon>
          <div class="sc-corner sc-tl">KP-04</div>
          <div class="sc-corner sc-br">CH-D</div>
        </div>
      </el-col>
    </el-row>

    <!-- 第一排图表：状态 + 来源 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :span="12">
        <div class="card-box panel-primary">
          <div class="industrial-panel__title">
            <PieIcon /> TASK&nbsp;STATUS · 任务状态分布
            <div style="flex:1"></div>
            <div class="tags-1">
              <span class="tt-item"><i class="led-dot led-run"></i>实时</span>
              <span class="tt-item mono">N={{ stats.pendingCount + stats.processingCount + stats.completedCount }}</span>
            </div>
          </div>
          <v-chart class="chart" :option="statusOption" autoresize />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card-box panel-success">
          <div class="industrial-panel__title success">
            <Promotion /> SOURCE · 任务来源分布
            <div style="flex:1"></div>
            <div class="tags-1">
              <span class="tt-item"><i class="led-dot led-on"></i>4 通道</span>
              <span class="tt-item mono">SUM=31</span>
            </div>
          </div>
          <v-chart class="chart" :option="sourceOption" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- 第二排：趋势 + 优先级 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :span="16">
        <div class="card-box panel-warn">
          <div class="industrial-panel__title warn">
            <Histogram /> TASK&nbsp;TREND · 近 {{ trendRange === '7' ? '7' : '30' }} 日任务处理趋势
            <div style="flex:1"></div>
            <el-radio-group v-model="trendRange" size="small">
              <el-radio-button value="7"><span class="mono">7D</span></el-radio-button>
              <el-radio-button value="30"><span class="mono">30D</span></el-radio-button>
            </el-radio-group>
          </div>
          <v-chart class="chart" :option="trendOption" autoresize />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="card-box panel-danger">
          <div class="industrial-panel__title danger">
            <Medal /> PRIORITY · 优先级分布
          </div>
          <v-chart class="chart" :option="priorityOption" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- 分类 -->
    <el-row :gutter="16">
      <el-col :span="24">
        <div class="card-box">
          <div class="industrial-panel__title">
            <DataAnalysis /> CATEGORY · 任务分类统计 (待办 / 完成 / 超时)
            <div style="flex:1"></div>
            <div class="tags-1">
              <span class="tt-item mono">CATS=7</span>
              <span class="tt-item"><i class="led-dot led-on"></i>OK</span>
            </div>
          </div>
          <v-chart class="chart" style="height: 320px" :option="categoryOption" autoresize />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { useUserStore } from '@/stores/user'
import { taskApi, TaskStatistics } from '@/api/task'
import dayjs from 'dayjs'
import { registerECharts } from '@/plugins/echarts'
import { Tickets, Warning, Finished, Trophy, PieChart as PieIcon, Promotion, Histogram, Medal, DataAnalysis, Refresh } from '@element-plus/icons-vue'

registerECharts()

const userStore = useUserStore()
const trendRange = ref('7')
const lastRefresh = ref<string>(dayjs().format('HH:mm:ss'))
const throughput = computed(() => Math.round(stats.value.completedCount * 2.3 + 4))
const displayName = computed(() => (userStore.userInfo && userStore.userInfo.realName) ? userStore.userInfo.realName : 'ADMIN')

// 工业风颜色板（阴极蓝 / 青柠 / 琥珀 / 工业红）
const C_PRIMARY = '#1ab3ff'
const C_SUCCESS = '#00ff94'
const C_WARNING = '#ffb020'
const C_DANGER  = '#ff3d5a'
const C_INFO    = '#7ab8ff'
const C_PURPLE  = '#a78bfa'
const C_GRID    = '#1a2a42'
const C_TEXT    = '#b8c6d9'
const C_TEXT_2  = '#7a8da6'

const stats = ref<TaskStatistics>({
  userId: 1, pendingCount: 4, processingCount: 1, completedCount: 23,
  overdueCount: 0, urgentCount: 1, unreadCount: 3, todayNewCount: 2,
  todayCompletedCount: 1, avgHandleDuration: 3300, onTimeRate: 95.5
})

const formatDuration = (sec: number) => {
  if (!sec) return '--'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return h > 0 ? `${h}h${m}m` : `${m}min`
}

const handleRefresh = () => {
  lastRefresh.value = dayjs().format('HH:mm:ss')
  loadStats()
}

const loadStats = async () => {
  try {
    const res = await taskApi.getStatistics(userStore.userInfo.id)
    if (res.code === 200) stats.value = res.data
  } catch (e) {}
}

// ===== 通用 base：暗色底 + 工业字 =====
const baseTextStyle = {
  color: C_TEXT,
  fontFamily: "'Rajdhani', 'JetBrains Mono', 'Microsoft YaHei', sans-serif",
}
const axisStyle = (isY = false) => ({
  axisLine: { lineStyle: { color: C_GRID } },
  axisLabel: { color: C_TEXT_2, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 },
  splitLine: { lineStyle: { color: isY ? C_GRID : 'transparent', type: 'dashed' as const } },
  axisTick: { show: false },
})

// 饼：任务状态
const statusOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: baseTextStyle,
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} ({d}%)',
    backgroundColor: '#131c28',
    borderColor: '#243348',
    borderWidth: 1,
    textStyle: { color: '#e6f1ff', fontFamily: "'JetBrains Mono', monospace" },
  },
  legend: {
    bottom: 0, icon: 'circle',
    textStyle: { color: C_TEXT_2, fontSize: 12 },
  },
  color: [C_WARNING, C_PRIMARY, C_SUCCESS, C_DANGER, C_GRID, C_PURPLE],
  series: [{
    name: '任务状态',
    type: 'pie',
    radius: ['48%', '75%'],
    avoidLabelOverlap: true,
    itemStyle: { borderColor: '#0d131d', borderWidth: 3 },
    label: {
      color: '#e6f1ff', fontSize: 12,
      fontFamily: "'JetBrains Mono', monospace",
      formatter: '{b}\n{d}%',
    },
    labelLine: { lineStyle: { color: C_GRID } },
    emphasis: {
      scaleSize: 6,
      label: { color: '#fff', fontSize: 13, fontWeight: 700 },
      itemStyle: { shadowBlur: 16, shadowColor: C_PRIMARY },
    },
    data: [
      { value: stats.value.pendingCount, name: '待处理' },
      { value: stats.value.processingCount, name: '处理中' },
      { value: stats.value.completedCount, name: '已完成' },
      { value: 2, name: '已驳回' },
      { value: 1, name: '已撤销' },
      { value: stats.value.overdueCount, name: '已超时' },
    ],
  }],
}))

// 玫瑰：来源
const sourceOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: baseTextStyle,
  tooltip: {
    trigger: 'item', formatter: '{b}: {c} ({d}%)',
    backgroundColor: '#131c28', borderColor: '#243348', borderWidth: 1,
    textStyle: { color: '#e6f1ff', fontFamily: "'JetBrains Mono', monospace" },
  },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: C_TEXT_2, fontSize: 12 } },
  color: [C_PRIMARY, C_SUCCESS, C_WARNING, C_PURPLE],
  series: [{
    name: '来源系统',
    type: 'pie',
    radius: ['10%', '75%'],
    center: ['50%', '46%'],
    roseType: 'radius',
    itemStyle: { borderColor: '#0d131d', borderWidth: 2, shadowBlur: 8, shadowColor: 'rgba(26,179,255,0.25)' },
    label: {
      color: C_TEXT, fontSize: 12,
      fontFamily: "'JetBrains Mono', monospace",
      formatter: '{b}: {c}',
    },
    labelLine: { lineStyle: { color: C_GRID } },
    data: [
      { value: 10, name: 'OA' },
      { value: 8, name: 'ERP' },
      { value: 4, name: 'MES' },
      { value: 9, name: 'Enterprise Brain' },
    ],
  }],
}))

// 面积线：7/30 日趋势
const trendOption = computed(() => {
  const days = Number(trendRange.value)
  const dates = Array.from({ length: days }, (_, i) => dayjs().subtract(days - 1 - i, 'day').format('MM-DD'))
  const receiveData = dates.map((_, i) => Math.floor(Math.random() * 5) + 1 + (days === 7 ? 1 : 0))
  const completeData = dates.map((_, i) => Math.floor(Math.random() * 4) + 1)
  return {
    backgroundColor: 'transparent',
    textStyle: baseTextStyle,
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#131c28', borderColor: '#243348', borderWidth: 1,
      textStyle: { color: '#e6f1ff', fontFamily: "'JetBrains Mono', monospace" },
    },
    legend: {
      data: ['新增任务', '完成任务'],
      top: 0,
      textStyle: { color: C_TEXT_2, fontSize: 12 },
    },
    grid: { left: 48, right: 24, top: 44, bottom: 30 },
    xAxis: { type: 'category', data: dates, boundaryGap: false, ...axisStyle() },
    yAxis: { type: 'value', minInterval: 1, ...axisStyle(true) },
    series: [
      {
        name: '新增任务',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: receiveData,
        lineStyle: { color: C_PRIMARY, width: 3, shadowBlur: 8, shadowColor: C_PRIMARY },
        itemStyle: { color: C_PRIMARY, borderColor: '#0d131d', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(26,179,255,0.45)' },
              { offset: 1, color: 'rgba(26,179,255,0.01)' },
            ],
          },
        },
      },
      {
        name: '完成任务',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: completeData,
        lineStyle: { color: C_SUCCESS, width: 3, shadowBlur: 8, shadowColor: C_SUCCESS },
        itemStyle: { color: C_SUCCESS, borderColor: '#0d131d', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(0,255,148,0.38)' },
              { offset: 1, color: 'rgba(0,255,148,0.01)' },
            ],
          },
        },
      },
    ],
  }
})

// 横向条形：优先级
const priorityOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: baseTextStyle,
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' as const },
    backgroundColor: '#131c28', borderColor: '#243348', borderWidth: 1,
    textStyle: { color: '#e6f1ff', fontFamily: "'JetBrains Mono', monospace" },
  },
  grid: { left: 56, right: 44, top: 16, bottom: 24 },
  xAxis: { type: 'value', minInterval: 1, ...axisStyle(true) },
  yAxis: {
    type: 'category',
    data: ['URGENT', 'HIGH', 'MEDIUM', 'LOW'],
    ...axisStyle(),
    axisLabel: { color: C_TEXT, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 },
  },
  series: [{
    type: 'bar',
    data: [
      { value: 1, itemStyle: { color: C_DANGER,  borderRadius: [0, 4, 4, 0], shadowBlur: 10, shadowColor: C_DANGER } },
      { value: 8, itemStyle: { color: C_WARNING, borderRadius: [0, 4, 4, 0], shadowBlur: 10, shadowColor: C_WARNING } },
      { value: 12, itemStyle: { color: C_PRIMARY, borderRadius: [0, 4, 4, 0], shadowBlur: 10, shadowColor: C_PRIMARY } },
      { value: 7, itemStyle: { color: C_INFO,    borderRadius: [0, 4, 4, 0], shadowBlur: 10, shadowColor: C_INFO } },
    ],
    barWidth: 24,
    label: {
      show: true,
      position: 'right' as const,
      color: C_TEXT,
      fontFamily: "'JetBrains Mono', monospace",
      fontSize: 12,
      fontWeight: 700,
      formatter: '{c}',
    },
  }],
}))

// 分类：多组柱
const categoryOption = computed(() => ({
  backgroundColor: 'transparent',
  textStyle: baseTextStyle,
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' as const },
    backgroundColor: '#131c28', borderColor: '#243348', borderWidth: 1,
    textStyle: { color: '#e6f1ff', fontFamily: "'JetBrains Mono', monospace" },
  },
  legend: { top: 0, data: ['待办', '完成', '超时'], textStyle: { color: C_TEXT_2, fontSize: 12 } },
  grid: { left: 48, right: 24, top: 46, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['请假', '报销', '采购', '生产', '质量', '合同', '其他'],
    ...axisStyle(),
    axisLabel: { color: C_TEXT_2, fontSize: 12 },
  },
  yAxis: { type: 'value', minInterval: 1, ...axisStyle(true) },
  series: [
    {
      name: '待办', type: 'bar', barWidth: 18,
      data: [1, 0, 1, 0, 1, 1, 0],
      itemStyle: { color: C_PRIMARY, borderRadius: [4, 4, 0, 0], shadowBlur: 6, shadowColor: 'rgba(26,179,255,0.4)' },
    },
    {
      name: '完成', type: 'bar', barWidth: 18,
      data: [3, 2, 6, 4, 5, 2, 1],
      itemStyle: { color: C_SUCCESS, borderRadius: [4, 4, 0, 0], shadowBlur: 6, shadowColor: 'rgba(0,255,148,0.4)' },
    },
    {
      name: '超时', type: 'bar', barWidth: 18,
      data: [0, 0, 0, 0, 0, 0, 1],
      itemStyle: { color: C_DANGER, borderRadius: [4, 4, 0, 0], shadowBlur: 6, shadowColor: 'rgba(255,61,90,0.4)' },
    },
  ],
}))

onMounted(loadStats)
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as v;

.mono { font-family: v.$font-mono; }
.led-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: v.$text-secondary;
  &.led-on   { background: v.$success-color; box-shadow: v.$glow-success; }
  &.led-warn { background: v.$warning-color; box-shadow: v.$glow-warn; }
  &.led-off  { background: v.$danger-color;  box-shadow: v.$glow-danger; }
  &.led-run  { background: v.$primary-color; box-shadow: v.$glow-primary; animation: pulse 1.2s infinite; }
}
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity: 0.4; } }

.ph-left { display: flex; flex-direction: column; gap: 6px; }
.ph-sub {
  font-size: 12px;
  color: v.$text-secondary;
  letter-spacing: 0.14em;
  display: flex; align-items: center; gap: 18px;
  .li { display: inline-flex; align-items: center; gap: 6px; }
  .li b { color: v.$text-primary; font-weight: 700; }
}
.ph-right { display: flex; align-items: center; gap: 8px; }

.stat-card {
  .stat-value small { font-size: 18px; opacity: 0.8; margin-left: 2px; }
  .stat-footer b { color: v.$text-primary; font-family: v.$font-mono; }
}
.sc-corner {
  position: absolute;
  font-family: v.$font-mono;
  font-size: 10px;
  letter-spacing: 0.1em;
  color: rgba(26,179,255,0.55);
  pointer-events: none;
}
.sc-tl { top: 6px; left: 10px; }
.sc-br { right: 10px; bottom: 6px; }

.tags-1 { display: inline-flex; gap: 8px; margin-right: 10px; }
.tt-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-family: v.$font-mono; font-size: 11px; letter-spacing: 0.1em;
  color: v.$text-secondary; padding: 2px 8px;
  border: 1px dashed v.$border-color; border-radius: 2px;
}

.chart { width: 100%; height: 320px; }
</style>
