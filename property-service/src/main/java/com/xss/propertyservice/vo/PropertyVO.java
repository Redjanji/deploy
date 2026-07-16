package com.xss.propertyservice.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PropertyVO {
    private Long id;
    private String title;
    private String type;
    private Long price;
    private Integer rentalArea;
    private String rooms;
    private String orientation;
    private String floor;
    private String address;
    private String provinceName;
    private String cityName;
    private String districtName;
    private String coverUrl;
    private Boolean hot;
    private Boolean featured;
    private LocalDateTime createdAt;
}
