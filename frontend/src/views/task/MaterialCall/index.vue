<template>
  <div class="page-container mcs-page">
    <!-- 页面头 -->
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">MATERIAL&nbsp;CALL · 物料呼叫系统</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>SYSTEM&nbsp;ONLINE</span>
          <span class="li"><i class="led-dot led-run"></i>NODE&nbsp;MASTER-01</span>
          <span class="li"><i class="led-dot led-warn"></i>CALLS&nbsp;<b>{{ pendingCallCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>ALERTS&nbsp;<b>{{ activeAlertCount }}</b></span>
        </div>
      </div>
      <div style="display: flex; gap: 8px; align-items: center">
        <span class="hm-clock mono">{{ clockText }}</span>
        <el-button type="primary" :icon="Refresh" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <!-- 工业工具栏 -->
    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><Box /></el-icon><span>物料总数</span><b>{{ stats.materials.total }}</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><i class="led-dot led-run"></i><span>备料中</span><b>{{ stats.materials.preparing }}</b></div>
      <div class="tb-item"><i class="led-dot led-warn"></i><span>待备料</span><b>{{ stats.materials.pending }}</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>已备齐</span><b>{{ stats.materials.ready }}</b></div>
      <div class="tb-item"><i class="led-dot led-off"></i><span>缺料</span><b>{{ stats.materials.shortage }}</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>完成率</span><b>{{ stats.completionRate }}%</b></div>
      <div class="tb-item"><span>活跃叫料</span><b>{{ stats.calls.active }}</b></div>
    </div>

    <!-- 子视图切换 · 工业分段开关 -->
    <div class="mcs-tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="mcs-tab"
        :class="{ active: activeView === t.key }"
        @click="onTabClick(t.key)"
      >
        <el-icon><component :is="t.icon" /></el-icon>
        <span>{{ t.label }}</span>
        <el-badge
          v-if="t.badge"
          :value="t.badge"
          :hidden="!t.badge"
          class="mcs-tab-badge"
        />
      </button>
    </div>

    <!-- ============== 看板总览 ============== -->
    <div v-if="activeView === 'dashboard'" class="mcs-view">
      <div class="stat-grid">
        <div v-for="s in statCards" :key="s.label" class="stat-card" :class="`stat-${s.cls}`">
          <div class="stat-icon" :class="`icon-${s.cls}`">
            <el-icon><component :is="s.icon" /></el-icon>
          </div>
          <div>
            <div class="stat-value mono">{{ s.value }}</div>
            <div class="stat-label">{{ s.label }}</div>
            <div class="stat-sub mono">{{ s.sub }}</div>
          </div>
        </div>
      </div>

      <div class="industrial-panel panel-primary">
        <div class="industrial-panel__title">
          <el-icon><Grid /></el-icon> 备料看板&nbsp;·&nbsp;KANBAN
          <span class="mcs-kanban-tools">
            <span class="refresh-dot"></span>
            <span class="mono">AUTO·{{ refreshTimeText }}</span>
            <el-button size="small" @click="hideReadyTasks = !hideReadyTasks">
              {{ hideReadyTasks ? '显示已备齐' : '隐藏已备齐' }}
            </el-button>
          </span>
        </div>

        <div class="kanban-board" :style="{ gridTemplateColumns: `repeat(${pagedKanbanColumns.length}, 1fr)` }">
          <div
            v-for="col in pagedKanbanColumns"
            :key="col.key"
            class="kanban-column"
            :class="`col-${col.key}`"
          >
            <div class="kanban-col-header">
              <span>{{ col.label }}</span>
              <span class="col-count mono">{{ col.total }}</span>
            </div>
            <div class="kanban-col-body">
              <div v-if="col.total === 0" class="kanban-empty">暂无任务</div>
              <div
                v-for="t in col.pagedTasks"
                :key="t.id"
                class="task-card"
                :class="`priority-${priorityKey(t.schedulepriority)}`"
                @click="goToTaskDetail(t.id)"
              >
                <div class="tc-top">
                  <div>
                    <div class="task-card-id mono">{{ t.id }}</div>
                    <div class="task-card-name">{{ t.product_name }}</div>
                  </div>
                  <span class="priority-badge" :class="priorityKey(t.schedulepriority)">优先级 {{ priorityLabelByNum(t.schedulepriority) }}</span>
                </div>
                <div class="task-card-meta">
                  <span><el-icon><OfficeBuilding /></el-icon>{{ t.workshop }} · {{ t.line }}</span>
                  <span><el-icon><Box /></el-icon>{{ t.quantity }} {{ t.unit }}</span>
                </div>
                <div class="task-card-meta">
                  <span><el-icon><Clock /></el-icon>{{ (t.planned_start || '').slice(11) || '-' }}</span>
                  <span><el-icon><Tools /></el-icon>{{ t.material_count || 0 }}项物料</span>
                  <span v-if="(t.shortage_count || 0) > 0" class="shortage-tag">
                    <el-icon><WarningFilled /></el-icon>缺{{ t.shortage_count || 0 }}项
                  </span>
                </div>
                <div class="task-progress">
                  <el-progress
                    :percentage="t.progress || 0"
                    :color="progressColor(t.progress || 0)"
                    :stroke-width="5"
                    :show-text="false"
                  />
                  <div class="progress-text mono">
                    <span>备料进度</span>
                    <span>{{ t.progress }}%</span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 看板列分页 -->
            <div v-if="col.totalPages > 1" class="kanban-col-pager">
              <el-button size="small" :disabled="col.currentPage <= 1" @click="onKanbanPageChange(col.key, col.currentPage - 1)">上一页</el-button>
              <span class="kanban-page-info mono">{{ col.currentPage }}/{{ col.totalPages }}</span>
              <el-button size="small" :disabled="col.currentPage >= col.totalPages" @click="onKanbanPageChange(col.key, col.currentPage + 1)">下一页</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============== 生产任务 ============== -->
    <div v-else-if="activeView === 'tasks'" class="mcs-view">
      <div class="industrial-panel panel-primary">
        <div class="industrial-panel__title">
          <el-icon><Tickets /></el-icon> 生产任务列表
        </div>
        <div class="filter-bar">
          <el-form :inline="true" :model="filters">
            <el-form-item label="状态">
              <el-select v-model="filters.status" style="width: 180px" @change="loadFilteredTasks">
                <el-option label="进行中（隐藏已备齐）" value="active" />
                <el-option label="全部状态" value="all" />
                <el-option label="待备料" value="pending" />
                <el-option label="备料中" value="preparing" />
                <el-option label="已备齐" value="ready" />
                <el-option label="缺料/叫料" value="calling" />
              </el-select>
            </el-form-item>
            <el-form-item label="车间">
              <el-select v-model="filters.workshop" style="width: 140px" @change="loadFilteredTasks">
                <el-option label="全部车间" value="all" />
                <el-option v-for="w in workshops" :key="w" :label="w" :value="w" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="filters.search"
                placeholder="搜索任务号/产品名/工单号"
                style="width: 260px"
                clearable
                @keydown.enter="loadFilteredTasks"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadFilteredTasks">检索</el-button>
              <el-button :icon="RefreshLeft" @click="resetTaskFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table
          :data="pagedTasks"
          class="ind-table"
          row-key="id"
          :row-class-name="rowClass"
          stripe
        >
          <el-table-column label="任务单号" width="170">
            <template #default="{ row }"><span class="clickable mono" @click="goToTaskDetail(row.id)">{{ row.id }}</span></template>
          </el-table-column>
          <el-table-column label="产品名称" min-width="160" show-overflow-tooltip prop="product_name" />
          <el-table-column label="数量" width="100">
            <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="车间" width="100" prop="workshop" />
          <el-table-column label="产线" width="80" prop="line" />
          <el-table-column label="计划开始" width="160" prop="planned_start" />
          <el-table-column label="优先级" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="priorityTagTypeByNum(row.schedulepriority)" effect="dark">
                {{ priorityLabelByNum(row.schedulepriority) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备料进度" width="160">
            <template #default="{ row }">
              <div class="td-progress">
                <el-progress :percentage="row.progress" :color="progressColor(row.progress)" :stroke-width="6" :show-text="false" />
                <span class="mono">{{ row.progress }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.computed_status)" effect="dark">
                <i class="led-dot" :class="statusDotClass(row.computed_status)"></i>
                {{ statusLabel(row.computed_status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="goToTaskDetail(row.id)">查看物料</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="taskPagination.currentPage"
            v-model:page-size="taskPagination.pageSize"
            :page-sizes="taskPagination.pageSizes"
            :total="taskPagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="onTaskPageChange"
            @size-change="onTaskSizeChange"
          />
        </div>
      </div>
    </div>

    <!-- ============== 备料明细 ============== -->
    <div v-else-if="activeView === 'materials'" class="mcs-view">
      <div class="industrial-panel panel-primary">
        <div class="industrial-panel__title">
          <el-icon><Box /></el-icon> 备料明细
        </div>
        <div class="filter-bar">
          <el-form :inline="true">
            <el-form-item label="选择任务">
              <el-select v-model="selectedTaskId" style="width: 380px" @change="loadMaterials">
                <el-option
                  v-for="t in tasksWithComputed"
                  :key="t.id"
                  :label="`${t.id} - ${t.product_name}`"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="!taskDetail" class="empty-state">
          <el-icon class="empty-icon"><Box /></el-icon>
          <div class="empty-text">请选择任务</div>
        </div>
        <template v-else>
          <!-- 任务详情面板 -->
          <div class="detail-panel">
            <div class="detail-header">
              <h3>
                {{ taskDetail.product_name }}
                <span class="detail-id mono">{{ taskDetail.id }}</span>
              </h3>
              <el-tag size="small" :type="statusTagType(taskDetail.computed_status)" effect="dark">
                {{ statusLabel(taskDetail.computed_status) }}
              </el-tag>
            </div>
            <div class="detail-info">
              <div class="info-item"><span class="info-label">工单号</span><span class="info-value">{{ taskDetail.order_no }}</span></div>
              <div class="info-item"><span class="info-label">产品编码</span><span class="info-value">{{ taskDetail.product_code }}</span></div>
              <div class="info-item"><span class="info-label">计划数量</span><span class="info-value">{{ taskDetail.quantity }} {{ taskDetail.unit }}</span></div>
              <div class="info-item"><span class="info-label">车间</span><span class="info-value">{{ taskDetail.workshop || '-' }}</span></div>
              <div class="info-item"><span class="info-label">产线</span><span class="info-value">{{ taskDetail.line || '-' }}</span></div>
              <div class="info-item"><span class="info-label">计划开始</span><span class="info-value">{{ taskDetail.planned_start || '-' }}</span></div>
              <div class="info-item"><span class="info-label">计划完成</span><span class="info-value">{{ taskDetail.planned_end || '-' }}</span></div>
            </div>
          </div>

          <el-table :data="taskDetail.materials" class="ind-table" stripe>
            <el-table-column label="物料编码" width="140">
              <template #default="{ row }"><span class="mono">{{ row.material_code }}</span></template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="330" prop="material_name" show-overflow-tooltip />
            <el-table-column label="需求量" width="90">
              <template #default="{ row }">{{ row.required_qty }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column label="已备量" width="90">
              <template #default="{ row }">{{ row.prepared_qty }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column label="缺口" width="90">
              <template #default="{ row }">
                <span :style="{ color: row.required_qty - row.prepared_qty > 0 ? 'var(--el-color-danger)' : 'var(--el-color-success)', fontWeight: row.required_qty - row.prepared_qty > 0 ? 600 : 400 }">
                  {{ Math.max(0, row.required_qty - row.prepared_qty) }} {{ row.unit }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120" prop="remark" show-overflow-tooltip>
              <template #default="{ row }"><span :class="{ 'text-muted': !row.remark }">{{ row.remark || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="备料员" width="90" prop="preparer" />
            <el-table-column label="备齐时间" width="150" prop="prepare_time" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)" effect="dark">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="td-actions">
                  <el-button v-if="row.status !== 'ready'" type="success" size="small" @click="openPrepareModal(row as Material)">
                    备齐
                  </el-button>
                  <el-button v-if="row.status !== 'preparing' && row.status !== 'ready'" type="warning" size="small" @click="startPrepare(row.id as number)">
                    备料
                  </el-button>
                  <el-button v-if="row.required_qty - row.prepared_qty > 0" type="danger" size="small" @click="openCallModal(row as Material)">
                    叫料
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </div>

    <!-- ============== 叫料记录 ============== -->
    <div v-else-if="activeView === 'calls'" class="mcs-view">
      <div class="industrial-panel panel-danger">
        <div class="industrial-panel__title danger">
          <el-icon><Bell /></el-icon> 叫料记录
        </div>
        <div class="filter-bar">
          <el-form :inline="true">
            <el-form-item label="状态">
              <el-select v-model="filters.callStatus" style="width: 160px" @change="loadFilteredCalls">
                <el-option label="全部状态" value="all" />
                <el-option label="待响应" value="pending" />
                <el-option label="配送中" value="delivering" />
                <el-option label="已送达" value="delivered" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <el-table :data="filteredCalls" class="ind-table" stripe>
          <el-table-column label="叫料单号" width="110">
            <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
          </el-table-column>
          <el-table-column label="任务单号" width="160">
            <template #default="{ row }"><span class="mono">{{ row.task_id }}</span></template>
          </el-table-column>
          <el-table-column label="物料编码" width="140">
            <template #default="{ row }"><span class="mono">{{ row.material_code }}</span></template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="140" prop="material_name" />
          <el-table-column label="需求数量" width="100" prop="required_qty" />
          <el-table-column label="叫料数量" width="110">
            <template #default="{ row }">
              <span class="danger-text mono">{{ row.call_qty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.call_type === 'urgent' ? 'danger' : 'info'" effect="dark">
                {{ row.call_type === 'urgent' ? '紧急' : '普通' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="叫料人" width="100" prop="caller" />
          <el-table-column label="叫料时间" width="160" prop="call_time" />
          <el-table-column label="响应人" width="100" prop="responder" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)" effect="dark">
                <i class="led-dot" :class="statusDotClass(row.status)"></i>
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending'" type="primary" size="small" @click="openRespondModal(row.id)">
                响应
              </el-button>
              <el-button v-else-if="row.status === 'delivering'" type="success" size="small" @click="deliverCall(row.id)">
                确认送达
              </el-button>
              <span v-else class="finished-text mono">已完成</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- ============== 预警中心 ============== -->
    <div v-else-if="activeView === 'alerts'" class="mcs-view">
      <div class="industrial-panel panel-warn">
        <div class="industrial-panel__title warn">
          <el-icon><WarningFilled /></el-icon> 预警中心
        </div>
        <div class="filter-bar">
          <el-form :inline="true">
            <el-form-item label="状态">
              <el-select v-model="filters.alertStatus" style="width: 160px" @change="loadFilteredAlerts">
                <el-option label="活跃预警" value="active" />
                <el-option label="已解决" value="resolved" />
                <el-option label="全部" value="all" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="filteredAlerts.length === 0" class="empty-state">
          <el-icon class="empty-icon"><CircleCheckFilled /></el-icon>
          <div class="empty-text">暂无预警</div>
        </div>
        <div v-else class="alert-list">
          <div
            v-for="a in filteredAlerts"
            :key="a.id"
            class="alert-card"
            :class="[a.level, { resolved: a.status === 'resolved' }]"
          >
            <div class="alert-icon">
              <el-icon :color="alertIconColor(a.level)">
                <WarningFilled v-if="a.level === 'danger'" />
                <Warning v-else-if="a.level === 'warning'" />
                <InfoFilled v-else />
              </el-icon>
            </div>
            <div class="alert-content">
              <div class="alert-message">{{ a.message }}</div>
              <div class="alert-meta mono">
                <span>任务: {{ a.task_id || '-' }}</span>
                <span>类型: {{ alertTypeLabel(a.type) }}</span>
                <span>时间: {{ a.created_at || '-' }}</span>
                <el-tag size="small" :type="a.status === 'active' ? 'warning' : 'info'" effect="plain">
                  {{ statusLabel(a.status) }}
                </el-tag>
              </div>
            </div>
            <div class="alert-action">
              <el-button v-if="a.status === 'active'" type="success" size="small" @click="resolveAlert(a.id)">
                解决
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============== 弹窗：备齐 ============== -->
    <el-dialog v-model="prepareModalVisible" title="确认备齐" width="460px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="本次备料数量">
          <el-input-number v-model="prepareForm.qty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备料员">
          <el-input v-model="prepareForm.preparer" placeholder="输入备料员姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="prepareModalVisible = false">取消</el-button>
        <el-button type="success" @click="confirmPrepare">确认备齐</el-button>
      </template>
    </el-dialog>

    <!-- ============== 弹窗：叫料 ============== -->
    <el-dialog v-model="callModalVisible" title="发起叫料" width="460px" append-to-body>
      <div v-if="callContext" class="call-info">
        <div class="form-group">
          <label>物料</label>
          <div class="readonly-field mono">
            {{ callContext.material_code }} - {{ callContext.material_name }}
          </div>
        </div>
        <div class="form-group">
          <label>需求数量</label>
          <div class="readonly-field mono">
            {{ callContext.required_qty }} {{ callContext.unit }}
          </div>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item :label="`叫料数量 (缺口 ${callShortage} ${callContext?.unit || ''})`">
          <el-input-number v-model="callForm.qty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="叫料类型">
          <el-select v-model="callForm.type" style="width: 100%">
            <el-option label="普通叫料" value="normal" />
            <el-option label="紧急叫料" value="urgent" />
          </el-select>
        </el-form-item>
        <el-form-item label="叫料人">
          <el-input v-model="callForm.caller" placeholder="输入叫料人姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="callForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callModalVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmCall">发起叫料</el-button>
      </template>
    </el-dialog>

    <!-- ============== 弹窗：响应 ============== -->
    <el-dialog v-model="respondModalVisible" title="响应叫料" width="420px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="响应人">
          <el-input v-model="respondForm.responder" placeholder="输入响应人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="respondModalVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRespond">确认响应</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  Refresh, Search, RefreshLeft, Box, Grid, Clock, Tools,
  Tickets, Bell, WarningFilled, Warning, InfoFilled,
  CircleCheckFilled, OfficeBuilding,
} from '@element-plus/icons-vue'
import { useMaterialCall } from './useMaterialCall'
import type { Material } from './types'

const {
  activeView,
  selectedTaskId,
  hideReadyTasks,
  clockText,
  refreshTimeText,
  filters,
  filteredTasks,
  filteredCalls,
  filteredAlerts,
  taskPagination,
  pagedTasks,
  onTaskPageChange,
  onTaskSizeChange,
  stats,
  pendingCallCount,
  activeAlertCount,
  statCards,
  kanbanColumns,
  pagedKanbanColumns,
  onKanbanPageChange,
  tabs,
  tasksWithComputed,
  taskDetail,
  workshops,
  callShortage,
  prepareModalVisible,
  prepareContext,
  prepareForm,
  callModalVisible,
  callContext,
  callForm,
  respondModalVisible,
  respondForm,
  refreshAll,
  loadFilteredTasks,
  loadFilteredCalls,
  loadFilteredAlerts,
  resetTaskFilters,
  loadMaterials,
  materialsLoading,
  onTabClick,
  goToTaskDetail,
  openPrepareModal,
  confirmPrepare,
  startPrepare,
  openCallModal,
  confirmCall,
  openRespondModal,
  confirmRespond,
  deliverCall,
  resolveAlert,
  rowClass,
  statusLabel,
  priorityLabelByNum,
  priorityTagTypeByNum,
  priorityKey,
  statusTagType,
  statusDotClass,
  progressColor,
  alertIconColor,
  alertTypeLabel,
} = useMaterialCall()
</script>

<style scoped lang="scss" src="./style.scss"></style>
