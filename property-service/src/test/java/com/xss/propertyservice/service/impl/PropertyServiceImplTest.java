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
import com.xss.propertyservice.vo.PropertyDetailVO;
import com.xss.propertyservice.vo.PropertyVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceImplTest {

    @Mock
    private PropertyMapper propertyMapper;

    @Mock
    private PropertyImageMapper imageMapper;

    @Mock
    private ImageHubClient imageHubClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private StatsEventPublisher statsEventPublisher;

    @InjectMocks
    private PropertyServiceImpl propertyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(propertyService, "maxImages", 20);
        ReflectionTestUtils.setField(propertyService, "geoPrecision", 12);
        ReflectionTestUtils.setField(propertyService, "defaultRadiusKm", 5.0);
        ReflectionTestUtils.setField(propertyService, "propertyExchange", "property.exchange");
        ReflectionTestUtils.setField(propertyService, "propertyRoutingKey", "property.routing");
        ReflectionTestUtils.setField(propertyService, "messageSendExchange", "message.exchange");
        ReflectionTestUtils.setField(propertyService, "messageSendRoutingKey", "message.routing");
        ReflectionTestUtils.setField(propertyService, "adminEmail", "admin@test.com");
    }

    private PropertyEntity createMockPropertyEntity(Long id, String appId, Long ownerId) {
        PropertyEntity entity = new PropertyEntity();
        entity.setId(id);
        entity.setAppId(appId);
        entity.setOwnerId(ownerId);
        entity.setTitle("测试房源");
        entity.setType("apartment");
        entity.setPrice(5000L);
        entity.setRentalArea(80);
        entity.setRooms("2室1厅");
        entity.setLat(new BigDecimal("39.9042"));
        entity.setLng(new BigDecimal("116.4074"));
        entity.setGeohash("wx4g0s8q9j5m");
        entity.setCityCode("110100");
        entity.setCityName("北京市");
        entity.setPublishStatus(1);
        entity.setStatus(1);
        entity.setHot(false);
        entity.setFeatured(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private PropertyImageEntity createMockImageEntity(Long id, Long propertyId, Long imageId, boolean isCover, int sortOrder) {
        PropertyImageEntity entity = new PropertyImageEntity();
        entity.setId(id);
        entity.setPropertyId(propertyId);
        entity.setImageId(imageId);
        entity.setIsCover(isCover);
        entity.setSortOrder(sortOrder);
        return entity;
    }

    // ==================== create 测试 ====================

    @Test
    @DisplayName("create: 正常创建成功(验证insert, mq消息发送, stats事件发送)")
    void create_shouldCreatePropertySuccessfully() {
        PropertyCreateRequest req = new PropertyCreateRequest();
        req.setTitle("测试房源");
        req.setType("apartment");
        req.setPrice(5000L);
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));
        req.setImageIds(Arrays.asList(1L, 2L, 3L));

        PropertyEntity savedEntity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.insert(any(PropertyEntity.class))).thenAnswer(invocation -> {
            PropertyEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(savedEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(
                        createMockImageEntity(1L, 1L, 1L, true, 0),
                        createMockImageEntity(2L, 1L, 2L, false, 1),
                        createMockImageEntity(3L, 1L, 3L, false, 2)
                ));
        when(imageHubClient.getImageUrl(anyLong(), anyString())).thenAnswer(invocation ->
                "https://cdn.example.com/" + invocation.getArgument(0) + "/" + invocation.getArgument(1) + ".webp");

        PropertyDetailVO result = propertyService.create(req, "test-app", 1001L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("测试房源", result.getTitle());

        ArgumentCaptor<PropertyEntity> entityCaptor = ArgumentCaptor.forClass(PropertyEntity.class);
        verify(propertyMapper, times(1)).insert(entityCaptor.capture());
        PropertyEntity capturedEntity = entityCaptor.getValue();
        assertEquals("test-app", capturedEntity.getAppId());
        assertEquals(1001L, capturedEntity.getOwnerId());
        assertEquals(0, capturedEntity.getPublishStatus());
        assertEquals(0, capturedEntity.getStatus());
        assertNotNull(capturedEntity.getGeohash());

        verify(imageMapper, times(3)).insert(any(PropertyImageEntity.class));

        verify(rabbitTemplate, atLeast(2)).convertAndSend(anyString(), anyString(), anyString());

        verify(statsEventPublisher, times(1)).publish(
                eq("PROPERTY_CREATE"), eq("test-app"), eq(1001L), eq(1L));
    }

    @Test
    @DisplayName("create: 图片数量超过maxImages抛出异常")
    void create_whenImageCountExceedsMaxImages_shouldThrowException() {
        PropertyCreateRequest req = new PropertyCreateRequest();
        req.setTitle("测试房源");
        req.setType("apartment");
        List<Long> imageIds = Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L
        );
        req.setImageIds(imageIds);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.create(req, "test-app", 1001L));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("图片数量不能超过20"));

        verify(propertyMapper, never()).insert(any(PropertyEntity.class));
        verify(imageMapper, never()).insert(any(PropertyImageEntity.class));
    }

    @Test
    @DisplayName("create: appId为null时使用默认值default")
    void create_withNullAppId_shouldUseDefaultAppId() {
        PropertyCreateRequest req = new PropertyCreateRequest();
        req.setTitle("测试房源");
        req.setType("apartment");
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));

        PropertyEntity savedEntity = createMockPropertyEntity(1L, "default", 1001L);
        when(propertyMapper.insert(any(PropertyEntity.class))).thenAnswer(invocation -> 1);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(savedEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        propertyService.create(req, null, 1001L);

        ArgumentCaptor<PropertyEntity> entityCaptor = ArgumentCaptor.forClass(PropertyEntity.class);
        verify(propertyMapper).insert(entityCaptor.capture());
        assertEquals("default", entityCaptor.getValue().getAppId());
    }

    @Test
    @DisplayName("create: 没有图片时正常创建")
    void create_withNoImages_shouldCreateSuccessfully() {
        PropertyCreateRequest req = new PropertyCreateRequest();
        req.setTitle("测试房源");
        req.setType("apartment");
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));
        req.setImageIds(null);

        PropertyEntity savedEntity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.insert(any(PropertyEntity.class))).thenAnswer(invocation -> 1);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(savedEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PropertyDetailVO result = propertyService.create(req, "test-app", 1001L);

        assertNotNull(result);
        verify(imageMapper, never()).insert(any(PropertyImageEntity.class));
    }

    // ==================== update 测试 ====================

    @Test
    @DisplayName("update: 正常更新成功")
    void update_shouldUpdatePropertySuccessfully() {
        PropertyEntity existingEntity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(existingEntity);

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setTitle("更新后的标题");
        req.setPrice(6000L);

        PropertyEntity updatedEntity = createMockPropertyEntity(1L, "test-app", 1001L);
        updatedEntity.setTitle("更新后的标题");
        updatedEntity.setPrice(6000L);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(updatedEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PropertyDetailVO result = propertyService.update(1L, req, "test-app", 1001L);

        assertNotNull(result);
        verify(propertyMapper, times(1)).updateById(any(PropertyEntity.class));
    }

    @Test
    @DisplayName("update: 房源不存在抛出异常")
    void update_whenPropertyNotExists_shouldThrowException() {
        when(propertyMapper.selectById(999L)).thenReturn(null);

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setTitle("更新标题");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.update(999L, req, "test-app", 1001L));

        assertEquals(404, exception.getCode());
        assertEquals("房源不存在", exception.getMessage());
    }

    @Test
    @DisplayName("update: 越权操作抛出异常")
    void update_whenNotOwner_shouldThrowException() {
        PropertyEntity existingEntity = createMockPropertyEntity(1L, "test-app", 2002L);
        when(propertyMapper.selectById(1L)).thenReturn(existingEntity);

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setTitle("更新标题");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.update(1L, req, "test-app", 1001L));

        assertEquals(403, exception.getCode());
        assertEquals("无权操作该房源", exception.getMessage());
    }

    @Test
    @DisplayName("update: appId不匹配抛出异常")
    void update_whenAppIdNotMatch_shouldThrowException() {
        PropertyEntity existingEntity = createMockPropertyEntity(1L, "other-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(existingEntity);

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setTitle("更新标题");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.update(1L, req, "test-app", 1001L));

        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("update: ownerId为null时跳过权限检查")
    void update_withNullOwnerId_shouldSkipOwnerCheck() {
        PropertyEntity existingEntity = createMockPropertyEntity(1L, "test-app", 2002L);
        when(propertyMapper.selectById(1L)).thenReturn(existingEntity);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setTitle("更新标题");

        assertDoesNotThrow(() -> propertyService.update(1L, req, "test-app", null));
        verify(propertyMapper, times(1)).updateById(any(PropertyEntity.class));
    }

    @Test
    @DisplayName("update: 更新经纬度时更新geohash")
    void update_whenLatLngChanged_shouldUpdateGeohash() {
        PropertyEntity existingEntity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(existingEntity);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setLat(new BigDecimal("31.2304"));
        req.setLng(new BigDecimal("121.4737"));

        propertyService.update(1L, req, "test-app", 1001L);

        ArgumentCaptor<PropertyEntity> captor = ArgumentCaptor.forClass(PropertyEntity.class);
        verify(propertyMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getGeohash());
        assertNotEquals("wx4g0s8q9j5m", captor.getValue().getGeohash());
    }

    @Test
    @DisplayName("update: 更新图片时先删除旧图片再插入新图片")
    void update_whenImageIdsProvided_shouldReplaceImages() {
        PropertyEntity existingEntity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(existingEntity);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingEntity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(createMockImageEntity(1L, 1L, 10L, true, 0)));

        PropertyUpdateRequest req = new PropertyUpdateRequest();
        req.setImageIds(Arrays.asList(20L, 21L));

        propertyService.update(1L, req, "test-app", 1001L);

        verify(imageMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        verify(imageMapper, times(2)).insert(any(PropertyImageEntity.class));
    }

    // ==================== getDetail 测试 ====================

    @Test
    @DisplayName("getDetail: 存在返回详情")
    void getDetail_whenPropertyExists_shouldReturnDetail() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(
                        createMockImageEntity(1L, 1L, 1L, true, 0),
                        createMockImageEntity(2L, 1L, 2L, false, 1)
                ));
        when(imageHubClient.getImageUrl(anyLong(), eq("large")))
                .thenReturn("https://cdn.example.com/1/large.webp");
        when(imageHubClient.getImageUrl(anyLong(), eq("medium")))
                .thenReturn("https://cdn.example.com/1/medium.webp");

        PropertyDetailVO result = propertyService.getDetail(1L, "test-app");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("测试房源", result.getTitle());
        assertNotNull(result.getImages());
        assertEquals(2, result.getImages().size());
        assertNotNull(result.getCoverUrl());
    }

    @Test
    @DisplayName("getDetail: 不存在抛出异常")
    void getDetail_whenPropertyNotExists_shouldThrowException() {
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.getDetail(999L, "test-app"));

        assertEquals(404, exception.getCode());
        assertEquals("房源不存在", exception.getMessage());
    }

    @Test
    @DisplayName("getDetail: 没有图片时images为空列表")
    void getDetail_whenNoImages_shouldReturnEmptyImageList() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        PropertyDetailVO result = propertyService.getDetail(1L, "test-app");

        assertNotNull(result);
        assertNotNull(result.getImages());
        assertTrue(result.getImages().isEmpty());
        assertNull(result.getCoverUrl());
    }

    // ==================== viewDetail 测试 ====================

    @Test
    @DisplayName("viewDetail: 正常查看(调用getDetail + 发送PROPERTY_VIEW事件)")
    void viewDetail_shouldCallGetDetailAndPublishViewEvent() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        PropertyDetailVO result = propertyService.viewDetail(1L, "test-app", 2002L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(statsEventPublisher, times(1)).publish(
                eq("PROPERTY_VIEW"), eq("test-app"), eq(2002L), eq(1L));
    }

    @Test
    @DisplayName("viewDetail: userId为null时也发布事件")
    void viewDetail_withNullUserId_shouldStillPublishEvent() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        propertyService.viewDetail(1L, "test-app", null);

        verify(statsEventPublisher, times(1)).publish(
                eq("PROPERTY_VIEW"), eq("test-app"), isNull(), eq(1L));
    }

    // ==================== search 测试 ====================

    @Test
    @DisplayName("search: 正常搜索返回分页结果")
    void search_shouldReturnPagedResults() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setPage(1);
        req.setSize(10);
        req.setCityCode("110100");
        req.setKeyword("测试");

        PropertyEntity entity1 = createMockPropertyEntity(1L, "test-app", 1001L);
        PropertyEntity entity2 = createMockPropertyEntity(2L, "test-app", 1002L);
        Page<PropertyEntity> pageResult = new Page<>(1, 10, 2);
        pageResult.setRecords(Arrays.asList(entity1, entity2));

        when(propertyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);
        when(imageMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createMockImageEntity(1L, 1L, 1L, true, 0));
        when(imageHubClient.getImageUrl(anyLong(), eq("small")))
                .thenReturn("https://cdn.example.com/1/small.webp");

        IPage<PropertyVO> result = propertyService.search(req, "test-app");

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    @DisplayName("search: 带经纬度的geohash搜索")
    void search_withLatLng_shouldUseGeohashSearch() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setPage(1);
        req.setSize(10);
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));
        req.setRadius(3.0);

        Page<PropertyEntity> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(Collections.emptyList());

        when(propertyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        IPage<PropertyVO> result = propertyService.search(req, "test-app");

        assertNotNull(result);
        verify(propertyMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("search: 未指定radius时使用默认值defaultRadiusKm")
    void search_withoutRadius_shouldUseDefaultRadius() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setPage(1);
        req.setSize(10);
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));

        Page<PropertyEntity> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(Collections.emptyList());

        when(propertyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        propertyService.search(req, "test-app");

        verify(propertyMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("search: 支持按价格区间搜索")
    void search_withPriceRange_shouldFilterByPrice() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setPage(1);
        req.setSize(10);
        req.setMinPrice(3000L);
        req.setMaxPrice(8000L);

        Page<PropertyEntity> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(Collections.emptyList());

        when(propertyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        propertyService.search(req, "test-app");

        verify(propertyMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("search: 支持按类型搜索")
    void search_withType_shouldFilterByType() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setPage(1);
        req.setSize(10);
        req.setType("apartment");

        Page<PropertyEntity> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(Collections.emptyList());

        when(propertyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        propertyService.search(req, "test-app");

        verify(propertyMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("search: 支持热门和精选筛选")
    void search_withHotAndFeatured_shouldFilter() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setPage(1);
        req.setSize(10);
        req.setHot(true);
        req.setFeatured(true);

        Page<PropertyEntity> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(Collections.emptyList());

        when(propertyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        propertyService.search(req, "test-app");

        verify(propertyMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ==================== delete 测试 ====================

    @Test
    @DisplayName("delete: 正常删除")
    void delete_shouldDeletePropertySuccessfully() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        assertDoesNotThrow(() -> propertyService.delete(1L, "test-app", 1001L));

        verify(propertyMapper, times(1)).deleteById(1L);
        verify(rabbitTemplate, atLeast(1)).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("delete: 越权操作抛出异常")
    void delete_whenNotOwner_shouldThrowException() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 2002L);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.delete(1L, "test-app", 1001L));

        assertEquals(403, exception.getCode());
        assertEquals("无权操作该房源", exception.getMessage());
    }

    @Test
    @DisplayName("delete: 房源不存在抛出异常")
    void delete_whenPropertyNotExists_shouldThrowException() {
        when(propertyMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.delete(999L, "test-app", 1001L));

        assertEquals(404, exception.getCode());
    }

    // ==================== updatePublishStatus 测试 ====================

    @Test
    @DisplayName("updatePublishStatus: 正常更新发布状态")
    void updatePublishStatus_shouldUpdateSuccessfully() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        entity.setPublishStatus(0);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        propertyService.updatePublishStatus(1L, 1, "test-app", 1001L);

        ArgumentCaptor<PropertyEntity> captor = ArgumentCaptor.forClass(PropertyEntity.class);
        verify(propertyMapper, times(1)).updateById(captor.capture());
        assertEquals(1, captor.getValue().getPublishStatus());
        verify(rabbitTemplate, atLeast(1)).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("updatePublishStatus: 越权操作抛出异常")
    void updatePublishStatus_whenNotOwner_shouldThrowException() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 2002L);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.updatePublishStatus(1L, 1, "test-app", 1001L));

        assertEquals(403, exception.getCode());
    }

    // ==================== updateAuditStatus 测试 ====================

    @Test
    @DisplayName("updateAuditStatus: 正常更新审核状态(发送通知消息)")
    void updateAuditStatus_shouldUpdateAndSendNotification() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        entity.setStatus(2);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        propertyService.updateAuditStatus(1L, 1, "test-app");

        ArgumentCaptor<PropertyEntity> captor = ArgumentCaptor.forClass(PropertyEntity.class);
        verify(propertyMapper, times(1)).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());

        verify(rabbitTemplate, atLeast(2)).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("updateAuditStatus: 审核驳回也发送通知")
    void updateAuditStatus_whenRejected_shouldSendNotification() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        propertyService.updateAuditStatus(1L, 3, "test-app");

        verify(rabbitTemplate, atLeast(2)).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("updateAuditStatus: 房源不存在抛出异常")
    void updateAuditStatus_whenPropertyNotExists_shouldThrowException() {
        when(propertyMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> propertyService.updateAuditStatus(999L, 1, "test-app"));

        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("updateAuditStatus: 无需ownerId(管理员操作)")
    void updateAuditStatus_noOwnerIdRequired_shouldWork() {
        PropertyEntity entity = createMockPropertyEntity(1L, "test-app", 1001L);
        when(propertyMapper.selectById(1L)).thenReturn(entity);

        assertDoesNotThrow(() ->
                propertyService.updateAuditStatus(1L, 1, "test-app"));

        verify(propertyMapper, times(1)).updateById(any(PropertyEntity.class));
    }
}
