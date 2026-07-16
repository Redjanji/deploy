package com.xss.propertyservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PropertyUpdateRequest {
    private String title;
    private String type;
    private Long price;
    private Integer rentalArea;
    private String rooms;
    private String orientation;
    private String floor;
    private Integer totalFloors;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String decoration;
    private String heatingMethod;
    private String waterSupply;
    private String powerSupply;
    private String gasSupply;
    private String internet;
    private String tvService;
    private String description;
    private String contactPhone;
    private String agentName;
    private String agentTitle;
    private String agentPhone;
    private List<Long> imageIds;    // 若提供则全量替换图片关联
}
