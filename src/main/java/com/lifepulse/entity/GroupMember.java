package com.lifepulse.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("group_member") public class GroupMember { private Long id; private Long groupId; private Long activityId; private Long userId; private Long orderId; private String status; private LocalDateTime createdAt;
public Long getId(){return id;} public void setId(Long v){id=v;} public Long getGroupId(){return groupId;} public void setGroupId(Long v){groupId=v;} public Long getActivityId(){return activityId;} public void setActivityId(Long v){activityId=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public Long getOrderId(){return orderId;} public void setOrderId(Long v){orderId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} }
