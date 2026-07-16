package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_country")
public class Country {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String countryCode;
    private String countryCode3;
    private String numericCode;
    private String nameZh;
    private String nameEn;
    private String phoneCode;
    private String currencyCode;
    private String continentCode;
    private String flagEmoji;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
