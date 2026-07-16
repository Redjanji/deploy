package com.xss.searchservice.vo;

import lombok.Data;

@Data
public class PropertyDocumentVO {
    private Long id;
    private String appId;
    private String title;
    private String type;
    private Long price;
    private Integer rentalArea;
    private String rooms;
    private String orientation;
    private String floor;
    private String address;
    private Double lat;
    private Double lon;
    private String provinceName;
    private String cityName;
    private String districtName;
    private String decoration;
    private String description;
    private Boolean hot;
    private Boolean featured;
    private String coverUrl;
    private Double distance;
}
