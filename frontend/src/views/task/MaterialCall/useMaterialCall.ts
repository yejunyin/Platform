// 物料呼叫系统 · 组合式函数（封装全部状态与业务逻辑）
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import {
  Box, Grid, Tickets, Bell, WarningFilled, Warning, CircleCheckFilled,
} from '@element-plus/icons-vue'
import { DB } from './mockData'
import { materialCallApi } from '@/api/materialCall'
import {
  nowStr, formatTime, statusLabel, priorityLabel, priorityTagType,
  priorityKey, priorityLabelByNum, priorityTagTypeByNum,
  statusTagType, statusDotClass, progressColor, alertIconColor, alertTypeLabel,
} from './utils'
import type {
  Task, TaskStatus, TaskComputed, Material, MaterialWithMeta,
  Call, Alert, DashboardStats, ViewKey,
} from './types'

export function useMaterialCall() {
  // ========================================================
  //  全局状态
  // ========================================================
  const currentUser = '备料员'
  const activeView = ref<ViewKey>('dashboard')
  const selectedTaskId = ref<string | null>(null)
  const hideReadyTasks = ref(true)
  const clockText = ref('')
  const refreshTimeText = ref('')
  let clockTimer: ReturnType<typeof setInterval> | null = null
  let dashboardTimer: ReturnType<typeof setInterval> | null = null

  const filters = reactive({
    status: 'active' as 'active' | 'all' | TaskStatus,
    workshop: 'all',
    search: '',
    callStatus: 'all' as 'all' | Call['status'],
    alertStatus: 'active' as 'active' | 'resolved' | 'all',
  })

  const filteredTasks = ref<Task[]>([])
  const filteredCalls = ref<Call[]>([])
  const filteredAlerts = ref<Alert[]>([])

  // 生产任务分页（默认 20 行，可选 100/500）
  const taskPagination = reactive({
    currentPage: 1,
    pageSize: 20,
    pageSizes: [20, 50, 100],
    total: 0,
  })
  const pagedTasks = computed<Task[]>(() => {
    const start = (taskPagination.currentPage - 1) * taskPagination.pageSize
    return filteredTasks.value.slice(start, start + taskPagination.pageSize)
  })
  const onTaskPageChange = (page: number) => { taskPagination.currentPage = page }
  const onTaskSizeChange = (size: number) => {
    taskPagination.pageSize = size
    taskPagination.currentPage = 1
  }

  // ========================================================
  //  计算属性 / 数据派生
  // ========================================================
  const calcTaskStatus = (taskId: string): TaskStatus => {
    const mats = DB.materials.filter((m) => m.task_id === taskId)
    if (!mats.length) return 'pending'
    if (mats.some((m) => m.status === 'shortage')) return 'calling'
    if (mats.every((m) => m.status === 'pending')) return 'pending'
    if (mats.some((m) => m.status === 'preparing' || m.status === 'pending')) return 'preparing'
    return 'ready'
  }

  const calcTaskProgress = (taskId: string): number => {
    const mats = DB.materials.filter((m) => m.task_id === taskId)
    if (!mats.length) return 0
    const ready = mats.filter((m) => m.status === 'ready').length
    return Math.round((ready / mats.length) * 100)
  }

  const tasksWithComputed = computed<TaskComputed[]>(() =>
    DB.tasks.map((t) => ({
      ...t,
      computed_status: calcTaskStatus(t.id),
      progress: calcTaskProgress(t.id),
      material_count: DB.materials.filter((m) => m.task_id === t.id).length,
      shortage_count: DB.materials.filter((m) => m.task_id === t.id && m.status === 'shortage').length,
    })),
  )

  const workshops = computed(() => [...new Set(DB.tasks.map((t) => t.workshop).filter(Boolean))])

  const taskDetail = computed<(TaskComputed & { materials: Material[] }) | null>(() => {
    if (!selectedTaskId.value) return null
    const task = DB.tasks.find((t) => t.id === selectedTaskId.value)
    if (!task) return null
    const computed_status = calcTaskStatus(task.id)
    const progress = calcTaskProgress(task.id)
    const materials = DB.materials.filter((m) => m.task_id === task.id)
    return {
      ...task,
      computed_status,
      progress,
      material_count: materials.length,
      shortage_count: materials.filter((m) => m.status === 'shortage').length,
      materials,
    }
  })

  const stats = computed<DashboardStats>(() => {
    const tasks = DB.tasks
    const statusCount = { pending: 0, preparing: 0, ready: 0, calling: 0 }
    for (const t of tasks) {
      const s = calcTaskStatus(t.id)
      if ((statusCount as Record<string, number>)[s] !== undefined) {
        (statusCount as Record<string, number>)[s]++
      } else {
        statusCount.pending++
      }
    }
    const allMats = DB.materials
    const matStats = { total: allMats.length, ready: 0, preparing: 0, pending: 0, shortage: 0 }
    for (const m of allMats) {
      if ((matStats as Record<string, number>)[m.status] !== undefined) {
        (matStats as Record<string, number>)[m.status]++
      }
    }
    const activeCalls = DB.calls.filter((c) => c.status !== 'delivered').length
    const activeAlerts = DB.alerts.filter((a) => a.status === 'active').length
    const completionRate = tasks.length ? Math.round((statusCount.ready / tasks.length) * 100) : 0
    return {
      tasks: { total: tasks.length, ...statusCount },
      materials: matStats,
      calls: { active: activeCalls },
      alerts: { active: activeAlerts },
      completionRate,
    }
  })

  const pendingCallCount = computed(() => DB.calls.filter((c) => c.status === 'pending').length)
  const activeAlertCount = computed(() => DB.alerts.filter((a) => a.status === 'active').length)

  const statCards = computed(() => [
    { icon: Tickets, cls: 'blue', value: stats.value.tasks.total, label: '生产任务总数', sub: `待备料 ${stats.value.tasks.pending} · 备料中 ${stats.value.tasks.preparing}` },
    { icon: CircleCheckFilled, cls: 'green', value: stats.value.tasks.ready, label: '已备齐', sub: `备料完成率 ${stats.value.completionRate}%` },
    { icon: Warning, cls: 'amber', value: stats.value.materials.preparing + stats.value.materials.pending, label: '备料中/待备料', sub: `物料总数 ${stats.value.materials.total}` },
    { icon: WarningFilled, cls: 'red', value: stats.value.tasks.calling + stats.value.materials.shortage, label: '缺料/叫料', sub: `叫料待处理 ${stats.value.calls.active}` },
  ])

  const kanbanColumns = computed(() => {
    const cols = [
      { key: 'pending', label: '待备料', tasks: tasksWithComputed.value.filter((t) => t.computed_status === 'pending') },
      { key: 'preparing', label: '备料中', tasks: tasksWithComputed.value.filter((t) => t.computed_status === 'preparing') },
      { key: 'ready', label: '已备齐', tasks: tasksWithComputed.value.filter((t) => t.computed_status === 'ready') },
      { key: 'calling', label: '缺料/叫料', tasks: tasksWithComputed.value.filter((t) => t.computed_status === 'calling') },
    ]
    return hideReadyTasks.value ? cols.filter((c) => c.key !== 'ready') : cols
  })

  // 看板每列分页：最多 20 行/列
  const KANBAN_PAGE_SIZE = 20
  const kanbanPage = reactive<Record<string, number>>({ pending: 1, preparing: 1, ready: 1, calling: 1 })

  const pagedKanbanColumns = computed(() =>
    kanbanColumns.value.map((col) => {
      const total = col.tasks.length
      const totalPages = Math.max(1, Math.ceil(total / KANBAN_PAGE_SIZE))
      let page = kanbanPage[col.key] ?? 1
      if (page > totalPages) page = totalPages
      const start = (page - 1) * KANBAN_PAGE_SIZE
      return {
        ...col,
        total,
        currentPage: page,
        totalPages,
        pagedTasks: col.tasks.slice(start, start + KANBAN_PAGE_SIZE),
      }
    }),
  )

  const onKanbanPageChange = (colKey: string, page: number) => {
    kanbanPage[colKey] = page
  }

  const tabs = computed<{ key: ViewKey; label: string; icon: any; badge: number }[]>(() => [
    { key: 'dashboard', label: '看板总览', icon: Grid, badge: 0 },
    { key: 'tasks', label: '生产任务', icon: Tickets, badge: 0 },
    { key: 'materials', label: '备料明细', icon: Box, badge: 0 },
    { key: 'calls', label: '叫料记录', icon: Bell, badge: pendingCallCount.value },
    { key: 'alerts', label: '预警中心', icon: WarningFilled, badge: activeAlertCount.value },
  ])

  // ========================================================
  //  数据加载 / 过滤
  // ========================================================
  const loadFilteredTasks = () => {
    let list = tasksWithComputed.value.slice()
    if (filters.status === 'active') list = list.filter((t) => t.computed_status !== 'ready')
    else if (filters.status !== 'all') list = list.filter((t) => t.computed_status === filters.status)
    if (filters.workshop !== 'all') list = list.filter((t) => t.workshop === filters.workshop)
    if (filters.search) {
      const s = filters.search.toLowerCase()
      list = list.filter((t) =>
        t.id.toLowerCase().includes(s) ||
        t.product_name.toLowerCase().includes(s) ||
        t.order_no.toLowerCase().includes(s),
      )
    }
    list.sort((a, b) => (a.schedulepriority ?? 99) - (b.schedulepriority ?? 99))
    filteredTasks.value = list
    // 同步分页总数；若当前页超出新总数则回退到第 1 页
    taskPagination.total = list.length
    const maxPage = Math.max(1, Math.ceil(list.length / taskPagination.pageSize))
    if (taskPagination.currentPage > maxPage) taskPagination.currentPage = 1
  }

  const resetTaskFilters = () => {
    filters.status = 'active'
    filters.workshop = 'all'
    filters.search = ''
    loadFilteredTasks()
  }

  const loadMaterials = () => {
    // 切换任务下拉时触发同步：BOMflag=1 后端直接返回；BOMflag=0 后端从金蝶拉取
    if (selectedTaskId.value) {
      loadMaterialsForTask(selectedTaskId.value)
    }
  }

  // 备料明细同步状态（true 时显示遮罩）
  const materialsLoading = ref(false)

  // 调用 /materials/sync?taskId=xxx：BOMflag=1 直接返回；BOMflag=0 后端调金蝶 ERP 拉取并写回
  const loadMaterialsForTask = async (taskId: string) => {
    if (!taskId) return
    const loading = ElLoading.service({
      lock: true,
      text: '正在从ERP获取生产用料清单',
      background: 'rgba(0, 0, 0, 0.7)',
    })
    materialsLoading.value = true
    try {
      const res = await materialCallApi.syncMaterials(taskId)
      if (Array.isArray(res.data)) {
        // 用最新数据替换该任务下的物料
        const others = DB.materials.filter((m) => m.task_id !== taskId)
        const synced = res.data.filter((m) => m && m.id != null)
        DB.materials = [...others, ...synced]
        // 物料变化会影响任务进度/状态/统计，重新计算
        loadFilteredTasks()
        loadFilteredCalls()
      }
    } catch (e) {
      console.error('从 ERP 同步备料明细失败:', e)
      ElMessage.error('从 ERP 获取备料明细失败，请稍后重试')
    } finally {
      materialsLoading.value = false
      loading.close()
    }
  }

  // 从后端拉取备料明细，失败沿用本地 mock
  const loadMaterialsFromApi = async () => {
    try {
      const res = await materialCallApi.listMaterials()
      if (Array.isArray(res.data)) {
        DB.materials = res.data.filter((m) => m && m.id != null)
      }
    } catch (e) {
      console.error('拉取备料明细失败，沿用本地 mock:', e)
    }
  }

  // 从后端拉取叫料记录，失败沿用本地 mock
  const loadCallsFromApi = async () => {
    try {
      // 拉取全量叫料记录，前端再按 callStatus 做二级筛选（叫料记录按对应状态显示）
      const res = await materialCallApi.listCalls('all')
      if (Array.isArray(res.data)) {
        DB.calls = res.data.filter((c) => c && c.id != null)
        // 同步 nextCallId
        const maxId = DB.calls.reduce((m, c) => Math.max(m, Number(c.id) || 0), 0)
        if (maxId >= DB.nextCallId) DB.nextCallId = maxId + 1
      }
    } catch (e) {
      console.error('拉取叫料记录失败，沿用本地 mock:', e)
    }
  }

  // 从后端拉取预警，失败沿用本地 mock
  const loadAlertsFromApi = async () => {
    try {
      const res = await materialCallApi.listAlerts('all')
      if (Array.isArray(res.data)) {
        DB.alerts = res.data.filter((a) => a && a.id != null)
        const maxId = DB.alerts.reduce((m, a) => Math.max(m, Number(a.id) || 0), 0)
        if (maxId >= DB.nextAlertId) DB.nextAlertId = maxId + 1
      }
    } catch (e) {
      console.error('拉取预警失败，沿用本地 mock:', e)
    }
  }

  // 从 MES 后端拉取生产任务，覆盖本地 mock 的 DB.tasks
  const loadTasksFromApi = async () => {
    try {
      const res = await materialCallApi.listTasks()
      if (Array.isArray(res.data)) {
        DB.tasks = res.data.filter((t) => t && t.id)
      }
    } catch (e) {
      console.error('拉取 MES 生产任务失败，沿用本地 mock:', e)
    }
  }

  // 全量拉取（任务 + 备料 + 叫料 + 预警）
  const loadAllFromApi = async () => {
    await Promise.all([
      loadTasksFromApi(),
      loadMaterialsFromApi(),
      loadCallsFromApi(),
      loadAlertsFromApi(),
    ])
  }

  const loadFilteredCalls = () => {
    const list = DB.calls.slice().sort((a, b) => (b.call_time || '').localeCompare(a.call_time || ''))
    filteredCalls.value = filters.callStatus !== 'all' ? list.filter((c) => c.status === filters.callStatus) : list
  }

  const loadFilteredAlerts = () => {
    const list = DB.alerts.slice().sort((a, b) => (b.created_at || '').localeCompare(a.created_at || ''))
    filteredAlerts.value = filters.alertStatus !== 'all' ? list.filter((a) => a.status === filters.alertStatus) : list
  }

  const refreshAll = async () => {
    await loadAllFromApi()
    // 重置看板每列页码到第 1 页
    Object.keys(kanbanPage).forEach((k) => { kanbanPage[k] = 1 })
    loadFilteredTasks()
    loadFilteredCalls()
    loadFilteredAlerts()
    refreshTimeText.value = formatTime(new Date())
    ElMessage.success('已刷新')
  }

  const rowClass = ({ row }: { row: Task }) =>
    row.computed_status === 'preparing' ? 'row-preparing' : ''

  // ========================================================
  //  业务操作
  // ========================================================
  const goToTaskDetail = async (taskId: string) => {
    selectedTaskId.value = taskId
    activeView.value = 'materials'
    await loadMaterialsForTask(taskId)
  }

  // Tab 切换：点击"备料明细"时，若已选任务则触发同步
  const onTabClick = (key: ViewKey) => {
    activeView.value = key
    if (key === 'materials' && selectedTaskId.value) {
      loadMaterialsForTask(selectedTaskId.value)
    }
  }

  // ---- 备齐 ----
  const prepareModalVisible = ref(false)
  const prepareContext = ref<MaterialWithMeta | null>(null)
  const prepareForm = reactive({ qty: 1, preparer: currentUser })

  const openPrepareModal = (m: Material) => {
    const shortage = m.required_qty - m.prepared_qty
    const canAutoFill = m.available_qty >= shortage
    prepareContext.value = { ...m, shortage, canAutoFill }
    prepareForm.qty = canAutoFill ? shortage : 1
    prepareForm.preparer = currentUser
    prepareModalVisible.value = true
  }

  const confirmPrepare = async () => {
    const m = prepareContext.value
    if (!m) return
    const qty = prepareForm.qty
    if (!qty || qty <= 0) {
      ElMessage.error('请输入有效的备料数量')
      return
    }
    try {
      await materialCallApi.prepareMaterial(m.id, { qty, preparer: prepareForm.preparer })
    } catch (e) {
      return
    }
    // 后端已持久化，本地同步更新以保持 UI 即时反馈
    const totalPrepared = m.prepared_qty + qty
    updateMaterial(m.id, { status: 'ready', prepared_qty: totalPrepared, preparer: prepareForm.preparer })
    ElMessage.success(`物料备齐成功（本次备料 ${qty}，总计 ${totalPrepared}）`)
    prepareModalVisible.value = false
  }

  const startPrepare = async (id: number) => {
    try {
      await materialCallApi.startPrepare(id)
    } catch (e) {
      return
    }
    updateMaterial(id, { status: 'preparing' })
    ElMessage.success('已标记为备料中')
  }

  const updateMaterial = (id: number, updates: Partial<Material>) => {
    const mat = DB.materials.find((m) => m.id === id)
    if (!mat) return
    Object.assign(mat, updates)
    if (updates.status === 'ready' || updates.status === 'preparing') {
      if (!mat.prepare_time) mat.prepare_time = nowStr()
      if (!mat.preparer) mat.preparer = currentUser
    }
    if (mat.prepared_qty < mat.required_qty && mat.status === 'ready') {
      mat.status = 'shortage'
      mat.remark = `备料${mat.prepared_qty}/${mat.required_qty}，缺口${mat.required_qty - mat.prepared_qty}${mat.unit || ''}`
    }
    const task = DB.tasks.find((t) => t.id === mat.task_id)
    if (task) task.status = calcTaskStatus(mat.task_id)
    loadFilteredTasks()
    loadFilteredCalls()
    loadFilteredAlerts()
  }

  // ---- 叫料 ----
  const callModalVisible = ref(false)
  const callContext = ref<Material | null>(null)
  const callForm = reactive({ qty: 1, type: 'normal' as 'normal' | 'urgent', caller: currentUser, remark: '' })

  const callShortage = computed(() => {
    if (!callContext.value) return 0
    return callContext.value.required_qty - callContext.value.prepared_qty
  })

  const openCallModal = (m: Material) => {
    callContext.value = m
    callForm.qty = callShortage.value
    callForm.type = 'normal'
    callForm.caller = currentUser
    callForm.remark = ''
    callModalVisible.value = true
  }

  const confirmCall = async () => {
    const m = callContext.value
    if (!m) return
    if (!callForm.qty || callForm.qty <= 0) {
      ElMessage.error('请输入有效的叫料数量')
      return
    }
    let created: Call | null = null
    try {
      const res = await materialCallApi.createCall({
        taskId: m.task_id,
        materialCode: m.material_code,
        materialName: m.material_name,
        requiredQty: m.required_qty,
        callQty: callForm.qty,
        callType: callForm.type,
        caller: callForm.caller,
        remark: callForm.remark,
      })
      created = res.data
    } catch (e) {
      return
    }
    // 用后端返回的真实记录（含生成 id）回填本地
    addCall(created || {
      id: DB.nextCallId++,
      task_id: m.task_id,
      material_code: m.material_code,
      material_name: m.material_name,
      required_qty: m.required_qty,
      call_qty: callForm.qty,
      call_type: callForm.type,
      caller: callForm.caller,
      call_time: nowStr(),
      status: 'pending',
      responder: null,
      response_time: null,
      deliver_time: null,
      remark: callForm.remark || null,
    })
    ElMessage.success(`叫料已发起 (${callForm.type === 'urgent' ? '紧急' : '普通'})`)
    callModalVisible.value = false
    loadFilteredCalls()
    loadFilteredAlerts()
    loadFilteredTasks()
  }

  const addCall = (record: Call) => {
    DB.calls.push(record)
    const mat = DB.materials.find((m) => m.task_id === record.task_id && m.material_code === record.material_code)
    if (mat) {
      mat.status = 'shortage'
      mat.remark = `已叫料 ${record.call_qty} ${mat.unit || ''}`
    }
    const alertId = DB.nextAlertId++
    DB.alerts.push({
      id: alertId,
      task_id: record.task_id,
      type: 'shortage',
      level: record.call_type === 'urgent' ? 'danger' : 'warning',
      message: `物料 [${record.material_name}] 叫料 ${record.call_qty}，${record.call_type === 'urgent' ? '紧急' : '普通'}叫料`,
      status: 'active',
      created_at: nowStr(),
    })
    const task = DB.tasks.find((t) => t.id === record.task_id)
    if (task) task.status = calcTaskStatus(record.task_id)
  }

  // ---- 响应 / 送达 ----
  const respondModalVisible = ref(false)
  const respondForm = reactive({ responder: currentUser })
  let currentRespondCallId: number | null = null

  const openRespondModal = (id: number) => {
    currentRespondCallId = id
    respondForm.responder = currentUser
    respondModalVisible.value = true
  }

  const confirmRespond = async () => {
    if (!respondForm.responder) {
      ElMessage.error('请输入响应人')
      return
    }
    if (currentRespondCallId === null) return
    try {
      await materialCallApi.respondCall(currentRespondCallId, { responder: respondForm.responder })
    } catch (e) {
      return
    }
    updateCall(currentRespondCallId, { status: 'delivering', responder: respondForm.responder })
    ElMessage.success('已响应叫料，开始配送')
    respondModalVisible.value = false
    currentRespondCallId = null
    loadFilteredCalls()
    loadFilteredAlerts()
  }

  const deliverCall = async (id: number) => {
    try {
      await ElMessageBox.confirm('确认物料已送达？', '确认送达', {
        confirmButtonText: '送达',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await materialCallApi.deliverCall(id)
    } catch (e) {
      return
    }
    updateCall(id, { status: 'delivered' })
    ElMessage.success('已确认送达')
    loadFilteredCalls()
    loadFilteredAlerts()
    loadFilteredTasks()
  }

  const updateCall = (id: number, updates: Partial<Call>) => {
    const call = DB.calls.find((c) => c.id === id)
    if (!call) return
    Object.assign(call, updates)
    if (updates.status === 'delivering') {
      if (!call.responder) call.responder = '仓管员'
      call.response_time = nowStr()
    }
    if (updates.status === 'delivered') {
      call.deliver_time = nowStr()
      const mat = DB.materials.find((m) => m.task_id === call.task_id && m.material_code === call.material_code)
      if (mat) {
        mat.prepared_qty += call.call_qty
        if (mat.prepared_qty >= mat.required_qty) {
          mat.status = 'ready'
          mat.remark = null
        } else {
          mat.status = 'shortage'
          mat.remark = `已送达${call.call_qty}，仍缺${mat.required_qty - mat.prepared_qty}${mat.unit}`
        }
      }
      DB.alerts.forEach((a) => {
        if (a.task_id === call.task_id && a.type === 'shortage' && a.message.includes(call.material_name)) {
          a.status = 'resolved'
        }
      })
      const task = DB.tasks.find((t) => t.id === call.task_id)
      if (task) task.status = calcTaskStatus(call.task_id)
    }
  }

  // ---- 预警 ----
  const resolveAlert = (id: number) => {
    const a = DB.alerts.find((x) => x.id === id)
    if (a) a.status = 'resolved'
    ElMessage.success('预警已标记为解决')
    loadFilteredAlerts()
  }

  // ========================================================
  //  时钟 / 生命周期
  // ========================================================
  const updateClock = () => {
    const n = new Date()
    const p = (x: number) => String(x).padStart(2, '0')
    clockText.value = `${n.getFullYear()}-${p(n.getMonth() + 1)}-${p(n.getDate())} ${p(n.getHours())}:${p(n.getMinutes())}:${p(n.getSeconds())}`
  }

  onMounted(() => {
    updateClock()
    refreshTimeText.value = formatTime(new Date())
    loadFilteredCalls()
    loadFilteredAlerts()
    loadAllFromApi().finally(() => {
      loadFilteredTasks()
      loadFilteredCalls()
      loadFilteredAlerts()
      if (!selectedTaskId.value && DB.tasks.length) selectedTaskId.value = DB.tasks[0].id
    })
    clockTimer = setInterval(updateClock, 1000)
    dashboardTimer = setInterval(() => {
      refreshTimeText.value = formatTime(new Date())
    }, 10000)
  })

  onBeforeUnmount(() => {
    if (clockTimer) clearInterval(clockTimer)
    if (dashboardTimer) clearInterval(dashboardTimer)
  })

  return {
    // 状态
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
    // 弹窗 - 备齐
    prepareModalVisible,
    prepareContext,
    prepareForm,
    // 弹窗 - 叫料
    callModalVisible,
    callContext,
    callForm,
    // 弹窗 - 响应
    respondModalVisible,
    respondForm,
    // 数据加载
    refreshAll,
    loadFilteredTasks,
    loadFilteredCalls,
    loadFilteredAlerts,
    resetTaskFilters,
    loadMaterials,
    materialsLoading,
    onTabClick,
    // 业务操作
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
    // 工具
    rowClass,
    // 透出工具函数
    statusLabel,
    priorityLabel,
    priorityTagType,
    priorityKey,
    priorityLabelByNum,
    priorityTagTypeByNum,
    statusTagType,
    statusDotClass,
    progressColor,
    alertIconColor,
    alertTypeLabel,
  }
}
