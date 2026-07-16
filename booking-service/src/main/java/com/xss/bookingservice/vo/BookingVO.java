package com.xss.bookingservice.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingVO {
    private Long id;
    private Long userId;
    private Long propertyId;
    private Long agentId;
    private LocalDateTime appointmentTime;
    private Integer status;
    private String statusDesc;
    private String remark;
    private String cancelReason;
    private String propertyTitle;
    private String propertyCover;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
