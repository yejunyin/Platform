<template>
  <div class="page-container">
    <!-- 页面头 -->
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">QMS · 质检人员维护</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>在册人员 <b>{{ list.length }}</b></span>
          <span class="li"><i class="led-dot led-run"></i>数据源 <b>sys_user</b></span>
        </div>
      </div>
      <div class="ph-tags mono">
        <span class="ph-tag"><i class="led-dot led-on"></i>SYS</span>
        <span class="ph-tag"><i class="led-dot led-run"></i>QC-STAFF</span>
      </div>
    </div>

    <!-- 检索 + 操作 -->
    <div class="card-box panel-primary">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> 人员名单
        <div style="flex:1"></div>
        <el-input
          v-model="keyword"
          placeholder="工号 / 姓名"
          clearable
          style="width: 220px; margin-right: 12px"
          @keyup.enter="loadList"
          @clear="loadList"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
        <el-button type="success" :icon="Plus" @click="openAddDialog">新增人员</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="username" label="工号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="120" show-overflow-tooltip />
        <el-table-column label="钉钉ID" min-width="200">
          <template #default="{ row }">
            <el-input
              v-if="editingId === row.id"
              v-model="dingdingDraft"
              placeholder="请输入钉钉ID（留空可清除）"
              maxlength="64"
              clearable
              size="small"
              @keyup.enter="handleSaveDingding(row as QcStaff)"
            />
            <span v-else class="dingding-cell" :class="{ 'is-empty': !row.dingdingId }">
              {{ row.dingdingId || '未绑定' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="editingId === row.id">
              <el-button
                type="primary"
                size="small"
                :loading="savingId === row.id"
                @click="handleSaveDingding(row as QcStaff)"
              >
                保存
              </el-button>
              <el-button size="small" :disabled="savingId === row.id" @click="cancelEditDingding">
                取消
              </el-button>
            </template>
            <template v-else>
              <el-button type="primary" size="small" :icon="Edit" @click="startEditDingding(row as QcStaff)">
                编辑
              </el-button>
              <el-button type="danger" size="small" :icon="Delete" @click="handleDelete(row as QcStaff)">
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无质检人员" />
        </template>
      </el-table>
    </div>

    <!-- 新增对话框 -->
    <el-dialog v-model="dialogVisible" title="新增质检人员" width="420px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-form-item label="工号" prop="username">
          <el-input v-model="form.username" placeholder="请输入工号（登录账号）" maxlength="64" clearable />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入正确姓名" maxlength="64" clearable />
        </el-form-item>
        <el-form-item label="钉钉ID" prop="dingdingId">
          <el-input v-model="form.dingdingId" placeholder="选填，请输入钉钉ID" maxlength="64" clearable />
        </el-form-item>
      </el-form>
      <div class="form-tip">初始密码为 123456，同工号已删除时将直接恢复该人员。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Filter, Plus, Search } from '@element-plus/icons-vue'
import { qcStaffApi, type QcStaff } from '@/api/qcStaff'

const loading = ref(false)
const submitting = ref(false)
const list = ref<QcStaff[]>([])
const keyword = ref('')

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', realName: '', dingdingId: '' })

/** 钉钉ID 行内编辑状态：editingId 为当前编辑行，dingdingDraft 为输入草稿，savingId 为保存中行 */
const editingId = ref<number | null>(null)
const dingdingDraft = ref('')
const savingId = ref<number | null>(null)

const rules: FormRules = {
  username: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    { max: 64, message: '工号长度不能超过64', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 64, message: '姓名长度不能超过64', trigger: 'blur' },
  ],
  dingdingId: [{ max: 64, message: '钉钉ID长度不能超过64', trigger: 'blur' }],
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await qcStaffApi.list(keyword.value.trim())
    list.value = Array.isArray(res.data) ? res.data : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  dialogVisible.value = true
}

const resetForm = () => {
  form.username = ''
  form.realName = ''
  form.dingdingId = ''
  formRef.value?.clearValidate()
}

const startEditDingding = (row: QcStaff) => {
  editingId.value = row.id
  dingdingDraft.value = row.dingdingId ?? ''
}

const cancelEditDingding = () => {
  editingId.value = null
  dingdingDraft.value = ''
}

const handleSaveDingding = async (row: QcStaff) => {
  const dingdingId = dingdingDraft.value.trim()
  if (dingdingId === (row.dingdingId ?? '')) {
    cancelEditDingding()
    return
  }
  savingId.value = row.id
  try {
    const res = await qcStaffApi.updateDingding(row.id, dingdingId)
    row.dingdingId = res.data?.dingdingId ?? dingdingId
    ElMessage.success('保存成功')
    cancelEditDingding()
  } catch {
    // 错误提示已由请求拦截器统一弹出
  } finally {
    savingId.value = null
  }
}

const handleAdd = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await qcStaffApi.add({
        username: form.username.trim(),
        realName: form.realName.trim(),
        dingdingId: form.dingdingId.trim(),
      })
      ElMessage.success('新增成功')
      dialogVisible.value = false
      await loadList()
    } catch {
      // 错误提示已由请求拦截器统一弹出
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = (row: QcStaff) => {
  ElMessageBox.confirm(
    `确认删除质检人员「${row.realName}（${row.username}）」吗？删除后该工号可再次添加。`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  )
    .then(async () => {
      await qcStaffApi.remove(row.id)
      ElMessage.success('删除成功')
      await loadList()
    })
    .catch(() => {})
}

onMounted(() => {
  loadList()
})
</script>

<style scoped lang="scss">
/* 页头副标题 / 状态标签（深色面板上的浅字，与其他 QMS 页面一致；SCSS 变量由 vite additionalData 全局注入） */
.ph-sub {
  margin-top: 6px;
  font-size: 12px;
  color: $text-secondary;
  letter-spacing: 0.14em;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;

  .li {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .li b {
    color: $text-primary;
    font-weight: 700;
  }
}
.ph-tags {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.ph-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: $text-secondary;
  border: 1px solid $border-color;
  background: $panel-bg-deep;
}

/* 弹窗内提示文字：用主题浅色，避免深底上中灰看不清 */
.form-tip {
  margin: -6px 0 0 72px;
  font-size: 12px;
  color: $text-regular;
  line-height: 1.5;
}

/* 钉钉ID 单元格：未绑定时空值弱化显示 */
.dingding-cell {
  color: $text-primary;
  word-break: break-all;
}
.dingding-cell.is-empty {
  color: $text-secondary;
  opacity: 0.7;
}
</style>
