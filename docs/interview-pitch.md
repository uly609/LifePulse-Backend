# LifePulse 面试介绍稿

## 项目定位

LifePulse 是一个本地生活营销交易全栈项目，核心业务包括商户查询、优惠券秒杀、订单支付/取消/退款、评价互动、MCP 工具调用和后台统计。

## 后端主线

- 登录鉴权：登录后签发 JWT，拦截器解析 Bearer Token 后把 userId 放入 ThreadLocal，请求结束后清理。
- 商户读链路：使用 Caffeine 本地缓存承接热门商户详情，评价发布后更新 MySQL 并失效本地缓存。
- 秒杀下单链路：资格 Token -> Redisson 用户维度锁 -> Redis Lua 原子校验 -> 库存预扣 -> Outbox 可靠消息 -> RocketMQ 异步创建订单。
- 交易一致性：MySQL 真实扣库存和订单插入放在同一个事务里，配合 `(voucher_id, user_id)` 唯一索引兜底一人一单。
- 状态竞争：支付、取消、退款都使用 `WHERE status = PENDING/PAID` 的条件更新，避免支付和取消并发覆盖。
- 可靠消息：Outbox 先落库再发 MQ，发送失败由定时任务扫描 `PENDING/FAILED` 记录重试。
- Agent 接入：通过 MCP 统一封装商户查询、优惠券查询、用户订单和运营统计工具，方便 AI Agent 按工具列表调用业务能力。
- 部署入口：提供 Nginx 反向代理配置，把前端页面、后端 API 和 MCP 入口统一代理到 Spring Boot 服务。

## 简历版写法

**LifePulse 本地生活拼团探店营销交易平台**

技术栈：Spring Boot、Spring MVC、MyBatis-Plus、MySQL、Redis、Lua、Redisson、RocketMQ、Outbox、Caffeine、JWT、MCP、Nginx

- 设计商户、优惠券、订单、评价、MCP 工具和后台统计等核心模块，形成从商户浏览到抢券下单、支付取消、退款补偿的完整交易闭环。
- 针对优惠券秒杀场景，采用资格 Token + Redisson + Redis Lua 完成库存预扣和一人一单校验，降低高并发请求直接冲击 MySQL 的风险。
- 引入 Outbox + RocketMQ 可靠消息方案，将下单请求异步化处理，并通过定时重试、消费幂等和数据库唯一索引保障最终一致性。
- 订单支付、取消、退款使用 MySQL 事务和条件更新实现状态机控制，避免并发场景下重复扣减、重复回补和状态覆盖。
