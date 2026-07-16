package com.xss.favoriteservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorites")
public class UserFavoriteEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long propertyId;
    private LocalDateTime createdAt;
}
