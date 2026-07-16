package com.xss.reviewservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.reviewservice.entity.AuditRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditRecordMapper extends BaseMapper<AuditRecordEntity> {
}
