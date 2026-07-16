package com.xss.analyticsservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.analyticsservice.entity.StatsImageUpload;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatsImageUploadMapper extends BaseMapper<StatsImageUpload> {

    @Insert("INSERT INTO stats_image_uploads (app_id, upload_count, total_size, stats_date, stats_hour) " +
            "VALUES (#{appId}, #{uploadCount}, #{totalSize}, #{statsDate}, #{statsHour}) " +
            "ON DUPLICATE KEY UPDATE upload_count = VALUES(upload_count), total_size = VALUES(total_size)")
    void upsert(@Param("appId") String appId,
                @Param("uploadCount") Long uploadCount,
                @Param("totalSize") Long totalSize,
                @Param("statsDate") String statsDate,
                @Param("statsHour") Integer statsHour);
}
