package com.xss.propertyservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xss.propertyservice.client.ImageHubClient;
import com.xss.propertyservice.common.BusinessException;
import com.xss.propertyservice.dto.PropertyCreateRequest;
import com.xss.propertyservice.dto.PropertySearchRequest;
import com.xss.propertyservice.dto.PropertyUpdateRequest;
import com.xss.propertyservice.entity.PropertyEntity;
import com.xss.propertyservice.entity.PropertyImageEntity;
import com.xss.propertyservice.mapper.PropertyImageMapper;
import com.xss.propertyservice.mapper.PropertyMapper;
import com.xss.propertyservice.mq.StatsEventPublisher;
import com.xss.propertyservice.service.PropertyService;
import com.xss.propertyservice.util.GeoHashUtil;
import com.xss.propertyservice.vo.PropertyDetailVO;
import com.xss.propertyservice.vo.PropertyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyServiceImpl implements PropertyService {

    private final PropertyMapper propertyMapper;
    private final PropertyImageMapper imageMapper;
    private final ImageHubClient imageHubClient;
    private final RabbitTemplate rabbitTemplate;
    private final StatsEventPublisher statsEventPublisher;

    @Value("${property.max-images}")
    private int maxImages;

    @Value("${property.geohash-precision}")
    private int geoPrecision;

    @Value("${property.default-radius-km}")
    private double defaultRadiusKm;

    @Value("${sync.property-exchange}")
    private String propertyExchange;

    @Value("${sync.property-routing-key}")
    private String propertyRoutingKey;

    @Value("${message.send.exchange}")
    private String messageSendExchange;

    @Value("${message.send.routing-key}")
    private String messageSendRoutingKey;

    @Value("${message.notify.admin-email}")
    private String adminEmail;

    @Override
    public PropertyDetailVO create(PropertyCreateRequest req, String appId, Long ownerId) {
        if (req.getImageIds() != null && req.getImageIds().size() > maxImages) {
            throw new BusinessException(400, "图片数量不能超过" + maxImages);
        }

        PropertyEntity entity = new PropertyEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setAppId(appId == null ? "default" : appId);
        entity.setOwnerId(ownerId);
        if (req.getLat() != null && req.getLng() != null) {
            entity.setGeohash(GeoHashUtil.encode(
                    req.getLat().doubleValue(), req.getLng().doubleValue(), geoPrecision));
        }
        entity.setPublishStatus(0); // 草稿
        entity.setStatus(0);
        entity.setHot(false);
        entity.setFeatured(false);
        propertyMapper.insert(entity);

        saveImages(entity.getId(), req.getImageIds());
        publishSyncMessage("CREATE", entity.getId(), entity.getAppId());
        publishNotificationMessage("PROPERTY_CREATE_NOTIFY", "新房源创建通知",
                entity.getId(), entity.getTitle(), entity.getPrice(), entity.getAppId());
        statsEventPublisher.publish("PROPERTY_CREATE", entity.getAppId(), ownerId, entity.getId());
        return getDetail(entity.getId(), entity.getAppId());
    }

    @Override
    public PropertyDetailVO update(Long id, PropertyUpdateRequest req, String appId, Long ownerId) {
        PropertyEntity entity = getEntityOrThrow(id, appId);
        checkOwner(entity, ownerId);

        BeanUtils.copyProperties(req, entity, "id", "appId", "ownerId", "createdAt", "updatedAt",
                "geohash", "lat", "lng", "publishStatus", "status", "hot", "featured");
        if (req.getLat() != null && req.getLng() != null) {
            entity.setLat(req.getLat());
            entity.setLng(req.getLng());
            entity.setGeohash(GeoHashUtil.encode(
                    req.getLat().doubleValue(), req.getLng().doubleValue(), geoPrecision));
        }
        propertyMapper.updateById(entity);

        if (req.getImageIds() != null) {
            if (req.getImageIds().size() > maxImages) {
                throw new BusinessException(400, "图片数量不能超过" + maxImages);
            }
            imageMapper.delete(new LambdaQueryWrapper<PropertyImageEntity>()
                    .eq(PropertyImageEntity::getPropertyId, id));
            saveImages(id, req.getImageIds());
        }
        publishSyncMessage("UPDATE", id, appId);
        return getDetail(id, appId);
    }

    @Override
    public PropertyDetailVO getDetail(Long id, String appId) {
        PropertyEntity property = propertyMapper.selectOne(
                new LambdaQueryWrapper<PropertyEntity>()
                        .eq(PropertyEntity::getId, id)
                        .eq(PropertyEntity::getAppId, appId));
        if (property == null) {
            throw new BusinessException(404, "房源不存在");
        }
        return convertToDetailVO(property);
    }

    @Override
    public PropertyDetailVO viewDetail(Long id, String appId, Long userId) {
        PropertyDetailVO detail = getDetail(id, appId);
        statsEventPublisher.publish("PROPERTY_VIEW", appId, userId, id);
        return detail;
    }

    @Override
    public IPage<PropertyVO> search(PropertySearchRequest req, String appId) {
        LambdaQueryWrapper<PropertyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PropertyEntity::getAppId, appId)
                .eq(PropertyEntity::getPublishStatus, 1)
                .eq(PropertyEntity::getStatus, 1);

        if (StringUtils.isNotBlank(req.getCityCode())) {
            wrapper.eq(PropertyEntity::getCityCode, req.getCityCode());
        }
        if (StringUtils.isNotBlank(req.getType())) {
            wrapper.eq(PropertyEntity::getType, req.getType());
        }
        if (req.getMinPrice() != null) {
            wrapper.ge(PropertyEntity::getPrice, req.getMinPrice());
        }
        if (req.getMaxPrice() != null) {
            wrapper.le(PropertyEntity::getPrice, req.getMaxPrice());
        }
        if (StringUtils.isNotBlank(req.getKeyword())) {
            wrapper.like(PropertyEntity::getTitle, req.getKeyword());
        }
        if (Boolean.TRUE.equals(req.getHot())) {
            wrapper.eq(PropertyEntity::getHot, true);
        }
        if (Boolean.TRUE.equals(req.getFeatured())) {
            wrapper.eq(PropertyEntity::getFeatured, true);
        }

        if (req.getLat() != null && req.getLng() != null) {
            double radius = req.getRadius() != null ? req.getRadius() : defaultRadiusKm;
            int precision = GeoHashUtil.selectPrecisionByRadius(radius);
            String centerHash = GeoHashUtil.encode(
                    req.getLat().doubleValue(), req.getLng().doubleValue(), precision);
            String[] neighbors = GeoHashUtil.getAdjacent(centerHash);
            List<String> prefixes = new ArrayList<>(Arrays.asList(neighbors));
            prefixes.add(centerHash);
            wrapper.and(w -> {
                for (String prefix : prefixes) {
                    w.or().likeRight(PropertyEntity::getGeohash, prefix);
                }
            });
        }

        wrapper.orderByDesc(PropertyEntity::getCreatedAt);
        Page<PropertyEntity> page = new Page<>(req.getPage(), req.getSize());
        Page<PropertyEntity> result = propertyMapper.selectPage(page, wrapper);
        return result.convert(this::convertToListVO);
    }

    @Override
    public void delete(Long id, String appId, Long ownerId) {
        PropertyEntity entity = getEntityOrThrow(id, appId);
        checkOwner(entity, ownerId);
        propertyMapper.deleteById(id);
        publishSyncMessage("DELETE", id, appId);
    }

    @Override
    public void updatePublishStatus(Long id, Integer publishStatus, String appId, Long ownerId) {
        PropertyEntity entity = getEntityOrThrow(id, appId);
        checkOwner(entity, ownerId);
        entity.setPublishStatus(publishStatus);
        propertyMapper.updateById(entity);
        publishSyncMessage("UPDATE", id, appId);
    }

    @Override
    public void updateAuditStatus(Long id, Integer status, String appId) {
        PropertyEntity entity = getEntityOrThrow(id, appId);
        entity.setStatus(status);
        propertyMapper.updateById(entity);
        publishSyncMessage("UPDATE", id, appId);
        String auditResult = (status != null && status == 1) ? "审核通过" : "审核未通过";
        publishNotificationMessage("PROPERTY_AUDIT_NOTIFY", "房源审核通知 - " + auditResult,
                id, entity.getTitle(), entity.getPrice(), appId);
    }

    @Override
    public boolean exists(Long id) {
        return propertyMapper.selectById(id) != null;
    }

    @Override
    public com.xss.propertyservice.vo.PropertyBriefVO getBrief(Long id) {
        PropertyEntity entity = propertyMapper.selectById(id);
        if (entity == null) return null;
        com.xss.propertyservice.vo.PropertyBriefVO vo = new com.xss.propertyservice.vo.PropertyBriefVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        PropertyImageEntity cover = imageMapper.selectOne(new LambdaQueryWrapper<PropertyImageEntity>()
                .eq(PropertyImageEntity::getPropertyId, entity.getId())
                .eq(PropertyImageEntity::getIsCover, true));
        if (cover != null) {
            vo.setCoverUrl(imageHubClient.getImageUrl(cover.getImageId(), "small"));
        }
        return vo;
    }

    // ===== 内部辅助方法 =====

    private PropertyEntity getEntityOrThrow(Long id, String appId) {
        PropertyEntity entity = propertyMapper.selectById(id);
        if (entity == null || !entity.getAppId().equals(appId)) {
            throw new BusinessException(404, "房源不存在");
        }
        return entity;
    }

    private void checkOwner(PropertyEntity entity, Long ownerId) {
        if (ownerId == null) return;
        if (entity.getOwnerId() != null && !entity.getOwnerId().equals(ownerId)) {
            throw new BusinessException(403, "无权操作该房源");
        }
    }

    private void saveImages(Long propertyId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) return;
        int sort = 0;
        for (Long imageId : imageIds) {
            PropertyImageEntity img = new PropertyImageEntity();
            img.setPropertyId(propertyId);
            img.setImageId(imageId);
            img.setIsCover(sort == 0);
            img.setSortOrder(sort++);
            imageMapper.insert(img);
        }
    }

    private PropertyDetailVO convertToDetailVO(PropertyEntity entity) {
        PropertyDetailVO vo = new PropertyDetailVO();
        BeanUtils.copyProperties(entity, vo);

        List<PropertyImageEntity> images = imageMapper.selectList(
                new LambdaQueryWrapper<PropertyImageEntity>()
                        .eq(PropertyImageEntity::getPropertyId, entity.getId())
                        .orderByAsc(PropertyImageEntity::getSortOrder));
        List<String> imageUrls = images.stream()
                .map(img -> imageHubClient.getImageUrl(img.getImageId(), "large"))
                .collect(Collectors.toList());
        vo.setImages(imageUrls);
        images.stream().filter(PropertyImageEntity::getIsCover).findFirst()
                .ifPresent(cover -> vo.setCoverUrl(
                        imageHubClient.getImageUrl(cover.getImageId(), "medium")));
        return vo;
    }

    private PropertyVO convertToListVO(PropertyEntity entity) {
        PropertyVO vo = new PropertyVO();
        BeanUtils.copyProperties(entity, vo);
        PropertyImageEntity cover = imageMapper.selectOne(new LambdaQueryWrapper<PropertyImageEntity>()
                .eq(PropertyImageEntity::getPropertyId, entity.getId())
                .eq(PropertyImageEntity::getIsCover, true));
        if (cover != null) {
            vo.setCoverUrl(imageHubClient.getImageUrl(cover.getImageId(), "small"));
        }
        return vo;
    }

    private void publishSyncMessage(String operation, Long propertyId, String appId) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.xss.propertyservice.dto.PropertySyncMessage message =
                    new com.xss.propertyservice.dto.PropertySyncMessage(operation, propertyId, appId, System.currentTimeMillis());
            String json = mapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(propertyExchange, propertyRoutingKey, json);
            log.info("Published sync message: operation={}, propertyId={}, appId={}", operation, propertyId, appId);
        } catch (Exception e) {
            log.error("Failed to publish sync message: operation={}, propertyId={}", operation, propertyId, e);
        }
    }

    private void publishNotificationMessage(String templateCode, String subject,
                                            Long propertyId, String title, Long price, String appId) {
        try {
            String traceId = java.util.UUID.randomUUID().toString();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode event = mapper.createObjectNode();
            event.put("appId", appId != null ? appId : "default");
            event.put("templateCode", templateCode);
            event.put("receiver", adminEmail);
            event.put("channel", "EMAIL");
            event.put("subject", subject);
            event.put("traceId", traceId);

            com.fasterxml.jackson.databind.node.ObjectNode params = mapper.createObjectNode();
            params.put("propertyId", propertyId);
            params.put("title", title != null ? title : "");
            params.put("price", price != null ? price : 0);
            event.set("params", params);

            String json = mapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(messageSendExchange, messageSendRoutingKey, json);
            log.info("Published notification message: templateCode={}, propertyId={}, receiver={}, traceId={}",
                    templateCode, propertyId, adminEmail, traceId);
        } catch (Exception e) {
            log.error("Failed to publish notification message: templateCode={}, propertyId={}", templateCode, propertyId, e);
        }
    }
}
