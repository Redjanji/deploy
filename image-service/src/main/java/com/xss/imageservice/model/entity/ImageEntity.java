package com.xss.imageservice.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("images")
public class ImageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String originKey;
    private String largeKey;
    private String mediumKey;
    private String smallKey;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String mimeType;
    private String status;
    private Long ownerId;
    private LocalDateTime createdAt;
}
