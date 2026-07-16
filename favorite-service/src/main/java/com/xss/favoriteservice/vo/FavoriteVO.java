package com.xss.favoriteservice.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteVO {
    private Long id;
    private Long userId;
    private Long propertyId;
    private LocalDateTime createdAt;
}
