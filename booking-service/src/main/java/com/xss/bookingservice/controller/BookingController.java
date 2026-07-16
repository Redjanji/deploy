package com.xss.bookingservice.controller;

import com.xss.bookingservice.common.Result;
import com.xss.bookingservice.dto.BookingCreateRequest;
import com.xss.bookingservice.service.BookingService;
import com.xss.bookingservice.vo.BookingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @PostMapping
    public Result<Long> create(@RequestHeader("X-User-Id") Long userId, @RequestBody BookingCreateRequest request) {
        Long bookingId = bookingService.create(userId, request);
        return Result.success(bookingId);
    }

    @PutMapping("/{bookingId}/confirm")
    public Result<Void> confirm(@PathVariable Long bookingId, @RequestHeader("X-User-Id") Long agentId) {
        bookingService.confirm(bookingId, agentId);
        return Result.success();
    }

    @PutMapping("/{bookingId}/complete")
    public Result<Void> complete(@PathVariable Long bookingId, @RequestHeader("X-User-Id") Long agentId) {
        bookingService.complete(bookingId, agentId);
        return Result.success();
    }

    @PutMapping("/{bookingId}/cancel/user")
    public Result<Void> cancelByUser(@PathVariable Long bookingId, @RequestHeader("X-User-Id") Long userId,
                                     @RequestParam(required = false) String reason) {
        bookingService.cancelByUser(bookingId, userId, reason);
        return Result.success();
    }

    @PutMapping("/{bookingId}/cancel/agent")
    public Result<Void> cancelByAgent(@PathVariable Long bookingId, @RequestHeader("X-User-Id") Long agentId,
                                      @RequestParam(required = false) String reason) {
        bookingService.cancelByAgent(bookingId, agentId, reason);
        return Result.success();
    }

    @GetMapping("/{bookingId}")
    public Result<BookingVO> getById(@PathVariable Long bookingId, @RequestHeader("X-User-Id") Long userId,
                                     @RequestParam(defaultValue = "false") boolean isAgent) {
        BookingVO vo = bookingService.getById(bookingId, userId, isAgent);
        return Result.success(vo);
    }

    @GetMapping("/user")
    public Result<List<BookingVO>> listByUser(@RequestHeader("X-User-Id") Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        List<BookingVO> list = bookingService.listByUser(userId, page, size);
        return Result.success(list);
    }

    @GetMapping("/agent")
    public Result<List<BookingVO>> listByAgent(@RequestHeader("X-User-Id") Long agentId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        List<BookingVO> list = bookingService.listByAgent(agentId, page, size);
        return Result.success(list);
    }
}
