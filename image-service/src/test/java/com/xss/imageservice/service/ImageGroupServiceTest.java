package com.xss.imageservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.imageservice.exception.BusinessException;
import com.xss.imageservice.mapper.ImageGroupItemMapper;
import com.xss.imageservice.mapper.ImageGroupMapper;
import com.xss.imageservice.model.entity.ImageEntity;
import com.xss.imageservice.model.entity.ImageGroupEntity;
import com.xss.imageservice.model.entity.ImageGroupItemEntity;
import com.xss.imageservice.model.vo.GroupVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageGroupServiceTest {

    @Mock
    private ImageGroupMapper groupMapper;

    @Mock
    private ImageGroupItemMapper groupItemMapper;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageGroupService imageGroupService;

    @Test
    void create_newGroup_createsGroup() {
        String appId = "test-app";
        String name = "new-group";
        String description = "A new group";

        when(groupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(groupMapper.insert(any(ImageGroupEntity.class))).thenAnswer(invocation -> {
            ImageGroupEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        ImageGroupEntity result = imageGroupService.create(appId, name, description);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(name, result.getName());
        assertEquals(description, result.getDescription());
        assertEquals(appId, result.getAppId());

        ArgumentCaptor<ImageGroupEntity> captor = ArgumentCaptor.forClass(ImageGroupEntity.class);
        verify(groupMapper, times(1)).insert(captor.capture());
        assertEquals(0, captor.getValue().getSortOrder());
    }

    @Test
    void create_existingGroup_returnsExistingGroup() {
        String appId = "test-app";
        String name = "existing-group";
        String description = "Existing description";

        ImageGroupEntity existingGroup = new ImageGroupEntity();
        existingGroup.setId(1L);
        existingGroup.setAppId(appId);
        existingGroup.setName(name);
        existingGroup.setDescription("Original description");

        when(groupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingGroup);

        ImageGroupEntity result = imageGroupService.create(appId, name, description);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Original description", result.getDescription());

        verify(groupMapper, never()).insert(any(ImageGroupEntity.class));
    }

    @Test
    void addImage_success_addsImageToGroup() {
        Long groupId = 1L;
        String appId = "test-app";
        Long ownerId = 1L;
        Long imageId = 10L;
        Integer sortOrder = 5;

        ImageGroupEntity group = new ImageGroupEntity();
        group.setId(groupId);
        group.setAppId(appId);

        ImageEntity image = new ImageEntity();
        image.setId(imageId);
        image.setAppId(appId);
        image.setOwnerId(ownerId);

        when(groupMapper.selectById(groupId)).thenReturn(group);
        when(imageService.getById(imageId)).thenReturn(image);
        when(groupItemMapper.insert(any(ImageGroupItemEntity.class))).thenReturn(1);

        imageGroupService.addImage(groupId, appId, ownerId, imageId, sortOrder);

        ArgumentCaptor<ImageGroupItemEntity> captor = ArgumentCaptor.forClass(ImageGroupItemEntity.class);
        verify(groupItemMapper, times(1)).insert(captor.capture());
        assertEquals(groupId, captor.getValue().getGroupId());
        assertEquals(imageId, captor.getValue().getImageId());
        assertEquals(sortOrder, captor.getValue().getSortOrder());
    }

    @Test
    void addImage_groupNotFound_throwsException() {
        Long groupId = 999L;
        String appId = "test-app";
        Long ownerId = 1L;
        Long imageId = 10L;

        when(groupMapper.selectById(groupId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> imageGroupService.addImage(groupId, appId, ownerId, imageId, 0));

        assertEquals(403, exception.getCode());
        assertEquals("无权操作该分组", exception.getMessage());
        verify(groupItemMapper, never()).insert(any(ImageGroupItemEntity.class));
    }

    @Test
    void addImage_unauthorizedGroup_throwsException() {
        Long groupId = 1L;
        String appId = "test-app";
        String wrongAppId = "other-app";
        Long ownerId = 1L;
        Long imageId = 10L;

        ImageGroupEntity group = new ImageGroupEntity();
        group.setId(groupId);
        group.setAppId(appId);

        when(groupMapper.selectById(groupId)).thenReturn(group);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> imageGroupService.addImage(groupId, wrongAppId, ownerId, imageId, 0));

        assertEquals(403, exception.getCode());
        assertEquals("无权操作该分组", exception.getMessage());
        verify(groupItemMapper, never()).insert(any(ImageGroupItemEntity.class));
    }

    @Test
    void addImage_imageNotFound_throwsException() {
        Long groupId = 1L;
        String appId = "test-app";
        Long ownerId = 1L;
        Long imageId = 999L;

        ImageGroupEntity group = new ImageGroupEntity();
        group.setId(groupId);
        group.setAppId(appId);

        when(groupMapper.selectById(groupId)).thenReturn(group);
        when(imageService.getById(imageId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> imageGroupService.addImage(groupId, appId, ownerId, imageId, 0));

        assertEquals(400, exception.getCode());
        assertEquals("图片不存在或无权操作", exception.getMessage());
        verify(groupItemMapper, never()).insert(any(ImageGroupItemEntity.class));
    }

    @Test
    void getGroupWithImages_success_returnsGroupWithImages() {
        Long groupId = 1L;
        String appId = "test-app";
        Long ownerId = 1L;
        String sizeType = "original";

        ImageGroupEntity group = new ImageGroupEntity();
        group.setId(groupId);
        group.setAppId(appId);
        group.setName("Test Group");
        group.setDescription("Test Description");
        group.setSortOrder(1);

        ImageEntity image1 = new ImageEntity();
        image1.setId(1L);
        image1.setAppId(appId);
        image1.setOriginKey("img1.webp");
        image1.setLargeKey("img1-large.webp");
        image1.setMediumKey("img1-medium.webp");
        image1.setSmallKey("img1-small.webp");
        image1.setWidth(800);
        image1.setHeight(600);
        image1.setFileSize(1024L);
        image1.setMimeType("image/webp");

        ImageEntity image2 = new ImageEntity();
        image2.setId(2L);
        image2.setAppId(appId);
        image2.setOriginKey("img2.webp");
        image2.setWidth(400);
        image2.setHeight(300);
        image2.setFileSize(512L);
        image2.setMimeType("image/webp");

        when(groupMapper.selectById(groupId)).thenReturn(group);
        when(groupItemMapper.selectImagesByGroupId(groupId, ownerId)).thenReturn(List.of(image1, image2));
        when(imageService.getBaseUrl()).thenReturn("/api/images/");

        GroupVO result = imageGroupService.getGroupWithImages(groupId, appId, ownerId, sizeType);

        assertNotNull(result);
        assertEquals(groupId, result.getId());
        assertEquals("Test Group", result.getName());
        assertEquals("Test Description", result.getDescription());
        assertEquals(1, result.getSortOrder());
        assertEquals(2, result.getImages().size());
        assertEquals("/api/images/img1.webp", result.getImages().get(0).getUrl());
    }

    @Test
    void listByApp_success_returnsGroups() {
        String appId = "test-app";

        ImageGroupEntity group1 = new ImageGroupEntity();
        group1.setId(1L);
        group1.setAppId(appId);
        group1.setName("Group 1");

        ImageGroupEntity group2 = new ImageGroupEntity();
        group2.setId(2L);
        group2.setAppId(appId);
        group2.setName("Group 2");

        when(groupMapper.selectByAppId(appId)).thenReturn(List.of(group1, group2));

        List<ImageGroupEntity> result = imageGroupService.listByApp(appId);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }
}
