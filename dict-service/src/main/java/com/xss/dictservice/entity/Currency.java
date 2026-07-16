package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_currency")
public class Currency {
    @TableId(type = IdType.INPUT)
    private String currencyCode;
    private String nameZh;
    private String nameEn;
    private String symbol;
    private Integer status;
}
