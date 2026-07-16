package com.xss.propertyservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("property_images")
public class PropertyImageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long propertyId;
    private Long imageId;
    private Boolean isCover;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
