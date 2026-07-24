# 拼团库存链路

拼团使用 Redis 前置预占和 MySQL 最终校验两层控制。

## 开团与参团

```text
活动限流
-> 资格规则链
-> Redis Lua 原子检查重复参与、活动总库存和单团库存
-> Redis 预扣活动库存/单团库存并写入用户集合
-> MySQL 条件更新 joined_count/current_size
-> 同一事务创建订单与成员记录
```

- 开团只预扣活动总库存，事务提交后初始化新团的剩余库存。
- 参团同时预扣活动总库存与单团库存。
- Redis Key 使用活动 ID hash tag，确保 Redis Cluster 中的 Lua Key 落在同一槽位。
- Redis 不可用时降级到 MySQL 条件更新，数据库仍负责最终防超卖。

## 回滚与关团

- Redis 预占后注册事务同步器；MySQL 事务回滚时执行补偿 Lua。
- 补偿脚本先从参与集合移除用户，只有移除成功才回补库存，避免重复补偿。
- 拼团失败事务提交后，逐个释放活动名额并删除单团库存 Key。
- 拼团成功后删除单团库存 Key，活动库存保持已售状态。

## 并发边界

- Redis Lua 减少高并发请求对 MySQL 热点行的竞争。
- MySQL 使用 `joined_count < total_stock` 和 `current_size < required_size` 条件更新最终兜底。
- `UNIQUE(activity_id, user_id)` 防止同一用户并发加入同一活动下的多个团。
