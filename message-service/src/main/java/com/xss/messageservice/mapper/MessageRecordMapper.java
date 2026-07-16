package com.xss.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.messageservice.entity.MessageRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageRecordMapper extends BaseMapper<MessageRecord> {
}
