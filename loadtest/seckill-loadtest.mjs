const BASE_URL = process.env.BASE_URL || "http://localhost:8088";
const VOUCHER_ID = Number(process.env.VOUCHER_ID || 2);
const USERS = Number(process.env.USERS || 100);
const CONCURRENCY = Number(process.env.CONCURRENCY || 20);

function percentile(values, ratio) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)] || 0;
}

async function login() {
  const res = await fetch(`${BASE_URL}/api/users/login`, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({username: "admin", password: "123456"})
  });
  const json = await res.json();
  if (json.code !== 200) {
    throw new Error(json.message || "login failed");
  }
  return json.data.token;
}

async function call(path, options = {}) {
  const started = performance.now();
  const res = await fetch(`${BASE_URL}${path}`, options);
  const json = await res.json();
  return {
    ok: json.code === 200,
    message: json.message,
    data: json.data,
    rt: performance.now() - started
  };
}

async function runOne(userId) {
  const qualification = await call(`/api/vouchers/${VOUCHER_ID}/qualification?userId=${userId}`, {
    method: "POST"
  });
  if (!qualification.ok) {
    return qualification;
  }
  return call(`/api/vouchers/${VOUCHER_ID}/seckill?userId=${userId}&qualificationToken=${qualification.data.qualificationToken}`, {
    method: "POST"
  });
}

async function main() {
  const authToken = await login();
  const tasks = Array.from({length: USERS}, (_, index) => 30000 + index);
  const results = [];
  const started = performance.now();

  async function worker() {
    while (tasks.length > 0) {
      const userId = tasks.shift();
      results.push(await runOne(userId));
    }
  }

  await Promise.all(Array.from({length: CONCURRENCY}, worker));
  const elapsedSeconds = (performance.now() - started) / 1000;
  await new Promise(resolve => setTimeout(resolve, 2500));

  const successful = results.filter(item => item.ok).length;
  const failed = results.length - successful;
  const rts = results.map(item => item.rt);
  const avg = rts.reduce((sum, value) => sum + value, 0) / Math.max(1, rts.length);
  const p95 = percentile(rts, 0.95);
  const qps = results.length / elapsedSeconds;
  const businessTps = successful / elapsedSeconds;

  const stats = await call("/api/admin/stats", {
    headers: {Authorization: `Bearer ${authToken}`}
  });
  const diagnosis = await call("/api/agent/diagnosis", {
    headers: {Authorization: `Bearer ${authToken}`}
  });

  console.log(JSON.stringify({
    baseUrl: BASE_URL,
    voucherId: VOUCHER_ID,
    users: USERS,
    concurrency: CONCURRENCY,
    avgRtMs: Number(avg.toFixed(2)),
    p95RtMs: Number(p95.toFixed(2)),
    qps: Number(qps.toFixed(2)),
    businessTps: Number(businessTps.toFixed(2)),
    errorRate: `${((failed / Math.max(1, results.length)) * 100).toFixed(2)}%`,
    success: successful,
    failed,
    adminStats: stats.data,
    diagnosis: diagnosis.data
  }, null, 2));
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
