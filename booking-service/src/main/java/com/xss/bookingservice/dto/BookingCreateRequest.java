package com.xss.bookingservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingCreateRequest {
    private Long propertyId;
    private LocalDateTime appointmentTime;
    private String remark;
}
