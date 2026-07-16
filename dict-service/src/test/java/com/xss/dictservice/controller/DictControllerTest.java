package com.xss.dictservice.controller;

import com.xss.dictservice.service.DictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DictController.class)
@DisplayName("DictController 单元测试")
class DictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DictService dictService;

    private Map<String, Object> createDictItem(String code, String name) {
        Map<String, Object> item = new HashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("sort_order", 1);
        item.put("status", 1);
        return item;
    }

    // ========== /api/dict/types 测试 ==========

    @Test
    @DisplayName("GET /api/dict/types - 获取所有字典类型")
    void getDictTypes_shouldReturnList() throws Exception {
        List<String> types = Arrays.asList("gender", "education", "common_status");
        when(dictService.getAllDictTypes()).thenReturn(types);

        mockMvc.perform(get("/api/dict/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("gender"));

        verify(dictService, times(1)).getAllDictTypes();
    }

    // ========== /api/dict/{dictType}/list 测试 ==========

    @Test
    @DisplayName("GET /api/dict/{dictType}/list - 获取字典列表")
    void getDictList_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createDictItem("1", "男"),
                createDictItem("2", "女")
        );
        when(dictService.getDictList("gender", null, null)).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/gender/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("1"))
                .andExpect(jsonPath("$.data[0].name").value("男"));

        verify(dictService, times(1)).getDictList("gender", null, null);
    }

    @Test
    @DisplayName("GET /api/dict/{dictType}/list - 带status和keyword参数")
    void getDictList_withStatusAndKeyword_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createDictItem("1", "男")
        );
        when(dictService.getDictList("gender", 1, "男")).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/gender/list")
                        .param("status", "1")
                        .param("keyword", "男"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(dictService, times(1)).getDictList("gender", 1, "男");
    }

    // ========== /api/dict/{dictType}/item/{code} 测试 ==========

    @Test
    @DisplayName("GET /api/dict/{dictType}/item/{code} - 获取单条字典项")
    void getDictItem_shouldReturnItem() throws Exception {
        Map<String, Object> mockData = createDictItem("1", "男");
        when(dictService.getDictItem("gender", "1")).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/gender/item/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("1"))
                .andExpect(jsonPath("$.data.name").value("男"));

        verify(dictService, times(1)).getDictItem("gender", "1");
    }

    // ========== /api/dict/items 测试 ==========

    @Test
    @DisplayName("GET /api/dict/items - 获取房产字典项列表")
    void getPropertyDictItems_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createDictItem("apartment", "公寓")
        );
        when(dictService.getPropertyDictItems("property_type", null, null)).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/items")
                        .param("type", "property_type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("apartment"));

        verify(dictService, times(1)).getPropertyDictItems("property_type", null, null);
    }

    @Test
    @DisplayName("GET /api/dict/items - 带status和keyword参数")
    void getPropertyDictItems_withParams_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        when(dictService.getPropertyDictItems("property_type", 1, "公")).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/items")
                        .param("type", "property_type")
                        .param("status", "1")
                        .param("keyword", "公"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(dictService, times(1)).getPropertyDictItems("property_type", 1, "公");
    }

    // ========== /api/dict/items/{type}/{itemKey} 测试 ==========

    @Test
    @DisplayName("GET /api/dict/items/{type}/{itemKey} - 获取单条房产字典项")
    void getPropertyDictItem_shouldReturnItem() throws Exception {
        Map<String, Object> mockData = createDictItem("apartment", "公寓");
        when(dictService.getPropertyDictItem("property_type", "apartment")).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/items/property_type/apartment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("apartment"))
                .andExpect(jsonPath("$.data.name").value("公寓"));

        verify(dictService, times(1)).getPropertyDictItem("property_type", "apartment");
    }

    // ========== /api/dict/property-types 测试 ==========

    @Test
    @DisplayName("GET /api/dict/property-types - 获取所有房产字典类型")
    void getAllPropertyDictTypes_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> type1 = new HashMap<>();
        type1.put("type_code", "property_type");
        type1.put("type_name", "房产类型");
        mockData.add(type1);

        when(dictService.getAllPropertyDictTypes()).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/property-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].type_code").value("property_type"));

        verify(dictService, times(1)).getAllPropertyDictTypes();
    }

    // ========== /api/dict/{dictType}/tree 测试 ==========

    @Test
    @DisplayName("GET /api/dict/{dictType}/tree - 获取树形字典")
    void getTreeDict_shouldReturnTree() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("code", "01");
        item.put("name", "农业");
        item.put("parent_code", "0");
        item.put("level", 1);
        mockData.add(item);

        when(dictService.getTreeDictList("industry_category", null, null, null)).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/industry_category/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("01"));

        verify(dictService, times(1)).getTreeDictList("industry_category", null, null, null);
    }

    @Test
    @DisplayName("GET /api/dict/{dictType}/tree - 带parent_code和level参数")
    void getTreeDict_withParams_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        when(dictService.getTreeDictList("industry_category", "01", 2, null)).thenReturn(mockData);

        mockMvc.perform(get("/api/dict/industry_category/tree")
                        .param("parent_code", "01")
                        .param("level", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(dictService, times(1)).getTreeDictList("industry_category", "01", 2, null);
    }

    // ========== /api/dict/admin/refresh/{dictType} 测试 ==========

    @Test
    @DisplayName("POST /api/dict/admin/refresh/{dictType} - 清除指定字典缓存")
    void refreshDict_shouldReturnOk() throws Exception {
        doNothing().when(dictService).clearDictCache("gender");

        mockMvc.perform(post("/api/dict/admin/refresh/gender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("gender 字典缓存已清除"));

        verify(dictService, times(1)).clearDictCache("gender");
    }

    // ========== /api/dict/admin/refresh-all 测试 ==========

    @Test
    @DisplayName("POST /api/dict/admin/refresh-all - 清除所有字典缓存")
    void refreshAllDicts_shouldReturnOk() throws Exception {
        List<String> types = Arrays.asList("gender", "education");
        when(dictService.getAllDictTypes()).thenReturn(types);
        doNothing().when(dictService).clearDictCache(anyString());

        mockMvc.perform(post("/api/dict/admin/refresh-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("所有字典缓存已清除"));

        verify(dictService, times(1)).getAllDictTypes();
        verify(dictService, times(2)).clearDictCache(anyString());
    }
}
