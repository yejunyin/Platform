<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        用料清单查询
        <span class="crud-tip">开工提交时由金蝶《生产用料清单》下发，业务状态随任务单联动</span>
      </div>

      <div class="toolbar">
        <el-select
          v-model="query.taskNo"
          placeholder="任务单号"
          clearable
          filterable
          class="ipt task"
          @change="reload"
        >
          <el-option v-for="t in taskNos" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="query.bizStatus" placeholder="业务状态" clearable class="ipt" @change="reload">
          <el-option label="计划" value="计划" />
          <el-option label="开工" value="开工" />
          <el-option label="完工" value="完工" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="单号/投料单/物料代码/名称"
          clearable
          class="ipt kw"
          @keyup.enter="reload"
        />
        <el-button class="btn-primary" @click="reload">查询</el-button>
      </div>

      <el-table :data="pageRows" v-loading="loading" border stripe class="data-table">
        <el-table-column label="任务单号" prop="taskNo" width="180" />
        <el-table-column label="投料单号" prop="feedNo" width="160">
          <template #default="{ row }">{{ row.feedNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="物料代码" prop="materialCode" width="160" />
        <el-table-column label="物料名称" prop="materialName" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.materialName || '—' }}</template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="90">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <el-table-column label="数量" prop="qty" width="120" align="right" />
        <el-table-column label="业务状态" width="100">
          <template #default="{ row }">
            <span :class="['status-tag', bizClass(row.bizStatus)]">{{ row.bizStatus }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="rows.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listMaterials, listTaskNos } from '@/api/wage/orders'
import type { WageMaterial } from '@/api/wage/types'

onMounted(() => {
  loadTaskNos()
  reload()
})

const loading = ref(false)
const rows = ref<WageMaterial[]>([])
const taskNos = ref<string[]>([])
const pageNum = ref(1)
const pageSize = ref(20)
const query = reactive({ taskNo: '', bizStatus: '', keyword: '' })

const pageRows = computed(() => {
  const from = (pageNum.value - 1) * pageSize.value
  return rows.value.slice(from, from + pageSize.value)
})

async function loadTaskNos() {
  const { data } = await listTaskNos()
  taskNos.value = data || []
}

async function reload() {
  pageNum.value = 1
  loading.value = true
  try {
    const { data } = await listMaterials({
      taskNo: query.taskNo || undefined,
      bizStatus: query.bizStatus || undefined,
      keyword: query.keyword || undefined,
    })
    rows.value = data || []
  } finally {
    loading.value = false
  }
}

function bizClass(s: string) {
  if (s === '开工') return 'biz-run'
  if (s === '完工') return 'biz-done'
  return 'biz-plan'
}
</script>

<style scoped lang="scss">
.crud-tip {
  margin-left: 12px;
  font-size: 12px;
  color: $text-secondary;
  font-weight: normal;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.ipt { width: 150px; }
.task { width: 200px; }
.kw { width: 260px; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid;
}
.biz-plan {
  color: $text-secondary;
  border-color: $border-light;
}
.biz-run {
  color: $warning-color;
  border-color: $warning-color;
  background: rgba(255, 176, 32, 0.08);
}
.biz-done {
  color: $success-color;
  border-color: $success-color;
  background: rgba(0, 255, 148, 0.08);
}
</style>
