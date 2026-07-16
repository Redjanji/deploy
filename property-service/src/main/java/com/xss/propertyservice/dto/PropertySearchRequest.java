package com.xss.propertyservice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PropertySearchRequest {
    private String cityCode;
    private String type;
    private Long minPrice;
    private Long maxPrice;
    private BigDecimal lat;     // 附近搜索纬度
    private BigDecimal lng;     // 附近搜索经度
    private Double radius;      // 半径(km)，默认5km
    private String keyword;     // 标题关键字
    private Boolean hot;        // 仅查热门
    private Boolean featured;   // 仅查精选
    private int page = 1;
    private int size = 20;
}
