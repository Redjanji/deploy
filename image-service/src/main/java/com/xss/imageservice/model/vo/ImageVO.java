package com.xss.imageservice.model.vo;

import com.xss.imageservice.model.entity.ImageEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageVO {
    private Long id;
    private String url;
    private String originUrl;
    private String largeUrl;
    private String mediumUrl;
    private String smallUrl;
    private int width;
    private int height;
    private Long fileSize;
    private String mimeType;

    public static ImageVO from(ImageEntity entity, String baseUrl) {
        return ImageVO.builder()
                .id(entity.getId())
                .originUrl(baseUrl + entity.getOriginKey())
                .largeUrl(entity.getLargeKey() != null ? baseUrl + entity.getLargeKey() : null)
                .mediumUrl(entity.getMediumKey() != null ? baseUrl + entity.getMediumKey() : null)
                .smallUrl(entity.getSmallKey() != null ? baseUrl + entity.getSmallKey() : null)
                .url(baseUrl + entity.getOriginKey())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .fileSize(entity.getFileSize())
                .mimeType(entity.getMimeType())
                .build();
    }
}
