<template>
  <div class="page-container">
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">QMS&nbsp;IPQC · 过程检验</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>PASS <b>{{ passCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>ABNORMAL <b>{{ abnormalCount }}</b></span>
          <span class="li"><i class="led-dot led-run"></i>PENDING <b>{{ pendingCount }}</b></span>
          <span class="li"><i class="led-dot led-warn"></i>OEE <b>{{ oee }}%</b></span>
        </div>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button type="warning" :icon="Plus" @click="handleAdd">
          <span class="mono">NEW&nbsp;PATROL</span>
        </el-button>
        <el-button type="warning" :icon="Download" @click="handleExport">
          <span class="mono">EXPORT</span>
        </el-button>
      </div>
    </div>

    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><Monitor /></el-icon><span>模块</span><b>IPQC</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>MES同步</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-run"></i><span>产线监控</span><b>RUN</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>巡检总数</span><b>{{ list.length }}</b></div>
      <div class="tb-item"><span>不良率</span><b>{{ defectRate }}%</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>本页显示</span><b>{{ filteredList.length }}</b></div>
    </div>

    <div class="card-box panel-warn">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> QUERY&nbsp;CONDITION · 检索条件
      </div>

      <div class="filter-bar">
        <el-form :inline="true" :model="query" size="default">
          <el-form-item label="工单号">
            <el-input v-model="query.orderNo" placeholder="工单号" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item label="工序">
            <el-input v-model="query.process" placeholder="工序名称" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item label="产线">
            <el-input v-model="query.line" placeholder="产线编号" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item label="检验结果">
            <el-select v-model="query.result" placeholder="全部" clearable style="width: 120px">
              <el-option label="合格" value="PASS" />
              <el-option label="异常" value="ABNORMAL" />
              <el-option label="待检" value="PENDING" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch"><span class="mono">▶ GO</span></el-button>
            <el-button :icon="RefreshLeft" @click="handleReset">
              <span class="mono">RST</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        :data="filteredList"
        class="ind-table"
        stripe
      >
        <el-table-column label="WO NO / 工单号" width="160">
          <template #default="{ row }"><span class="mono tid">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="STATUS" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'PASS'" type="success" size="small" effect="dark">
              <i class="led-dot led-on"></i>&nbsp; PASS
            </el-tag>
            <el-tag v-else-if="row.result === 'ABNORMAL'" type="danger" size="small" effect="dark">
              <i class="led-dot led-off"></i>&nbsp; ABN
            </el-tag>
            <el-tag v-else type="info" size="small" effect="dark">
              <i class="led-dot led-run"></i>&nbsp; PENDING
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCode" label="产品编码" width="130">
          <template #default="{ row }"><span class="mono">{{ row.productCode }}</span></template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column prop="process" label="工序" width="110" />
        <el-table-column prop="line" label="产线" width="90" />
        <el-table-column prop="shift" label="班次" width="80" align="center" />
        <el-table-column prop="sampleSize" label="抽检数" width="90" align="right">
          <template #default="{ row }"><span class="mono">{{ row.sampleSize }}</span></template>
        </el-table-column>
        <el-table-column prop="defectCount" label="不良数" width="80" align="right">
          <template #default="{ row }">
            <span class="mono" :class="{ danger: row.defectCount > 0 }">{{ row.defectCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="不良率" width="100" align="right">
          <template #default="{ row }">
            <span class="mono" :class="{ warn: (row.defectCount / row.sampleSize * 100) >= 2, danger: (row.defectCount / row.sampleSize * 100) >= 5 }">
              {{ (row.defectCount / row.sampleSize * 100).toFixed(2) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="inspector" label="检验员" width="90" />
        <el-table-column label="检验时间" width="160">
          <template #default="{ row }"><span class="mono">{{ row.inspectTime }}</span></template>
        </el-table-column>
        <el-table-column label="动作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="warning" size="small" @click="handleView(row)">详情</el-button>
            <el-button type="warning" size="small" @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer flex-between mt-16" style="justify-content: flex-end">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Plus, Download, Search, RefreshLeft, Filter } from '@element-plus/icons-vue'

interface IpqcRecord {
  orderNo: string
  productCode: string
  productName: string
  process: string
  line: string
  shift: string
  sampleSize: number
  defectCount: number
  result: 'PASS' | 'ABNORMAL' | 'PENDING'
  inspector: string
  inspectTime: string
}

const query = reactive({ orderNo: '', process: '', line: '', result: '' })
const page = reactive({ current: 1, size: 10, total: 0 })

const list = ref<IpqcRecord[]>([
  { orderNo: 'WO20260815001', productCode: 'P-A001', productName: '智能终端 X1', process: '组装', line: 'A线', shift: '白', sampleSize: 100, defectCount: 1, result: 'PASS', inspector: '王工', inspectTime: '2026-08-15 08:30:12' },
  { orderNo: 'WO20260815001', productCode: 'P-A001', productName: '智能终端 X1', process: '焊接', line: 'A线', shift: '白', sampleSize: 100, defectCount: 5, result: 'ABNORMAL', inspector: '王工', inspectTime: '2026-08-15 10:15:42' },
  { orderNo: 'WO20260815002', productCode: 'P-B003', productName: '控制板 V3', process: '测试', line: 'B线', shift: '白', sampleSize: 80, defectCount: 0, result: 'PASS', inspector: '李工', inspectTime: '2026-08-15 11:22:08' },
  { orderNo: 'WO20260815003', productCode: 'P-C010', productName: '电源模块', process: '灌封', line: 'C线', shift: '白', sampleSize: 50, defectCount: 3, result: 'ABNORMAL', inspector: '张工', inspectTime: '2026-08-15 13:05:55' },
  { orderNo: 'WO20260815004', productCode: 'P-A001', productName: '智能终端 X1', process: '总装', line: 'A线', shift: '夜', sampleSize: 100, defectCount: 2, result: 'PASS', inspector: '陈工', inspectTime: '2026-08-15 20:18:33' },
  { orderNo: 'WO20260815005', productCode: 'P-D005', productName: '传感器组件', process: '校准', line: 'D线', shift: '夜', sampleSize: 60, defectCount: 0, result: 'PENDING', inspector: '陈工', inspectTime: '2026-08-15 21:42:19' },
  { orderNo: 'WO20260815006', productCode: 'P-B003', productName: '控制板 V3', process: '组装', line: 'B线', shift: '夜', sampleSize: 80, defectCount: 1, result: 'PASS', inspector: '李工', inspectTime: '2026-08-15 22:55:01' },
])

const passCount = computed(() => list.value.filter(r => r.result === 'PASS').length)
const abnormalCount = computed(() => list.value.filter(r => r.result === 'ABNORMAL').length)
const pendingCount = computed(() => list.value.filter(r => r.result === 'PENDING').length)
const oee = computed(() => {
  const total = list.value.reduce((s, r) => s + r.sampleSize, 0)
  const def = list.value.reduce((s, r) => s + r.defectCount, 0)
  return total ? Math.round((1 - def / total) * 1000) / 10 : 0
})
const defectRate = computed(() => {
  const total = list.value.reduce((s, r) => s + r.sampleSize, 0)
  const def = list.value.reduce((s, r) => s + r.defectCount, 0)
  return total ? Math.round(def / total * 1000) / 10 : 0
})

const filteredList = computed(() => {
  let data = list.value
  if (query.orderNo) data = data.filter(r => r.orderNo.includes(query.orderNo))
  if (query.process) data = data.filter(r => r.process.includes(query.process))
  if (query.line) data = data.filter(r => r.line.includes(query.line))
  if (query.result) data = data.filter(r => r.result === query.result)
  page.total = data.length
  const start = (page.current - 1) * page.size
  return data.slice(start, start + page.size)
})

const handleSearch = () => { page.current = 1 }
const handleReset = () => { Object.assign(query, { orderNo: '', process: '', line: '', result: '' }); page.current = 1 }
const handleAdd = () => ElMessage.info('新建巡检记录功能开发中')
const handleExport = () => ElMessage.success('已导出当前列表数据')
const handleView = (row: IpqcRecord) => ElMessage.info(`查看巡检记录 ${row.orderNo} - ${row.process}`)
const handleEdit = (row: IpqcRecord) => ElMessage.info(`编辑巡检记录 ${row.orderNo} - ${row.process}`)
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

.ph-sub {
  margin-top: 6px;
  font-size: 12px;
  color: v.$text-secondary;
  letter-spacing: 0.14em;
  display: flex; align-items: center; gap: 18px;
  .li { display: inline-flex; align-items: center; gap: 6px; }
  .li b { color: v.$text-primary; font-weight: 700; }
}

.filter-bar {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: v.$panel-bg-deep;
  border: 1px solid v.$border-color;
  border-radius: 2px;
  :deep(.el-form-item) {
    margin-bottom: 8px;
    margin-right: 10px;
    :deep(.el-form-item__label) { font-size: 12px; letter-spacing: 0.04em; }
  }
}

.tid { color: v.$primary-color; letter-spacing: 0.04em; }
.danger { color: v.$danger-color !important; text-shadow: 0 0 5px rgba(255,61,90,0.5); font-weight: 600; }
.warn   { color: v.$warning-color !important; text-shadow: 0 0 5px rgba(255,176,32,0.5); }
.dltip { font-size: 11px; color: v.$text-secondary; margin-top: 2px; }

.table-footer {
  padding: 14px 4px 0;
  border-top: 1px dashed v.$border-color;
  margin-top: 12px !important;
}
</style>
