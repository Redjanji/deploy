package com.xss.bookingservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bookings")
public class Booking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long propertyId;
    private Long agentId;
    private LocalDateTime appointmentTime;
    private Integer status;
    private String remark;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String statusDesc;
}
