package com.xss.imageservice.config;

import com.xss.imageservice.common.Result;
import com.xss.imageservice.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Result<Void>> handleSecurity(SecurityException e) {
        log.warn("Security exception: {}", e.getMessage());
        return ResponseEntity.status(400).body(Result.error(400, e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode()).body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneral(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.status(500).body(Result.error(500, "服务器内部错误: " + e.getMessage()));
    }
}
