<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        工序基础资料
      </div>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="编号/名称/车间"
          clearable
          class="ipt"
          @keyup.enter="load"
        />
        <el-button class="btn-primary" @click="load">查询</el-button>
        <el-button class="btn-primary" @click="openAdd">新增工序</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe class="data-table">
        <el-table-column label="工序编号" prop="processCode" width="160" />
        <el-table-column label="工序名称" prop="processName" min-width="180" />
        <el-table-column label="所属车间" prop="workshop" width="160">
          <template #default="{ row }">{{ row.workshop || '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="220">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as WageProcess)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row as WageProcess)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑工序' : '新增工序'" width="460px">
        <el-form :model="form" label-width="90px">
          <el-form-item label="工序编号" required>
            <el-input v-model="form.processCode" placeholder="如 SY01" />
          </el-form-item>
          <el-form-item label="工序名称" required>
            <el-input v-model="form.processName" placeholder="如 丝印、移印" />
          </el-form-item>
          <el-form-item label="所属车间">
            <el-input v-model="form.workshop" placeholder="如 印刷车间" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import { listProcess, saveProcess, deleteProcess } from '@/api/wage/process'
import type { WageProcess } from '@/api/wage/types'

onMounted(load)

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const list = ref<WageProcess[]>([])
const dialog = reactive({ visible: false, isEdit: false, id: 0 })
const form = reactive({ processCode: '', processName: '', workshop: '', remark: '' })

async function load() {
  loading.value = true
  try {
    const { data } = await listProcess(keyword.value || undefined)
    list.value = data || []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  form.processCode = ''
  form.processName = ''
  form.workshop = ''
  form.remark = ''
  dialog.isEdit = false
  dialog.id = 0
  dialog.visible = true
}

function openEdit(row: WageProcess) {
  form.processCode = row.processCode
  form.processName = row.processName
  form.workshop = row.workshop || ''
  form.remark = row.remark || ''
  dialog.isEdit = true
  dialog.id = row.id
  dialog.visible = true
}

async function onSave() {
  if (!form.processCode.trim() || !form.processName.trim()) {
    ElMessage.warning('请填写工序编号和名称')
    return
  }
  saving.value = true
  try {
    await saveProcess({
      id: dialog.isEdit ? dialog.id : undefined,
      processCode: form.processCode.trim(),
      processName: form.processName.trim(),
      workshop: form.workshop || undefined,
      remark: form.remark || undefined,
    })
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: WageProcess) {
  await ElMessageBox.confirm(`确认删除工序「${row.processName}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  })
  await deleteProcess(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.ipt { width: 220px; }
</style>
