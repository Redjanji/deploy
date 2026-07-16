package com.xss.searchservice.controller;

import com.xss.searchservice.dto.PropertySearchRequest;
import com.xss.searchservice.service.PropertySearchService;
import com.xss.searchservice.vo.PropertyDocumentVO;
import com.xss.searchservice.vo.SearchResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private PropertySearchService searchService;

    @InjectMocks
    private SearchController searchController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    void search_搜索成功() throws Exception {
        SearchResultVO resultVO = new SearchResultVO();
        resultVO.setTotal(2L);
        resultVO.setSize(10);
        resultVO.setCurrent(1);
        resultVO.setPages(1);

        PropertyDocumentVO vo1 = new PropertyDocumentVO();
        vo1.setId(1L);
        vo1.setTitle("房源1");
        vo1.setPrice(3000L);

        PropertyDocumentVO vo2 = new PropertyDocumentVO();
        vo2.setId(2L);
        vo2.setTitle("房源2");
        vo2.setPrice(4000L);

        resultVO.setRecords(Arrays.asList(vo1, vo2));

        when(searchService.search(any(PropertySearchRequest.class), eq("test-app"))).thenReturn(resultVO);

        mockMvc.perform(get("/api/search/properties")
                        .header("X-App-Id", "test-app")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "精装")
                        .param("cityCode", "110000")
                        .param("type", "APARTMENT")
                        .param("rooms", "2室1厅")
                        .param("minPrice", "2000")
                        .param("maxPrice", "5000")
                        .param("hot", "true")
                        .param("featured", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.pages").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("房源1"))
                .andExpect(jsonPath("$.data.records[0].price").value(3000))
                .andExpect(jsonPath("$.data.records[1].id").value(2))
                .andExpect(jsonPath("$.data.records[1].title").value("房源2"))
                .andExpect(jsonPath("$.data.records[1].price").value(4000));

        verify(searchService).search(any(PropertySearchRequest.class), eq("test-app"));
    }

    @Test
    void search_空结果成功返回() throws Exception {
        SearchResultVO resultVO = new SearchResultVO();
        resultVO.setTotal(0L);
        resultVO.setSize(10);
        resultVO.setCurrent(1);
        resultVO.setPages(0);
        resultVO.setRecords(Collections.emptyList());

        when(searchService.search(any(PropertySearchRequest.class), eq("test-app"))).thenReturn(resultVO);

        mockMvc.perform(get("/api/search/properties")
                        .header("X-App-Id", "test-app")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(0));

        verify(searchService).search(any(PropertySearchRequest.class), eq("test-app"));
    }

    @Test
    void search_不带appId请求头() throws Exception {
        SearchResultVO resultVO = new SearchResultVO();
        resultVO.setTotal(0L);
        resultVO.setSize(20);
        resultVO.setCurrent(1);
        resultVO.setPages(0);
        resultVO.setRecords(Collections.emptyList());

        when(searchService.search(any(PropertySearchRequest.class), isNull())).thenReturn(resultVO);

        mockMvc.perform(get("/api/search/properties")
                        .param("page", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(searchService).search(any(PropertySearchRequest.class), isNull());
    }

    @Test
    void search_使用默认分页参数() throws Exception {
        SearchResultVO resultVO = new SearchResultVO();
        resultVO.setTotal(0L);
        resultVO.setSize(20);
        resultVO.setCurrent(1);
        resultVO.setPages(0);
        resultVO.setRecords(Collections.emptyList());

        when(searchService.search(any(PropertySearchRequest.class), eq("test-app"))).thenReturn(resultVO);

        mockMvc.perform(get("/api/search/properties")
                        .header("X-App-Id", "test-app")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(searchService).search(any(PropertySearchRequest.class), eq("test-app"));
    }

    @Test
    void reindex_全量重建索引成功() throws Exception {
        doNothing().when(searchService).reindexAll(eq("test-app"));

        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-App-Id", "test-app")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(searchService).reindexAll(eq("test-app"));
    }

    @Test
    void reindex_不带appId请求头() throws Exception {
        doNothing().when(searchService).reindexAll(isNull());

        mockMvc.perform(post("/api/search/admin/reindex")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(searchService).reindexAll(isNull());
    }

    @Test
    void search_使用关键字参数() throws Exception {
        SearchResultVO resultVO = new SearchResultVO();
        resultVO.setTotal(1L);
        resultVO.setSize(10);
        resultVO.setCurrent(1);
        resultVO.setPages(1);

        PropertyDocumentVO vo = new PropertyDocumentVO();
        vo.setId(1L);
        vo.setTitle("精装两居室");

        resultVO.setRecords(Collections.singletonList(vo));

        when(searchService.search(any(PropertySearchRequest.class), eq("test-app"))).thenReturn(resultVO);

        mockMvc.perform(get("/api/search/properties")
                        .header("X-App-Id", "test-app")
                        .param("keyword", "精装")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].title").value("精装两居室"));

        verify(searchService).search(any(PropertySearchRequest.class), eq("test-app"));
    }
}
