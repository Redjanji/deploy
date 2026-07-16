package com.xss.searchservice.service.impl;

import com.xss.searchservice.common.Result;
import com.xss.searchservice.dto.PropertySearchRequest;
import com.xss.searchservice.entity.PropertyDocument;
import com.xss.searchservice.vo.PropertyDocumentVO;
import com.xss.searchservice.vo.SearchResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertySearchServiceImplTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private IndexOperations indexOperations;

    @InjectMocks
    private PropertySearchServiceImpl propertySearchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(propertySearchService, "propertyServiceUrl", "http://127.0.0.1:8085");
    }

    @Test
    void search_正常搜索返回分页结果() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        PropertyDocument doc1 = createPropertyDocument(1L, "房源1");
        PropertyDocument doc2 = createPropertyDocument(2L, "房源2");

        SearchHit<PropertyDocument> hit1 = mock(SearchHit.class);
        SearchHit<PropertyDocument> hit2 = mock(SearchHit.class);
        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(hit1.getContent()).thenReturn(doc1);
        when(hit2.getContent()).thenReturn(doc2);
        when(searchHits.getTotalHits()).thenReturn(2L);
        when(searchHits.stream()).thenReturn(Stream.of(hit1, hit2));
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        SearchResultVO result = propertySearchService.search(request, appId);

        assertNotNull(result);
        assertEquals(2L, result.getTotal());
        assertEquals(10, result.getSize());
        assertEquals(1, result.getCurrent());
        assertEquals(1, result.getPages());
        assertEquals(2, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
        assertEquals(2L, result.getRecords().get(1).getId());

        verify(elasticsearchOperations).search(any(CriteriaQuery.class), eq(PropertyDocument.class));
    }

    @Test
    void search_带关键字全文搜索() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setKeyword("精装");
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        PropertyDocument doc = createPropertyDocument(1L, "精装两居室");
        SearchHit<PropertyDocument> hit = mock(SearchHit.class);
        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(hit.getContent()).thenReturn(doc);
        when(searchHits.getTotalHits()).thenReturn(1L);
        when(searchHits.stream()).thenReturn(Stream.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        SearchResultVO result = propertySearchService.search(request, appId);

        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("精装两居室", result.getRecords().get(0).getTitle());
    }

    @Test
    void search_带过滤条件搜索() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setCityCode("110000");
        request.setType("APARTMENT");
        request.setRooms("2室1厅");
        request.setMinPrice(2000L);
        request.setMaxPrice(5000L);
        request.setHot(true);
        request.setFeatured(true);
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        PropertyDocument doc = createPropertyDocument(1L, "测试房源");
        SearchHit<PropertyDocument> hit = mock(SearchHit.class);
        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(hit.getContent()).thenReturn(doc);
        when(searchHits.getTotalHits()).thenReturn(1L);
        when(searchHits.stream()).thenReturn(Stream.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        SearchResultVO result = propertySearchService.search(request, appId);

        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        verify(elasticsearchOperations).search(any(CriteriaQuery.class), eq(PropertyDocument.class));
    }

    @Test
    void search_空结果返回空列表() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(searchHits.stream()).thenReturn(Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        SearchResultVO result = propertySearchService.search(request, appId);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getPages());
    }

    @Test
    void search_索引不存在时自动创建() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(searchHits.stream()).thenReturn(Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        propertySearchService.search(request, appId);

        verify(indexOperations).createWithMapping();
        verify(elasticsearchOperations).search(any(Query.class), eq(PropertyDocument.class));
    }

    @Test
    void search_索引创建失败不影响搜索() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);
        doThrow(new RuntimeException("Index creation failed")).when(indexOperations).createWithMapping();
        when(searchHits.getTotalHits()).thenReturn(0L);
        when(searchHits.stream()).thenReturn(Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        assertDoesNotThrow(() -> propertySearchService.search(request, appId));
        verify(elasticsearchOperations).search(any(Query.class), eq(PropertyDocument.class));
    }

    @Test
    void indexProperty_成功索引房源() {
        Long propertyId = 1L;
        Map<String, Object> propertyData = createPropertyData(propertyId);
        Result result = Result.success(propertyData);
        ResponseEntity<Result> responseEntity = new ResponseEntity<>(result, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(responseEntity);
        when(elasticsearchOperations.save(any(PropertyDocument.class))).thenReturn(null);

        propertySearchService.indexProperty(propertyId);

        verify(elasticsearchOperations).save(any(PropertyDocument.class));
    }

    @Test
    void indexProperty_房源不存在时记录警告() {
        Long propertyId = 999L;
        Result result = Result.fail(404, "Not Found");
        ResponseEntity<Result> responseEntity = new ResponseEntity<>(result, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(responseEntity);

        propertySearchService.indexProperty(propertyId);

        verify(elasticsearchOperations, never()).save(any(PropertyDocument.class));
    }

    @Test
    void indexProperty_RestTemplate调用失败不抛出异常() {
        Long propertyId = 1L;

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        assertDoesNotThrow(() -> propertySearchService.indexProperty(propertyId));
    }

    @Test
    void deleteProperty_成功删除房源() {
        Long propertyId = 1L;

        when(elasticsearchOperations.delete(anyString(), eq(PropertyDocument.class))).thenReturn("1");

        assertDoesNotThrow(() -> propertySearchService.deleteProperty(propertyId));

        verify(elasticsearchOperations).delete(eq("1"), eq(PropertyDocument.class));
    }

    @Test
    void deleteProperty_删除失败不抛出异常() {
        Long propertyId = 1L;

        when(elasticsearchOperations.delete(anyString(), eq(PropertyDocument.class)))
                .thenThrow(new RuntimeException("Delete failed"));

        assertDoesNotThrow(() -> propertySearchService.deleteProperty(propertyId));
    }

    @Test
    void deleteProperty_带appId参数() {
        Long propertyId = 1L;
        String appId = "test-app";

        when(elasticsearchOperations.delete(anyString(), eq(PropertyDocument.class))).thenReturn("1");

        assertDoesNotThrow(() -> propertySearchService.deleteProperty(propertyId, appId));

        verify(elasticsearchOperations).delete(eq("1"), eq(PropertyDocument.class));
    }

    @Test
    void syncProperty_成功同步房源() {
        Long propertyId = 1L;
        String appId = "test-app";
        Map<String, Object> propertyData = createPropertyData(propertyId);
        propertyData.put("appId", appId);
        Result result = Result.success(propertyData);
        ResponseEntity<Result> responseEntity = new ResponseEntity<>(result, HttpStatus.OK);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(responseEntity);
        when(elasticsearchOperations.save(any(PropertyDocument.class))).thenReturn(null);

        propertySearchService.syncProperty(propertyId, appId);

        verify(elasticsearchOperations).save(any(PropertyDocument.class));
    }

    @Test
    void reindexAll_成功全量重建索引() {
        String appId = "test-app";
        Map<String, Object> propertyData = createPropertyData(1L);

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", Collections.singletonList(propertyData));
        pageData.put("total", 1L);

        Result pageResult = Result.success(pageData);
        ResponseEntity<Result> pageResponse = new ResponseEntity<>(pageResult, HttpStatus.OK);

        Result detailResult = Result.success(propertyData);
        ResponseEntity<Result> detailResponse = new ResponseEntity<>(detailResult, HttpStatus.OK);

        when(restTemplate.exchange(
                contains("/api/properties?page="),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(pageResponse);

        when(restTemplate.exchange(
                matches(".*/api/properties/\\d+"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(detailResponse);

        when(elasticsearchOperations.save(any(PropertyDocument.class))).thenReturn(null);

        propertySearchService.reindexAll(appId);

        verify(elasticsearchOperations, atLeastOnce()).save(any(PropertyDocument.class));
    }

    @Test
    void reindexAll_空结果直接结束() {
        String appId = "test-app";

        Result result = Result.fail(500, "Error");
        ResponseEntity<Result> responseEntity = new ResponseEntity<>(result, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(responseEntity);

        propertySearchService.reindexAll(appId);

        verify(elasticsearchOperations, never()).save(any(PropertyDocument.class));
    }

    @Test
    void reindexAll_records为空时结束() {
        String appId = "test-app";
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", Collections.emptyList());
        pageData.put("total", 0L);

        Result result = Result.success(pageData);
        ResponseEntity<Result> responseEntity = new ResponseEntity<>(result, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenReturn(responseEntity);

        propertySearchService.reindexAll(appId);

        verify(elasticsearchOperations, never()).save(any(PropertyDocument.class));
    }

    @Test
    void reindexAll_调用失败不抛出异常() {
        String appId = "test-app";

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Result.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        assertDoesNotThrow(() -> propertySearchService.reindexAll(appId));
    }

    @Test
    void search_搜索结果字段映射正确() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setPage(1);
        request.setSize(10);
        String appId = "test-app";

        PropertyDocument doc = createPropertyDocument(1L, "测试房源");
        SearchHit<PropertyDocument> hit = mock(SearchHit.class);
        SearchHits<PropertyDocument> searchHits = mock(SearchHits.class);

        when(elasticsearchOperations.indexOps(PropertyDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(hit.getContent()).thenReturn(doc);
        when(searchHits.getTotalHits()).thenReturn(1L);
        when(searchHits.stream()).thenReturn(Stream.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(PropertyDocument.class))).thenReturn(searchHits);

        SearchResultVO result = propertySearchService.search(request, appId);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        PropertyDocumentVO vo = result.getRecords().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("test-app", vo.getAppId());
        assertEquals("测试房源", vo.getTitle());
        assertEquals("APARTMENT", vo.getType());
        assertEquals(3000L, vo.getPrice());
        assertEquals(80, vo.getRentalArea());
        assertEquals("2室1厅", vo.getRooms());
        assertEquals("南", vo.getOrientation());
        assertEquals("中层", vo.getFloor());
        assertEquals("北京市朝阳区测试街道123号", vo.getAddress());
        assertEquals(39.9042, vo.getLat());
        assertEquals(116.4074, vo.getLon());
        assertEquals("北京市", vo.getProvinceName());
        assertEquals("北京市", vo.getCityName());
        assertEquals("朝阳区", vo.getDistrictName());
        assertEquals("精装修", vo.getDecoration());
        assertEquals("这是一套测试房源", vo.getDescription());
        assertTrue(vo.getHot());
        assertTrue(vo.getFeatured());
        assertEquals("http://example.com/cover.jpg", vo.getCoverUrl());
    }

    private PropertyDocument createPropertyDocument(Long id, String title) {
        PropertyDocument doc = new PropertyDocument();
        doc.setId(id);
        doc.setAppId("test-app");
        doc.setTitle(title);
        doc.setType("APARTMENT");
        doc.setPrice(3000L);
        doc.setRentalArea(80);
        doc.setRooms("2室1厅");
        doc.setOrientation("南");
        doc.setFloor("中层");
        doc.setAddress("北京市朝阳区测试街道123号");
        doc.setLat(39.9042);
        doc.setLon(116.4074);
        doc.setProvinceName("北京市");
        doc.setCityName("北京市");
        doc.setDistrictName("朝阳区");
        doc.setDecoration("精装修");
        doc.setDescription("这是一套测试房源");
        doc.setHot(true);
        doc.setFeatured(true);
        doc.setPublishStatus(1);
        doc.setStatus(1);
        doc.setCoverUrl("http://example.com/cover.jpg");
        doc.setCreatedAt("2024-01-01T00:00:00");
        doc.setUpdatedAt("2024-01-02T00:00:00");
        return doc;
    }

    private Map<String, Object> createPropertyData(Long id) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("appId", "test-app");
        data.put("title", "测试房源");
        data.put("type", "APARTMENT");
        data.put("price", 3000);
        data.put("rentalArea", 80);
        data.put("rooms", "2室1厅");
        data.put("orientation", "南");
        data.put("floor", "中层");
        data.put("totalFloors", 20);
        data.put("address", "北京市朝阳区测试街道123号");
        data.put("lat", 39.9042);
        data.put("lng", 116.4074);
        data.put("provinceCode", "110000");
        data.put("provinceName", "北京市");
        data.put("cityCode", "110000");
        data.put("cityName", "北京市");
        data.put("districtCode", "110105");
        data.put("districtName", "朝阳区");
        data.put("decoration", "精装修");
        data.put("heatingMethod", "集中供暖");
        data.put("waterSupply", "民水");
        data.put("powerSupply", "民电");
        data.put("gasSupply", "有");
        data.put("internet", "有");
        data.put("tvService", "有");
        data.put("description", "这是一套测试房源");
        data.put("hot", true);
        data.put("featured", true);
        data.put("publishStatus", 1);
        data.put("status", 1);
        data.put("coverUrl", "http://example.com/cover.jpg");
        data.put("createdAt", "2024-01-01T00:00:00");
        data.put("updatedAt", "2024-01-02T00:00:00");
        return data;
    }
}
