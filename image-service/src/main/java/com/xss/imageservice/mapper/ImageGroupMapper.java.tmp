package com.xss.imageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.imageservice.model.entity.ImageGroupEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImageGroupMapper extends BaseMapper<ImageGroupEntity> {
    @Select("SELECT * FROM image_groups WHERE app_id = #{appId} ORDER BY sort_order")
    List<ImageGroupEntity> selectByAppId(@Param("appId") String appId);
}
