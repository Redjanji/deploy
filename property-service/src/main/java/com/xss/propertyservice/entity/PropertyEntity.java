package com.xss.propertyservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("properties")
public class PropertyEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
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
    private String geohash;
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
    private Integer publishStatus;  // 0草稿 1已发布 2已下架
    private Integer status;         // 0草稿 1通过 2待审核 3驳回
    private Boolean hot;
    private Boolean featured;
    private Long branchId;
    private Long buildingId;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
