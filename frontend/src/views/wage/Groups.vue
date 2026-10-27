<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        人员小组
        <span class="crud-tip">组内系数和/成员数由系统按在职工资员工自动汇总，不可手工填写</span>
      </div>

      <div class="toolbar">
        <el-button class="btn-primary" @click="openAdd">新增小组</el-button>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table :data="groups" v-loading="loading" border stripe class="data-table">
        <el-table-column label="小组名称" prop="groupName" min-width="160" />
        <el-table-column label="组长工号" prop="leaderNo" width="120">
          <template #default="{ row }">{{ row.leaderNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="默认工序" prop="defaultProcess" width="110">
          <template #default="{ row }">{{ row.defaultProcess || '—' }}</template>
        </el-table-column>
        <el-table-column label="在职成员数" prop="memberCount" width="100" align="right" />
        <el-table-column label="系数和" prop="coefSum" width="100" align="right" />
        <el-table-column label="备注" prop="remark" min-width="200">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as WageGroup)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row as WageGroup)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑小组' : '新增小组'" width="460px" append-to-body>
        <el-form :model="form" label-width="90px">
          <el-form-item label="小组名称" required>
            <el-input v-model="form.groupName" placeholder="如：丝印一组" />
          </el-form-item>
          <el-form-item label="组长工号">
            <el-input v-model="form.leaderNo" placeholder="组长工号（可空）" />
          </el-form-item>
          <el-form-item label="默认工序">
            <el-select v-model="form.defaultProcess" placeholder="选择默认工序" clearable class="full">
              <el-option label="丝印" value="丝印" />
              <el-option label="移印" value="移印" />
            </el-select>
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
import { listGroups, saveGroup, deleteGroup } from '@/api/wage/groups'
import type { WageGroup } from '@/api/wage/types'

onMounted(load)

const loading = ref(false)
const saving = ref(false)
const groups = ref<WageGroup[]>([])
const dialog = reactive({ visible: false, isEdit: false, id: '' as string })
const form = reactive({
  groupName: '',
  leaderNo: '',
  defaultProcess: '',
  remark: '',
})

async function load() {
  loading.value = true
  try {
    const { data } = await listGroups()
    groups.value = data || []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  form.groupName = ''
  form.leaderNo = ''
  form.defaultProcess = ''
  form.remark = ''
  dialog.isEdit = false
  dialog.id = ''
  dialog.visible = true
}

function openEdit(row: WageGroup) {
  form.groupName = row.groupName
  form.leaderNo = row.leaderNo || ''
  form.defaultProcess = row.defaultProcess || ''
  form.remark = row.remark || ''
  dialog.isEdit = true
  dialog.id = row.id
  dialog.visible = true
}

async function onSave() {
  if (!form.groupName.trim()) {
    ElMessage.warning('请填写小组名称')
    return
  }
  saving.value = true
  try {
    await saveGroup({
      id: dialog.isEdit ? dialog.id : undefined,
      groupName: form.groupName.trim(),
      leaderNo: form.leaderNo || undefined,
      defaultProcess: form.defaultProcess || undefined,
      remark: form.remark || undefined,
    })
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: WageGroup) {
  await ElMessageBox.confirm(
    `确认删除小组「${row.groupName}」？`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
  await deleteGroup(row.id)
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
}
.full { width: 100%; }
</style>
