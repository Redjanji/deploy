package com.xss.imageservice.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_groups")
public class ImageGroupEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String name;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
