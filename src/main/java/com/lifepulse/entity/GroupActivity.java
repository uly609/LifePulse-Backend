package com.lifepulse.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("group_activity")
public class GroupActivity {
    private Long id; private Long voucherId; private String title; private String description; private Integer requiredSize;
    private BigDecimal groupPrice; private Integer totalStock; private Integer joinedCount; private String allowedRole; private String status;
    private LocalDateTime beginTime; private LocalDateTime endTime; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getVoucherId(){return voucherId;} public void setVoucherId(Long v){voucherId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Integer getRequiredSize(){return requiredSize;} public void setRequiredSize(Integer v){requiredSize=v;} public BigDecimal getGroupPrice(){return groupPrice;} public void setGroupPrice(BigDecimal v){groupPrice=v;}
    public Integer getTotalStock(){return totalStock;} public void setTotalStock(Integer v){totalStock=v;} public Integer getJoinedCount(){return joinedCount;} public void setJoinedCount(Integer v){joinedCount=v;}
    public String getAllowedRole(){return allowedRole;} public void setAllowedRole(String v){allowedRole=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getBeginTime(){return beginTime;} public void setBeginTime(LocalDateTime v){beginTime=v;} public LocalDateTime getEndTime(){return endTime;} public void setEndTime(LocalDateTime v){endTime=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
