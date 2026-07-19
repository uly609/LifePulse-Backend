# LifePulse 压测说明

## 压测目标

验证优惠券秒杀入口在高并发下能否做到：

- 请求快速返回“订单创建中”
- Redis Lua 不超卖
- RocketMQ + Outbox 不丢消息
- MySQL 最终订单数、库存数一致
- Outbox 不长期堆积

## 运行方式

先启动 Docker 环境，再执行：

```bash
BASE_URL=http://localhost:8088 VOUCHER_ID=2 USERS=100 CONCURRENCY=20 node loadtest/seckill-loadtest.mjs
```

脚本秒杀阶段不带管理员 JWT，而是通过不同 `userId` 模拟不同用户；最后查询管理统计和 Agent 诊断时，再使用管理员 JWT。

参数含义：

- `USERS`：模拟多少个不同用户
- `CONCURRENCY`：同时并发多少个请求
- `VOUCHER_ID`：抢哪张券
- `BASE_URL`：压测入口，默认走 Nginx

## 看哪些指标

- `avgRtMs`：平均响应时间
- `p95RtMs`：95% 请求都低于这个响应时间
- `qps`：接口总吞吐
- `businessTps`：业务成功吞吐
- `errorRate`：系统错误或业务失败比例
- `adminStats`：最终订单、待支付订单、Outbox 状态
- `diagnosis`：运营诊断 Agent 给出的风险判断

## 面试表达

我不是只看 HTTP 200，而是同时看业务成功数、RT、P95、QPS/TPS、Outbox 状态和最终数据库库存。这样可以区分“接口返回成功”和“订单最终真的创建成功”。
