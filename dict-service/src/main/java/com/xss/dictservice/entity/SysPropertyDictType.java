package com.xss.dictservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_property_dict_type")
public class SysPropertyDictType {
    @TableId(type = IdType.INPUT)
    private String typeCode;
    private String typeName;
    private String remark;
}
