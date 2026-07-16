package com.xss.imageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.imageservice.model.entity.ImageEntity;
import com.xss.imageservice.model.entity.ImageGroupItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImageGroupItemMapper extends BaseMapper<ImageGroupItemEntity> {
    @Select("<script>" +
            "SELECT i.* FROM images i " +
            "JOIN image_group_items gi ON i.id = gi.image_id " +
            "WHERE gi.group_id = #{groupId} AND i.status = 'READY' " +
            "<if test='ownerId != null'>" +
            "  AND (i.owner_id IS NULL OR i.owner_id = #{ownerId}) " +
            "</if>" +
            "<if test='ownerId == null'>" +
            "  AND i.owner_id IS NULL " +
            "</if>" +
            "ORDER BY gi.sort_order" +
            "</script>")
    List<ImageEntity> selectImagesByGroupId(@Param("groupId") Long groupId, @Param("ownerId") Long ownerId);
}
