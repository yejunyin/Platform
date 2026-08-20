<template>
  <el-container class="layout-container">
    <!-- 侧边工业舱 -->
    <el-aside :width="isCollapse ? '64px' : '228px'" class="sidebar">
      <!-- LOGO 区：工业铭牌 -->
      <div class="logo-area" :class="{ collapsed: isCollapse }">
        <div class="logo-mark">
          <el-icon size="26" class="logo-cpu"><Cpu /></el-icon>
        </div>
        <div v-show="!isCollapse" class="logo-text">
          <div class="logo-title">ENTERPRISE&nbsp;BRAIN</div>
          <div class="logo-sub">企 业 大 脑 · 中 控 台</div>
        </div>
      </div>

      <!-- 侧边状态条：系统 LED 指示 -->
      <div v-show="!isCollapse" class="sidebar-status">
        <div class="ss-item"><i class="led-dot led-on"></i><span>SYS&nbsp;OK</span></div>
        <div class="ss-item"><i class="led-dot led-run"></i><span>CORE&nbsp;RUN</span></div>
        <div class="ss-item"><i class="led-dot led-warn"></i><span>ALM&nbsp;02</span></div>
      </div>

      <!-- 菜单 -->
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <template #title><span>工作台</span></template>
        </el-menu-item>
        <el-sub-menu index="/task">
          <template #title>
            <el-icon><Tickets /></el-icon>
            <span>任务中心</span>
          </template>
          <el-menu-item index="/task/pending">
            <el-icon><Clock /></el-icon>
            <template #title>待办任务</template>
          </el-menu-item>
          <el-menu-item index="/task/done">
            <el-icon><Finished /></el-icon>
            <template #title>已办任务</template>
          </el-menu-item>
          <el-menu-item index="/task/statistics">
            <el-icon><DataAnalysis /></el-icon>
            <template #title>任务统计</template>
          </el-menu-item>
          <el-menu-item index="/task/material-call">
            <el-icon><Box /></el-icon>
            <template #title>物料呼叫</template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/qms">
          <template #title>
            <el-icon><CircleCheck /></el-icon>
            <span>质量管理</span>
          </template>
          <el-menu-item index="/qms/iqc">
            <el-icon><Box /></el-icon>
            <template #title>来料检验</template>
          </el-menu-item>
          <el-menu-item index="/qms/ipqc">
            <el-icon><Monitor /></el-icon>
            <template #title>过程检验</template>
          </el-menu-item>
          <el-menu-item index="/qms/fqc">
            <el-icon><Files /></el-icon>
            <template #title>成品检验</template>
          </el-menu-item>
          <el-menu-item index="/qms/nonconforming">
            <el-icon><WarningFilled /></el-icon>
            <template #title>不合格品处理</template>
          </el-menu-item>
          <el-menu-item index="/qms/report">
            <el-icon><TrendCharts /></el-icon>
            <template #title>质量报表</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <!-- 侧边底部铭牌 -->
      <div v-show="!isCollapse" class="sidebar-footer">
        <div class="sf-line"></div>
        <div class="sf-model">MODEL&nbsp;EB-1000</div>
        <div class="sf-ver">VER&nbsp;1.0.0 &nbsp;·&nbsp; 2026</div>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶栏：工业仪表条 -->
      <el-header class="header">
        <div class="header-left">
          <div class="collapse-btn" @click="isCollapse = !isCollapse" title="折叠侧栏">
            <el-icon size="18">
              <Expand v-if="isCollapse" />
              <Fold v-else />
            </el-icon>
          </div>
          <div class="hb-divider"></div>
          <div class="hb-marker">◆</div>
          <el-breadcrumb separator="›" class="hb-crumbs">
            <el-breadcrumb-item v-for="(item, i) in breadCrumbs" :key="i">
              {{ item }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <!-- 顶栏状态指示 -->
        <div class="header-meta">
          <div class="hm-item">
            <span class="hm-k">NODE</span>
            <span class="hm-v">MASTER-01</span>
          </div>
          <div class="hm-item">
            <span class="hm-k">TIME</span>
            <span class="hm-v mono">{{ nowTime }}</span>
          </div>
          <div class="hm-item">
            <i class="led-dot led-run"></i>
            <span class="hm-v mono">OEE&nbsp;{{ oee }}%</span>
          </div>
        </div>

        <div class="header-right">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="task-badge">
            <div class="header-icon-btn" @click="$router.push('/task/pending')" title="待办消息">
              <el-icon><Bell /></el-icon>
            </div>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <div class="avatar-ring">
                <el-avatar :size="34" :icon="User" />
                <span class="ring-on"></span>
              </div>
              <div class="user-meta">
                <div class="user-name">{{ userStore.userInfo.realName }}</div>
                <div class="user-role">OPERATOR&nbsp;·&nbsp;LEVEL-3</div>
              </div>
              <el-icon class="arrow-down"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { taskApi } from '@/api/task'
import {
  Cpu, House, Tickets, Clock, Finished, DataAnalysis,
  Bell, User, ArrowDown, SwitchButton, Expand, Fold,
  CircleCheck, Box, Monitor, Files, WarningFilled, TrendCharts
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const unreadCount = ref(0)
const nowTime = ref('')
const oee = ref(87) // mock OEE

let timer: any = null
const updateTime = () => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  nowTime.value = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const activeMenu = computed(() => route.path)
const breadCrumbs = computed(() => {
  const crumbs: string[] = []
  route.matched.forEach((r) => {
    if (r.meta && r.meta.title) {
      crumbs.push(r.meta.title as string)
    }
  })
  return crumbs.length ? crumbs : ['工作台']
})

const loadUnreadCount = async () => {
  try {
    const res = await taskApi.getStatistics(userStore.userInfo.id)
    if (res.code === 200) {
      unreadCount.value = res.data.unreadCount || 0
    }
  } catch (e) {
    unreadCount.value = 4
  }
}

const handleCommand = (cmd: string) => {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '确认退出', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => {
        userStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
  } else if (cmd === 'profile') {
    ElMessage.info('个人中心功能开发中')
  }
}

onMounted(() => {
  loadUnreadCount()
  updateTime()
  timer = setInterval(updateTime, 1000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as v;

.layout-container {
  height: 100vh;
  width: 100vw;
  background: v.$bg-color;
}

/* ================= 侧栏 · 工业舱 ================= */
.sidebar {
  position: relative;
  background:
    linear-gradient(180deg, #0a1220 0%, #070c15 100%);
  border-right: 1px solid v.$border-color;
  box-shadow: inset -1px 0 0 rgba(26,179,255,0.12);
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  /* 顶部 LOGO 铭牌 */
  .logo-area {
    height: 68px;
    padding: 0 14px;
    display: flex;
    align-items: center;
    gap: 12px;
    background:
      linear-gradient(90deg, rgba(26,179,255,0.10) 0%, transparent 70%),
      #060b13;
    border-bottom: 1px solid v.$border-color;
    position: relative;
    &::after {
      content: '';
      position: absolute; left: 0; right: 0; bottom: 0; height: 2px;
      background: linear-gradient(90deg,
        transparent, rgba(26,179,255,0.8) 30%, rgba(0,255,148,0.5) 70%, transparent
      );
    }
    &.collapsed { justify-content: center; padding: 0; gap: 0; }
  }
  .logo-mark {
    position: relative;
    width: 40px; height: 40px;
    display: flex; align-items: center; justify-content: center;
    background: linear-gradient(135deg, #1a2a45, #0a1320);
    border: 1px solid v.$border-accent;
    box-shadow: inset 0 0 10px rgba(26,179,255,0.25), v.$glow-primary;
    clip-path: polygon(0 6px, 6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%);
  }
  .logo-cpu { color: v.$primary-color; filter: drop-shadow(0 0 4px rgba(26,179,255,0.8)); }
  .logo-text {
    display: flex; flex-direction: column; gap: 2px; line-height: 1.1;
    .logo-title {
      font-family: v.$font-display;
      font-weight: 700;
      font-size: 15px;
      letter-spacing: 0.12em;
      color: #fff;
      text-shadow: 0 0 6px rgba(26,179,255,0.6);
    }
    .logo-sub {
      font-size: 11px;
      letter-spacing: 0.25em;
      color: v.$text-secondary;
    }
  }

  /* 侧边状态条 */
  .sidebar-status {
    display: flex; align-items: center; justify-content: space-between;
    padding: 8px 14px;
    border-bottom: 1px dashed v.$border-color;
    background: #060c14;
    font-family: v.$font-mono;
    font-size: 10px;
    letter-spacing: 0.08em;
    color: v.$text-secondary;
    .ss-item { display: inline-flex; align-items: center; gap: 5px; }
  }

  .sidebar-menu {
    border-right: none;
    flex: 1;
    background: transparent !important;
    :deep(.el-menu) { background: transparent !important; }
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 46px;
      line-height: 46px;
      color: v.$text-regular !important;
      border-left: 3px solid transparent;
      font-family: v.$font-sans;
      letter-spacing: 0.04em;
      margin: 1px 0;
      transition: all .15s;
      &:hover {
        background: rgba(26,179,255,0.08) !important;
        color: v.$primary-color !important;
      }
    }
    :deep(.el-menu-item.is-active) {
      background: linear-gradient(90deg, rgba(26,179,255,0.20) 0%, rgba(26,179,255,0.02) 100%) !important;
      color: v.$primary-color !important;
      border-left-color: v.$primary-color;
      box-shadow: inset 0 0 22px rgba(26,179,255,0.12);
    }
    :deep(.el-sub-menu .el-menu-item) {
      min-width: auto;
      padding-left: 54px !important;
      background: #060c14 !important;
    }
  }

  /* 底部铭牌 */
  .sidebar-footer {
    padding: 10px 14px 14px;
    border-top: 1px dashed v.$border-color;
    background: #060b13;
    font-family: v.$font-mono;
    font-size: 10px;
    color: v.$text-secondary;
    letter-spacing: 0.1em;
    line-height: 1.5;
    .sf-line {
      height: 2px; width: 100%;
      background: repeating-linear-gradient(90deg, v.$border-accent 0 6px, transparent 6px 14px);
      margin-bottom: 8px;
    }
    .sf-model { color: v.$text-primary; font-weight: 700; letter-spacing: 0.18em; }
  }
}

/* ================= 顶栏 · 工业仪表条 ================= */
.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 20px;
  gap: 20px;
  background:
    linear-gradient(90deg, #111b2a 0%, #0d1522 100%);
  border-bottom: 1px solid v.$border-color;
  box-shadow: 0 1px 0 rgba(26,179,255,0.12);
  position: relative;
  &::after {
    content: '';
    position: absolute; left: 0; right: 0; top: 0; height: 2px;
    background: linear-gradient(90deg,
      transparent, v.$primary-color 40%, v.$success-color 70%, transparent
    );
    opacity: 0.9;
  }

  .header-left {
    display: flex; align-items: center; gap: 10px; min-width: 0;
    .collapse-btn {
      width: 34px; height: 34px;
      display: flex; align-items: center; justify-content: center;
      background: linear-gradient(180deg, #1a283c 0%, #0f1a2b 100%);
      border: 1px solid v.$border-color;
      color: v.$text-secondary;
      cursor: pointer;
      clip-path: polygon(0 4px, 4px 0, 100% 0, 100% calc(100% - 4px), calc(100% - 4px) 100%, 0 100%);
      &:hover { color: v.$primary-color; border-color: v.$primary-color; box-shadow: inset 0 0 10px rgba(26,179,255,0.2); }
    }
    .hb-divider { width: 1px; height: 22px; background: v.$border-color; }
    .hb-marker { color: v.$primary-color; text-shadow: 0 0 6px v.$primary-color; font-size: 12px; }
    .hb-crumbs {
      :deep(.el-breadcrumb__item .el-breadcrumb__inner) {
        font-family: v.$font-display; letter-spacing: 0.08em;
        color: v.$text-secondary !important;
      }
      :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
        color: v.$text-primary !important;
        font-weight: 600;
      }
      :deep(.el-breadcrumb__separator) { color: v.$text-placeholder; margin: 0 4px; }
    }
  }

  .header-meta {
    display: flex; align-items: center; gap: 18px;
    padding: 6px 14px;
    background: v.$panel-bg-deep;
    border: 1px solid v.$border-color;
    border-radius: 2px;
    .hm-item {
      display: inline-flex; align-items: center; gap: 6px;
      font-family: v.$font-display;
      .hm-k {
        font-size: 10px; letter-spacing: 0.15em; color: v.$text-secondary;
      }
      .hm-v {
        font-weight: 600; color: v.$text-primary; letter-spacing: 0.04em;
      }
      .hm-v.mono {
        font-family: v.$font-mono; color: v.$primary-color;
        text-shadow: 0 0 4px rgba(26,179,255,0.55);
      }
    }
  }

  .header-right {
    display: flex; align-items: center; gap: 14px;
    .header-icon-btn {
      width: 34px; height: 34px;
      display: flex; align-items: center; justify-content: center;
      background: linear-gradient(180deg, #1a283c 0%, #0f1a2b 100%);
      border: 1px solid v.$border-color;
      color: v.$text-secondary;
      cursor: pointer;
      font-size: 16px;
      clip-path: polygon(0 4px, 4px 0, 100% 0, 100% calc(100% - 4px), calc(100% - 4px) 100%, 0 100%);
      &:hover { color: v.$primary-color; border-color: v.$primary-color; }
    }
    .task-badge { cursor: pointer; }
    :deep(.el-badge__content) {
      background: v.$danger-color; border: 1px solid v.$danger-color;
      box-shadow: v.$glow-danger;
      font-family: v.$font-mono; font-weight: 700;
    }

    .user-info {
      display: flex; align-items: center; gap: 10px;
      padding: 5px 12px 5px 6px;
      background: linear-gradient(180deg, #162233 0%, #0e1725 100%);
      border: 1px solid v.$border-color;
      border-radius: 2px;
      cursor: pointer;
      clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
      &:hover { border-color: v.$primary-color; box-shadow: inset 0 0 14px rgba(26,179,255,0.15); }
      .arrow-down { color: v.$text-secondary; font-size: 12px; }
    }
    .avatar-ring {
      position: relative;
      .ring-on {
        position: absolute; right: -2px; bottom: -2px;
        width: 10px; height: 10px;
        border-radius: 50%;
        background: v.$success-color;
        box-shadow: 0 0 6px v.$success-color, 0 0 0 2px v.$header-bg;
      }
    }
    .user-meta { display: flex; flex-direction: column; line-height: 1.15; gap: 2px; }
    .user-name {
      color: v.$text-primary;
      font-size: 13px;
      font-weight: 600;
    }
    .user-role {
      color: v.$text-secondary;
      font-family: v.$font-mono;
      font-size: 10px;
      letter-spacing: 0.12em;
    }
  }
}

/* ================= 主内容 ================= */
.main-content {
  padding: 0;
  overflow-y: auto;
  background: transparent;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(4px); }

/* ================= 全局 Led Dot（scoped 重写，兼容引用） ================= */
.led-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: v.$text-secondary;
  &.led-on   { background: v.$success-color; box-shadow: v.$glow-success; }
  &.led-warn { background: v.$warning-color; box-shadow: v.$glow-warn; }
  &.led-off  { background: v.$danger-color;  box-shadow: v.$glow-danger; }
  &.led-run  {
    background: v.$primary-color; box-shadow: v.$glow-primary;
    animation: pulse 1.2s infinite;
  }
}
@keyframes pulse {
  0%,100% { opacity: 1; }
  50%     { opacity: 0.4; }
}
.mono { font-family: v.$font-mono; }
</style>
