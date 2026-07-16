package com.xss.dictservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.SysPropertyDictItemMapper;
import com.xss.dictservice.mapper.SysPropertyDictTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DictService 单元测试")
class DictServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SysPropertyDictTypeMapper typeMapper;

    @Mock
    private SysPropertyDictItemMapper itemMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DictService dictService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        dictService = new DictService(jdbcTemplate, redisTemplate, typeMapper, itemMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Map<String, Object> createDictItem(String code, String name, int sortOrder, int status) {
        Map<String, Object> item = new HashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("sort_order", sortOrder);
        item.put("status", status);
        return item;
    }

    // ========== getDictList 测试 ==========

    @Test
    @DisplayName("getDictList: 缓存命中直接返回")
    void getDictList_cacheHit_returnsFromCache() throws Exception {
        String dictType = "gender";
        List<Map<String, Object>> expected = Arrays.asList(
                createDictItem("1", "男", 1, 1),
                createDictItem("2", "女", 2, 1)
        );
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("dict:gender:list:all:all")).thenReturn(cachedJson);

        List<Map<String, Object>> result = dictService.getDictList(dictType, null, null);

        assertEquals(2, result.size());
        assertEquals("男", result.get(0).get("name"));
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("getDictList: 缓存未命中查DB并写入缓存")
    void getDictList_cacheMiss_queriesDbAndCaches() throws Exception {
        String dictType = "gender";
        List<Map<String, Object>> dbData = Arrays.asList(
                createDictItem("1", "男", 1, 1)
        );

        when(valueOperations.get("dict:gender:list:all:all")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(dbData);

        List<Map<String, Object>> result = dictService.getDictList(dictType, null, null);

        assertEquals(1, result.size());
        assertEquals("男", result.get(0).get("name"));
        verify(valueOperations, times(1)).set(eq("dict:gender:list:all:all"), anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("getDictList: Redis异常降级查DB")
    void getDictList_redisException_fallsBackToDb() {
        String dictType = "gender";
        List<Map<String, Object>> dbData = Arrays.asList(
                createDictItem("1", "男", 1, 1)
        );

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(dbData);

        List<Map<String, Object>> result = dictService.getDictList(dictType, null, null);

        assertEquals(1, result.size());
        assertEquals("男", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getDictList: 缓存反序列化失败降级查DB")
    void getDictList_cacheDeserializeError_fallsBackToDb() {
        String dictType = "gender";
        List<Map<String, Object>> dbData = Arrays.asList(
                createDictItem("1", "男", 1, 1)
        );

        when(valueOperations.get("dict:gender:list:all:all")).thenReturn("invalid-json");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(dbData);

        List<Map<String, Object>> result = dictService.getDictList(dictType, null, null);

        assertEquals(1, result.size());
        assertEquals("男", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getDictList: 带status和keyword过滤")
    void getDictList_withStatusAndKeyword_filtersCorrectly() {
        String dictType = "gender";
        List<Map<String, Object>> filteredData = Arrays.asList(
                createDictItem("1", "男", 1, 1)
        );

        when(valueOperations.get("dict:gender:list:1:男")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(filteredData);

        List<Map<String, Object>> result = dictService.getDictList(dictType, 1, "男");

        assertEquals(1, result.size());
        assertEquals("男", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getDictList: 不支持的字典类型抛出IllegalArgumentException")
    void getDictList_unsupportedType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                dictService.getDictList("invalid_type", null, null)
        );
    }

    // ========== getDictItem 测试 ==========

    @Test
    @DisplayName("getDictItem: 正常查询单条")
    void getDictItem_existingCode_returnsItem() throws Exception {
        String dictType = "gender";
        String code = "1";
        Map<String, Object> expected = createDictItem("1", "男", 1, 1);
        String cachedJson = objectMapper.writeValueAsString(expected);

        when(valueOperations.get("dict:gender:item:1")).thenReturn(cachedJson);

        Map<String, Object> result = dictService.getDictItem(dictType, code);

        assertNotNull(result);
        assertEquals("男", result.get("name"));
    }

    @Test
    @DisplayName("getDictItem: 不存在返回null")
    void getDictItem_nonExistingCode_returnsNull() {
        String dictType = "gender";
        String code = "999";

        when(valueOperations.get("dict:gender:item:999")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), eq(code))).thenReturn(Collections.emptyList());

        Map<String, Object> result = dictService.getDictItem(dictType, code);

        assertNull(result);
    }

    @Test
    @DisplayName("getDictItem: 不支持的字典类型抛出IllegalArgumentException")
    void getDictItem_unsupportedType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                dictService.getDictItem("invalid_type", "1")
        );
    }

    // ========== getTreeDictList 测试 ==========

    @Test
    @DisplayName("getTreeDictList: 树形字典查询")
    void getTreeDictList_industryCategory_returnsTree() {
        String dictType = "industry_category";
        List<Map<String, Object>> treeData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("code", "01");
        item.put("name", "农业");
        item.put("parent_code", "0");
        item.put("level", 1);
        item.put("sort_order", 1);
        item.put("status", 1);
        treeData.add(item);

        when(valueOperations.get("dict:industry_category:tree:all:all:all")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(treeData);

        List<Map<String, Object>> result = dictService.getTreeDictList(dictType, null, null, null);

        assertEquals(1, result.size());
        assertEquals("农业", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getTreeDictList: 带parentCode和level过滤")
    void getTreeDictList_withParentCodeAndLevel_filtersCorrectly() {
        String dictType = "industry_category";
        String parentCode = "01";
        Integer level = 2;

        List<Map<String, Object>> filteredData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("code", "0101");
        item.put("name", "种植业");
        item.put("parent_code", "01");
        item.put("level", 2);
        item.put("sort_order", 1);
        item.put("status", 1);
        filteredData.add(item);

        when(valueOperations.get("dict:industry_category:tree:01:2:all")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(filteredData);

        List<Map<String, Object>> result = dictService.getTreeDictList(dictType, parentCode, level, null);

        assertEquals(1, result.size());
        assertEquals("种植业", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getTreeDictList: 不支持的树形字典类型抛出IllegalArgumentException")
    void getTreeDictList_unsupportedType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                dictService.getTreeDictList("gender", null, null, null)
        );
    }

    // ========== clearDictCache 测试 ==========

    @Test
    @DisplayName("clearDictCache: 清除指定类型缓存")
    @SuppressWarnings("unchecked")
    void clearDictCache_withType_deletesMatchingKeys() {
        String dictType = "gender";
        Set<String> keys = new HashSet<>(Arrays.asList(
                "dict:gender:list:all:all",
                "dict:gender:item:1"
        ));

        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Set<String>> callback = invocation.getArgument(0);
            return keys;
        });

        dictService.clearDictCache(dictType);

        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("clearDictCache: 无匹配键时不删除")
    @SuppressWarnings("unchecked")
    void clearDictCache_noKeys_doesNotDelete() {
        String dictType = "gender";

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptySet());

        dictService.clearDictCache(dictType);

        verify(redisTemplate, never()).delete(anySet());
    }

    // ========== getAllDictTypes 测试 ==========

    @Test
    @DisplayName("getAllDictTypes: 返回所有类型列表")
    void getAllDictTypes_returnsAllTypesSorted() {
        List<String> types = dictService.getAllDictTypes();

        assertNotNull(types);
        assertTrue(types.size() > 15);
        assertTrue(types.contains("gender"));
        assertTrue(types.contains("education"));
        assertTrue(types.contains("industry_category"));
        assertTrue(types.contains("property_type"));
        assertEquals(types.stream().sorted().toList(), types);
    }

    // ========== getPropertyDictItems 测试 ==========

    @Test
    @DisplayName("getPropertyDictItems: 房产字典项查询")
    void getPropertyDictItems_validType_returnsItems() throws Exception {
        String typeCode = "property_type";
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("code", "apartment");
        item.put("name", "公寓");
        item.put("sort_order", 1);
        item.put("status", 1);
        items.add(item);

        String cachedJson = objectMapper.writeValueAsString(items);
        when(valueOperations.get("dict:property:property_type:items:all:all")).thenReturn(cachedJson);

        List<Map<String, Object>> result = dictService.getPropertyDictItems(typeCode, null, null);

        assertEquals(1, result.size());
        assertEquals("公寓", result.get(0).get("name"));
        verify(itemMapper, never()).selectByTypeCode(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("getPropertyDictItems: 缓存未命中查itemMapper")
    void getPropertyDictItems_cacheMiss_queriesItemMapper() {
        String typeCode = "property_type";
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("code", "house");
        item.put("name", "住宅");
        item.put("sort_order", 2);
        item.put("status", 1);
        items.add(item);

        when(valueOperations.get("dict:property:property_type:items:1:all")).thenReturn(null);
        when(itemMapper.selectByTypeCode(eq("property_type"), eq(1), isNull())).thenReturn(items);

        List<Map<String, Object>> result = dictService.getPropertyDictItems(typeCode, 1, null);

        assertEquals(1, result.size());
        assertEquals("住宅", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getPropertyDictItems: status不为1时enabledOnly为0")
    void getPropertyDictItems_statusNotOne_enabledOnlyIsZero() {
        String typeCode = "property_type";
        List<Map<String, Object>> items = new ArrayList<>();

        when(valueOperations.get("dict:property:property_type:items:0:all")).thenReturn(null);
        when(itemMapper.selectByTypeCode(eq("property_type"), eq(0), isNull())).thenReturn(items);

        dictService.getPropertyDictItems(typeCode, 0, null);

        verify(itemMapper, times(1)).selectByTypeCode(eq("property_type"), eq(0), isNull());
    }

    // ========== getPropertyDictItem 测试 ==========

    @Test
    @DisplayName("getPropertyDictItem: 单条房产字典项")
    void getPropertyDictItem_validKey_returnsItem() throws Exception {
        String typeCode = "property_type";
        String itemKey = "apartment";
        Map<String, Object> expected = new HashMap<>();
        expected.put("code", "apartment");
        expected.put("name", "公寓");
        expected.put("sort_order", 1);
        expected.put("status", 1);

        String cachedJson = objectMapper.writeValueAsString(expected);
        when(valueOperations.get("dict:property:property_type:item:apartment")).thenReturn(cachedJson);

        Map<String, Object> result = dictService.getPropertyDictItem(typeCode, itemKey);

        assertNotNull(result);
        assertEquals("公寓", result.get("name"));
    }

    @Test
    @DisplayName("getPropertyDictItem: 缓存未命中查itemMapper")
    void getPropertyDictItem_cacheMiss_queriesItemMapper() {
        String typeCode = "property_type";
        String itemKey = "house";
        Map<String, Object> expected = new HashMap<>();
        expected.put("code", "house");
        expected.put("name", "住宅");

        when(valueOperations.get("dict:property:property_type:item:house")).thenReturn(null);
        when(itemMapper.selectByTypeCodeAndItemKey("property_type", "house")).thenReturn(expected);

        Map<String, Object> result = dictService.getPropertyDictItem(typeCode, itemKey);

        assertNotNull(result);
        assertEquals("住宅", result.get("name"));
    }

    @Test
    @DisplayName("getPropertyDictItem: 不存在返回null")
    void getPropertyDictItem_nonExistingKey_returnsNull() {
        String typeCode = "property_type";
        String itemKey = "nonexistent";

        when(valueOperations.get("dict:property:property_type:item:nonexistent")).thenReturn(null);
        when(itemMapper.selectByTypeCodeAndItemKey("property_type", "nonexistent")).thenReturn(null);

        Map<String, Object> result = dictService.getPropertyDictItem(typeCode, itemKey);

        assertNull(result);
    }

    // ========== getAllPropertyDictTypes 测试 ==========

    @Test
    @DisplayName("getAllPropertyDictTypes: 所有房产字典类型")
    void getAllPropertyDictTypes_returnsAllTypes() throws Exception {
        List<Map<String, Object>> types = new ArrayList<>();
        Map<String, Object> type1 = new HashMap<>();
        type1.put("type_code", "property_type");
        type1.put("type_name", "房产类型");
        types.add(type1);

        String cachedJson = objectMapper.writeValueAsString(types);
        when(valueOperations.get("dict:property:types")).thenReturn(cachedJson);

        List<Map<String, Object>> result = dictService.getAllPropertyDictTypes();

        assertEquals(1, result.size());
        assertEquals("房产类型", result.get(0).get("type_name"));
        verify(typeMapper, never()).selectAllTypes();
    }

    @Test
    @DisplayName("getAllPropertyDictTypes: 缓存未命中查typeMapper")
    void getAllPropertyDictTypes_cacheMiss_queriesTypeMapper() {
        List<Map<String, Object>> types = new ArrayList<>();
        Map<String, Object> type1 = new HashMap<>();
        type1.put("type_code", "decoration");
        type1.put("type_name", "装修情况");
        types.add(type1);

        when(valueOperations.get("dict:property:types")).thenReturn(null);
        when(typeMapper.selectAllTypes()).thenReturn(types);

        List<Map<String, Object>> result = dictService.getAllPropertyDictTypes();

        assertEquals(1, result.size());
        assertEquals("装修情况", result.get(0).get("type_name"));
    }

    // ========== 缓存写入异常测试 ==========

    @Test
    @DisplayName("缓存写入失败不影响返回结果")
    void getDictList_cacheWriteFailure_stillReturnsData() {
        String dictType = "gender";
        List<Map<String, Object>> dbData = Arrays.asList(
                createDictItem("1", "男", 1, 1)
        );

        when(valueOperations.get("dict:gender:list:all:all")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(dbData);
        doThrow(new RuntimeException("Redis写入失败")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        List<Map<String, Object>> result = dictService.getDictList(dictType, null, null);

        assertEquals(1, result.size());
        assertEquals("男", result.get(0).get("name"));
    }

    @Test
    @DisplayName("getTreeDictList: occupation 类型也支持")
    void getTreeDictList_occupation_returnsTree() {
        String dictType = "occupation";
        List<Map<String, Object>> treeData = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("code", "01");
        item.put("name", "企业负责人");
        item.put("parent_code", "0");
        item.put("level", 1);
        item.put("sort_order", 1);
        item.put("status", 1);
        treeData.add(item);

        when(valueOperations.get("dict:occupation:tree:all:all:all")).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(treeData);

        List<Map<String, Object>> result = dictService.getTreeDictList(dictType, null, null, null);

        assertEquals(1, result.size());
        assertEquals("企业负责人", result.get(0).get("name"));
    }
}
