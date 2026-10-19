<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        员工基础资料
        <span class="crud-tip">身份复用系统账号（工号=登录账号，初始密码 123456），工种/系数/小组为工资扩展属性</span>
      </div>

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="工号/姓名"
          clearable
          class="ipt kw"
          @keyup.enter="loadStaff"
        />
        <el-select v-model="query.groupId" placeholder="所属小组" clearable class="ipt">
          <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="在职状态" clearable class="ipt">
          <el-option label="在职" :value="1" />
          <el-option label="离职" :value="0" />
        </el-select>
        <el-button class="btn-primary" @click="loadStaff">查询</el-button>
        <el-button class="btn-primary" @click="openAdd">新增员工</el-button>
      </div>

      <el-table :data="staffList" v-loading="loading" border stripe class="data-table">
        <el-table-column label="工号" prop="username" width="120" />
        <el-table-column label="姓名" prop="realName" width="120" />
        <el-table-column label="工种" prop="jobType" width="90">
          <template #default="{ row }">{{ row.jobType || '员工' }}</template>
        </el-table-column>
        <el-table-column label="所属小组" prop="groupName" width="140">
          <template #default="{ row }">{{ row.groupName || '—' }}</template>
        </el-table-column>
        <el-table-column label="人员系数" prop="coef" width="100" align="right">
          <template #default="{ row }">{{ row.coef ?? 1 }}</template>
        </el-table-column>
        <el-table-column label="入职日期" prop="hireDate" width="130">
          <template #default="{ row }">{{ row.hireDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="在职状态" width="100">
          <template #default="{ row }">
            <span :class="['status-tag', row.status === 1 ? 'status-pass' : 'status-fail']">
              {{ row.status === 1 ? '在职' : '离职' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as WageStaff)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row as WageStaff)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑员工' : '新增员工'" width="480px" append-to-body>
        <el-form :model="form" label-width="90px">
          <el-form-item label="工号" required>
            <el-input v-model="form.username" placeholder="登录账号，如 A001" :disabled="dialog.isEdit" />
          </el-form-item>
          <el-form-item label="姓名" required>
            <el-input v-model="form.realName" placeholder="员工真实姓名" />
          </el-form-item>
          <el-form-item label="工种">
            <el-select v-model="form.jobType" class="full">
              <el-option label="员工" value="员工" />
              <el-option label="组长" value="组长" />
              <el-option label="班长" value="班长" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属小组">
            <el-select v-model="form.groupId" placeholder="可暂不分配" clearable class="full">
              <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="人员系数">
            <el-input-number v-model="form.coef" :min="0" :step="0.1" :precision="2" class="full" />
          </el-form-item>
          <el-form-item label="入职日期">
            <el-date-picker
              v-model="form.hireDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择入职日期"
              class="full"
            />
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
import { listGroups } from '@/api/wage/groups'
import { listStaff, addStaff, updateStaff, deleteStaff } from '@/api/wage/staff'
import type { WageGroup, WageStaff } from '@/api/wage/types'

onMounted(() => {
  loadGroups()
  loadStaff()
})

const loading = ref(false)
const saving = ref(false)
const groups = ref<WageGroup[]>([])
const staffList = ref<WageStaff[]>([])
const query = reactive<{ keyword: string; groupId?: number; status?: number }>({
  keyword: '',
  groupId: undefined,
  status: undefined,
})

const dialog = reactive({ visible: false, isEdit: false, userId: 0 })
const form = reactive({
  username: '',
  realName: '',
  jobType: '员工',
  groupId: undefined as number | undefined,
  coef: 1,
  hireDate: '' as string | undefined,
})

async function loadGroups() {
  const { data } = await listGroups()
  groups.value = data || []
}

async function loadStaff() {
  loading.value = true
  try {
    const { data } = await listStaff({
      keyword: query.keyword || undefined,
      groupId: query.groupId,
      status: query.status,
    })
    staffList.value = data || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.username = ''
  form.realName = ''
  form.jobType = '员工'
  form.groupId = undefined
  form.coef = 1
  form.hireDate = undefined
}

function openAdd() {
  resetForm()
  dialog.isEdit = false
  dialog.userId = 0
  dialog.visible = true
}

function openEdit(row: WageStaff) {
  resetForm()
  dialog.isEdit = true
  dialog.userId = row.userId
  form.username = row.username
  form.realName = row.realName
  form.jobType = row.jobType || '员工'
  form.groupId = row.groupId
  form.coef = row.coef ?? 1
  form.hireDate = row.hireDate || undefined
  dialog.visible = true
}

async function onSave() {
  if (!form.username.trim() || !form.realName.trim()) {
    ElMessage.warning('请填写工号和姓名')
    return
  }
  saving.value = true
  try {
    if (dialog.isEdit) {
      await updateStaff(dialog.userId, {
        realName: form.realName.trim(),
        jobType: form.jobType,
        groupId: form.groupId,
        coef: form.coef,
        hireDate: form.hireDate,
      })
    } else {
      await addStaff({
        username: form.username.trim(),
        realName: form.realName.trim(),
        jobType: form.jobType,
        groupId: form.groupId,
        coef: form.coef,
        hireDate: form.hireDate,
      })
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    loadGroups()
    loadStaff()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: WageStaff) {
  await ElMessageBox.confirm(
    `确认删除员工「${row.realName}（${row.username}）」？删除后该账号将无法登录，工资扩展属性同步移除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
  await deleteStaff(row.userId)
  ElMessage.success('已删除')
  loadGroups()
  loadStaff()
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
.ipt { width: 180px; }
.kw { width: 200px; }
.full { width: 100%; }
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid;
}
.status-pass {
  color: $success-color;
  border-color: $success-color;
  background: rgba(0, 255, 148, 0.08);
}
.status-fail {
  color: $text-secondary;
  border-color: $border-light;
  background: rgba(122, 141, 166, 0.08);
}
</style>
