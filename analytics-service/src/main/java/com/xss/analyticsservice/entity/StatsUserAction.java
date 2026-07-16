package com.xss.analyticsservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("stats_user_actions")
public class StatsUserAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private String appId;

    @TableField("event_type")
    private String eventType;

    @TableField("action_count")
    private Long actionCount;

    @TableField("stats_date")
    private LocalDate statsDate;

    @TableField("stats_hour")
    private Integer statsHour;
}
