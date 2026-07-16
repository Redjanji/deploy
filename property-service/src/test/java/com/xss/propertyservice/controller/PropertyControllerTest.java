package com.xss.propertyservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.propertyservice.common.Result;
import com.xss.propertyservice.dto.PropertyCreateRequest;
import com.xss.propertyservice.dto.PropertySearchRequest;
import com.xss.propertyservice.service.PropertyService;
import com.xss.propertyservice.vo.PropertyDetailVO;
import com.xss.propertyservice.vo.PropertyVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PropertyController.class)
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PropertyService propertyService;

    private PropertyDetailVO createMockDetailVO(Long id) {
        PropertyDetailVO vo = new PropertyDetailVO();
        vo.setId(id);
        vo.setAppId("test-app");
        vo.setTitle("测试房源" + id);
        vo.setType("apartment");
        vo.setPrice(5000L);
        vo.setRentalArea(80);
        vo.setRooms("2室1厅");
        vo.setLat(new BigDecimal("39.9042"));
        vo.setLng(new BigDecimal("116.4074"));
        vo.setCityCode("110100");
        vo.setCityName("北京市");
        vo.setPublishStatus(1);
        vo.setStatus(1);
        vo.setOwnerId(1001L);
        vo.setImages(Arrays.asList("https://cdn.example.com/1/large.webp"));
        vo.setCoverUrl("https://cdn.example.com/1/medium.webp");
        vo.setCreatedAt(LocalDateTime.now());
        vo.setUpdatedAt(LocalDateTime.now());
        return vo;
    }

    private PropertyVO createMockPropertyVO(Long id) {
        PropertyVO vo = new PropertyVO();
        vo.setId(id);
        vo.setTitle("测试房源" + id);
        vo.setType("apartment");
        vo.setPrice(5000L);
        vo.setRentalArea(80);
        vo.setRooms("2室1厅");
        vo.setCityName("北京市");
        vo.setCoverUrl("https://cdn.example.com/1/small.webp");
        vo.setHot(false);
        vo.setFeatured(false);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Test
    @DisplayName("POST /api/properties: 创建房源成功")
    void createProperty_shouldReturnSuccess() throws Exception {
        PropertyCreateRequest req = new PropertyCreateRequest();
        req.setTitle("测试房源");
        req.setType("apartment");
        req.setPrice(5000L);
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));

        PropertyDetailVO detailVO = createMockDetailVO(1L);
        when(propertyService.create(any(PropertyCreateRequest.class), anyString(), anyLong()))
                .thenReturn(detailVO);

        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-App-Id", "test-app")
                        .header("X-User-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试房源1"));

        verify(propertyService, times(1)).create(
                any(PropertyCreateRequest.class), eq("test-app"), eq(1001L));
    }

    @Test
    @DisplayName("POST /api/properties: 缺少标题时返回400")
    void createProperty_withBlankTitle_shouldReturnBadRequest() throws Exception {
        PropertyCreateRequest req = new PropertyCreateRequest();
        req.setTitle("");
        req.setType("apartment");
        req.setLat(new BigDecimal("39.9042"));
        req.setLng(new BigDecimal("116.4074"));

        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-App-Id", "test-app")
                        .header("X-User-Id", "1001"))
                .andExpect(status().isBadRequest());

        verify(propertyService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/properties/{id}: 获取详情成功(验证调用viewDetail)")
    void getPropertyDetail_shouldCallViewDetailAndReturnSuccess() throws Exception {
        PropertyDetailVO detailVO = createMockDetailVO(1L);
        when(propertyService.viewDetail(1L, "test-app", 2002L)).thenReturn(detailVO);

        mockMvc.perform(get("/api/properties/1")
                        .header("X-App-Id", "test-app")
                        .header("X-User-Id", "2002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试房源1"));

        verify(propertyService, times(1)).viewDetail(eq(1L), eq("test-app"), eq(2002L));
    }

    @Test
    @DisplayName("GET /api/properties/{id}: 不带X-User-Id也能正常访问")
    void getPropertyDetail_withoutUserId_shouldStillWork() throws Exception {
        PropertyDetailVO detailVO = createMockDetailVO(1L);
        when(propertyService.viewDetail(eq(1L), eq("test-app"), isNull())).thenReturn(detailVO);

        mockMvc.perform(get("/api/properties/1")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(propertyService, times(1)).viewDetail(eq(1L), eq("test-app"), isNull());
    }

    @Test
    @DisplayName("GET /api/properties: 搜索列表")
    void searchProperties_shouldReturnPagedResults() throws Exception {
        Page<PropertyVO> page = new Page<>(1, 20, 2);
        page.setRecords(Arrays.asList(
                createMockPropertyVO(1L),
                createMockPropertyVO(2L)
        ));
        when(propertyService.search(any(PropertySearchRequest.class), anyString()))
                .thenReturn(page);

        mockMvc.perform(get("/api/properties")
                        .param("cityCode", "110100")
                        .param("page", "1")
                        .param("size", "20")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(1));

        verify(propertyService, times(1)).search(
                any(PropertySearchRequest.class), eq("test-app"));
    }

    @Test
    @DisplayName("GET /api/properties: 支持关键字搜索")
    void searchProperties_withKeyword_shouldPassKeyword() throws Exception {
        Page<PropertyVO> page = new Page<>(1, 20, 0);
        page.setRecords(Collections.emptyList());
        when(propertyService.search(any(PropertySearchRequest.class), anyString()))
                .thenReturn(page);

        mockMvc.perform(get("/api/properties")
                        .param("keyword", "测试")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(propertyService, times(1)).search(
                any(PropertySearchRequest.class), anyString());
    }

    @Test
    @DisplayName("DELETE /api/properties/{id}: 删除成功")
    void deleteProperty_shouldReturnSuccess() throws Exception {
        doNothing().when(propertyService).delete(1L, "test-app", 1001L);

        mockMvc.perform(delete("/api/properties/1")
                        .header("X-App-Id", "test-app")
                        .header("X-User-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(propertyService, times(1)).delete(eq(1L), eq("test-app"), eq(1001L));
    }

    @Test
    @DisplayName("PUT /api/properties/{id}: 更新房源成功")
    void updateProperty_shouldReturnSuccess() throws Exception {
        PropertyDetailVO detailVO = createMockDetailVO(1L);
        detailVO.setTitle("更新后的房源");
        when(propertyService.update(eq(1L), any(), eq("test-app"), eq(1001L)))
                .thenReturn(detailVO);

        String updateBody = "{\"title\":\"更新后的房源\",\"price\":6000}";

        mockMvc.perform(put("/api/properties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .header("X-App-Id", "test-app")
                        .header("X-User-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(propertyService, times(1)).update(
                eq(1L), any(), eq("test-app"), eq(1001L));
    }

    @Test
    @DisplayName("PUT /api/properties/{id}/publish-status: 更新发布状态成功")
    void updatePublishStatus_shouldReturnSuccess() throws Exception {
        doNothing().when(propertyService).updatePublishStatus(1L, 1, "test-app", 1001L);

        mockMvc.perform(put("/api/properties/1/publish-status")
                        .param("status", "1")
                        .header("X-App-Id", "test-app")
                        .header("X-User-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(propertyService, times(1)).updatePublishStatus(
                eq(1L), eq(1), eq("test-app"), eq(1001L));
    }

    @Test
    @DisplayName("PUT /api/properties/{id}/audit-status: 更新审核状态成功")
    void updateAuditStatus_shouldReturnSuccess() throws Exception {
        doNothing().when(propertyService).updateAuditStatus(1L, 1, "test-app");

        mockMvc.perform(put("/api/properties/1/audit-status")
                        .param("status", "1")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(propertyService, times(1)).updateAuditStatus(
                eq(1L), eq(1), eq("test-app"));
    }
}
