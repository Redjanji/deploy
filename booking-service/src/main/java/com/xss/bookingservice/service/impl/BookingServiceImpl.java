package com.xss.bookingservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xss.bookingservice.client.PropertyClient;
import com.xss.bookingservice.common.BusinessException;
import com.xss.bookingservice.config.RabbitMQConfig;
import com.xss.bookingservice.dto.BookingCreateRequest;
import com.xss.bookingservice.entity.Booking;
import com.xss.bookingservice.mapper.BookingMapper;
import com.xss.bookingservice.service.BookingService;
import com.xss.bookingservice.vo.BookingVO;
import com.xss.bookingservice.vo.PropertyBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {
    private final BookingMapper bookingMapper;
    private final PropertyClient propertyClient;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public Long create(Long userId, BookingCreateRequest request) {
        if (!propertyClient.exists(request.getPropertyId())) {
            throw new BusinessException("房源不存在");
        }
        if (request.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间必须是将来时间");
        }
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setPropertyId(request.getPropertyId());
        booking.setAppointmentTime(request.getAppointmentTime());
        booking.setRemark(request.getRemark());
        booking.setStatus(0);
        bookingMapper.insert(booking);
        publishEvent(booking, "BOOKING_CREATED");
        return booking.getId();
    }

    @Override
    public void confirm(Long bookingId, Long agentId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) throw new BusinessException("预约不存在");
        if (booking.getStatus() != 0) throw new BusinessException("预约状态不允许确认");
        booking.setStatus(1);
        booking.setAgentId(agentId);
        bookingMapper.updateById(booking);
        publishEvent(booking, "BOOKING_CONFIRMED");
    }

    @Override
    public void cancelByUser(Long bookingId, Long userId, String reason) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) throw new BusinessException("预约不存在");
        if (!booking.getUserId().equals(userId)) throw new BusinessException("无权取消");
        if (booking.getStatus() == 0 || booking.getStatus() == 1) {
            booking.setStatus(3);
            booking.setCancelReason(reason);
            bookingMapper.updateById(booking);
            publishEvent(booking, "BOOKING_CANCELED");
        } else {
            throw new BusinessException("当前状态不可取消");
        }
    }

    @Override
    public void cancelByAgent(Long bookingId, Long agentId, String reason) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) throw new BusinessException("预约不存在");
        if (booking.getAgentId() == null || !booking.getAgentId().equals(agentId))
            throw new BusinessException("无权操作");
        if (booking.getStatus() == 0 || booking.getStatus() == 1) {
            booking.setStatus(4);
            booking.setCancelReason(reason);
            bookingMapper.updateById(booking);
            publishEvent(booking, "BOOKING_REJECTED");
        } else {
            throw new BusinessException("当前状态不可操作");
        }
    }

    @Override
    public void complete(Long bookingId, Long agentId) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) throw new BusinessException("预约不存在");
        if (booking.getAgentId() != null && !booking.getAgentId().equals(agentId))
            throw new BusinessException("无权操作");
        booking.setStatus(2);
        bookingMapper.updateById(booking);
        publishEvent(booking, "BOOKING_COMPLETED");
    }

    @Override
    public BookingVO getById(Long bookingId, Long userIdOrAgentId, boolean isAgent) {
        Booking booking = bookingMapper.selectById(bookingId);
        if (booking == null) throw new BusinessException("预约不存在");
        if (isAgent) {
            if (booking.getAgentId() == null || !booking.getAgentId().equals(userIdOrAgentId))
                throw new BusinessException("无权查看");
        } else {
            if (!booking.getUserId().equals(userIdOrAgentId))
                throw new BusinessException("无权查看");
        }
        return convertToVO(booking);
    }

    @Override
    public List<BookingVO> listByUser(Long userId, int page, int size) {
        Page<Booking> p = new Page<>(page, size);
        Page<Booking> result = bookingMapper.selectPage(p,
                new LambdaQueryWrapper<Booking>()
                        .eq(Booking::getUserId, userId)
                        .orderByDesc(Booking::getCreatedAt));
        return result.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<BookingVO> listByAgent(Long agentId, int page, int size) {
        Page<Booking> p = new Page<>(page, size);
        Page<Booking> result = bookingMapper.selectPage(p,
                new LambdaQueryWrapper<Booking>()
                        .eq(Booking::getAgentId, agentId)
                        .orderByDesc(Booking::getCreatedAt));
        return result.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private BookingVO convertToVO(Booking booking) {
        BookingVO vo = new BookingVO();
        BeanUtils.copyProperties(booking, vo);
        vo.setStatusDesc(switch (booking.getStatus()) {
            case 0 -> "待确认";
            case 1 -> "已确认";
            case 2 -> "已完成";
            case 3 -> "已取消";
            case 4 -> "已拒绝";
            default -> "未知";
        });
        PropertyBriefVO brief = propertyClient.getBrief(booking.getPropertyId());
        if (brief != null) {
            vo.setPropertyTitle(brief.getTitle());
            vo.setPropertyCover(brief.getCoverUrl());
        }
        return vo;
    }

    private void publishEvent(Booking booking, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("bookingId", booking.getId());
        event.put("userId", booking.getUserId());
        event.put("propertyId", booking.getPropertyId());
        event.put("agentId", booking.getAgentId());
        event.put("appointmentTime", booking.getAppointmentTime());
        event.put("timestamp", System.currentTimeMillis());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);
    }
}
