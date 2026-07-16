package com.xss.analyticsservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("stats_image_uploads")
public class StatsImageUpload {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private String appId;

    @TableField("upload_count")
    private Long uploadCount;

    @TableField("total_size")
    private Long totalSize;

    @TableField("stats_date")
    private LocalDate statsDate;

    @TableField("stats_hour")
    private Integer statsHour;
}
