import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'

NProgress.configure({ showSpinner: false })

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'House' },
      },
      {
        path: 'task',
        name: 'TaskLayout',
        redirect: '/task/pending',
        meta: { title: '任务中心', icon: 'Tickets' },
        children: [
          {
            path: 'pending',
            name: 'TaskPending',
            component: () => import('@/views/task/Pending.vue'),
            meta: { title: '待办任务', icon: 'Clock' },
          },
          {
            path: 'done',
            name: 'TaskDone',
            component: () => import('@/views/task/Done.vue'),
            meta: { title: '已办任务', icon: 'Finished' },
          },
          {
            path: 'statistics',
            name: 'TaskStatistics',
            component: () => import('@/views/task/Statistics.vue'),
            meta: { title: '任务统计', icon: 'DataAnalysis' },
          },
          {
            path: 'material-call',
            name: 'TaskMaterialCall',
            component: () => import('@/views/task/MaterialCall/index.vue'),
            meta: { title: '物料呼叫', icon: 'Box' },
          },
        ],
      },
      {
        path: 'qms',
        name: 'QmsLayout',
        redirect: '/qms/iqc',
        meta: { title: '质量管理', icon: 'CircleCheck' },
        children: [
          {
            path: 'iqc',
            name: 'QmsIqc',
            component: () => import('@/views/qms/Iqc.vue'),
            meta: { title: '来料检验', icon: 'Box' },
          },
          {
            path: 'ipqc',
            name: 'QmsIpqc',
            component: () => import('@/views/qms/Ipqc.vue'),
            meta: { title: '过程检验', icon: 'Monitor' },
          },
          {
            path: 'fqc',
            name: 'QmsFqc',
            component: () => import('@/views/qms/Fqc.vue'),
            meta: { title: '成品检验', icon: 'Files' },
          },
          {
            path: 'nonconforming',
            name: 'QmsNonconforming',
            component: () => import('@/views/qms/Nonconforming.vue'),
            meta: { title: '不合格品处理', icon: 'WarningFilled' },
          },
          {
            path: 'report',
            name: 'QmsReport',
            component: () => import('@/views/qms/Report.vue'),
            meta: { title: '质量报表', icon: 'TrendCharts' },
          },
          {
            path: 'staff',
            name: 'QmsStaff',
            component: () => import('@/views/qms/Staff.vue'),
            meta: { title: '质检人员维护', icon: 'User' },
          },
        ],
      },
    ],
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || ''} - 企业大脑`

  const token = localStorage.getItem('token') || 'mock-token-for-dev'
  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
