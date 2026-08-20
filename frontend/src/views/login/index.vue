<template>
  <div class="login-container">
    <!-- 背景扫描线 -->
    <div class="bg-grid"></div>
    <div class="bg-rad bg-rad-tl"></div>
    <div class="bg-rad bg-rad-br"></div>
    <div class="bg-scan"></div>

    <!-- 侧边铭牌带 -->
    <aside class="side-plate">
      <div class="sp-inner">
        <div class="sp-logo">
          <div class="sp-mark">
            <el-icon size="30" class="sp-icon"><Cpu /></el-icon>
          </div>
          <div class="sp-title">ENTERPRISE&nbsp;BRAIN</div>
          <div class="sp-sub">企 业 大 脑 · 综 合 智 能 中 枢</div>
        </div>

        <div class="sp-status">
          <div class="ss-block">
            <div class="ss-head">
              <div class="ss-ttl">SYSTEM&nbsp;HEALTH</div>
              <div class="ss-val mono">98.4&nbsp;%</div>
            </div>
            <div class="ss-bar"><span style="width:98%"></span></div>
          </div>

          <div class="ss-grid">
            <div class="ss-item"><i class="led-dot led-on"></i><span>GATEWAY</span><b>ONLINE</b></div>
            <div class="ss-item"><i class="led-dot led-run"></i><span>CORE&nbsp;APP</span><b>RUN</b></div>
            <div class="ss-item"><i class="led-dot led-on"></i><span>AUTH&nbsp;SVC</span><b>OK</b></div>
            <div class="ss-item"><i class="led-dot led-warn"></i><span>QUEUE</span><b>LOW</b></div>
          </div>
        </div>

        <div class="sp-feature">
          <div class="sf-row"><el-icon><Connection /></el-icon><span>ERP · MES · OA &nbsp;三系统联动</span></div>
          <div class="sf-row"><el-icon><Aim /></el-icon><span>UNIFIED&nbsp;TASK&nbsp;CENTER · 统一任务中枢</span></div>
          <div class="sf-row"><el-icon><DataBoard /></el-icon><span>OEE&nbsp;DASHBOARD · 工业数据驾驶舱</span></div>
        </div>

        <div class="sp-foot">
          <div class="sf-code mono">EB-1000 &nbsp;·&nbsp; v1.0.0 &nbsp;·&nbsp; BUILD&nbsp;2026.08</div>
          <div class="sf-copy">© 2026 Enterprise Brain Platform · Confidential</div>
        </div>
      </div>
    </aside>

    <!-- 登录舱 -->
    <section class="login-main">
      <div class="lm-top">
        <div class="lm-node mono">
          <span>NODE</span><b>MASTER-01</b>
        </div>
        <div class="lm-clock mono">
          <i class="led-dot led-run"></i>
          {{ nowTime }}
        </div>
      </div>

      <div class="login-box">
        <div class="lb-head">
          <div class="lb-leds">
            <i class="led-dot led-off"></i>
            <i class="led-dot led-warn"></i>
            <i class="led-dot led-on"></i>
          </div>
          <div class="lb-serial mono">SN · EB-LOGIN-2026-0817</div>
        </div>

        <div class="login-header">
          <div class="lh-emblem">
            <el-icon size="38"><Cpu /></el-icon>
          </div>
          <h1>OPERATOR&nbsp;LOGIN</h1>
          <p>请输入中控室操作员账号与密码进行身份认证</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
          <div class="lf-group">
            <div class="lf-label"><el-icon><User /></el-icon> USER-ID</div>
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="请输入账号" size="large">
                <template #prefix>
                  <span class="mono" style="color:#3f4f66">USR/</span>
                </template>
              </el-input>
            </el-form-item>
          </div>

          <div class="lf-group">
            <div class="lf-label"><el-icon><Lock /></el-icon> PASSWORD</div>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              >
                <template #prefix>
                  <span class="mono" style="color:#3f4f66">PWD/</span>
                </template>
              </el-input>
            </el-form-item>
          </div>

          <div class="lf-options">
            <el-checkbox v-model="form.remember">
              <span class="mono">REMEMBER&nbsp;SESSION</span>
            </el-checkbox>
            <el-checkbox v-model="form.sso">
              <span class="mono">SSO&nbsp;OAUTH2</span>
            </el-checkbox>
          </div>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              <span class="mono">▶&nbsp; ENTER&nbsp;SYSTEM</span>
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <el-tag effect="dark" type="primary" size="small" class="mono">
            DEMO&nbsp;ACC · admin / 123456
          </el-tag>
        </div>
      </div>

      <div class="lm-btm mono">
        <span>SECURE&nbsp;CHANNEL&nbsp;TLSv1.3</span>
        <span>·</span>
        <span>AUTH‑LEVEL&nbsp;3</span>
        <span>·</span>
        <span>SESS&nbsp;TMO&nbsp;30MIN</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { Cpu, User, Lock, Connection, Aim, DataBoard } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const nowTime = ref('')

const form = reactive({
  username: 'admin',
  password: '123456',
  remember: true,
  sso: false,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

let timer: any = null
const updateTime = () => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  nowTime.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await new Promise((r) => setTimeout(r, 600))
        userStore.setToken('mock-dev-token')
        ElMessage.success('身份验证通过 · 正在进入中控室')
        const redirect = (route.query.redirect as string) || '/dashboard'
        router.push(redirect)
      } finally {
        loading.value = false
      }
    }
  })
}

onMounted(() => { updateTime(); timer = setInterval(updateTime, 1000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as v;

.mono { font-family: v.$font-mono; }
.led-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: v.$text-secondary;
  &.led-on   { background: v.$success-color; box-shadow: v.$glow-success; }
  &.led-warn { background: v.$warning-color; box-shadow: v.$glow-warn; }
  &.led-off  { background: v.$danger-color;  box-shadow: v.$glow-danger; }
  &.led-run  { background: v.$primary-color; box-shadow: v.$glow-primary; animation: pulse 1.2s infinite; }
}
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity: 0.4; } }

/* ============ 登录容器 ============ */
.login-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: stretch;
  background:
    radial-gradient(ellipse at 10% 0%, rgba(26,179,255,0.12) 0%, transparent 55%),
    radial-gradient(ellipse at 100% 100%, rgba(0,255,148,0.07) 0%, transparent 55%),
    linear-gradient(180deg, #070c14 0%, #0b1422 100%);
  position: relative;
  overflow: hidden;
  color: v.$text-primary;
}
.bg-grid {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(26,179,255,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(26,179,255,0.04) 1px, transparent 1px);
  background-size: 32px 32px;
  pointer-events: none;
  mask-image: radial-gradient(ellipse at 50% 40%, black 0%, transparent 80%);
}
.bg-rad {
  position: absolute; border-radius: 50%; filter: blur(70px); opacity: 0.45; pointer-events: none;
}
.bg-rad-tl { width: 520px; height: 520px; top: -140px; left: -140px; background: radial-gradient(circle, #1ab3ff 0%, transparent 65%); }
.bg-rad-br { width: 520px; height: 520px; bottom: -140px; right: -140px; background: radial-gradient(circle, #00ff94 0%, transparent 65%); opacity: 0.28; }
.bg-scan {
  position: absolute; left: 0; right: 0; top: 0; height: 2px;
  background: linear-gradient(90deg, transparent, v.$primary-color, transparent);
  opacity: 0.45;
  animation: scan 6s linear infinite;
}
@keyframes scan {
  0%   { transform: translateY(0); }
  100% { transform: translateY(100vh); }
}

/* ============ 侧铭牌 ============ */
.side-plate {
  width: 420px;
  flex-shrink: 0;
  position: relative;
  background:
    linear-gradient(180deg, rgba(26,179,255,0.06) 0%, transparent 60%),
    linear-gradient(180deg, #0a121e 0%, #080d17 100%);
  border-right: 1px solid v.$border-color;
  box-shadow: inset -1px 0 0 rgba(26,179,255,0.18);
  &::before {
    content: ''; position: absolute; left: 0; right: 0; top: 0; height: 3px;
    background: linear-gradient(90deg, v.$primary-color 0%, v.$success-color 100%);
    box-shadow: 0 0 12px rgba(26,179,255,0.5);
  }
}
.sp-inner {
  position: relative; z-index: 1;
  padding: 32px 36px;
  height: 100%;
  display: flex; flex-direction: column;
}
.sp-logo { display: flex; flex-direction: column; gap: 10px; align-items: flex-start; margin-bottom: 28px; }
.sp-mark {
  width: 52px; height: 52px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1a2a45 0%, #0a1320 100%);
  border: 1px solid v.$border-accent;
  box-shadow: inset 0 0 10px rgba(26,179,255,0.25), v.$glow-primary;
  clip-path: polygon(0 8px, 8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%);
}
.sp-icon { color: v.$primary-color; filter: drop-shadow(0 0 5px rgba(26,179,255,0.8)); }
.sp-title {
  font-family: v.$font-display; font-weight: 700;
  font-size: 24px; letter-spacing: 0.14em; color: #fff;
  text-shadow: 0 0 8px rgba(26,179,255,0.5);
}
.sp-sub {
  font-size: 13px; letter-spacing: 0.22em; color: v.$text-secondary;
  padding-left: 2px;
}

/* 状态模块 */
.sp-status { padding: 16px; background: v.$panel-bg-deep; border: 1px solid v.$border-color; margin-bottom: 22px;
  clip-path: polygon(0 6px, 6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%);
}
.ss-block { margin-bottom: 14px; }
.ss-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px; }
.ss-ttl { font-family: v.$font-display; letter-spacing: 0.14em; font-size: 11px; color: v.$text-secondary; }
.ss-val { font-size: 18px; font-weight: 700; color: v.$success-color; text-shadow: 0 0 5px rgba(0,255,148,0.45); }
.ss-bar { height: 8px; background: #0a1220; border: 1px solid v.$border-color; position: relative; overflow: hidden;
  span {
    display: block; height: 100%;
    background: linear-gradient(90deg, v.$primary-color, v.$success-color);
    box-shadow: 0 0 8px rgba(0,255,148,0.6);
  }
}
.ss-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 10px; }
.ss-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: rgba(255,255,255,0.02); border: 1px dashed v.$border-color;
  font-family: v.$font-mono; font-size: 11px; color: v.$text-regular; letter-spacing: 0.06em;
  span { color: v.$text-secondary; }
  b { margin-left: auto; color: v.$text-primary; }
}

/* 特色 */
.sp-feature { flex: 1; display: flex; flex-direction: column; gap: 10px; }
.sf-row {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  background: v.$panel-bg;
  border: 1px solid v.$border-color;
  border-left: 3px solid v.$primary-color;
  color: v.$text-regular;
  font-family: v.$font-sans; font-size: 13px;
  letter-spacing: 0.04em;
  clip-path: polygon(0 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%);
  :deep(.el-icon) { color: v.$primary-color; }
}

/* 底部 */
.sp-foot { margin-top: 20px; padding-top: 12px; border-top: 1px dashed v.$border-color; line-height: 1.7;
  .sf-code { font-size: 12px; color: v.$primary-color; text-shadow: 0 0 3px rgba(26,179,255,0.4); letter-spacing: 0.12em; }
  .sf-copy { font-size: 11px; color: v.$text-secondary; margin-top: 2px; }
}

/* ============ 右侧主登录 ============ */
.login-main {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column;
  padding: 30px 46px;
  position: relative;
}
.lm-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.lm-node {
  display: inline-flex; align-items: center; gap: 10px;
  padding: 6px 14px;
  background: v.$panel-bg-deep;
  border: 1px solid v.$border-color;
  font-size: 12px; color: v.$text-secondary;
  letter-spacing: 0.1em;
  b { color: v.$primary-color; font-weight: 700; text-shadow: 0 0 4px rgba(26,179,255,0.5); }
}
.lm-clock {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 14px;
  background: rgba(26,179,255,0.06);
  border: 1px solid rgba(26,179,255,0.3);
  font-size: 13px; color: v.$primary-color; letter-spacing: 0.08em;
  text-shadow: 0 0 4px rgba(26,179,255,0.5);
}

/* 登录面板 */
.login-box {
  width: 460px;
  margin: auto;
  background:
    linear-gradient(180deg, v.$panel-bg 0%, v.$panel-bg-deep 100%);
  border: 1px solid v.$border-color;
  padding: 10px 40px 30px;
  box-shadow: 0 18px 60px rgba(0,0,0,0.7), 0 0 0 1px rgba(26,179,255,0.18);
  clip-path: polygon(0 12px, 12px 0, calc(100% - 12px) 0, 100% 12px, 100% calc(100% - 12px), calc(100% - 12px) 100%, 12px 100%, 0 calc(100% - 12px));
  position: relative;
}
/* 面板装饰角 */
.login-box::before, .login-box::after {
  content: ''; position: absolute; width: 32px; height: 32px;
  border: 2px solid v.$primary-color; opacity: 0.7; pointer-events: none;
}
.login-box::before { top: 12px; left: 12px; border-right: none; border-bottom: none; }
.login-box::after  { bottom: 12px; right: 12px; border-left: none; border-top: none; }

.lb-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 0 12px;
  border-bottom: 1px dashed v.$border-color;
  margin: 0 -12px 10px;
  padding-left: 12px; padding-right: 12px;
}
.lb-leds { display: inline-flex; gap: 8px; }
.lb-serial { font-size: 11px; color: v.$text-secondary; letter-spacing: 0.14em; }

/* 头部标题 */
.login-header {
  text-align: center;
  padding: 14px 0 22px;
  margin-bottom: 4px;
}
.lh-emblem {
  width: 72px; height: 72px;
  margin: 0 auto 14px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, rgba(26,179,255,0.2), rgba(0,255,148,0.08));
  border: 1px solid rgba(26,179,255,0.5);
  border-radius: 50%;
  color: v.$primary-color;
  box-shadow: inset 0 0 20px rgba(26,179,255,0.25), 0 0 20px rgba(26,179,255,0.3);
}
.login-header h1 {
  margin: 0 0 8px;
  font-family: v.$font-display; font-weight: 700;
  font-size: 24px; letter-spacing: 0.2em;
  color: #fff; text-shadow: 0 0 10px rgba(26,179,255,0.55);
}
.login-header p { margin: 0; color: v.$text-secondary; font-size: 12px; letter-spacing: 0.04em; }

/* 表单 */
.login-form {
  .login-btn { width: 100%; }
}
.lf-group { margin-bottom: 12px; }
.lf-label {
  display: inline-flex; align-items: center; gap: 6px;
  font-family: v.$font-display; letter-spacing: 0.12em;
  font-size: 11px; color: v.$text-secondary; margin: 0 0 6px 2px;
  :deep(.el-icon) { color: v.$primary-color; }
}
.lf-options {
  display: flex; align-items: center; justify-content: space-between;
  margin: -4px 2px 10px;
  color: v.$text-secondary; font-size: 12px;
  :deep(.el-checkbox__label) { color: v.$text-secondary; }
}

.login-footer { text-align: center; margin-top: 14px; }

.lm-btm {
  margin-top: 28px;
  display: flex; align-items: center; justify-content: center; gap: 14px;
  font-size: 11px; letter-spacing: 0.14em; color: v.$text-secondary;
  padding: 8px 0;
  border-top: 1px dashed v.$border-color;
}
</style>
