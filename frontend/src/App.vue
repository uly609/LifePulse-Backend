<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">LP</span>
        <div>
          <strong>LifePulse</strong>
          <small>本地生活交易平台</small>
        </div>
      </div>

      <nav>
        <button v-for="item in navItems" :key="item.key" class="nav-item" :class="{active: view === item.key}" @click="switchView(item.key)">
          {{ item.label }}
        </button>
      </nav>

      <div class="profile">
        <span>当前用户</span>
        <strong>{{ user.username }} / {{ user.userId }}</strong>
        <small>{{ user.role }} 演示账号</small>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <h1>本地生活拼团探店营销交易平台</h1>
          <p>Vue 3 前端、Spring Boot 后端、Redis/RocketMQ/Outbox 交易链路、MCP + 大模型智能客服。</p>
        </div>
        <button class="ghost" @click="refreshAll">刷新数据</button>
      </header>

      <section class="hero">
        <div>
          <span class="eyebrow">Full Stack Demo</span>
          <h2>交易、运营与 AI 助手一体化</h2>
          <p>抢券链路走资格 Token、Redis Lua、RocketMQ 和 Outbox；AI 助手会调用业务工具后再请求大模型。</p>
          <div class="hero-actions">
            <button @click="switchView('vouchers')">去抢优惠券</button>
            <button class="secondary" @click="switchView('ai')">问 AI 助手</button>
          </div>
        </div>
        <div class="flow-card">
          <span>Vue 3 / Vite</span>
          <b>JWT + RBAC</b>
          <span>Redis Lua</span>
          <b>RocketMQ + Outbox</b>
          <span>MCP Tool</span>
          <b>AI API + SSE</b>
        </div>
      </section>

      <section class="stats-grid">
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
          <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" @review="publishReview" />
        </div>
      </section>

      <section v-show="view === 'shops'" class="panel">
        <div class="section-title">
          <h3>商户探店</h3>
          <span>Caffeine 本地缓存承接热门详情</span>
        </div>
        <div class="card-grid">
          <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" @review="publishReview" />
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
          <h3>AI 智能客服</h3>
          <span>大模型 API + MCP 工具 + SSE 流式输出</span>
        </div>
        <div class="ai-layout">
          <div ref="chatBoxRef" class="chat-box">
            <div v-for="message in messages" :key="message.id" class="chat-message" :class="message.role">
              <strong>{{ message.role === 'user' ? '我' : 'LifePulse Assistant' }}</strong>
              <p>{{ message.content }}</p>
            </div>
          </div>
          <div class="ai-side">
            <h4>已封装工具</h4>
            <span v-for="tool in mcpTools" :key="tool">{{ tool }}</span>
          </div>
        </div>
        <form class="chat-form" @submit.prevent="askAi">
          <input v-model.trim="question" placeholder="问一句，比如：帮我分析现在优惠券秒杀链路有没有风险">
          <button type="submit">发送</button>
        </form>
      </section>

      <section v-show="view === 'admin'" class="panel">
        <div class="section-title">
          <h3>运营看板</h3>
          <span>订单、Outbox 与 Agent 诊断</span>
        </div>
        <div class="admin-grid">
          <div class="table-wrap">
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
        </div>
      </section>

      <div class="toast" :class="{show: toastMessage}">{{ toastMessage }}</div>
    </main>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, ref} from "vue";

const API_BASE = import.meta.env.VITE_API_BASE || "";
const navItems = [
  {key: "home", label: "首页总览"},
  {key: "shops", label: "商户探店"},
  {key: "vouchers", label: "优惠券秒杀"},
  {key: "orders", label: "我的订单"},
  {key: "ai", label: "AI 助手"},
  {key: "admin", label: "运营看板"}
];

const view = ref("home");
const authToken = ref("");
const user = ref({userId: 10003, username: "admin", role: "ADMIN"});
const stats = ref({});
const shops = ref([]);
const vouchers = ref([]);
const orders = ref([]);
const outbox = ref([]);
const diagnosis = ref(null);
const qualificationTokens = ref(new Map());
const toastMessage = ref("");
const question = ref("");
const chatBoxRef = ref(null);
const messages = ref([
  {
    id: 1,
    role: "assistant",
    content: "你可以问我：有哪些优惠券、我的订单状态、为什么 Outbox 积压、现在运营风险怎么样。"
  }
]);
const mcpTools = ["list_shops", "list_vouchers", "my_orders", "admin_stats", "ops_diagnosis", "ai_chat"];

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
  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || "请求失败");
  }
  return json.data;
}

async function loginDemoUser() {
  const data = await api("/api/users/login", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({username: "admin", password: "123456"})
  });
  authToken.value = data.token;
  user.value = data;
}

function showToast(message) {
  toastMessage.value = message;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => {
    toastMessage.value = "";
  }, 2600);
}

function switchView(nextView) {
  view.value = nextView;
  if (nextView === "orders") {
    loadOrders();
  }
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
  stats.value = await api("/api/admin/stats");
}

async function loadShops() {
  shops.value = await api("/api/shops");
}

async function loadVouchers() {
  vouchers.value = await api("/api/vouchers");
}

async function loadOrders() {
  orders.value = await api("/api/orders/me");
}

async function loadOutbox() {
  outbox.value = await api("/api/outbox");
}

async function loadDiagnosis() {
  diagnosis.value = await api("/api/agent/diagnosis");
}

async function loadAdmin() {
  await Promise.all([loadOutbox(), loadDiagnosis()]);
}

async function publishReview(shopId) {
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

async function applyQualification(voucherId) {
  try {
    const data = await api(`/api/vouchers/${voucherId}/qualification`, {method: "POST"});
    qualificationTokens.value.set(voucherId, data.qualificationToken);
    showToast(`资格 Token 已生成，有效 ${data.ttlSeconds} 秒`);
  } catch (error) {
    showToast(error.message);
  }
}

async function seckill(voucherId) {
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

function askAi() {
  if (!question.value) {
    return;
  }
  const currentQuestion = question.value;
  question.value = "";
  appendMessage("user", currentQuestion);
  const answer = appendMessage("assistant", "");
  const url = `${API_BASE}/api/ai/chat/stream?question=${encodeURIComponent(currentQuestion)}&token=${encodeURIComponent(authToken.value)}`;
  const source = new EventSource(url);
  source.addEventListener("meta", event => {
    answer.content += `[${event.data}]\n`;
  });
  source.addEventListener("message", event => {
    answer.content += event.data;
    nextTick(() => {
      if (chatBoxRef.value) {
        chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight;
      }
    });
  });
  source.addEventListener("done", () => {
    source.close();
  });
  source.onerror = () => {
    source.close();
    if (!answer.content) {
      answer.content = "AI 助手连接失败，请检查后端或 API Key。";
    }
  };
}

async function refreshAll() {
  try {
    await Promise.all([loadStats(), loadShops(), loadVouchers(), loadOrders(), loadAdmin()]);
  } catch (error) {
    showToast(error.message);
  }
}

const ShopCard = {
  props: {
    shop: {
      type: Object,
      required: true
    }
  },
  emits: ["review"],
  template: `
    <article class="card">
      <div>
        <h4>{{ shop.name }}</h4>
        <div class="meta">
          <span>{{ shop.category }}</span>
          <span>{{ shop.address }}</span>
          <span>评分 {{ shop.avgScore }} / 评价 {{ shop.commentCount }} / 热度 {{ shop.hotScore }}</span>
        </div>
        <div class="tag-row">
          <span class="tag">探店</span>
          <span class="tag">本地缓存</span>
        </div>
      </div>
      <div class="card-actions">
        <button class="light" @click="$emit('review', shop.id)">发布评价</button>
      </div>
    </article>
  `
};

onMounted(async () => {
  try {
    await loginDemoUser();
    await refreshAll();
  } catch (error) {
    showToast(error.message);
  }
});
</script>
