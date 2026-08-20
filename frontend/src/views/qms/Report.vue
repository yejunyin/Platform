<template>
  <div class="qms-page">
    <el-card shadow="never" class="stat-cards">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-pass">
            <div class="stat-label">来料合格率</div>
            <div class="stat-value">98.5%</div>
            <div class="stat-trend up">↑ 0.3%</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-process">
            <div class="stat-label">过程合格率</div>
            <div class="stat-value">96.2%</div>
            <div class="stat-trend down">↓ 0.8%</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-fqc">
            <div class="stat-label">成品合格率</div>
            <div class="stat-value">99.1%</div>
            <div class="stat-trend up">↑ 0.5%</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card stat-ncr">
            <div class="stat-label">未处置不合格</div>
            <div class="stat-value">3</div>
            <div class="stat-trend">待处理</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="chart-section">
      <template #header>
        <div class="section-header">
          <div class="title">
            <el-icon><TrendCharts /></el-icon>
            <span>质量趋势分析</span>
          </div>
          <el-radio-group v-model="trendRange" size="small">
            <el-radio-button label="week">近7天</el-radio-button>
            <el-radio-button label="month">近30天</el-radio-button>
            <el-radio-button label="quarter">近3月</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <v-chart class="chart" :option="trendOption" autoresize />
    </el-card>

    <el-row :gutter="16" class="dual-charts">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="section-header">
              <div class="title"><span>不良类型分布</span></div>
            </div>
          </template>
          <v-chart class="chart small" :option="defectPieOption" autoresize />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="section-header">
              <div class="title"><span>供应商质量排名 (Top5)</span></div>
            </div>
          </template>
          <v-chart class="chart small" :option="supplierBarOption" autoresize />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import VChart from 'vue-echarts'
import { registerECharts } from '@/plugins/echarts'
import { TrendCharts } from '@element-plus/icons-vue'

registerECharts()

const trendRange = ref<'week' | 'month' | 'quarter'>('week')

const trendData = {
  week: {
    dates: ['08-09', '08-10', '08-11', '08-12', '08-13', '08-14', '08-15'],
    iqc: [98.2, 98.8, 97.5, 99.0, 98.4, 98.6, 98.5],
    ipqc: [97.1, 96.5, 96.8, 95.8, 96.2, 96.9, 96.2],
    fqc: [98.7, 99.2, 98.9, 99.3, 99.0, 99.1, 99.1],
  },
  month: {
    dates: ['07-16', '07-21', '07-26', '07-31', '08-05', '08-10', '08-15'],
    iqc: [97.8, 98.1, 98.5, 98.3, 98.6, 98.4, 98.5],
    ipqc: [95.5, 96.0, 96.3, 96.1, 96.5, 96.8, 96.2],
    fqc: [98.9, 99.0, 99.1, 99.2, 99.0, 99.1, 99.1],
  },
  quarter: {
    dates: ['05月', '06月', '07月', '08月'],
    iqc: [97.5, 98.0, 98.3, 98.5],
    ipqc: [95.2, 95.8, 96.1, 96.2],
    fqc: [98.5, 98.8, 99.0, 99.1],
  },
}

const trendOption = computed(() => {
  const data = trendData[trendRange.value]
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['来料合格率', '过程合格率', '成品合格率'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: data.dates },
    yAxis: { type: 'value', min: 92, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      { name: '来料合格率', type: 'line', smooth: true, data: data.iqc, itemStyle: { color: '#409eff' } },
      { name: '过程合格率', type: 'line', smooth: true, data: data.ipqc, itemStyle: { color: '#67c23a' } },
      { name: '成品合格率', type: 'line', smooth: true, data: data.fqc, itemStyle: { color: '#e6a23c' } },
    ],
  }
})

const defectPieOption = {
  tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'middle' },
  series: [{
    name: '不良类型',
    type: 'pie',
    radius: ['40%', '70%'],
    center: ['60%', '50%'],
    avoidLabelOverlap: false,
    label: { show: false, position: 'center' },
    emphasis: { label: { show: true, fontSize: '16', fontWeight: 'bold' } },
    labelLine: { show: false },
    data: [
      { value: 35, name: '外观不良' },
      { value: 28, name: '尺寸超差' },
      { value: 18, name: '功能失效' },
      { value: 12, name: '短路' },
      { value: 7, name: '其他' },
    ],
  }],
}

const supplierBarOption = {
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'value', min: 95, max: 100, axisLabel: { formatter: '{value}%' } },
  yAxis: { type: 'category', data: ['东莞橡塑', '深圳华星', '宁波机电', '苏州铝业', '上海精工'] },
  series: [{
    name: '合格率',
    type: 'bar',
    data: [96.5, 97.2, 98.3, 98.8, 99.2],
    itemStyle: {
      color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [
        { offset: 0, color: '#409eff' }, { offset: 1, color: '#67c23a' }
      ] }
    },
    label: { show: true, position: 'right', formatter: '{c}%' },
  }],
}
</script>

<style lang="scss" scoped>
.qms-page {
  padding: 16px;

  .stat-cards {
    margin-bottom: 16px;
  }

  .stat-card {
    text-align: center;
    padding: 12px 0;

    .stat-label {
      font-size: 13px;
      color: #909399;
      margin-bottom: 8px;
    }

    .stat-value {
      font-size: 28px;
      font-weight: 700;
      color: #303133;
    }

    .stat-trend {
      font-size: 12px;
      margin-top: 6px;
      color: #909399;

      &.up { color: #67c23a; }
      &.down { color: #f56c6c; }
    }
  }

  .chart-section, .dual-charts .el-card {
    margin-bottom: 16px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
    }
  }

  .chart {
    height: 320px;

    &.small {
      height: 280px;
    }
  }

  .dual-charts {
    margin-bottom: 0;
  }
}
</style>
