package com.xss.bookingservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.bookingservice.entity.Booking;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookingMapper extends BaseMapper<Booking> {
}
