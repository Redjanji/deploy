package com.xss.propertyservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PropertyCreateRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "房源类型不能为空")
    private String type;
    private Long price;
    private Integer rentalArea;
    private String rooms;
    private String orientation;
    private String floor;
    private Integer totalFloors;
    private String address;
    @NotNull(message = "纬度不能为空")
    private BigDecimal lat;
    @NotNull(message = "经度不能为空")
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
    @Size(max = 20, message = "最多20张图片")
    private List<Long> imageIds;
}
