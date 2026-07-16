package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.SysPropertyDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysPropertyDictTypeMapper extends BaseMapper<SysPropertyDictType> {

    @Select("SELECT type_code, type_name, remark FROM sys_property_dict_type ORDER BY type_code")
    List<Map<String, Object>> selectAllTypes();

    @Select("SELECT type_code, type_name, remark FROM sys_property_dict_type WHERE type_code = #{tc} LIMIT 1")
    Map<String, Object> selectByTypeCode(@Param("tc") String typeCode);
}
