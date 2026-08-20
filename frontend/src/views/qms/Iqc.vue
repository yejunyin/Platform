<template>
  <div class="page-container">
    <!-- 页面头 -->
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">QMS&nbsp;IQC · 来料检验</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>PASS <b>{{ passCount }}</b></span>
          <span class="li"><i class="led-dot led-warn"></i>CONCESSION <b>{{ concessionCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>FAIL <b>{{ failCount }}</b></span>
          <span class="li"><i class="led-dot led-run"></i>PENDING <b>{{ pendingCount }}</b></span>
          <span class="li ph-tag-dot">AQL <b>{{ aqlLevel }}</b></span>
        </div>
      </div>
      <div class="ph-tags mono">
        <span class="ph-tag"><i class="led-dot led-on"></i>SYS</span>
        <span class="ph-tag"><i class="led-dot led-run"></i>IQC</span>
        <span class="ph-tag"><i class="led-dot led-warn"></i>AQL-04</span>
        <span class="ph-tag">MODEL&nbsp;EB-QC-1000</span>
      </div>
    </div>

    <!-- 工业状态工具栏 -->
    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><Box /></el-icon><span>模块</span><b>IQC</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>供应商同步</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-run"></i><span>采样器</span><b>RUN</b></div>
      <div class="tb-item"><i class="led-dot led-warn"></i><span>AQL标准</span><b>Ⅱ</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>批次总数</span><b>{{ list.length }}</b></div>
      <div class="tb-item"><span>合格率</span><b class="rate">{{ passRate }}%</b></div>
      <div class="tb-item"><span>不良率</span><b :class="{ danger: defectRate > 1.5 }">{{ defectRate }}%</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>本页显示</span><b>{{ filteredList.length }}</b></div>
      <div class="tb-item"><span>采样时间</span><b class="mono">{{ sampleTime }}</b></div>
      <el-button size="small" type="primary" :icon="Refresh" @click="handleRefresh" style="margin-left:auto">
        <span class="mono">REFRESH</span>
      </el-button>
    </div>

    <!-- KPI 通道卡 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :span="6">
        <div class="stat-card card-success">
          <div class="stat-label">PASS / 合格</div>
          <div class="stat-value">{{ passCount }}</div>
          <div class="stat-footer"><i class="led-dot led-on"></i> 批次占比 <b>{{ Math.round(passCount/list.length*100) }}%</b></div>
          <el-icon class="stat-icon"><CircleCheck /></el-icon>
          <div class="sc-corner sc-tl">KP-01</div>
          <div class="sc-corner sc-br">CH-A</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-warn">
          <div class="stat-label">CONCESSION / 让步接收</div>
          <div class="stat-value">{{ concessionCount }}</div>
          <div class="stat-footer"><i class="led-dot led-warn"></i> 特采待审 <b>{{ concessionCount }}</b></div>
          <el-icon class="stat-icon"><Promotion /></el-icon>
          <div class="sc-corner sc-tl">KP-02</div>
          <div class="sc-corner sc-br">CH-B</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-danger">
          <div class="stat-label">FAIL / 不合格</div>
          <div class="stat-value">{{ failCount }}</div>
          <div class="stat-footer"><i class="led-dot led-off"></i> 待处置 <b>{{ failCount }}</b></div>
          <el-icon class="stat-icon"><CircleClose /></el-icon>
          <div class="sc-corner sc-tl">KP-03</div>
          <div class="sc-corner sc-br">CH-C</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-primary">
          <div class="stat-label">PENDING / 待检队列</div>
          <div class="stat-value">{{ pendingCount }}</div>
          <div class="stat-footer"><i class="led-dot led-run"></i> SLA <b>2H</b></div>
          <el-icon class="stat-icon"><Clock /></el-icon>
          <div class="sc-corner sc-tl">KP-04</div>
          <div class="sc-corner sc-br">CH-D</div>
        </div>
      </el-col>
    </el-row>

    <!-- 面板：查询 + 表格 -->
    <div class="card-box panel-primary">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> QUERY&nbsp;CONDITION · 检索条件
        <div style="flex:1"></div>
        <div class="panel-tags mono">
          <span class="tt-item"><i class="led-dot led-on"></i> IN-SPEC</span>
          <span class="tt-item">RANGE=2026-08-01~TODAY</span>
        </div>
      </div>

      <div class="filter-bar">
        <el-form :inline="true" :model="query" size="default">
          <el-form-item label="检验单号">
            <el-input v-model="query.iqcNo" placeholder="请输入检验单号" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="物料编码">
            <el-input v-model="query.materialCode" placeholder="物料编码" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="供应商">
            <el-input v-model="query.supplier" placeholder="供应商" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="检验结果">
            <el-select v-model="query.result" placeholder="全部" clearable style="width: 140px">
              <el-option label="[PASS] 合格" value="PASS" />
              <el-option label="[CONC] 让步接收" value="CONCESSION" />
              <el-option label="[FAIL] 不合格" value="FAIL" />
              <el-option label="[PEND] 待检" value="PENDING" />
            </el-select>
          </el-form-item>
          <el-form-item label="AQL 等级">
            <el-select v-model="query.aql" placeholder="全部" clearable style="width: 120px">
              <el-option label="Ⅰ 严格" value="1" />
              <el-option label="Ⅱ 正常" value="2" />
              <el-option label="Ⅲ 放宽" value="3" />
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
        class="ind-table iqc-table"
        stripe
        :row-class-name="rowClassName"
      >
        <el-table-column label="IQC NO / 单号" width="170">
          <template #default="{ row }">
            <span class="mono tid">{{ row.iqcNo }}</span>
            <el-tooltip v-if="row.aql" effect="dark" :content="'AQL Level ' + row.aql" placement="top">
              <span class="aql-chip mono">AQL-{{ row.aql }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="STATUS" width="140" align="center">
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
              <i class="led-dot led-run"></i>&nbsp; PEND
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="280">
          <template #default="{ row }">
            <div class="mat-cell">
              <div class="mat-name">{{ row.materialName }}</div>
              <div class="mat-code mono">CODE · {{ row.materialCode }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" min-width="200">
          <template #default="{ row }">
            <span class="sup-name">{{ row.supplier }}</span>
            <span v-if="row.supLevel" class="sup-level mono">S-{{ row.supLevel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="BATCH / 批次" width="160">
          <template #default="{ row }">
            <span class="mono batch-no">{{ row.batchNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="QTY / 数量" width="200" align="right">
          <template #default="{ row }">
            <div class="qty-cell">
              <div><span class="mono big">{{ fmt(row.quantity) }}</span> <small class="mono">PCS</small></div>
              <div class="qty-sub mono">SAMPLE <b>{{ row.sampleSize }}</b> · RATIO {{ Math.round(row.sampleSize/row.quantity*1000)/10 }}%</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="DEFECT / 不良" width="120" align="right">
          <template #default="{ row }">
            <div class="defect-cell mono" :class="defectClass(row)">
              <div class="dc-num"><b>{{ row.defectCount }}</b><small>/{{ row.sampleSize }}</small></div>
              <div class="dc-rate">{{ defectRateOf(row) }}%</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="AQL 判定" width="100" align="center">
          <template #default="{ row }">
            <div class="aql-verdict mono" :class="aqlVerdictClass(row)">
              <i class="led-dot" :class="aqlLedClass(row)"></i>
              <span>{{ aqlVerdict(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="inspector" label="检验员" width="90" />
        <el-table-column label="检验时间" width="170">
          <template #default="{ row }"><span class="mono inspect-time">{{ row.inspectTime }}</span></template>
        </el-table-column>
        <el-table-column label="ACTION / 动作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button-group>
              <el-button type="primary" size="small" @click="handleView(row)"><span class="mono">VIEW</span></el-button>
              <el-button type="primary" size="small" :disabled="row.result !== 'PENDING'" @click="handleEdit(row)"><span class="mono">EDIT</span></el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)"><span class="mono">DEL</span></el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer flex-between mt-16">
        <div class="footer-left mono">
          <span class="li"><i class="led-dot led-on"></i> INSPECTOR <b>IN-OFFICE</b></span>
          <span class="li"><i class="led-dot led-run"></i> QA-AUDIT <b>AUTO</b></span>
          <span class="li"><i class="led-dot led-warn"></i> NCR-TRIG <b>{{ failCount > 0 ? 'YES' : 'NO' }}</b></span>
        </div>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50, 100]"
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
import { Box, Plus, Download, Search, RefreshLeft, Filter, Refresh, CircleCheck, CircleClose, Promotion, Clock } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

interface IqcRecord {
  iqcNo: string
  materialCode: string
  materialName: string
  supplier: string
  supLevel?: string
  batchNo: string
  quantity: number
  sampleSize: number
  defectCount: number
  result: 'PASS' | 'CONCESSION' | 'FAIL' | 'PENDING'
  inspector: string
  inspectTime: string
  aql?: '1' | '2' | '3'
}

const query = reactive({
  iqcNo: '',
  materialCode: '',
  supplier: '',
  result: '',
  aql: '',
})

const page = reactive({ current: 1, size: 10, total: 0 })

const sampleTime = ref<string>(dayjs().format('HH:mm:ss'))

const list = ref<IqcRecord[]>([
  { iqcNo: 'IQC20260815001', materialCode: 'MAT-A001', materialName: '不锈钢外壳 304#', supplier: '上海精工金属制品有限公司', supLevel: 'A', batchNo: 'B20260815-01', quantity: 5000, sampleSize: 200, defectCount: 3, result: 'PASS', inspector: '张工', inspectTime: '2026-08-15 09:12:35', aql: '2' },
  { iqcNo: 'IQC20260815002', materialCode: 'MAT-B003', materialName: '锂电池组 12V/20Ah', supplier: '宁德时代电子科技', supLevel: 'S', batchNo: 'B20260815-02', quantity: 800, sampleSize: 80, defectCount: 5, result: 'CONCESSION', inspector: '李工', inspectTime: '2026-08-15 10:24:18', aql: '2' },
  { iqcNo: 'IQC20260815003', materialCode: 'MAT-C007', materialName: 'PCB 主板 V2.3', supplier: '深圳华星电子', supLevel: 'B', batchNo: 'B20260815-03', quantity: 1200, sampleSize: 120, defectCount: 12, result: 'FAIL', inspector: '王工', inspectTime: '2026-08-15 11:05:42', aql: '1' },
  { iqcNo: 'IQC20260815004', materialCode: 'MAT-A012', materialName: '铝合金面板 银色', supplier: '苏州铝业股份', supLevel: 'A', batchNo: 'B20260815-04', quantity: 3000, sampleSize: 150, defectCount: 0, result: 'PASS', inspector: '张工', inspectTime: '2026-08-15 13:48:21', aql: '2' },
  { iqcNo: 'IQC20260815005', materialCode: 'MAT-D021', materialName: '密封圈硅胶 Φ25', supplier: '东莞市橡塑制品厂', supLevel: 'C', batchNo: 'B20260815-05', quantity: 10000, sampleSize: 500, defectCount: 8, result: 'PASS', inspector: '陈工', inspectTime: '2026-08-15 14:30:55', aql: '3' },
  { iqcNo: 'IQC20260815006', materialCode: 'MAT-E005', materialName: '直流电机 24V', supplier: '宁波机电科技', supLevel: 'A', batchNo: 'B20260815-06', quantity: 600, sampleSize: 60, defectCount: 4, result: 'PENDING', inspector: '李工', inspectTime: '2026-08-15 15:12:09', aql: '2' },
  { iqcNo: 'IQC20260815007', materialCode: 'MAT-F002', materialName: '连接器 USB-C', supplier: '深圳联接电子', supLevel: 'B', batchNo: 'B20260815-07', quantity: 8000, sampleSize: 400, defectCount: 2, result: 'PASS', inspector: '王工', inspectTime: '2026-08-15 15:55:33', aql: '2' },
  { iqcNo: 'IQC20260815008', materialCode: 'MAT-G010', materialName: '散热片 铜质', supplier: '上海精工金属制品有限公司', supLevel: 'A', batchNo: 'B20260815-08', quantity: 2000, sampleSize: 100, defectCount: 15, result: 'FAIL', inspector: '陈工', inspectTime: '2026-08-15 16:21:48', aql: '1' },
])

const passCount = computed(() => list.value.filter(r => r.result === 'PASS').length)
const concessionCount = computed(() => list.value.filter(r => r.result === 'CONCESSION').length)
const failCount = computed(() => list.value.filter(r => r.result === 'FAIL').length)
const pendingCount = computed(() => list.value.filter(r => r.result === 'PENDING').length)
const passRate = computed(() => list.value.length ? Math.round(passCount.value / list.value.length * 1000) / 10 : 0)
const defectRate = computed(() => {
  const totalQty = list.value.reduce((s, r) => s + r.sampleSize, 0)
  const totalDefect = list.value.reduce((s, r) => s + r.defectCount, 0)
  return totalQty ? Math.round(totalDefect / totalQty * 1000) / 10 : 0
})
const aqlLevel = computed(() => 'Ⅱ')

const fmt = (n: number) => (n || 0).toLocaleString()

const defectRateOf = (row: IqcRecord) => row.sampleSize ? Math.round(row.defectCount / row.sampleSize * 1000) / 10 : 0
const defectClass = (row: IqcRecord) => {
  const r = defectRateOf(row)
  if (r === 0) return 'd-ok'
  if (r < 2) return 'd-minor'
  if (r < 5) return 'd-warn'
  return 'd-danger'
}

const aqlVerdict = (row: IqcRecord) => {
  if (row.result === 'PASS') return 'AC'
  if (row.result === 'CONCESSION') return 'MR'
  if (row.result === 'FAIL') return 'RJ'
  return '—'
}
const aqlVerdictClass = (row: IqcRecord) => {
  if (row.result === 'PASS') return 'v-pass'
  if (row.result === 'CONCESSION') return 'v-conc'
  if (row.result === 'FAIL') return 'v-fail'
  return 'v-pend'
}
const aqlLedClass = (row: IqcRecord) => {
  if (row.result === 'PASS') return 'led-on'
  if (row.result === 'CONCESSION') return 'led-warn'
  if (row.result === 'FAIL') return 'led-off'
  return 'led-run'
}

const rowClassName = ({ row }: { row: IqcRecord }) => {
  if (row.result === 'FAIL') return 'row-fail'
  if (row.result === 'PENDING') return 'row-pend'
  if (row.result === 'CONCESSION') return 'row-conc'
  return ''
}

const filteredList = computed(() => {
  let data = list.value
  if (query.iqcNo) data = data.filter(r => r.iqcNo.includes(query.iqcNo))
  if (query.materialCode) data = data.filter(r => r.materialCode.includes(query.materialCode))
  if (query.supplier) data = data.filter(r => r.supplier.includes(query.supplier))
  if (query.result) data = data.filter(r => r.result === query.result)
  if (query.aql) data = data.filter(r => r.aql === query.aql)
  page.total = data.length
  const start = (page.current - 1) * page.size
  return data.slice(start, start + page.size)
})

const handleSearch = () => { page.current = 1 }
const handleReset = () => { Object.assign(query, { iqcNo: '', materialCode: '', supplier: '', result: '', aql: '' }); page.current = 1 }
const handleRefresh = () => { sampleTime.value = dayjs().format('HH:mm:ss'); ElMessage.success('已刷新 IQC 采样视图') }
const handleAdd = () => ElMessage.info('新建检验单功能开发中')
const handleExport = () => ElMessage.success('已导出当前列表数据')
const handleView = (row: IqcRecord) => ElMessage.info(`查看检验单 ${row.iqcNo}`)
const handleEdit = (row: IqcRecord) => ElMessage.info(`编辑检验单 ${row.iqcNo}`)
const handleDelete = (row: IqcRecord) => {
  ElMessageBox.confirm(`确定删除检验单 ${row.iqcNo} 吗？`, '提示', { type: 'warning' })
    .then(() => {
      const idx = list.value.findIndex(r => r.iqcNo === row.iqcNo)
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
  background: v.$text-secondary; vertical-align: middle;
  &.led-on   { background: v.$success-color; box-shadow: v.$glow-success; }
  &.led-warn { background: v.$warning-color; box-shadow: v.$glow-warn; }
  &.led-off  { background: v.$danger-color;  box-shadow: v.$glow-danger; }
  &.led-run  { background: v.$primary-color; box-shadow: v.$glow-primary; animation: pulse 1.2s infinite; }
}
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity: 0.4; } }

/* 页头 */
.ph-sub {
  margin-top: 6px;
  font-size: 12px;
  color: v.$text-secondary;
  letter-spacing: 0.14em;
  display: flex; align-items: center; gap: 18px; flex-wrap: wrap;
  .li { display: inline-flex; align-items: center; gap: 6px; }
  .li b { color: v.$text-primary; font-weight: 700; }
  .ph-tag-dot {
    padding: 2px 10px;
    border: 1px dashed rgba(122,141,166,0.4);
    b { color: v.$primary-color; }
  }
}
.ph-tags { display: inline-flex; gap: 8px; align-items: center; }
.ph-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 10px; font-size: 11px; letter-spacing: 0.16em;
  color: v.$text-secondary;
  border: 1px solid v.$border-color;
  background: v.$panel-bg-deep;
}

/* 工具栏 */
.rate { color: v.$success-color; text-shadow: 0 0 5px rgba(0,255,148,0.5); }
.danger { color: v.$danger-color !important; text-shadow: 0 0 5px rgba(255,61,90,0.5); font-weight: 700; }

/* 统计卡 */
.stat-card {
  .stat-label { letter-spacing: 0.12em; }
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

/* 面板条 */
.panel-tags { display: inline-flex; gap: 8px; margin-right: 10px; }
.tt-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; letter-spacing: 0.1em;
  color: v.$text-secondary; padding: 2px 8px;
  border: 1px dashed v.$border-color; border-radius: 2px;
}

/* 过滤栏 */
.filter-bar {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: v.$panel-bg-deep;
  border: 1px solid v.$border-color;
  border-radius: 2px;
  :deep(.el-form-item) {
    margin-bottom: 8px;
    margin-right: 10px;
    :deep(.el-form-item__label) { font-size: 12px; letter-spacing: 0.04em; color: v.$text-secondary !important; }
  }
}

/* 表格细节样式 */
.iqc-table {
  :deep(.row-fail) td { background: rgba(255,61,90,0.05) !important; }
  :deep(.row-fail td:first-child) { box-shadow: inset 3px 0 0 v.$danger-color; }
  :deep(.row-pend) td:first-child { box-shadow: inset 3px 0 0 v.$primary-color; }
  :deep(.row-conc) td:first-child { box-shadow: inset 3px 0 0 v.$warning-color; }
}
.tid { color: v.$primary-color; letter-spacing: 0.04em; font-weight: 600; }
.aql-chip {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 5px;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: v.$primary-color;
  background: rgba(26,179,255,0.08);
  border: 1px solid rgba(26,179,255,0.35);
  border-radius: 2px;
}

/* 物料单元格 */
.mat-cell {
  .mat-name { color: v.$text-primary; font-weight: 500; font-size: 13px; }
  .mat-code { font-size: 11px; color: v.$text-secondary; margin-top: 2px; letter-spacing: 0.04em; }
}

/* 供应商 */
.sup-name { color: v.$text-primary; }
.sup-level {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  font-size: 10px;
  letter-spacing: 0.08em;
  background: rgba(0,255,148,0.08);
  color: v.$success-color;
  border: 1px solid rgba(0,255,148,0.3);
  border-radius: 2px;
}

/* 批次 */
.batch-no { color: rgba(184,198,217,0.85); letter-spacing: 0.04em; }

/* 数量 */
.qty-cell {
  .big { font-size: 15px; color: v.$text-primary; font-weight: 700; }
  small { color: v.$text-secondary; font-size: 11px; margin-left: 4px; }
  .qty-sub {
    margin-top: 3px;
    font-size: 11px; color: v.$text-secondary; letter-spacing: 0.04em;
    b { color: v.$text-primary; font-weight: 600; }
  }
}

/* 不良 */
.defect-cell {
  display: flex; flex-direction: column; align-items: flex-end; gap: 2px;
  .dc-num {
    b { font-size: 15px; font-weight: 700; }
    small { color: v.$text-secondary; font-size: 11px; margin-left: 2px; }
  }
  .dc-rate { font-size: 11px; color: v.$text-secondary; }
  &.d-ok     { .dc-num b { color: v.$success-color; } }
  &.d-minor  { .dc-num b { color: v.$text-primary; } .dc-rate { color: v.$text-primary; } }
  &.d-warn   { .dc-num b { color: v.$warning-color; text-shadow: 0 0 4px rgba(255,176,32,0.5); } .dc-rate { color: v.$warning-color; } }
  &.d-danger { .dc-num b { color: v.$danger-color;  text-shadow: 0 0 5px rgba(255,61,90,0.5);  } .dc-rate { color: v.$danger-color; font-weight: 700; } }
}

/* AQL 判定 */
.aql-verdict {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px; letter-spacing: 0.14em; font-weight: 700;
  border: 1px solid;
  &.v-pass  { color: v.$success-color; border-color: rgba(0,255,148,0.4);  background: rgba(0,255,148,0.06); }
  &.v-conc  { color: v.$warning-color; border-color: rgba(255,176,32,0.4);  background: rgba(255,176,32,0.06); }
  &.v-fail  { color: v.$danger-color;  border-color: rgba(255,61,90,0.4);   background: rgba(255,61,90,0.08); }
  &.v-pend  { color: v.$primary-color; border-color: rgba(26,179,255,0.4);  background: rgba(26,179,255,0.06); }
}

.inspect-time { color: rgba(184,198,217,0.85); letter-spacing: 0.04em; }

/* 表尾 */
.table-footer {
  padding: 14px 4px 0;
  border-top: 1px dashed v.$border-color;
  margin-top: 12px !important;
  .footer-left {
    display: flex; gap: 18px; align-items: center;
    font-size: 12px; letter-spacing: 0.14em; color: v.$text-secondary;
    .li { display: inline-flex; align-items: center; gap: 6px; }
    .li b { color: v.$text-primary; font-weight: 700; }
  }
}
</style>
