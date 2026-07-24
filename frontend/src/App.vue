<template>
  <section v-if="!authToken" class="auth-page">
    <form class="auth-panel" @submit.prevent="authMode === 'login' ? loginManual() : registerAccount()">
      <div class="auth-brand">
        <span class="brand-mark">LP</span>
        <div>
          <strong>LifePulse</strong>
          <small>本地生活交易平台</small>
        </div>
      </div>
      <h1>{{ authMode === 'login' ? '登录 LifePulse' : '创建账号' }}</h1>
      <p>{{ authMode === 'login' ? '登录后查看商户、领取优惠券、管理订单。' : '注册完成后将自动登录为普通用户。' }}</p>
      <div class="auth-switch" role="tablist">
        <button type="button" :class="{active: authMode === 'login'}" @click="authMode = 'login'">登录</button>
        <button type="button" :class="{active: authMode === 'register'}" @click="authMode = 'register'">注册</button>
      </div>
      <label>账号<input v-model.trim="loginUsername" autocomplete="username" placeholder="输入账号"></label>
      <label>密码<input v-model="loginPassword" type="password" autocomplete="current-password" placeholder="至少 6 位"></label>
      <label v-if="authMode === 'register'">确认密码<input v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码"></label>
      <button class="auth-submit" type="submit">{{ authMode === 'login' ? '登录进入平台' : '注册并进入平台' }}</button>
      <small class="auth-note">管理员和商户使用已有账号登录；新注册账号默认为普通用户。</small>
    </form>
    <div class="toast" :class="{show: toastMessage}">{{ toastMessage }}</div>
  </section>

  <div v-else class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">LP</span>
        <div>
          <strong>LifePulse</strong>
          <small>本地生活交易平台</small>
        </div>
      </div>

      <nav>
        <button v-for="item in visibleNavItems" :key="item.key" class="nav-item" :class="{active: view === item.key}" @click="switchView(item.key)">
          {{ item.label }}
        </button>
      </nav>

      <div class="profile">
        <span>当前用户</span>
        <strong>{{ user.username }} / {{ user.userId }}</strong>
        <small>{{ user.role }}</small>
        <button class="mini danger" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <h1>LifePulse 本地生活</h1>
          <p>发现附近好店，领取限时优惠。</p>
        </div>
        <button class="ghost" @click="refreshAll">刷新数据</button>
      </header>

      <section class="hero">
        <div>
          <span class="eyebrow">本周精选</span>
          <h2>好店、优惠和订单，都在这里</h2>
          <p>查看附近商户，领取限时优惠券；下单后的支付和订单状态也能随时查看。</p>
          <div class="hero-actions">
            <button @click="switchView('vouchers')">去抢优惠券</button>
            <button class="secondary" @click="switchView('shops')">浏览商户</button>
          </div>
        </div>
      </section>

      <section v-if="canViewOperations" class="stats-grid">
        <div v-for="item in statItems" :key="item.label" class="stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section v-show="view === 'home'" class="panel">
        <div class="section-title">
          <h3>推荐商户</h3>
          <span>按热度排序</span>
        </div>
        <div class="card-grid">
          <div v-if="shops.length === 0" class="empty-card">暂无推荐商户，先确认后端 `/api/shops` 是否返回数据。</div>
          <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" :can-manage="canViewOperations" @review="publishReview" @toggle-status="toggleShopStatus" @boost="boostShop" />
        </div>
      </section>

      <section v-show="view === 'shops'" class="panel">
        <div class="section-title">
          <h3>商户探店</h3>
          <span>Caffeine 本地缓存承接热门详情</span>
        </div>
        <div class="card-grid">
          <div v-if="shops.length === 0" class="empty-card">暂无商户数据。</div>
          <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" :can-manage="canViewOperations" @review="publishReview" @toggle-status="toggleShopStatus" @boost="boostShop" />
        </div>
      </section>

      <section v-show="view === 'vouchers'" class="panel">
        <div class="section-title">
          <h3>优惠券秒杀</h3>
          <span>先申请资格 Token，再抢券</span>
        </div>
        <div class="card-grid">
          <article v-for="voucher in vouchers" :key="voucher.id" class="card">
            <div>
              <h4>{{ voucher.title }}</h4>
              <div class="price">{{ money(voucher.salePrice) }}</div>
              <div class="meta">
                <span>原价 {{ money(voucher.originalPrice) }}</span>
                <span>库存 {{ voucher.stock }} / 商户ID {{ voucher.shopId }}</span>
                <span>{{ formatTime(voucher.beginTime) }} 至 {{ formatTime(voucher.endTime) }}</span>
              </div>
              <div class="tag-row">
                <span class="tag">Redis Lua</span>
                <span class="tag">Outbox</span>
                <span class="tag">MQ削峰</span>
              </div>
            </div>
            <div class="card-actions">
              <button class="light" @click="applyQualification(voucher.id)">申请资格</button>
              <button @click="seckill(voucher.id)">立即抢券</button>
            </div>
          </article>
        </div>
      </section>

      <section v-show="view === 'groups'" class="panel">
        <div class="section-title"><h3>拼团活动</h3><span>邀请好友一起拼，达到人数即可成团</span></div>
        <div class="card-grid">
          <article v-for="activity in groupActivities" :key="activity.id" class="card">
            <div><h4>{{ activity.title }}</h4><div class="price">{{ money(activity.groupPrice) }}</div><div class="meta"><span>{{ activity.description }}</span><span>{{ activity.requiredSize }} 人成团 / 剩余 {{ activity.totalStock - activity.joinedCount }} 个名额</span><span>活动截止 {{ formatTime(activity.endTime) }}</span></div></div>
            <div class="card-actions"><button :disabled="groupDetails[activity.id] && !groupDetails[activity.id].eligibility.eligible" @click="createGroup(activity.id)">发起拼团</button></div>
            <div class="card-actions"><button class="light" @click="loadActivityGroups(activity.id)">查看可加入的团</button></div>
            <div v-if="groupDetails[activity.id]" class="group-detail">
              <span v-if="groupDetails[activity.id].shop">商户：{{ groupDetails[activity.id].shop.name }}</span>
              <span>关联券：{{ groupDetails[activity.id].voucher.title }}</span>
              <span>{{ groupDetails[activity.id].eligibility.reason }}</span>
            </div>
            <div v-for="group in groupDetails[activity.id]?.openGroups || []" :key="group.id" class="open-group"><span>{{ group.currentSize }}/{{ group.requiredSize }} 人，{{ formatTime(group.expireTime) }} 截止</span><button class="mini" @click="joinGroup(group.id)">加入</button></div>
          </article>
        </div>
      </section>

      <section v-show="view === 'my-groups'" class="panel">
        <div class="section-title"><h3>我的拼团</h3><span>支付后等待好友参团，人数满足即成团</span></div>
        <div class="card-grid"><article v-for="item in myGroups" :key="item.member.id" class="card"><div><h4>{{ item.activity.title }}</h4><div class="meta"><span>{{ item.group.currentSize }} / {{ item.group.requiredSize }} 人</span><span>状态：{{ item.group.status }}</span><span>截止 {{ formatTime(item.group.expireTime) }}</span></div></div><div class="card-actions"><button :disabled="item.member.status !== 'PENDING_PAY'" @click="payGroup(item.member.orderId)">支付拼团订单</button></div></article><div v-if="myGroups.length===0" class="empty-card">还没有参加拼团，去拼团活动页发起一个吧。</div></div>
      </section>

      <section v-show="view === 'notifications'" class="panel">
        <div class="section-title"><h3>消息通知</h3><span>拼团成团、失败退款和订单状态提醒</span></div>
        <div class="card-grid"><article v-for="item in notifications" :key="item.id" class="card"><div><h4>{{ item.title }}</h4><div class="meta"><span>{{ item.content }}</span><span>{{ formatTime(item.createdAt) }}</span></div></div><div class="card-actions"><button class="light" :disabled="item.readStatus" @click="readNotification(item.id)">{{ item.readStatus ? '已读' : '标记已读' }}</button></div></article><div v-if="notifications.length===0" class="empty-card">暂无消息通知。</div></div>
      </section>

      <section v-show="view === 'orders'" class="panel">
        <div class="section-title">
          <h3>我的订单</h3>
          <span>支付、取消、退款都走条件更新</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
            <tr>
              <th>订单ID</th>
              <th>券ID</th>
              <th>金额</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
            </thead>
            <tbody>
            <tr v-if="orders.length === 0">
              <td colspan="6">暂无订单，先去优惠券秒杀页抢一张。</td>
            </tr>
            <tr v-for="order in orders" :key="order.id">
              <td>{{ order.id }}</td>
              <td>{{ order.voucherId }}</td>
              <td>{{ money(order.amount) }}</td>
              <td><span class="status" :class="order.status">{{ order.status }}</span></td>
              <td>{{ formatTime(order.createdAt) }}</td>
              <td>
                <button :disabled="order.status !== 'PENDING'" @click="payOrder(order.id)">支付</button>
                <button :disabled="order.status !== 'PENDING'" @click="cancelOrder(order.id)">取消</button>
                <button :disabled="order.status !== 'PAID'" @click="refundOrder(order.id)">退款</button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-show="view === 'ai'" class="panel">
        <div class="section-title">
          <h3>在线客服</h3>
          <span>帮你查商户、优惠券和订单</span>
        </div>
        <div class="ai-layout">
          <div ref="chatBoxRef" class="chat-box">
            <div v-for="message in messages" :key="message.id" class="chat-message" :class="message.role">
              <strong>{{ message.role === 'user' ? '我' : 'LifePulse 客服' }}</strong>
              <p>{{ message.content }}</p>
            </div>
          </div>
        </div>
        <div class="quick-questions">
          <button class="light" :disabled="isAnswering" @click="askSuggestion('附近有什么咖啡店？')">附近有什么咖啡店？</button>
          <button class="light" :disabled="isAnswering" @click="askSuggestion('现在有哪些优惠券？')">现在有哪些优惠券？</button>
          <button class="light" :disabled="isAnswering" @click="askSuggestion('帮我查询我的订单')">查询我的订单</button>
        </div>
        <form class="chat-form" @submit.prevent="askAi">
          <input v-model.trim="question" :disabled="isAnswering" placeholder="输入你的问题，例如：附近有什么咖啡店？" @keydown.enter.prevent="askAi">
          <button type="submit" :disabled="isAnswering || !question">{{ isAnswering ? '正在回复' : '发送' }}</button>
        </form>
      </section>

      <section v-show="view === 'admin'" class="panel">
        <div class="section-title">
          <h3>运营看板</h3>
          <span>订单、Outbox 与 Agent 诊断</span>
        </div>
        <div class="admin-grid">
          <form v-if="canViewOperations" class="activity-form" @submit.prevent="configureActivity">
            <div class="form-heading"><h4>新建拼团活动</h4><span>发布后立即进入活动列表</span></div>
            <label class="wide-field">活动名称<input v-model.trim="activityForm.title" placeholder="例如：周末双人下午茶拼团"></label>
            <label class="wide-field">活动说明<input v-model.trim="activityForm.description" placeholder="填写用户可见的活动规则说明"></label>
            <label>关联优惠券 ID<input v-model.number="activityForm.voucherId" type="number" min="1"></label>
            <label>成团人数<input v-model.number="activityForm.requiredSize" type="number" min="2"></label>
            <label>拼团价格（元）<input v-model.number="activityForm.groupPrice" type="number" min="0.01" step="0.01"></label>
            <label>活动名额<input v-model.number="activityForm.totalStock" type="number" min="1"></label>
            <label>参与人群<select v-model="activityForm.allowedRole"><option value="USER">普通用户</option><option value="ALL">所有角色</option></select></label>
            <button type="submit">发布活动</button>
          </form>
          <div v-if="user.role === 'ADMIN'" class="table-wrap">
            <table>
              <thead>
              <tr>
                <th>Outbox ID</th>
                <th>事件</th>
                <th>Topic</th>
                <th>状态</th>
              </tr>
              </thead>
              <tbody>
              <tr v-if="outbox.length === 0">
                <td colspan="4">暂无 Outbox 消息。</td>
              </tr>
              <tr v-for="item in outbox" :key="item.id">
                <td>{{ item.id }}</td>
                <td>{{ item.eventType }}</td>
                <td>{{ item.topic }}</td>
                <td><span class="status" :class="item.status">{{ item.status }}</span></td>
              </tr>
              </tbody>
            </table>
          </div>
          <div class="assistant-box">
            <h4>AI 运营诊断</h4>
            <div v-if="diagnosis" class="diagnosis-box">
              <div class="risk" :class="diagnosis.riskLevel">{{ diagnosis.riskLevel }}</div>
              <p>{{ diagnosis.summary }}</p>
              <strong>发现</strong>
              <ul>
                <li v-for="item in diagnosis.findings" :key="item">{{ item }}</li>
              </ul>
              <strong>建议</strong>
              <ul>
                <li v-for="item in diagnosis.suggestions" :key="item">{{ item }}</li>
              </ul>
              <strong>证据</strong>
              <div class="evidence-row">
                <span v-for="item in diagnosis.evidence" :key="item">{{ item }}</span>
              </div>
            </div>
            <button class="light block-button" @click="loadDiagnosis">重新诊断</button>
          </div>
          <form v-if="user.role === 'ADMIN'" class="activity-form" @submit.prevent="savePolicy">
            <div class="form-heading"><h4>运行策略</h4><span>调整热点入口和订单超时策略</span></div>
            <label>缓存 TTL（秒）<input v-model.number="policyForm.shopCacheTtlSeconds" type="number" min="30" max="3600"></label>
            <label>抢券限流 QPS<input v-model.number="policyForm.seckillRateLimitPerSecond" type="number" min="1" max="5000"></label>
            <label>拼团限流 QPS<input v-model.number="policyForm.groupRateLimitPerSecond" type="number" min="1" max="5000"></label>
            <label>订单超时（分钟）<input v-model.number="policyForm.orderTimeoutMinutes" type="number" min="1" max="120"></label>
            <label class="switch-field"><input v-model="policyForm.seckillEnabled" type="checkbox"> 秒杀入口开启</label>
            <button type="submit">保存策略</button>
          </form>
          <div class="assistant-box">
            <h4>系统指标</h4>
            <div class="evidence-row metrics-row">
              <span v-for="(value, key) in metrics" :key="key">{{ key }}：{{ value }}</span>
              <span v-if="Object.keys(metrics).length === 0">暂无指标数据</span>
            </div>
            <button class="light block-button" @click="loadMetrics">刷新指标</button>
          </div>
          <div class="assistant-box logs-panel">
            <h4>日志检索</h4>
            <form class="log-search" @submit.prevent="loadLogs">
              <input v-model.trim="logKeyword" placeholder="输入 traceId、订单ID、Outbox 或关键词">
              <button type="submit">检索</button>
            </form>
            <div class="log-list">
              <div v-if="logs.length === 0" class="empty-card">暂无日志结果。</div>
              <article v-for="item in logs" :key="`${item.timestamp}-${item.message}`" class="log-item">
                <strong>{{ item.level || '-' }} {{ item.traceId || '' }}</strong>
                <span>{{ item.timestamp }}</span>
                <p>{{ item.message }}</p>
              </article>
            </div>
          </div>
        </div>
      </section>

      <div class="toast" :class="{show: toastMessage}">{{ toastMessage }}</div>
    </main>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, ref} from "vue";

// 开发时留空，让 Vite 把 /api 代理到 8110；避免浏览器跨端口直连被拦截。
const API_BASE = import.meta.env.VITE_API_BASE || "";
const SESSION_STORAGE_KEY = "lifepulse.session";
const navItems = [
  {key: "home", label: "首页总览"},
  {key: "shops", label: "商户探店"},
  {key: "vouchers", label: "优惠券秒杀"},
  {key: "groups", label: "拼团活动"},
  {key: "my-groups", label: "我的拼团"},
  {key: "notifications", label: "消息通知"},
  {key: "orders", label: "我的订单"},
  {key: "ai", label: "AI 助手"},
  {key: "admin", label: "运营看板"}
];

const view = ref("home");
const authToken = ref("");
const user = ref({});
const stats = ref({});
const shops = ref([]);
const vouchers = ref([]);
const groupActivities = ref([]);
const myGroups = ref([]);
const notifications = ref([]);
const groupDetails = ref({});
const activityForm = ref({title:"",description:"",voucherId:1,requiredSize:2,groupPrice:29.9,totalStock:20,allowedRole:"USER"});
const orders = ref([]);
const outbox = ref([]);
const diagnosis = ref(null);
const metrics = ref({});
const logs = ref([]);
const logKeyword = ref("");
const policyForm = ref({
  shopCacheTtlSeconds: 600,
  seckillEnabled: true,
  seckillRateLimitPerSecond: 80,
  groupRateLimitPerSecond: 50,
  orderTimeoutMinutes: 15
});
const qualificationTokens = ref(new Map());
const toastMessage = ref("");
const question = ref("");
const chatBoxRef = ref(null);
const isAnswering = ref(false);
const authMode = ref("login");
const loginUsername = ref("");
const loginPassword = ref("");
const confirmPassword = ref("");
const canViewOperations = computed(() => ["ADMIN", "MERCHANT"].includes(user.value.role));
const visibleNavItems = computed(() => navItems.filter(item => item.key !== "admin" || canViewOperations.value));
const messages = ref([
  {
    id: 1,
    role: "assistant",
    content: "你好，我可以帮你查找商户、查看优惠券和查询订单。"
  }
]);

const statItems = computed(() => [
  ["商户", stats.value.shops ?? "-"],
  ["优惠券", stats.value.vouchers ?? "-"],
  ["订单", stats.value.orders ?? "-"],
  ["待支付", stats.value.pendingOrders ?? "-"],
  ["已支付", stats.value.paidOrders ?? "-"],
  ["Outbox待处理", stats.value.outboxPending ?? "-"]
].map(([label, value]) => ({label, value})));

async function api(path, options = {}) {
  const headers = {...(options.headers || {})};
  if (authToken.value) {
    headers.Authorization = `Bearer ${authToken.value}`;
  }
  const response = await fetch(`${API_BASE}${path}`, {...options, headers});
  const text = await response.text();
  let json;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(text || `请求失败：HTTP ${response.status}`);
  }
  if (!response.ok) {
    throw new Error(json.message || `请求失败：HTTP ${response.status}`);
  }
  if (json.code !== 200) {
    throw new Error(json.message || "请求失败");
  }
  return json.data;
}

async function loginDemoUser(username = "student", password = "123456") {
  const data = await api("/api/users/login", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({username, password})
  });
  saveSession(data);
  loginUsername.value = data.username;
}

async function loginManual() {
  if (!loginUsername.value || !loginPassword.value) {
    showToast("请输入账号和密码");
    return;
  }
  try {
    await loginDemoUser(loginUsername.value, loginPassword.value);
    showToast("登录成功");
    await refreshAll();
  } catch (error) {
    showToast(error.message);
  }
}

async function registerAccount() {
  if (!loginUsername.value || !loginPassword.value) {
    showToast("请输入账号和密码");
    return;
  }
  if (loginPassword.value !== confirmPassword.value) {
    showToast("两次密码输入不一致");
    return;
  }
  try {
    const data = await api("/api/users/register", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({username: loginUsername.value, password: loginPassword.value})
    });
    saveSession(data);
    confirmPassword.value = "";
    showToast("注册成功，已登录");
    await refreshAll();
  } catch (error) {
    showToast(error.message);
  }
}

function logout() {
  clearSession();
  loginPassword.value = "";
  confirmPassword.value = "";
  authMode.value = "login";
  view.value = "home";
  showToast("已退出登录");
}

function saveSession(data) {
  authToken.value = data.token;
  user.value = {
    userId: data.userId,
    username: data.username,
    role: data.role
  };
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({
    token: data.token,
    user: user.value
  }));
}

function clearSession() {
  authToken.value = "";
  user.value = {};
  orders.value = [];
  outbox.value = [];
  diagnosis.value = null;
  qualificationTokens.value.clear();
  localStorage.removeItem(SESSION_STORAGE_KEY);
}

async function restoreSession() {
  const saved = localStorage.getItem(SESSION_STORAGE_KEY);
  if (!saved) {
    return;
  }
  try {
    const session = JSON.parse(saved);
    if (!session?.token || !session?.user?.userId) {
      throw new Error("登录信息无效");
    }
    authToken.value = session.token;
    user.value = session.user;
    await loadOrders();
    await refreshAll();
  } catch {
    clearSession();
    showToast("登录已失效，请重新登录");
  }
}

function showToast(message) {
  toastMessage.value = message;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => {
    toastMessage.value = "";
  }, 2600);
}

function switchView(nextView) {
  if (!authToken.value && ["orders", "ai"].includes(nextView)) {
    showToast("请先登录后再使用该功能");
    return;
  }
  view.value = nextView;
  if (nextView === "orders") {
    loadOrders();
  }
  if (nextView === "groups") loadGroups();
  if (nextView === "my-groups") loadMyGroups();
  if (nextView === "notifications") loadNotifications();
  if (nextView === "admin") {
    loadAdmin();
  }
}

function formatTime(value) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 19);
}

function money(value) {
  return `¥${Number(value).toFixed(2)}`;
}

async function loadStats() {
  if (!canViewOperations.value) {
    stats.value = {};
    return;
  }
  stats.value = await api("/api/admin/stats");
}

async function loadShops() {
  shops.value = await api("/api/shops");
}

async function loadVouchers() {
  vouchers.value = await api("/api/vouchers");
}
async function loadGroups() { groupActivities.value = await api("/api/groups/activities"); }
async function loadActivityGroups(activityId) { try { groupDetails.value[activityId] = await api(`/api/groups/activities/${activityId}`); } catch(e){showToast(e.message);} }
async function loadMyGroups() { myGroups.value = await api("/api/groups/me"); }
async function loadNotifications() { notifications.value = await api("/api/notifications"); }
async function readNotification(id) { try { await api(`/api/notifications/${id}/read`,{method:"POST"}); await loadNotifications(); } catch(e){showToast(e.message);} }
async function createGroup(activityId) { try { await api(`/api/groups/activities/${activityId}`, {method:"POST"}); showToast("拼团已发起，请完成支付"); await Promise.all([loadGroups(), loadMyGroups(), loadOrders()]); view.value="my-groups"; } catch(e){showToast(e.message);} }
async function joinGroup(groupId) { try { await api(`/api/groups/${groupId}/join`, {method:"POST"}); showToast("已加入拼团，请完成支付"); await Promise.all([loadGroups(), loadMyGroups(), loadOrders()]); view.value="my-groups"; } catch(e){showToast(e.message);} }
async function payGroup(orderId) { try { await api(`/api/groups/orders/${orderId}/pay`, {method:"POST"}); showToast("支付成功，正在等待成团"); await Promise.all([loadMyGroups(),loadOrders(),loadGroups()]); } catch(e){showToast(e.message);} }
async function configureActivity() { try { const endTime = new Date(Date.now()+7*24*3600*1000).toISOString().slice(0,19); await api("/api/groups/admin/activities",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({...activityForm.value,endTime})}); showToast("拼团活动已发布"); await loadGroups(); } catch(e){showToast(e.message);} }

async function loadOrders() {
  orders.value = await api("/api/orders/me");
}

async function loadOutbox() {
  outbox.value = await api("/api/outbox");
}

async function loadDiagnosis() {
  diagnosis.value = await api("/api/agent/diagnosis");
}

async function loadMetrics() {
  metrics.value = await api("/api/agent/metrics");
}

async function loadLogs() {
  const keyword = encodeURIComponent(logKeyword.value || "");
  logs.value = await api(`/api/agent/logs?keyword=${keyword}&size=20`);
}

async function loadPolicy() {
  if (user.value.role !== "ADMIN") {
    return;
  }
  try {
    policyForm.value = await api("/api/admin/policies");
  } catch (error) {
    showToast(error.message);
  }
}

async function savePolicy() {
  try {
    policyForm.value = await api("/api/admin/policies", {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(policyForm.value)
    });
    showToast("运行策略已更新");
  } catch (error) {
    showToast(error.message);
  }
}

async function loadAdmin() {
  if (!canViewOperations.value) {
    return;
  }
  const tasks = [loadDiagnosis(), loadMetrics(), loadLogs()];
  if (user.value.role === "ADMIN") {
    tasks.push(loadOutbox());
    tasks.push(loadPolicy());
  } else {
    outbox.value = [];
  }
  await Promise.all(tasks);
}

async function publishReview(shopId) {
  if (!requireLogin()) {
    return;
  }
  try {
    await api(`/api/shops/${shopId}/reviews`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({score: 5, content: "环境不错，适合学习和小组讨论。"})
    });
    showToast("评价发布成功，商户缓存已失效");
    await Promise.all([loadShops(), loadStats()]);
  } catch (error) {
    showToast(error.message);
  }
}

async function toggleShopStatus(shop) {
  if (!canViewOperations.value) {
    showToast("当前账号无商户管理权限");
    return;
  }
  const nextStatus = shop.status === "OPEN" ? "CLOSED" : "OPEN";
  try {
    await api(`/api/shops/${shop.id}`, {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({status: nextStatus})
    });
    showToast(nextStatus === "OPEN" ? "商户已恢复营业，缓存已刷新" : "商户已暂停营业，缓存已刷新");
    await Promise.all([loadShops(), loadStats(), loadDiagnosis()]);
  } catch (error) {
    showToast(error.message);
  }
}

async function boostShop(shop) {
  if (!canViewOperations.value) {
    showToast("当前账号无商户管理权限");
    return;
  }
  try {
    await api(`/api/shops/${shop.id}`, {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({hotScore: Number(shop.hotScore || 0) + 50})
    });
    showToast("商户热度已更新，缓存已刷新");
    await Promise.all([loadShops(), loadStats(), loadDiagnosis()]);
  } catch (error) {
    showToast(error.message);
  }
}

async function applyQualification(voucherId) {
  if (!requireLogin()) {
    return;
  }
  try {
    const data = await api(`/api/vouchers/${voucherId}/qualification`, {method: "POST"});
    qualificationTokens.value.set(voucherId, data.qualificationToken);
    showToast(`资格 Token 已生成，有效 ${data.ttlSeconds} 秒`);
  } catch (error) {
    showToast(error.message);
  }
}

async function seckill(voucherId) {
  if (!requireLogin()) {
    return;
  }
  const token = qualificationTokens.value.get(voucherId);
  if (!token) {
    showToast("先点“申请资格”，再点“立即抢券”");
    return;
  }
  try {
    const data = await api(`/api/vouchers/${voucherId}/seckill?qualificationToken=${token}`, {method: "POST"});
    qualificationTokens.value.delete(voucherId);
    showToast(data.message);
    await Promise.all([loadOrders(), loadStats(), loadAdmin()]);
    switchView("orders");
  } catch (error) {
    showToast(error.message);
  }
}

async function payOrder(orderId) {
  try {
    await api(`/api/orders/${orderId}/pay`, {method: "POST"});
    showToast("支付成功");
    await Promise.all([loadOrders(), loadStats()]);
  } catch (error) {
    showToast(error.message);
  }
}

async function cancelOrder(orderId) {
  try {
    await api(`/api/orders/${orderId}/cancel`, {method: "POST"});
    showToast("订单已取消，库存已回补");
    await Promise.all([loadOrders(), loadStats(), loadVouchers()]);
  } catch (error) {
    showToast(error.message);
  }
}

async function refundOrder(orderId) {
  try {
    await api(`/api/orders/${orderId}/refund`, {method: "POST"});
    showToast("退款成功，库存已回补");
    await Promise.all([loadOrders(), loadStats(), loadVouchers()]);
  } catch (error) {
    showToast(error.message);
  }
}

function appendMessage(role, content) {
  const item = {id: Date.now() + Math.random(), role, content};
  messages.value.push(item);
  nextTick(() => {
    if (chatBoxRef.value) {
      chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight;
    }
  });
  return item;
}

async function askAi() {
  if (!requireLogin()) {
    return;
  }
  if (!question.value || isAnswering.value) {
    return;
  }
  const currentQuestion = question.value;
  question.value = "";
  appendMessage("user", currentQuestion);
  const answer = appendMessage("assistant", "正在查询，请稍候...");
  isAnswering.value = true;
  try {
    const data = await api("/api/ai/chat", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({question: currentQuestion})
    });
    answer.content = data.answer || "客服暂时没有找到可回复的内容。";
  } catch (error) {
    answer.content = error.message || "客服暂时无法回复，请稍后再试。";
  } finally {
    isAnswering.value = false;
    nextTick(() => {
      if (chatBoxRef.value) {
        chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight;
      }
    });
  }
}

function askSuggestion(text) {
  question.value = text;
  askAi();
}

function requireLogin() {
  if (authToken.value) {
    return true;
  }
  showToast("请先登录后再操作");
  return false;
}

async function refreshAll() {
  const tasks = [loadShops(), loadVouchers()];
  if (authToken.value) {
    tasks.push(loadOrders());
    if (canViewOperations.value) {
      tasks.push(loadStats());
      tasks.push(loadAdmin());
    }
  }
  const results = await Promise.allSettled(tasks);
  const rejected = results.find(result => result.status === "rejected");
  if (rejected) {
    showToast(rejected.reason?.message || "部分数据加载失败");
  }
}

const ShopCard = {
  props: {
    shop: {
      type: Object,
      required: true
    },
    canManage: {
      type: Boolean,
      default: false
    }
  },
  emits: ["review", "toggle-status", "boost"],
  template: `
    <article class="card">
      <div>
        <h4>{{ shop.name }}</h4>
        <div class="meta">
          <span>{{ shop.category }}</span>
          <span>{{ shop.address }}</span>
          <span>评分 {{ shop.avgScore }} / 评价 {{ shop.commentCount }} / 热度 {{ shop.hotScore }}</span>
          <span>状态 {{ shop.status }}</span>
        </div>
        <div class="tag-row">
          <span class="tag">探店</span>
          <span class="tag">本地缓存</span>
        </div>
      </div>
      <div class="card-actions">
        <button class="light" @click="$emit('review', shop.id)">发布评价</button>
        <button v-if="canManage" class="light" @click="$emit('boost', shop)">提升热度</button>
        <button v-if="canManage" @click="$emit('toggle-status', shop)">{{ shop.status === 'OPEN' ? '暂停营业' : '恢复营业' }}</button>
      </div>
    </article>
  `
};

onMounted(restoreSession);
</script>
