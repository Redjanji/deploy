package com.xss.dictservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.dictservice.mapper.SysPropertyDictItemMapper;
import com.xss.dictservice.mapper.SysPropertyDictTypeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DictService {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SysPropertyDictTypeMapper typeMapper;
    private final SysPropertyDictItemMapper itemMapper;

    private static final Map<String, DictTableConfig> DICT_TABLES = new HashMap<>();

    static {
        DICT_TABLES.put("gender", new DictTableConfig("sys_gender", "code", "name", "sort_order", true));
        DICT_TABLES.put("education", new DictTableConfig("sys_education", "code", "name", "sort_order", true));
        DICT_TABLES.put("degree", new DictTableConfig("sys_degree", "code", "name", "sort_order", true));
        DICT_TABLES.put("ethnicity", new DictTableConfig("sys_ethnicity", "code", "name", "sort_order", true));
        DICT_TABLES.put("marital_status", new DictTableConfig("sys_marital_status", "code", "name", "sort_order", true));
        DICT_TABLES.put("political_status", new DictTableConfig("sys_political_status", "code", "name", "sort_order", true));
        DICT_TABLES.put("id_document_type", new DictTableConfig("sys_id_document_type", "code", "name", "sort_order", true));
        DICT_TABLES.put("professional_title", new DictTableConfig("sys_professional_title", "code", "name", "sort_order", true));
        DICT_TABLES.put("enterprise_type", new DictTableConfig("sys_enterprise_type", "code", "name", "sort_order", true));
        DICT_TABLES.put("taxpayer_qualification", new DictTableConfig("sys_taxpayer_qualification", "code", "name", "sort_order", true));
        DICT_TABLES.put("settlement_method", new DictTableConfig("sys_settlement_method", "code", "name", "sort_order", true));
        DICT_TABLES.put("invoice_type", new DictTableConfig("sys_invoice_type", "code", "name", "sort_order", true));
        DICT_TABLES.put("unit", new DictTableConfig("sys_unit", "code", "name", "sort_order", true));
        DICT_TABLES.put("common_status", new DictTableConfig("sys_common_status", "code", "name", "sort_order", true));
        DICT_TABLES.put("payment_method", new DictTableConfig("sys_payment_method", "code", "name", "sort_order", true));
    }

    public DictService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                      SysPropertyDictTypeMapper typeMapper, SysPropertyDictItemMapper itemMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    public List<Map<String, Object>> getDictList(String dictType, Integer status, String keyword) {
        DictTableConfig config = getConfig(dictType);
        String st = status != null ? String.valueOf(status) : "all";
        String kw = keyword != null ? keyword : "all";
        String cacheKey = "dict:" + dictType + ":list:" + st + ":" + kw;

        return queryListWithCache(cacheKey, () -> {
            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(config.codeColumn).append(" AS code, ");
            sql.append(config.nameColumn).append(" AS name, ");
            sql.append(config.sortColumn).append(" AS sort_order");
            if (config.hasStatus) {
                sql.append(", status");
            }
            sql.append(" FROM ").append(config.tableName);
            sql.append(" WHERE 1=1");

            List<Object> params = new ArrayList<>();
            if (config.hasStatus && status != null) {
                sql.append(" AND status = ?");
                params.add(status);
            }
            if (keyword != null && !keyword.isEmpty()) {
                sql.append(" AND (").append(config.nameColumn).append(" LIKE ? OR ")
                        .append(config.codeColumn).append(" LIKE ?)");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }
            sql.append(" ORDER BY ").append(config.sortColumn).append(", ").append(config.codeColumn);

            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        });
    }

    public Map<String, Object> getDictItem(String dictType, String code) {
        DictTableConfig config = getConfig(dictType);
        String cacheKey = "dict:" + dictType + ":item:" + code;

        return queryMapWithCache(cacheKey, () -> {
            String sql = "SELECT " + config.codeColumn + " AS code, " +
                    config.nameColumn + " AS name, " +
                    config.sortColumn + " AS sort_order";
            if (config.hasStatus) {
                sql += ", status";
            }
            sql += " FROM " + config.tableName + " WHERE " + config.codeColumn + " = ? LIMIT 1";

            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, code);
            return list.isEmpty() ? null : list.get(0);
        });
    }

    public List<Map<String, Object>> getTreeDictList(String dictType, String parentCode, Integer level, String keyword) {
        DictTableConfig config = getTreeConfig(dictType);
        String pc = parentCode != null ? parentCode : "all";
        String lv = level != null ? String.valueOf(level) : "all";
        String kw = keyword != null ? keyword : "all";
        String cacheKey = "dict:" + dictType + ":tree:" + pc + ":" + lv + ":" + kw;

        return queryListWithCache(cacheKey, () -> {
            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(config.codeColumn).append(" AS code, ");
            sql.append(config.nameColumn).append(" AS name, ");
            sql.append("parent_code, level, ").append(config.sortColumn).append(" AS sort_order, status");
            sql.append(" FROM ").append(config.tableName);
            sql.append(" WHERE 1=1");

            List<Object> params = new ArrayList<>();
            if (parentCode != null && !parentCode.isEmpty()) {
                sql.append(" AND parent_code = ?");
                params.add(parentCode);
            }
            if (level != null) {
                sql.append(" AND level = ?");
                params.add(level);
            }
            if (keyword != null && !keyword.isEmpty()) {
                sql.append(" AND (").append(config.nameColumn).append(" LIKE ? OR ")
                        .append(config.codeColumn).append(" LIKE ?)");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }
            sql.append(" ORDER BY ").append(config.sortColumn).append(", ").append(config.codeColumn);

            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        });
    }

    public void clearDictCache(String dictType) {
        String pattern = "dict:" + dictType + ":*";
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return result;
        });
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public List<String> getAllDictTypes() {
        List<String> types = new ArrayList<>(DICT_TABLES.keySet());
        types.add("industry_category");
        types.add("occupation");
        types.add("property_type");
        types.add("decoration");
        types.add("heating_method");
        types.add("water_supply");
        types.add("power_supply");
        types.add("gas_supply");
        types.add("internet");
        types.add("tv_service");
        types.add("orientation");
        types.add("room_type");
        types.add("rental_area_unit");
        types.add("lease_term");
        types.add("publish_status");
        types.add("audit_status");
        types.add("property_label");
        Collections.sort(types);
        return types;
    }

    public List<Map<String, Object>> getPropertyDictItems(String typeCode, Integer status, String keyword) {
        String st = status != null ? String.valueOf(status) : "all";
        String kw = keyword != null ? keyword : "all";
        String cacheKey = "dict:property:" + typeCode + ":items:" + st + ":" + kw;

        return queryListWithCache(cacheKey, () -> {
            int enabledOnly = (status != null && status == 1) ? 1 : 0;
            return itemMapper.selectByTypeCode(typeCode, enabledOnly, keyword);
        });
    }

    public Map<String, Object> getPropertyDictItem(String typeCode, String itemKey) {
        String cacheKey = "dict:property:" + typeCode + ":item:" + itemKey;

        return queryMapWithCache(cacheKey, () -> {
            return itemMapper.selectByTypeCodeAndItemKey(typeCode, itemKey);
        });
    }

    public List<Map<String, Object>> getAllPropertyDictTypes() {
        String cacheKey = "dict:property:types";

        return queryListWithCache(cacheKey, () -> {
            return typeMapper.selectAllTypes();
        });
    }

    private DictTableConfig getConfig(String dictType) {
        DictTableConfig config = DICT_TABLES.get(dictType);
        if (config == null) {
            throw new IllegalArgumentException("不支持的字典类型: " + dictType);
        }
        return config;
    }

    private DictTableConfig getTreeConfig(String dictType) {
        if ("industry_category".equals(dictType)) {
            return new DictTableConfig("sys_industry_category", "code", "name", "sort_order", true);
        }
        if ("occupation".equals(dictType)) {
            return new DictTableConfig("sys_occupation", "code", "name", "sort_order", true);
        }
        throw new IllegalArgumentException("不支持的树形字典类型: " + dictType);
    }

    private List<Map<String, Object>> queryListWithCache(String cacheKey,
                                                          java.util.function.Supplier<List<Map<String, Object>>> supplier) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<List<Map<String, Object>>>() {});
                } catch (Exception e) {
                    log.warn("缓存反序列化失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
        }
        List<Map<String, Object>> data = supplier.get();
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(data), 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}, err={}", cacheKey, e.getMessage());
        }
        return data;
    }

    private Map<String, Object> queryMapWithCache(String cacheKey,
                                                    java.util.function.Supplier<Map<String, Object>> supplier) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("缓存反序列化失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级查 DB: key={}, err={}", cacheKey, e.getMessage());
        }
        Map<String, Object> data = supplier.get();
        try {
            if (data != null) {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(data), 1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}, err={}", cacheKey, e.getMessage());
        }
        return data;
    }

    private static class DictTableConfig {
        final String tableName;
        final String codeColumn;
        final String nameColumn;
        final String sortColumn;
        final boolean hasStatus;

        DictTableConfig(String tableName, String codeColumn, String nameColumn, String sortColumn, boolean hasStatus) {
            this.tableName = tableName;
            this.codeColumn = codeColumn;
            this.nameColumn = nameColumn;
            this.sortColumn = sortColumn;
            this.hasStatus = hasStatus;
        }
    }
}
