package com.xss.imageservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xss.imageservice.config.ImageConfigProperties;
import com.xss.imageservice.exception.BusinessException;
import com.xss.imageservice.mapper.ImageMapper;
import com.xss.imageservice.mq.StatsEventPublisher;
import com.xss.imageservice.model.entity.ImageEntity;
import com.xss.imageservice.model.vo.ImageVO;
import com.xss.imageservice.security.ImageSecurityChecker;
import com.xss.imageservice.service.impl.MinioStorageService;
import com.xss.imageservice.util.ImageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final ImageMapper imageMapper;
    private final MinioStorageService storageService;
    private final ImageSecurityChecker securityChecker;
    private final ImageConfigProperties config;
    private final StatsEventPublisher statsEventPublisher;

    @Transactional
    public ImageVO upload(MultipartFile file, String appId, Long ownerId) throws IOException {
        securityChecker.check(file);

        if (appId == null || appId.isEmpty()) {
            appId = "default";
        }

        byte[] data = file.getBytes();
        BufferedImage original = ImageConverter.readImage(data);
        int width = original.getWidth();
        int height = original.getHeight();

        byte[] webpOrigin = ImageConverter.toWebP(original, config.getWebpQuality());
        String originKey = storageService.upload(webpOrigin, appId, "original", "webp");

        String largeKey = null, mediumKey = null, smallKey = null;
        for (int w : config.getThumbnailWidths()) {
            byte[] resized = ImageConverter.resizeAndConvert(data, w, w, config.getWebpQuality());
            String sizeType;
            if (w == 1280) sizeType = "large";
            else if (w == 640) sizeType = "medium";
            else sizeType = "small";
            String key = storageService.upload(resized, appId, sizeType, "webp");
            switch (sizeType) {
                case "large" -> largeKey = key;
                case "medium" -> mediumKey = key;
                case "small" -> smallKey = key;
            }
        }

        ImageEntity entity = new ImageEntity();
        entity.setAppId(appId);
        entity.setOwnerId(ownerId);
        entity.setOriginKey(originKey);
        entity.setLargeKey(largeKey);
        entity.setMediumKey(mediumKey);
        entity.setSmallKey(smallKey);
        entity.setWidth(width);
        entity.setHeight(height);
        entity.setFileSize((long) webpOrigin.length);
        entity.setMimeType("image/webp");
        entity.setStatus("READY");
        imageMapper.insert(entity);

        statsEventPublisher.publish("IMAGE_UPLOAD", appId, ownerId, entity.getId(),
                java.util.Map.of("fileSize", entity.getFileSize()));
        return ImageVO.from(entity, getBaseUrl());
    }

    public List<ImageVO> listByApp(String appId, Long ownerId, int page, int size) {
        Page<ImageEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<ImageEntity> q = new LambdaQueryWrapper<ImageEntity>()
                .eq(ImageEntity::getAppId, appId)
                .eq(ImageEntity::getStatus, "READY");
        if (ownerId != null) {
            q.and(w -> w.isNull(ImageEntity::getOwnerId).or().eq(ImageEntity::getOwnerId, ownerId));
        } else {
            q.isNull(ImageEntity::getOwnerId);
        }
        q.orderByDesc(ImageEntity::getCreatedAt);
        Page<ImageEntity> result = imageMapper.selectPage(p, q);
        return result.getRecords().stream()
                .map(e -> ImageVO.from(e, getBaseUrl()))
                .toList();
    }

    public void delete(Long imageId, String appId, Long ownerId) {
        ImageEntity entity = imageMapper.selectById(imageId);
        if (entity == null || !entity.getAppId().equals(appId)) {
            throw new BusinessException(403, "无权操作");
        }
        if (entity.getOwnerId() != null && !entity.getOwnerId().equals(ownerId)) {
            throw new BusinessException(403, "无权操作他人私有图片");
        }
        entity.setStatus("DELETED");
        imageMapper.updateById(entity);
        statsEventPublisher.publish("IMAGE_DELETE", appId, ownerId, imageId);
    }

    public ImageEntity getById(Long imageId) {
        return imageMapper.selectById(imageId);
    }

    public String getBaseUrl() {
        if (config.getCdnUrl() != null && !config.getCdnUrl().isEmpty()) {
            return config.getCdnUrl();
        }
        return config.getLocalUrl();
    }
}
