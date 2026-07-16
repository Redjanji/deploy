package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_language")
public class Language {
    @TableId(type = IdType.INPUT)
    private String langCode;
    private String nameZh;
    private String nameEn;
    private String nativeName;
}
