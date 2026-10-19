<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        单价工序资料
        <span class="crud-tip">按「子物料代码 + 工序」匹配计件单价；生效/失效日期为空表示长期有效</span>
      </div>

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="子物料代码/名称"
          clearable
          class="ipt kw"
          @keyup.enter="load"
        />
        <el-select v-model="query.processName" placeholder="工序" clearable class="ipt">
          <el-option v-for="p in processList" :key="p.id" :label="p.processName" :value="p.processName" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable class="ipt st">
          <el-option label="启用" value="启用" />
          <el-option label="停用" value="停用" />
        </el-select>
        <el-button class="btn-primary" @click="load">查询</el-button>
        <el-button class="btn-primary" @click="openAdd">新增单价</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe class="data-table">
        <el-table-column label="子物料代码" prop="materialCode" width="160" />
        <el-table-column label="子项物料名称" prop="materialName" min-width="220">
          <template #default="{ row }">{{ row.materialName || '—' }}</template>
        </el-table-column>
        <el-table-column label="工序" prop="processName" width="100" />
        <el-table-column label="单价(元)" prop="unitPrice" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="生效日期" prop="effectiveDate" width="120">
          <template #default="{ row }">{{ row.effectiveDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="失效日期" prop="expiryDate" width="120">
          <template #default="{ row }">{{ row.expiryDate || '长期' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span :class="['status-tag', row.status === '启用' ? 'on' : 'off']">{{ row.status || '启用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as WagePrice)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row as WagePrice)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑单价' : '新增单价'" width="500px" append-to-body>
        <el-form :model="form" label-width="110px">
          <el-form-item label="子物料代码" required>
            <el-input v-model="form.materialCode" placeholder="子项物料编码" />
          </el-form-item>
          <el-form-item label="子项物料名称">
            <el-input v-model="form.materialName" />
          </el-form-item>
          <el-form-item label="工序" required>
            <el-select v-model="form.processName" placeholder="选择工序" filterable class="full">
              <el-option v-for="p in processList" :key="p.id" :label="p.processName" :value="p.processName" />
            </el-select>
          </el-form-item>
          <el-form-item label="单价(元)" required>
            <el-input-number v-model="form.unitPrice" :min="0" :step="0.01" :precision="4" class="full" />
          </el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="空=立即生效" class="full" />
          </el-form-item>
          <el-form-item label="失效日期">
            <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="空=长期有效" class="full" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio value="启用">启用</el-radio>
              <el-radio value="停用">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPrice, savePrice, deletePrice } from '@/api/wage/price'
import { listProcess } from '@/api/wage/process'
import type { WagePrice, WageProcess } from '@/api/wage/types'

onMounted(() => {
  loadProcessOptions()
  load()
})

const loading = ref(false)
const saving = ref(false)
const list = ref<WagePrice[]>([])
const processList = ref<WageProcess[]>([])
const query = reactive({ keyword: '', processName: '', status: '' })
const dialog = reactive({ visible: false, isEdit: false, id: 0 })
const form = reactive({
  materialCode: '',
  materialName: '',
  processName: '',
  unitPrice: 0,
  effectiveDate: '' as string | undefined,
  expiryDate: '' as string | undefined,
  status: '启用',
})

function fmt(v?: number) {
  return v == null ? '—' : Number(v).toFixed(4).replace(/\.?0+$/, '')
}

async function loadProcessOptions() {
  const { data } = await listProcess()
  processList.value = data || []
}

async function load() {
  loading.value = true
  try {
    const { data } = await listPrice({
      keyword: query.keyword || undefined,
      processName: query.processName || undefined,
      status: query.status || undefined,
    })
    list.value = data || []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, {
    materialCode: '',
    materialName: '',
    processName: '',
    unitPrice: 0,
    effectiveDate: undefined,
    expiryDate: undefined,
    status: '启用',
  })
  dialog.isEdit = false
  dialog.id = 0
  dialog.visible = true
}

function openEdit(row: WagePrice) {
  Object.assign(form, {
    materialCode: row.materialCode,
    materialName: row.materialName || '',
    processName: row.processName,
    unitPrice: Number(row.unitPrice),
    effectiveDate: row.effectiveDate || undefined,
    expiryDate: row.expiryDate || undefined,
    status: row.status || '启用',
  })
  dialog.isEdit = true
  dialog.id = row.id
  dialog.visible = true
}

async function onSave() {
  if (!form.materialCode.trim() || !form.processName.trim()) {
    ElMessage.warning('请填写子物料代码并选择工序')
    return
  }
  saving.value = true
  try {
    await savePrice({
      id: dialog.isEdit ? dialog.id : undefined,
      materialCode: form.materialCode.trim(),
      materialName: form.materialName || undefined,
      processName: form.processName,
      unitPrice: form.unitPrice,
      effectiveDate: form.effectiveDate || undefined,
      expiryDate: form.expiryDate || undefined,
      status: form.status,
    })
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: WagePrice) {
  await ElMessageBox.confirm(
    `确认删除「${row.materialCode} / ${row.processName}」单价记录？`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
  await deletePrice(row.id)
  ElMessage.success('已删除')
  load()
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
.ipt { width: 160px; }
.kw { width: 220px; }
.full { width: 100%; }
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid;
}
.status-tag.on {
  color: $success-color;
  border-color: $success-color;
  background: rgba(0, 255, 148, 0.08);
}
.status-tag.off {
  color: $text-secondary;
  border-color: $border-light;
  background: rgba(122, 141, 166, 0.08);
}
</style>
