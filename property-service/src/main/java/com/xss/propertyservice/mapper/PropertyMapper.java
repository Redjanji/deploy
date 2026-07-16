package com.xss.propertyservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.propertyservice.entity.PropertyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PropertyMapper extends BaseMapper<PropertyEntity> {
}
