<template>
  <div class="page-container">
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">QMS&nbsp;NCR · 不合格品处理</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-warn"></i>PENDING <b>{{ pendingCount }}</b></span>
          <span class="li"><i class="led-dot led-run"></i>PROCESSING <b>{{ processingCount }}</b></span>
          <span class="li"><i class="led-dot led-on"></i>CLOSED <b>{{ closedCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>SCRAP <b>{{ scrapCount }}</b></span>
        </div>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button type="danger" :icon="Plus" @click="handleAdd">
          <span class="mono">NEW&nbsp;NCR</span>
        </el-button>
        <el-button type="danger" :icon="Download" @click="handleExport">
          <span class="mono">EXPORT</span>
        </el-button>
      </div>
    </div>

    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><WarningFilled /></el-icon><span>模块</span><b>NCR</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>系统状态</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-warn"></i><span>待处置告警</span><b>ALERT</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>不合格总数</span><b>{{ list.length }}</b></div>
      <div class="tb-item"><span>待处理率</span><b>{{ pendingRate }}%</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>本页显示</span><b>{{ filteredList.length }}</b></div>
    </div>

    <div class="card-box panel-danger">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> QUERY&nbsp;CONDITION · 检索条件
      </div>

      <div class="filter-bar">
        <el-form :inline="true" :model="query" size="default">
          <el-form-item label="单号">
            <el-input v-model="query.ncrNo" placeholder="不合格单号" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="来源">
            <el-select v-model="query.source" placeholder="全部" clearable style="width: 140px">
              <el-option label="来料检验" value="IQC" />
              <el-option label="过程检验" value="IPQC" />
              <el-option label="成品检验" value="FQC" />
              <el-option label="客户投诉" value="CUSTOMER" />
            </el-select>
          </el-form-item>
          <el-form-item label="处置状态">
            <el-select v-model="query.disposition" placeholder="全部" clearable style="width: 140px">
              <el-option label="待处理" value="PENDING" />
              <el-option label="返工" value="REWORK" />
              <el-option label="返修" value="REPAIR" />
              <el-option label="让步接收" value="CONCESSION" />
              <el-option label="报废" value="SCRAP" />
              <el-option label="退货" value="RETURN" />
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
        <el-table-column label="NCR NO / 单号" width="170">
          <template #default="{ row }"><span class="mono tid">{{ row.ncrNo }}</span></template>
        </el-table-column>
        <el-table-column label="STATUS" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.disposition === 'PENDING'" type="warning" size="small" effect="dark">
              <i class="led-dot led-warn"></i>&nbsp; PEND
            </el-tag>
            <el-tag v-else-if="row.disposition === 'REWORK' || row.disposition === 'REPAIR'" type="warning" size="small" effect="dark">
              <i class="led-dot led-run"></i>&nbsp; PROC
            </el-tag>
            <el-tag v-else-if="row.disposition === 'CONCESSION'" type="success" size="small" effect="dark">
              <i class="led-dot led-on"></i>&nbsp; CONC
            </el-tag>
            <el-tag v-else-if="row.disposition === 'SCRAP' || row.disposition === 'RETURN'" type="danger" size="small" effect="dark">
              <i class="led-dot led-off"></i>&nbsp; CLOSE
            </el-tag>
            <el-tag v-else type="info" size="small" effect="dark">
              <i class="led-dot led-run"></i>&nbsp; OTHER
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="sourceTagType(row.source)" effect="plain" size="small">
              <span class="mono">{{ row.source }}</span>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCode" label="产品编码" width="130">
          <template #default="{ row }"><span class="mono">{{ row.productCode }}</span></template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column prop="batchNo" label="批次号" width="140">
          <template #default="{ row }"><span class="mono">{{ row.batchNo }}</span></template>
        </el-table-column>
        <el-table-column prop="quantity" label="不合格数量" width="120" align="right">
          <template #default="{ row }">
            <span class="mono danger">{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="defectType" label="不良类型" width="120" />
        <el-table-column prop="defectDesc" label="不良描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="creator" label="登记人" width="90" />
        <el-table-column label="登记时间" width="160">
          <template #default="{ row }"><span class="mono">{{ row.createTime }}</span></template>
        </el-table-column>
        <el-table-column label="动作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="handleView(row)">详情</el-button>
            <el-button type="warning" size="small" :disabled="row.disposition !== 'PENDING'" @click="handleDispose(row)">处置</el-button>
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
import { WarningFilled, Plus, Download, Search, RefreshLeft, Filter } from '@element-plus/icons-vue'

interface NcrRecord {
  ncrNo: string
  source: 'IQC' | 'IPQC' | 'FQC' | 'CUSTOMER'
  productCode: string
  productName: string
  batchNo: string
  quantity: number
  defectType: string
  defectDesc: string
  disposition: 'PENDING' | 'REWORK' | 'REPAIR' | 'CONCESSION' | 'SCRAP' | 'RETURN'
  creator: string
  createTime: string
}

const query = reactive({ ncrNo: '', source: '', disposition: '' })
const page = reactive({ current: 1, size: 10, total: 0 })

const list = ref<NcrRecord[]>([
  { ncrNo: 'NCR20260815001', source: 'IQC', productCode: 'MAT-C007', productName: 'PCB 主板 V2.3', batchNo: 'B20260815-03', quantity: 12, defectType: '短路', defectDesc: 'PCB 第3引脚与第5引脚短路', disposition: 'RETURN', creator: '王工', createTime: '2026-08-15 11:10:25' },
  { ncrNo: 'NCR20260815002', source: 'IPQC', productCode: 'P-A001', productName: '智能终端 X1', batchNo: 'B20260815-A1', quantity: 5, defectType: '焊接不良', defectDesc: 'A线焊接工序发现5台焊接虚焊', disposition: 'REWORK', creator: '王工', createTime: '2026-08-15 10:20:18' },
  { ncrNo: 'NCR20260815003', source: 'FQC', productCode: 'P-C010', productName: '电源模块', batchNo: 'B20260815-C1', quantity: 8, defectType: '功能失效', defectDesc: '电源输出电压超出公差', disposition: 'SCRAP', creator: '张工', createTime: '2026-08-15 18:10:33' },
  { ncrNo: 'NCR20260815004', source: 'CUSTOMER', productCode: 'P-B003', productName: '控制板 V3', batchNo: 'B20260810-B1', quantity: 3, defectType: '性能异常', defectDesc: '客户反馈使用过程中通讯中断', disposition: 'PENDING', creator: '客服部', createTime: '2026-08-15 09:45:00' },
  { ncrNo: 'NCR20260815005', source: 'IQC', productCode: 'MAT-G010', productName: '散热片 铜质', batchNo: 'B20260815-08', quantity: 15, defectType: '尺寸超差', defectDesc: '散热片厚度偏差超过±0.1mm', disposition: 'CONCESSION', creator: '陈工', createTime: '2026-08-15 16:25:42' },
  { ncrNo: 'NCR20260815006', source: 'IPQC', productCode: 'P-D005', productName: '传感器组件', batchNo: 'B20260815-D1', quantity: 2, defectType: '标定失效', defectDesc: '传感器零点漂移超差', disposition: 'REPAIR', creator: '陈工', createTime: '2026-08-15 21:50:18' },
])

const pendingCount = computed(() => list.value.filter(r => r.disposition === 'PENDING').length)
const processingCount = computed(() => list.value.filter(r => r.disposition === 'REWORK' || r.disposition === 'REPAIR').length)
const closedCount = computed(() => list.value.filter(r => ['CONCESSION', 'SCRAP', 'RETURN'].includes(r.disposition)).length)
const scrapCount = computed(() => list.value.filter(r => r.disposition === 'SCRAP' || r.disposition === 'RETURN').length)
const pendingRate = computed(() => list.value.length ? Math.round(pendingCount.value / list.value.length * 1000) / 10 : 0)

const filteredList = computed(() => {
  let data = list.value
  if (query.ncrNo) data = data.filter(r => r.ncrNo.includes(query.ncrNo))
  if (query.source) data = data.filter(r => r.source === query.source)
  if (query.disposition) data = data.filter(r => r.disposition === query.disposition)
  page.total = data.length
  const start = (page.current - 1) * page.size
  return data.slice(start, start + page.size)
})

const sourceTagType = (s: string) => ({ IQC: '', IPQC: 'success', FQC: 'warning', CUSTOMER: 'danger' }[s] || '')

const handleSearch = () => { page.current = 1 }
const handleReset = () => { Object.assign(query, { ncrNo: '', source: '', disposition: '' }); page.current = 1 }
const handleAdd = () => ElMessage.info('登记不合格品功能开发中')
const handleExport = () => ElMessage.success('已导出当前列表数据')
const handleView = (row: NcrRecord) => ElMessage.info(`查看不合格单 ${row.ncrNo}`)
const handleDispose = (row: NcrRecord) => ElMessage.info(`处置不合格单 ${row.ncrNo}`)
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
