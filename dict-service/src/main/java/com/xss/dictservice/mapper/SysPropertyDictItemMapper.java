package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.SysPropertyDictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysPropertyDictItemMapper extends BaseMapper<SysPropertyDictItem> {

    @Select("SELECT item_key AS code, item_value AS name, sort_order, is_enabled AS status " +
            "FROM sys_property_dict_item " +
            "WHERE type_code = #{tc} " +
            "AND (is_enabled = 1 OR #{enabledOnly} = 0) " +
            "AND ((item_key LIKE CONCAT('%', #{kw}, '%') OR item_value LIKE CONCAT('%', #{kw}, '%')) OR #{kw} IS NULL) " +
            "ORDER BY sort_order, item_key")
    List<Map<String, Object>> selectByTypeCode(@Param("tc") String typeCode,
                                               @Param("enabledOnly") Integer enabledOnly,
                                               @Param("kw") String keyword);

    @Select("SELECT item_key AS code, item_value AS name, sort_order, is_enabled AS status " +
            "FROM sys_property_dict_item " +
            "WHERE type_code = #{tc} AND item_key = #{ik} LIMIT 1")
    Map<String, Object> selectByTypeCodeAndItemKey(@Param("tc") String typeCode,
                                                   @Param("ik") String itemKey);
}
