package com.xss.analyticsservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.analyticsservice.entity.StatsUserAction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatsUserActionMapper extends BaseMapper<StatsUserAction> {

    @Insert("INSERT INTO stats_user_actions (app_id, event_type, action_count, stats_date, stats_hour) " +
            "VALUES (#{appId}, #{eventType}, #{actionCount}, #{statsDate}, #{statsHour}) " +
            "ON DUPLICATE KEY UPDATE action_count = VALUES(action_count)")
    void upsert(@Param("appId") String appId,
                @Param("eventType") String eventType,
                @Param("actionCount") Long actionCount,
                @Param("statsDate") String statsDate,
                @Param("statsHour") Integer statsHour);
}
