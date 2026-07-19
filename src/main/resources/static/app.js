const USER_ID = 10003;
const qualificationTokens = new Map();
let authToken = "";

const views = document.querySelectorAll("[data-view-panel]");
const navItems = document.querySelectorAll(".nav-item");
const toast = document.querySelector("#toast");
const chatBox = document.querySelector("#chatBox");
const chatForm = document.querySelector("#chatForm");
const chatInput = document.querySelector("#chatInput");

function showToast(message) {
    toast.textContent = message;
    toast.classList.add("show");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2600);
}

async function api(path, options = {}) {
    const headers = {...(options.headers || {})};
    if (authToken) {
        headers.Authorization = `Bearer ${authToken}`;
    }
    const finalOptions = {...options, headers};
    const finalRes = await fetch(path, finalOptions);
    const json = await finalRes.json();
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
    authToken = data.token;
}

function switchView(view) {
    views.forEach(panel => panel.classList.toggle("hidden", panel.dataset.viewPanel !== view));
    navItems.forEach(item => item.classList.toggle("active", item.dataset.view === view));
    if (view === "orders") {
        loadOrders();
    }
    if (view === "admin") {
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
    const stats = await api("/api/admin/stats");
    const items = [
        ["商户", stats.shops],
        ["优惠券", stats.vouchers],
        ["订单", stats.orders],
        ["待支付", stats.pendingOrders],
        ["已支付", stats.paidOrders],
        ["Outbox待处理", stats.outboxPending]
    ];
    document.querySelector("#statsGrid").innerHTML = items.map(([label, value]) => `
        <div class="stat">
            <span>${label}</span>
            <strong>${value}</strong>
        </div>
    `).join("");
}

async function loadShops() {
    const shops = await api("/api/shops");
    const html = shops.map(shop => `
        <article class="card">
            <div>
                <h4>${shop.name}</h4>
                <div class="meta">
                    <span>${shop.category}</span>
                    <span>${shop.address}</span>
                    <span>评分 ${shop.avgScore} / 评价 ${shop.commentCount} / 热度 ${shop.hotScore}</span>
                </div>
                <div class="tag-row">
                    <span class="tag">探店</span>
                    <span class="tag">本地缓存</span>
                </div>
            </div>
            <div class="card-actions">
                <button class="light" onclick="publishReview(${shop.id})">发布评价</button>
            </div>
        </article>
    `).join("");
    document.querySelector("#homeShops").innerHTML = html;
    document.querySelector("#shopList").innerHTML = html;
}

async function publishReview(shopId) {
    try {
        await api(`/api/shops/${shopId}/reviews`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
        score: 5,
                content: "环境不错，适合学习和小组讨论。"
            })
        });
        showToast("评价发布成功，商户缓存已失效");
        await Promise.all([loadShops(), loadStats()]);
    } catch (e) {
        showToast(e.message);
    }
}

async function loadVouchers() {
    const vouchers = await api("/api/vouchers");
    document.querySelector("#voucherList").innerHTML = vouchers.map(voucher => `
        <article class="card">
            <div>
                <h4>${voucher.title}</h4>
                <div class="price">${money(voucher.salePrice)}</div>
                <div class="meta">
                    <span>原价 ${money(voucher.originalPrice)}</span>
                    <span>库存 ${voucher.stock} / 商户ID ${voucher.shopId}</span>
                    <span>${formatTime(voucher.beginTime)} 至 ${formatTime(voucher.endTime)}</span>
                </div>
                <div class="tag-row">
                    <span class="tag">Redis Lua</span>
                    <span class="tag">Outbox</span>
                    <span class="tag">MQ削峰</span>
                </div>
            </div>
            <div class="card-actions">
                <button class="light" onclick="applyQualification(${voucher.id})">申请资格</button>
                <button onclick="seckill(${voucher.id})">立即抢券</button>
            </div>
        </article>
    `).join("");
}

async function applyQualification(voucherId) {
    try {
        const data = await api(`/api/vouchers/${voucherId}/qualification`, {method: "POST"});
        qualificationTokens.set(voucherId, data.qualificationToken);
        showToast(`资格 Token 已生成，有效 ${data.ttlSeconds} 秒`);
    } catch (e) {
        showToast(e.message);
    }
}

async function seckill(voucherId) {
    const token = qualificationTokens.get(voucherId);
    if (!token) {
        showToast("先点“申请资格”，再点“立即抢券”");
        return;
    }
    try {
        const data = await api(`/api/vouchers/${voucherId}/seckill?qualificationToken=${token}`, {method: "POST"});
        qualificationTokens.delete(voucherId);
        showToast(data.message);
        await Promise.all([loadOrders(), loadStats(), loadAdmin()]);
        switchView("orders");
    } catch (e) {
        showToast(e.message);
    }
}

async function loadOrders() {
    const orders = await api(`/api/orders/me`);
    document.querySelector("#orderRows").innerHTML = orders.map(order => `
        <tr>
            <td>${order.id}</td>
            <td>${order.voucherId}</td>
            <td>${money(order.amount)}</td>
            <td><span class="status ${order.status}">${order.status}</span></td>
            <td>${formatTime(order.createdAt)}</td>
            <td>
                <button ${order.status !== "PENDING" ? "disabled" : ""} onclick="payOrder(${order.id})">支付</button>
                <button ${order.status !== "PENDING" ? "disabled" : ""} onclick="cancelOrder(${order.id})">取消</button>
                <button ${order.status !== "PAID" ? "disabled" : ""} onclick="refundOrder(${order.id})">退款</button>
            </td>
        </tr>
    `).join("") || `<tr><td colspan="6">暂无订单，先去优惠券秒杀页抢一张。</td></tr>`;
}

async function payOrder(orderId) {
    try {
        await api(`/api/orders/${orderId}/pay`, {method: "POST"});
        showToast("支付成功");
        await Promise.all([loadOrders(), loadStats()]);
    } catch (e) {
        showToast(e.message);
    }
}

async function cancelOrder(orderId) {
    try {
        await api(`/api/orders/${orderId}/cancel`, {method: "POST"});
        showToast("订单已取消，库存已回补");
        await Promise.all([loadOrders(), loadStats(), loadVouchers()]);
    } catch (e) {
        showToast(e.message);
    }
}

async function refundOrder(orderId) {
    try {
        await api(`/api/orders/${orderId}/refund`, {method: "POST"});
        showToast("退款成功，库存已回补");
        await Promise.all([loadOrders(), loadStats(), loadVouchers()]);
    } catch (e) {
        showToast(e.message);
    }
}

async function loadAdmin() {
    const [outbox] = await Promise.all([api("/api/outbox"), loadDiagnosis()]);
    document.querySelector("#outboxRows").innerHTML = outbox.map(item => `
        <tr>
            <td>${item.id}</td>
            <td>${item.eventType}</td>
            <td>${item.topic}</td>
            <td><span class="status ${item.status}">${item.status}</span></td>
        </tr>
    `).join("") || `<tr><td colspan="4">暂无 Outbox 消息。</td></tr>`;
}

async function loadDiagnosis() {
    const diagnosis = await api("/api/agent/diagnosis");
    document.querySelector("#diagnosisBox").innerHTML = `
        <div class="risk ${diagnosis.riskLevel}">${diagnosis.riskLevel}</div>
        <p>${diagnosis.summary}</p>
        <strong>发现</strong>
        <ul>${diagnosis.findings.map(item => `<li>${item}</li>`).join("")}</ul>
        <strong>建议</strong>
        <ul>${diagnosis.suggestions.map(item => `<li>${item}</li>`).join("")}</ul>
        <strong>证据</strong>
        <div class="evidence-row">${diagnosis.evidence.map(item => `<span>${item}</span>`).join("")}</div>
    `;
    return diagnosis;
}

async function refreshAll() {
    try {
        await Promise.all([loadStats(), loadShops(), loadVouchers(), loadOrders(), loadAdmin()]);
    } catch (e) {
        showToast(e.message);
    }
}

function appendChat(role, content) {
    const node = document.createElement("div");
    node.className = `chat-message ${role}`;
    node.innerHTML = `<strong>${role === "user" ? "我" : "LifePulse Assistant"}</strong><p>${content}</p>`;
    chatBox.appendChild(node);
    chatBox.scrollTop = chatBox.scrollHeight;
    return node.querySelector("p");
}

function askAi(question) {
    appendChat("user", question);
    const answerNode = appendChat("assistant", "");
    const url = `/api/ai/chat/stream?question=${encodeURIComponent(question)}&token=${encodeURIComponent(authToken)}`;
    const source = new EventSource(url);
    source.addEventListener("meta", event => {
        answerNode.textContent += `[${event.data}]\n`;
    });
    source.addEventListener("message", event => {
        answerNode.textContent += event.data;
        chatBox.scrollTop = chatBox.scrollHeight;
    });
    source.addEventListener("done", () => {
        source.close();
    });
    source.onerror = () => {
        source.close();
        if (!answerNode.textContent) {
            answerNode.textContent = "AI 助手连接失败，请检查后端或 API Key。";
        }
    };
}

document.querySelector("#refreshBtn").addEventListener("click", refreshAll);
chatForm.addEventListener("submit", event => {
    event.preventDefault();
    const question = chatInput.value.trim();
    if (!question) {
        return;
    }
    chatInput.value = "";
    askAi(question);
});
document.querySelectorAll("[data-view-link]").forEach(button => {
    button.addEventListener("click", () => switchView(button.dataset.viewLink));
});
navItems.forEach(item => {
    item.addEventListener("click", () => switchView(item.dataset.view));
});

loginDemoUser().then(refreshAll).catch(e => showToast(e.message));
