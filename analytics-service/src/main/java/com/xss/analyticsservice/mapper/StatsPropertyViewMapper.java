package com.xss.analyticsservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.analyticsservice.entity.StatsPropertyView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatsPropertyViewMapper extends BaseMapper<StatsPropertyView> {

    @Insert("INSERT INTO stats_property_views (app_id, property_id, view_count, unique_visitors, stats_date, stats_hour, created_at) " +
            "VALUES (#{appId}, #{propertyId}, #{viewCount}, #{uniqueVisitors}, #{statsDate}, #{statsHour}, NOW()) " +
            "ON DUPLICATE KEY UPDATE view_count = VALUES(view_count), unique_visitors = VALUES(unique_visitors)")
    void upsert(@Param("appId") String appId,
                @Param("propertyId") Long propertyId,
                @Param("viewCount") Long viewCount,
                @Param("uniqueVisitors") Long uniqueVisitors,
                @Param("statsDate") String statsDate,
                @Param("statsHour") Integer statsHour);
}
