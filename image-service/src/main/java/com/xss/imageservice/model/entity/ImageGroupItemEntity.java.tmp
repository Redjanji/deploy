package com.xss.imageservice.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("image_group_items")
public class ImageGroupItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long imageId;
    private Integer sortOrder;
}
