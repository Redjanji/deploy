package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_china_region")
public class ChinaRegion {
    @TableId(type = IdType.INPUT)
    private String regionCode;
    private String regionName;
    private String parentCode;
    private Integer regionLevel;
    private String regionType;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
