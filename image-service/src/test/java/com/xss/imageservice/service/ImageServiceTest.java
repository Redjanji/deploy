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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageServiceTest {

    @Mock
    private ImageMapper imageMapper;

    @Mock
    private MinioStorageService storageService;

    @Mock
    private ImageSecurityChecker securityChecker;

    @Mock
    private ImageConfigProperties config;

    @Mock
    private StatsEventPublisher statsEventPublisher;

    @InjectMocks
    private ImageService imageService;

    private MultipartFile mockFile;
    private byte[] testImageData;

    void setUpUploadMocks() throws IOException {
        mockFile = mock(MultipartFile.class);
        testImageData = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        when(mockFile.getBytes()).thenReturn(testImageData);
        when(mockFile.getOriginalFilename()).thenReturn("test.png");
        when(mockFile.getSize()).thenReturn(1024L);
    }

    @Test
    void upload_success_verifyInsertAndStatsEvent() throws IOException {
        setUpUploadMocks();
        String appId = "test-app";
        Long ownerId = 1L;
        BufferedImage mockBufferedImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        byte[] mockWebpBytes = "webp-data".getBytes();

        when(config.getWebpQuality()).thenReturn(0.8f);
        when(config.getThumbnailWidths()).thenReturn(List.of(1280, 640, 200));
        when(config.getCdnUrl()).thenReturn("");
        when(config.getLocalUrl()).thenReturn("/api/images/");
        when(storageService.upload(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn("test/key.webp");
        when(imageMapper.insert(any(ImageEntity.class))).thenAnswer(invocation -> {
            ImageEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        try (MockedStatic<com.xss.imageservice.util.ImageConverter> mockedConverter = mockStatic(com.xss.imageservice.util.ImageConverter.class)) {
            mockedConverter.when(() -> com.xss.imageservice.util.ImageConverter.readImage(any(byte[].class)))
                    .thenReturn(mockBufferedImage);
            mockedConverter.when(() -> com.xss.imageservice.util.ImageConverter.toWebP(any(BufferedImage.class), anyFloat()))
                    .thenReturn(mockWebpBytes);
            mockedConverter.when(() -> com.xss.imageservice.util.ImageConverter.resizeAndConvert(any(byte[].class), anyInt(), anyInt(), anyFloat()))
                    .thenReturn(mockWebpBytes);

            ImageVO result = imageService.upload(mockFile, appId, ownerId);

            assertNotNull(result);
            assertEquals(1L, result.getId());

            ArgumentCaptor<ImageEntity> captor = ArgumentCaptor.forClass(ImageEntity.class);
            verify(imageMapper, times(1)).insert(captor.capture());
            ImageEntity captured = captor.getValue();
            assertEquals(appId, captured.getAppId());
            assertEquals(ownerId, captured.getOwnerId());
            assertEquals("READY", captured.getStatus());
            assertEquals("image/webp", captured.getMimeType());
            assertEquals(800, captured.getWidth());
            assertEquals(600, captured.getHeight());

            verify(statsEventPublisher, times(1)).publish(
                    eq("IMAGE_UPLOAD"), eq(appId), eq(ownerId), eq(1L), anyMap()
            );

            verify(storageService, times(4)).upload(any(byte[].class), eq(appId), anyString(), eq("webp"));
        }
    }

    @Test
    void upload_securityCheckerThrowsException_propagatesException() throws IOException {
        setUpUploadMocks();
        String appId = "test-app";
        Long ownerId = 1L;

        doThrow(new SecurityException("文件不安全")).when(securityChecker).check(mockFile);

        assertThrows(SecurityException.class, () -> imageService.upload(mockFile, appId, ownerId));

        verify(imageMapper, never()).insert(any(ImageEntity.class));
        verify(statsEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    void listByApp_withoutOwnerId_onlyPublicImages() {
        String appId = "test-app";
        int page = 1;
        int size = 20;

        ImageEntity publicImage = new ImageEntity();
        publicImage.setId(1L);
        publicImage.setAppId(appId);
        publicImage.setOwnerId(null);
        publicImage.setStatus("READY");
        publicImage.setOriginKey("public.webp");
        publicImage.setWidth(800);
        publicImage.setHeight(600);
        publicImage.setFileSize(1024L);
        publicImage.setMimeType("image/webp");

        Page<ImageEntity> pageResult = new Page<>(page, size);
        pageResult.setRecords(List.of(publicImage));
        pageResult.setTotal(1);

        when(config.getCdnUrl()).thenReturn("");
        when(config.getLocalUrl()).thenReturn("/api/images/");
        when(imageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);

        List<ImageVO> result = imageService.listByApp(appId, null, page, size);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());

        ArgumentCaptor<LambdaQueryWrapper<ImageEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(imageMapper, times(1)).selectPage(any(Page.class), captor.capture());
    }

    @Test
    void listByApp_withOwnerId_publicAndOwnedImages() {
        String appId = "test-app";
        Long ownerId = 1L;
        int page = 1;
        int size = 20;

        ImageEntity publicImage = new ImageEntity();
        publicImage.setId(1L);
        publicImage.setAppId(appId);
        publicImage.setOwnerId(null);
        publicImage.setStatus("READY");
        publicImage.setOriginKey("public.webp");
        publicImage.setWidth(800);
        publicImage.setHeight(600);
        publicImage.setFileSize(1024L);
        publicImage.setMimeType("image/webp");

        ImageEntity ownedImage = new ImageEntity();
        ownedImage.setId(2L);
        ownedImage.setAppId(appId);
        ownedImage.setOwnerId(ownerId);
        ownedImage.setStatus("READY");
        ownedImage.setOriginKey("owned.webp");
        ownedImage.setWidth(600);
        ownedImage.setHeight(400);
        ownedImage.setFileSize(512L);
        ownedImage.setMimeType("image/webp");

        Page<ImageEntity> pageResult = new Page<>(page, size);
        pageResult.setRecords(List.of(publicImage, ownedImage));
        pageResult.setTotal(2);

        when(config.getCdnUrl()).thenReturn("");
        when(config.getLocalUrl()).thenReturn("/api/images/");
        when(imageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);

        List<ImageVO> result = imageService.listByApp(appId, ownerId, page, size);

        assertEquals(2, result.size());

        ArgumentCaptor<LambdaQueryWrapper<ImageEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(imageMapper, times(1)).selectPage(any(Page.class), captor.capture());
    }

    @Test
    void delete_success_statusSetToDeletedAndEventPublished() {
        Long imageId = 1L;
        String appId = "test-app";
        Long ownerId = 1L;

        ImageEntity entity = new ImageEntity();
        entity.setId(imageId);
        entity.setAppId(appId);
        entity.setOwnerId(ownerId);
        entity.setStatus("READY");

        when(imageMapper.selectById(imageId)).thenReturn(entity);
        when(imageMapper.updateById(any(ImageEntity.class))).thenReturn(1);

        imageService.delete(imageId, appId, ownerId);

        ArgumentCaptor<ImageEntity> captor = ArgumentCaptor.forClass(ImageEntity.class);
        verify(imageMapper, times(1)).updateById(captor.capture());
        assertEquals("DELETED", captor.getValue().getStatus());

        verify(statsEventPublisher, times(1)).publish(
                eq("IMAGE_DELETE"), eq(appId), eq(ownerId), eq(imageId)
        );
    }

    @Test
    void delete_imageNotFound_throwsException() {
        Long imageId = 999L;
        String appId = "test-app";
        Long ownerId = 1L;

        when(imageMapper.selectById(imageId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> imageService.delete(imageId, appId, ownerId));

        assertEquals(403, exception.getCode());
        assertEquals("无权操作", exception.getMessage());
        verify(imageMapper, never()).updateById(any(ImageEntity.class));
        verify(statsEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    void delete_unauthorized_throwsException() {
        Long imageId = 1L;
        String appId = "test-app";
        Long ownerId = 2L;

        ImageEntity entity = new ImageEntity();
        entity.setId(imageId);
        entity.setAppId(appId);
        entity.setOwnerId(1L);
        entity.setStatus("READY");

        when(imageMapper.selectById(imageId)).thenReturn(entity);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> imageService.delete(imageId, appId, ownerId));

        assertEquals(403, exception.getCode());
        assertEquals("无权操作他人私有图片", exception.getMessage());
        verify(imageMapper, never()).updateById(any(ImageEntity.class));
        verify(statsEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    void getById_exists_returnsImageEntity() {
        Long imageId = 1L;
        ImageEntity entity = new ImageEntity();
        entity.setId(imageId);
        entity.setAppId("test-app");

        when(imageMapper.selectById(imageId)).thenReturn(entity);

        ImageEntity result = imageService.getById(imageId);

        assertNotNull(result);
        assertEquals(imageId, result.getId());
        assertEquals("test-app", result.getAppId());
    }

    @Test
    void getBaseUrl_withCdn_returnsCdnUrl() {
        when(config.getCdnUrl()).thenReturn("https://cdn.example.com/");
        when(config.getLocalUrl()).thenReturn("/api/images/");

        String result = imageService.getBaseUrl();

        assertEquals("https://cdn.example.com/", result);
    }

    @Test
    void getBaseUrl_withoutCdn_returnsLocalUrl() {
        when(config.getCdnUrl()).thenReturn("");
        when(config.getLocalUrl()).thenReturn("/api/images/");

        String result = imageService.getBaseUrl();

        assertEquals("/api/images/", result);
    }
}
