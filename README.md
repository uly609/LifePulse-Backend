# LifePulse Backend

本地生活拼团探店营销交易全栈项目，偏 Java 后端实习简历场景。

## Docker 完整环境运行

先打包后端 jar：

```bash
mvn -q -DskipTests package
```

再启动完整环境：

```bash
docker compose up -d --build
```

启动后访问：

- 前端页面：`http://localhost:8088/`
- 后端直连：`http://localhost:8110/`
- Prometheus：`http://localhost:9090/`
- Grafana：`http://localhost:3000/`，默认账号 `admin/admin`
- MySQL：`localhost:3307`
- Redis：`localhost:6379`
- RocketMQ NameServer：`localhost:9876`

停止环境：

```bash
docker compose down
```

如果要清空 MySQL / Redis / RocketMQ 数据：

```bash
docker compose down -v
```

## 轻量本地运行

不想启动中间件时，也可以只跑后端：

```bash
mvn -q -DskipTests package
java -jar target/lifepulse-backend-0.0.1-SNAPSHOT.jar
```

轻量模式默认使用 H2 内存数据库，端口 `8110`。
打开 `http://localhost:8110/` 可以直接访问前端页面。

## 主要接口

- `POST /api/users/login`
- `GET /api/shops`
- `GET /api/vouchers`
- `POST /api/vouchers/{voucherId}/qualification`
- `POST /api/vouchers/{voucherId}/seckill?qualificationToken=xxx`
- `GET /api/orders/me`
- `POST /api/orders/{orderId}/pay`
- `POST /api/orders/{orderId}/cancel`
- `POST /api/orders/{orderId}/refund`
- `GET /api/admin/stats`
- `GET /api/agent/diagnosis`
- `GET /api/outbox`
- `GET /api/mcp/tools`
- `POST /mcp`

## 面试重点

这个项目不是只做 CRUD，重点是交易系统里的并发和一致性：JWT 无状态登录、RBAC 角色校验、Redis Lua 预扣、Redisson 用户维度锁、RocketMQ 削峰、Outbox 防消息丢失、MySQL 事务兜底、唯一索引和条件更新保证幂等。Docker Compose 会把 MySQL、Redis、RocketMQ、Nginx、Prometheus、Grafana 和后端服务一起编排起来。

## 运营诊断 Agent

项目提供了一个规则型运营诊断 Agent：

- HTTP 入口：`GET /api/agent/diagnosis`
- MCP 工具：`ops_diagnosis`
- 诊断内容：Outbox 积压、失败消息、待支付订单、低库存活动、评价量等

它不是聊天玩具，定位是给智能客服或运营 Agent 调用的业务工具：Agent 通过 MCP 查询系统状态，再生成排查建议。

## 压测

完整 Docker 环境启动后，可以直接压测秒杀链路：

```bash
BASE_URL=http://localhost:8088 VOUCHER_ID=2 USERS=100 CONCURRENCY=20 node loadtest/seckill-loadtest.mjs
```

脚本会输出平均 RT、P95、QPS、业务 TPS、错误率、最终订单状态和 Agent 诊断结果。说明文档见 `docs/loadtest-report.md`。

## MCP 调用示例

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "list_vouchers",
    "arguments": {}
  }
}
```

## Nginx

示例配置在 `deploy/nginx.conf`，用于把 `/`、`/api/` 和 `/mcp` 反向代理到 Spring Boot 服务。
