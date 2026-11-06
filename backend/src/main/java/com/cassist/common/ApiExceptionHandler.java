package com.cassist.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e, HttpServletRequest request) {
        return ResponseEntity.status(e.status()).body(error(e.code(), e.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), "字段值不符合要求"));
        return ResponseEntity.badRequest().body(error("VALIDATION_FAILED", "请检查请求字段", request, fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", "请求格式不正确", request, Map.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> oversized(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return ResponseEntity.status(413).body(error("UPLOAD_TOO_LARGE", "上传文件超过大小限制", request, Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return ResponseEntity.status(405).body(error("METHOD_NOT_ALLOWED", "不支持此请求方式", request, Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException e, HttpServletRequest request) {
        return ResponseEntity.status(409).body(error("DATA_CONFLICT", "数据状态冲突，请刷新后重试", request, Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        // Exception messages may contain SQL, code or private problem material.
        LOG.error("Unhandled exception type={}", e.getClass().getName());
        return ResponseEntity.internalServerError().body(error("INTERNAL_ERROR", "服务暂时不可用", request, Map.of()));
    }

    private ApiError error(String code, String message, HttpServletRequest request, Map<String, String> fields) {
        return new ApiError(code, message, (String) request.getAttribute(TraceIdFilter.ATTRIBUTE), fields);
    }
}
