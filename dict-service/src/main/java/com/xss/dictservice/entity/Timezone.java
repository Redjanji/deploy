package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_timezone")
public class Timezone {
    @TableId(type = IdType.INPUT)
    private String timezoneId;
    private String offsetUtc;
    private String description;
}
