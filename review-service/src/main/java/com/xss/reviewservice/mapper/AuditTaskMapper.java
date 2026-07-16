package com.xss.reviewservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.reviewservice.entity.AuditTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditTaskMapper extends BaseMapper<AuditTaskEntity> {
}
