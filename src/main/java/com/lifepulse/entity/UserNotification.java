package com.lifepulse.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("user_notification") public class UserNotification { private Long id; private Long userId; private String title; private String content; private String type; private Boolean readStatus; private LocalDateTime createdAt;
public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public String getType(){return type;} public void setType(String v){type=v;} public Boolean getReadStatus(){return readStatus;} public void setReadStatus(Boolean v){readStatus=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} }
