<template>
  <div class="page-container">
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">QMS&nbsp;FQC · 成品检验</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>PASS <b>{{ passCount }}</b></span>
          <span class="li"><i class="led-dot led-warn"></i>CONCESSION <b>{{ concessionCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>FAIL <b>{{ failCount }}</b></span>
          <span class="li"><i class="led-dot led-run"></i>PENDING <b>{{ pendingCount }}</b></span>
        </div>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button type="success" :icon="Plus" @click="handleAdd">
          <span class="mono">NEW&nbsp;ORDER</span>
        </el-button>
        <el-button type="success" :icon="Download" @click="handleExport">
          <span class="mono">EXPORT</span>
        </el-button>
      </div>
    </div>

    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><Files /></el-icon><span>模块</span><b>FQC</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>WMS同步</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-run"></i><span>入库审核</span><b>RUN</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>批次总数</span><b>{{ list.length }}</b></div>
      <div class="tb-item"><span>合格率</span><b>{{ passRate }}%</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>本页显示</span><b>{{ filteredList.length }}</b></div>
    </div>

    <div class="card-box panel-success">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> QUERY&nbsp;CONDITION · 检索条件
      </div>

      <div class="filter-bar">
        <el-form :inline="true" :model="query" size="default">
          <el-form-item label="检验单号">
            <el-input v-model="query.fqcNo" placeholder="检验单号" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="产品名称">
            <el-input v-model="query.productName" placeholder="产品名称" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="检验结果">
            <el-select v-model="query.result" placeholder="全部" clearable style="width: 120px">
              <el-option label="合格" value="PASS" />
              <el-option label="让步接收" value="CONCESSION" />
              <el-option label="不合格" value="FAIL" />
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
        <el-table-column label="FQC NO / 单号" width="160">
          <template #default="{ row }"><span class="mono tid">{{ row.fqcNo }}</span></template>
        </el-table-column>
        <el-table-column label="STATUS" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'PASS'" type="success" size="small" effect="dark">
              <i class="led-dot led-on"></i>&nbsp; PASS
            </el-tag>
            <el-tag v-else-if="row.result === 'CONCESSION'" type="warning" size="small" effect="dark">
              <i class="led-dot led-warn"></i>&nbsp; CONC
            </el-tag>
            <el-tag v-else-if="row.result === 'FAIL'" type="danger" size="small" effect="dark">
              <i class="led-dot led-off"></i>&nbsp; FAIL
            </el-tag>
            <el-tag v-else type="info" size="small" effect="dark">
              <i class="led-dot led-run"></i>&nbsp; PENDING
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="工单号" width="150">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="productCode" label="产品编码" width="130">
          <template #default="{ row }"><span class="mono">{{ row.productCode }}</span></template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="170" />
        <el-table-column prop="batchNo" label="批次号" width="140">
          <template #default="{ row }"><span class="mono">{{ row.batchNo }}</span></template>
        </el-table-column>
        <el-table-column prop="quantity" label="入库数量" width="100" align="right">
          <template #default="{ row }"><span class="mono">{{ row.quantity }}</span></template>
        </el-table-column>
        <el-table-column prop="sampleSize" label="抽检数" width="90" align="right">
          <template #default="{ row }"><span class="mono">{{ row.sampleSize }}</span></template>
        </el-table-column>
        <el-table-column prop="defectCount" label="不良数" width="80" align="right">
          <template #default="{ row }">
            <span class="mono" :class="{ danger: row.defectCount > 0 }">{{ row.defectCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inspector" label="检验员" width="90" />
        <el-table-column label="检验时间" width="160">
          <template #default="{ row }"><span class="mono">{{ row.inspectTime }}</span></template>
        </el-table-column>
        <el-table-column label="动作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="handleView(row)">详情</el-button>
            <el-button type="success" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Files, Plus, Download, Search, RefreshLeft, Filter } from '@element-plus/icons-vue'

interface FqcRecord {
  fqcNo: string
  orderNo: string
  productCode: string
  productName: string
  batchNo: string
  quantity: number
  sampleSize: number
  defectCount: number
  result: 'PASS' | 'CONCESSION' | 'FAIL' | 'PENDING'
  inspector: string
  inspectTime: string
}

const query = reactive({ fqcNo: '', productName: '', result: '' })
const page = reactive({ current: 1, size: 10, total: 0 })

const list = ref<FqcRecord[]>([
  { fqcNo: 'FQC20260815001', orderNo: 'WO20260815001', productCode: 'P-A001', productName: '智能终端 X1', batchNo: 'B20260815-A1', quantity: 1000, sampleSize: 100, defectCount: 2, result: 'PASS', inspector: '王工', inspectTime: '2026-08-15 16:30:00' },
  { fqcNo: 'FQC20260815002', orderNo: 'WO20260815002', productCode: 'P-B003', productName: '控制板 V3', batchNo: 'B20260815-B1', quantity: 800, sampleSize: 80, defectCount: 6, result: 'CONCESSION', inspector: '李工', inspectTime: '2026-08-15 17:15:42' },
  { fqcNo: 'FQC20260815003', orderNo: 'WO20260815003', productCode: 'P-C010', productName: '电源模块', batchNo: 'B20260815-C1', quantity: 500, sampleSize: 50, defectCount: 8, result: 'FAIL', inspector: '张工', inspectTime: '2026-08-15 18:05:33' },
  { fqcNo: 'FQC20260815004', orderNo: 'WO20260815004', productCode: 'P-A001', productName: '智能终端 X1', batchNo: 'B20260815-A2', quantity: 1200, sampleSize: 120, defectCount: 1, result: 'PASS', inspector: '陈工', inspectTime: '2026-08-15 19:22:18' },
  { fqcNo: 'FQC20260815005', orderNo: 'WO20260815005', productCode: 'P-D005', productName: '传感器组件', batchNo: 'B20260815-D1', quantity: 600, sampleSize: 60, defectCount: 0, result: 'PENDING', inspector: '李工', inspectTime: '2026-08-15 20:48:55' },
])

const passCount = computed(() => list.value.filter(r => r.result === 'PASS').length)
const concessionCount = computed(() => list.value.filter(r => r.result === 'CONCESSION').length)
const failCount = computed(() => list.value.filter(r => r.result === 'FAIL').length)
const pendingCount = computed(() => list.value.filter(r => r.result === 'PENDING').length)
const passRate = computed(() => list.value.length ? Math.round(passCount.value / list.value.length * 1000) / 10 : 0)

const filteredList = computed(() => {
  let data = list.value
  if (query.fqcNo) data = data.filter(r => r.fqcNo.includes(query.fqcNo))
  if (query.productName) data = data.filter(r => r.productName.includes(query.productName))
  if (query.result) data = data.filter(r => r.result === query.result)
  page.total = data.length
  const start = (page.current - 1) * page.size
  return data.slice(start, start + page.size)
})

const handleSearch = () => { page.current = 1 }
const handleReset = () => { Object.assign(query, { fqcNo: '', productName: '', result: '' }); page.current = 1 }
const handleAdd = () => ElMessage.info('新建检验单功能开发中')
const handleExport = () => ElMessage.success('已导出当前列表数据')
const handleView = (row: FqcRecord) => ElMessage.info(`查看检验单 ${row.fqcNo}`)
const handleEdit = (row: FqcRecord) => ElMessage.info(`编辑检验单 ${row.fqcNo}`)
const handleDelete = (row: FqcRecord) => {
  ElMessageBox.confirm(`确定删除检验单 ${row.fqcNo} 吗？`, '提示', { type: 'warning' })
    .then(() => {
      const idx = list.value.findIndex(r => r.fqcNo === row.fqcNo)
      if (idx >= 0) list.value.splice(idx, 1)
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}
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
