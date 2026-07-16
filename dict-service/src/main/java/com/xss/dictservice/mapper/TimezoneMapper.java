package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.Timezone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TimezoneMapper extends BaseMapper<Timezone> {

    @Select("SELECT timezone_id, offset_utc, description " +
            "FROM sys_timezone " +
            "WHERE ((timezone_id LIKE CONCAT('%', #{kw}, '%') OR description LIKE CONCAT('%', #{kw}, '%')) OR #{kw} IS NULL) " +
            "ORDER BY offset_utc, timezone_id")
    List<Map<String, Object>> selectTimezones(@Param("kw") String keyword);

    @Select("SELECT timezone_id, offset_utc, description " +
            "FROM sys_timezone WHERE TRIM(timezone_id) = #{tz} LIMIT 1")
    Map<String, Object> selectTimezoneById(@Param("tz") String timezoneId);
}
