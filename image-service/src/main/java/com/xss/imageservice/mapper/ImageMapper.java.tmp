package com.xss.imageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.imageservice.model.entity.ImageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImageMapper extends BaseMapper<ImageEntity> {
    @Select("SELECT * FROM images WHERE app_id = #{appId} AND status = 'READY' ORDER BY created_at DESC")
    List<ImageEntity> selectByAppId(@Param("appId") String appId);
}
