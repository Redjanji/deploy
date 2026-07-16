package com.xss.bookingservice.service;

import com.xss.bookingservice.dto.BookingCreateRequest;
import com.xss.bookingservice.vo.BookingVO;

import java.util.List;

public interface BookingService {
    Long create(Long userId, BookingCreateRequest request);
    void confirm(Long bookingId, Long agentId);
    void cancelByUser(Long bookingId, Long userId, String reason);
    void cancelByAgent(Long bookingId, Long agentId, String reason);
    void complete(Long bookingId, Long agentId);
    BookingVO getById(Long bookingId, Long userIdOrAgentId, boolean isAgent);
    List<BookingVO> listByUser(Long userId, int page, int size);
    List<BookingVO> listByAgent(Long agentId, int page, int size);
}
