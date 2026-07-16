package com.xss.authservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.authservice.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
