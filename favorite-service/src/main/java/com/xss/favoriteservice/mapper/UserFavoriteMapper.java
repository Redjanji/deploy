package com.xss.favoriteservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.favoriteservice.entity.UserFavoriteEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavoriteEntity> {
}
