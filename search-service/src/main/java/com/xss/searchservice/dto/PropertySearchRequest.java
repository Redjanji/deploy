package com.xss.searchservice.dto;

import lombok.Data;

@Data
public class PropertySearchRequest {
    private String keyword;
    private String cityCode;
    private String type;
    private String rooms;
    private Long minPrice;
    private Long maxPrice;
    private Double centerLat;
    private Double centerLng;
    private Double radiusKm;
    private Double topLeftLat;
    private Double topLeftLng;
    private Double bottomRightLat;
    private Double bottomRightLng;
    private Boolean hot;
    private Boolean featured;
    private Integer page = 1;
    private Integer size = 20;
}
