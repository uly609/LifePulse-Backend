package com.lifepulse.group;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.common.IdGenerator;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.entity.GroupActivity;
import com.lifepulse.entity.GroupMember;
import com.lifepulse.entity.GroupTeam;
import com.lifepulse.entity.Shop;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.mapper.GroupActivityMapper;
import com.lifepulse.mapper.GroupMemberMapper;
import com.lifepulse.mapper.GroupTeamMapper;
import com.lifepulse.mapper.ShopMapper;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.notification.NotifyTaskService;
import com.lifepulse.order.OrderStatus;
import com.lifepulse.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class GroupService {
    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupActivityMapper activityMapper;
    private final GroupTeamMapper teamMapper;
    private final GroupMemberMapper memberMapper;
    private final DealOrderMapper orderMapper;
    private final VoucherMapper voucherMapper;
    private final ShopMapper shopMapper;
    private final IdGenerator ids;
    private final NotifyTaskService notifications;
    private final OutboxEventService outbox;
    private final Executor groupDetailExecutor;

    public GroupService(GroupActivityMapper activityMapper, GroupTeamMapper teamMapper,
                        GroupMemberMapper memberMapper, DealOrderMapper orderMapper,
                        VoucherMapper voucherMapper, ShopMapper shopMapper, IdGenerator ids,
                        NotifyTaskService notifications, OutboxEventService outbox,
                        @Qualifier("groupDetailExecutor") Executor groupDetailExecutor) {
        this.activityMapper = activityMapper;
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.orderMapper = orderMapper;
        this.voucherMapper = voucherMapper;
        this.shopMapper = shopMapper;
        this.ids = ids;
        this.notifications = notifications;
        this.outbox = outbox;
        this.groupDetailExecutor = groupDetailExecutor;
    }

    public List<GroupActivity> activities() {
        return activityMapper.selectList(new LambdaQueryWrapper<GroupActivity>()
                .eq(GroupActivity::getStatus, "ONGOING")
                .orderByDesc(GroupActivity::getCreatedAt));
    }

    public GroupView detail(Long activityId, Long userId, String role) {
        GroupActivity activity = requireActivity(activityId);
        CompletableFuture<Voucher> voucherFuture = CompletableFuture.supplyAsync(
                () -> voucherMapper.selectById(activity.getVoucherId()), groupDetailExecutor);
        CompletableFuture<Shop> shopFuture = voucherFuture.thenApplyAsync(voucher ->
                voucher == null ? null : shopMapper.selectById(voucher.getShopId()), groupDetailExecutor
        ).exceptionally(error -> {
            log.warn("group_detail_shop_lookup_failed activityId={}", activityId, error);
            return null;
        });
        CompletableFuture<List<GroupTeam>> groupsFuture = CompletableFuture.supplyAsync(() ->
                teamMapper.selectList(new LambdaQueryWrapper<GroupTeam>()
                        .eq(GroupTeam::getActivityId, activityId)
                        .eq(GroupTeam::getStatus, "OPEN")
                        .orderByAsc(GroupTeam::getExpireTime)), groupDetailExecutor
        ).exceptionally(error -> {
            log.warn("group_detail_open_groups_failed activityId={}", activityId, error);
            return List.of();
        });
        CompletableFuture<GroupEligibility> eligibilityFuture = CompletableFuture.supplyAsync(
                () -> eligibility(activity, userId, role), groupDetailExecutor
        ).exceptionally(error -> {
            log.warn("group_detail_eligibility_failed activityId={}", activityId, error);
            return new GroupEligibility(false, false, "资格查询暂不可用");
        });

        CompletableFuture.allOf(voucherFuture, shopFuture, groupsFuture, eligibilityFuture).join();
        Voucher voucher = voucherFuture.join();
        if (voucher == null) {
            throw new BusinessException("关联优惠券不存在");
        }
        return new GroupView(activity, voucher, shopFuture.join(), groupsFuture.join(),
                eligibilityFuture.join(), Math.max(0, activity.getTotalStock() - activity.getJoinedCount()));
    }

    public List<MyGroupView> mine(Long userId) {
        return memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getUserId, userId).orderByDesc(GroupMember::getCreatedAt))
                .stream().map(member -> new MyGroupView(activityMapper.selectById(member.getActivityId()),
                        teamMapper.selectById(member.getGroupId()), member)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupTeam create(Long activityId, Long userId, String role) {
        GroupActivity activity = checkJoin(activityId, userId, role);
        reserveActivitySlot(activity.getId());
        LocalDateTime now = LocalDateTime.now();
        GroupTeam group = new GroupTeam();
        group.setId(ids.nextId()); group.setActivityId(activityId); group.setLeaderUserId(userId);
        group.setCurrentSize(1); group.setRequiredSize(activity.getRequiredSize()); group.setStatus("OPEN");
        group.setExpireTime(now.plusMinutes(30)); group.setCreatedAt(now); group.setUpdatedAt(now);
        teamMapper.insert(group);
        addMember(activity, group, userId, now);
        log.info("group_created groupId={} activityId={} userId={}", group.getId(), activityId, userId);
        return group;
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupTeam join(Long groupId, Long userId, String role) {
        GroupTeam group = teamMapper.selectById(groupId);
        if (group == null || !"OPEN".equals(group.getStatus()) || group.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("该拼团已结束");
        }
        GroupActivity activity = checkJoin(group.getActivityId(), userId, role);
        reserveActivitySlot(activity.getId());
        int reserved = teamMapper.update(null, new LambdaUpdateWrapper<GroupTeam>().eq(GroupTeam::getId, groupId)
                .eq(GroupTeam::getStatus, "OPEN").apply("current_size < required_size")
                .setSql("current_size = current_size + 1").set(GroupTeam::getUpdatedAt, LocalDateTime.now()));
        if (reserved == 0) throw new BusinessException("该拼团人数已满");
        addMember(activity, group, userId, LocalDateTime.now());
        log.info("group_joined groupId={} activityId={} userId={}", groupId, activity.getId(), userId);
        return teamMapper.selectById(groupId);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupActivity createActivity(GroupActivityRequest request) {
        if (request.endTime().isBefore(LocalDateTime.now())) throw new BusinessException("活动结束时间必须晚于当前时间");
        LocalDateTime now = LocalDateTime.now();
        GroupActivity activity = new GroupActivity();
        activity.setId(ids.nextId()); activity.setVoucherId(request.voucherId()); activity.setTitle(request.title());
        activity.setDescription(request.description()); activity.setRequiredSize(request.requiredSize());
        activity.setGroupPrice(request.groupPrice()); activity.setTotalStock(request.totalStock()); activity.setJoinedCount(0);
        activity.setAllowedRole(request.allowedRole()); activity.setStatus("ONGOING"); activity.setBeginTime(now);
        activity.setEndTime(request.endTime()); activity.setCreatedAt(now); activity.setUpdatedAt(now);
        activityMapper.insert(activity);
        log.info("group_activity_created activityId={} voucherId={}", activity.getId(), activity.getVoucherId());
        return activity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void setActivityStatus(Long id, String status) {
        if (!List.of("ONGOING", "OFFLINE").contains(status)) throw new BusinessException("无效活动状态");
        activityMapper.update(null, new LambdaUpdateWrapper<GroupActivity>().eq(GroupActivity::getId, id)
                .set(GroupActivity::getStatus, status).set(GroupActivity::getUpdatedAt, LocalDateTime.now()));
        log.info("group_activity_status_changed activityId={} status={}", id, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId, Long userId) {
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DealOrder>().eq(DealOrder::getId, orderId)
                .eq(DealOrder::getUserId, userId).eq(DealOrder::getStatus, OrderStatus.PENDING)
                .set(DealOrder::getStatus, OrderStatus.PAID).set(DealOrder::getPaidAt, LocalDateTime.now())
                .set(DealOrder::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) throw new BusinessException("订单状态已变化");
        GroupMember member = memberMapper.selectOne(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getOrderId, orderId));
        if (member == null) return;
        memberMapper.update(null, new LambdaUpdateWrapper<GroupMember>().eq(GroupMember::getId, member.getId()).set(GroupMember::getStatus, "PAID"));
        GroupTeam group = teamMapper.selectById(member.getGroupId());
        long paidCount = memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, group.getId()).eq(GroupMember::getStatus, "PAID"));
        if (paidCount >= group.getRequiredSize()) completeGroup(group);
        log.info("group_order_paid orderId={} groupId={} userId={}", orderId, group.getId(), userId);
    }

    @Scheduled(fixedDelay = 30_000L)
    public void closeExpiredGroups() {
        List<GroupTeam> expired = teamMapper.selectList(new LambdaQueryWrapper<GroupTeam>().eq(GroupTeam::getStatus, "OPEN").lt(GroupTeam::getExpireTime, LocalDateTime.now()));
        expired.forEach(group -> failGroup(group.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void failGroup(Long groupId) {
        GroupTeam group = teamMapper.selectById(groupId);
        if (group == null || !"OPEN".equals(group.getStatus())) return;
        teamMapper.update(null, new LambdaUpdateWrapper<GroupTeam>().eq(GroupTeam::getId, groupId).eq(GroupTeam::getStatus, "OPEN")
                .set(GroupTeam::getStatus, "FAILED").set(GroupTeam::getUpdatedAt, LocalDateTime.now()));
        for (GroupMember member : members(groupId)) {
            memberMapper.update(null, new LambdaUpdateWrapper<GroupMember>().eq(GroupMember::getId, member.getId()).set(GroupMember::getStatus, "FAILED"));
            orderMapper.update(null, new LambdaUpdateWrapper<DealOrder>().eq(DealOrder::getId, member.getOrderId()).eq(DealOrder::getStatus, OrderStatus.PAID)
                    .set(DealOrder::getStatus, OrderStatus.REFUNDED).set(DealOrder::getRefundedAt, LocalDateTime.now()).set(DealOrder::getUpdatedAt, LocalDateTime.now()));
            activityMapper.update(null, new LambdaUpdateWrapper<GroupActivity>().eq(GroupActivity::getId, member.getActivityId()).setSql("joined_count = joined_count - 1").set(GroupActivity::getUpdatedAt, LocalDateTime.now()));
            notifications.notify(member.getUserId(), "拼团未成团", "本次拼团超时未成团，已支付金额已自动退款。", "GROUP_FAILED");
        }
        outbox.saveAndSend("GROUP_FAILED", groupId, "lifepulse-group-notify", "group=" + groupId, () -> {});
        log.info("group_failed_and_refunded groupId={}", groupId);
    }

    private void completeGroup(GroupTeam group) {
        teamMapper.update(null, new LambdaUpdateWrapper<GroupTeam>().eq(GroupTeam::getId, group.getId()).eq(GroupTeam::getStatus, "OPEN")
                .set(GroupTeam::getStatus, "SUCCESS").set(GroupTeam::getUpdatedAt, LocalDateTime.now()));
        memberMapper.update(null, new LambdaUpdateWrapper<GroupMember>().eq(GroupMember::getGroupId, group.getId()).set(GroupMember::getStatus, "SUCCESS"));
        for (GroupMember member : members(group.getId())) notifications.notify(member.getUserId(), "拼团成功", "你参加的拼团已成团，请按活动说明到店使用。", "GROUP_SUCCESS");
        outbox.saveAndSend("GROUP_SUCCESS", group.getId(), "lifepulse-group-notify", "group=" + group.getId(), () -> {});
        log.info("group_completed groupId={} activityId={}", group.getId(), group.getActivityId());
    }

    private List<GroupMember> members(Long groupId) { return memberMapper.selectList(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId)); }
    private GroupActivity checkJoin(Long id, Long user, String role) {
        GroupActivity activity = requireActivity(id);
        GroupEligibility eligibility = eligibility(activity, user, role);
        if (!eligibility.eligible()) throw new BusinessException(eligibility.reason());
        return activity;
    }
    private GroupEligibility eligibility(GroupActivity activity, Long userId, String role) {
        if (userId == null) return new GroupEligibility(false, false, "登录后可参加拼团");
        boolean joined = memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getActivityId, activity.getId()).eq(GroupMember::getUserId, userId)) > 0;
        if (joined) return new GroupEligibility(false, true, "你已参加该活动");
        if (!"ALL".equals(activity.getAllowedRole()) && !activity.getAllowedRole().equals(role)) return new GroupEligibility(false, false, "当前账号不满足活动参与资格");
        if (activity.getJoinedCount() >= activity.getTotalStock()) return new GroupEligibility(false, false, "活动名额已满");
        return new GroupEligibility(true, false, "可以参加");
    }
    private GroupActivity requireActivity(Long id) {
        GroupActivity activity = activityMapper.selectById(id);
        if (activity == null || !"ONGOING".equals(activity.getStatus()) || activity.getEndTime().isBefore(LocalDateTime.now())) throw new BusinessException("活动已结束或不存在");
        return activity;
    }
    private void addMember(GroupActivity activity, GroupTeam group, Long userId, LocalDateTime now) {
        DealOrder order = new DealOrder();
        order.setId(ids.nextId()); order.setVoucherId(activity.getVoucherId()); order.setShopId(0L); order.setUserId(userId); order.setAmount(activity.getGroupPrice()); order.setStatus(OrderStatus.PENDING); order.setCreatedAt(now); order.setUpdatedAt(now);
        orderMapper.insert(order);
        GroupMember member = new GroupMember();
        member.setId(ids.nextId()); member.setGroupId(group.getId()); member.setActivityId(activity.getId()); member.setUserId(userId); member.setOrderId(order.getId()); member.setStatus("PENDING_PAY"); member.setCreatedAt(now);
        memberMapper.insert(member);
    }

    private void reserveActivitySlot(Long activityId) {
        int reserved = activityMapper.update(null, new LambdaUpdateWrapper<GroupActivity>().eq(GroupActivity::getId, activityId)
                .eq(GroupActivity::getStatus, "ONGOING").apply("joined_count < total_stock")
                .setSql("joined_count = joined_count + 1").set(GroupActivity::getUpdatedAt, LocalDateTime.now()));
        if (reserved == 0) throw new BusinessException("活动名额已满或已结束");
    }
}
