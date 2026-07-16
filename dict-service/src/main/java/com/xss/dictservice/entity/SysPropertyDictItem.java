package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_property_dict_item")
public class SysPropertyDictItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeCode;
    private String itemKey;
    private String itemValue;
    private Integer sortOrder;
    private Integer isEnabled;
}
