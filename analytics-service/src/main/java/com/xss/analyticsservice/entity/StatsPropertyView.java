package com.xss.analyticsservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stats_property_views")
public class StatsPropertyView {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private String appId;

    @TableField("property_id")
    private Long propertyId;

    @TableField("view_count")
    private Long viewCount;

    @TableField("unique_visitors")
    private Long uniqueVisitors;

    @TableField("stats_date")
    private LocalDate statsDate;

    @TableField("stats_hour")
    private Integer statsHour;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
