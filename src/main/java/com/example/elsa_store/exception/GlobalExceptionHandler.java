package com.example.elsa_store.exception;

import com.example.elsa_store.dto.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Chuyển mọi lỗi thành khung {@code {success:false, code, message}} với HTTP status đúng nghĩa. Lỗi do người gọi (dữ
 * liệu sai, thiếu tham số, sai phương thức…) là 4xx kèm thông điệp có ích; lỗi máy chủ là 500 với thông điệp chung —
 * chi tiết chỉ nằm trong log, không lộ ra ngoài. App đọc mã {@link #VALIDATION_CODE} để dịch "trường: lỗi" sang tiếng
 * Việt.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Mã của lỗi kiểm tra dữ liệu đầu vào — app dịch thông điệp dạng {@code "field: message"}. */
    static final int VALIDATION_CODE = 402;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getErrorCode().getHttpStatusCode())
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode().getCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return fail(ErrorCode.UN_AUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidate(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElseGet(() -> ex.getBindingResult().getGlobalErrors().stream()
                        .findFirst()
                        .map(err -> err.getObjectName() + ": " + err.getDefaultMessage())
                        .orElse("Validation error"));
        return validation(msg);
    }

    /** {@code @Valid} trên tham số đơn (path / query) của controller. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        String msg = ex.getParameterValidationResults().stream()
                .findFirst()
                .map(result -> result.getMethodParameter().getParameterName() + ": "
                        + result.getResolvableErrors().stream()
                                .findFirst()
                                .map(error -> error.getDefaultMessage())
                                .orElse("invalid"))
                .orElse("Validation error");
        return validation(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> lastNode(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .orElse("Validation error");
        return validation(msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return validation(ex.getParameterName() + ": must not be null");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return validation(ex.getRequestPartName() + ": must not be null");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation(ex.getName() + ": invalid value");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("Malformed request body", ErrorCode.BAD_REQUEST.getCode()));
    }

    /** Lỗi nghiệp vụ còn ném bằng ngoại lệ chuẩn của Java — vẫn là lỗi của người gọi. */
    @ExceptionHandler({IllegalArgumentException.class, BadRequestException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(
                        ex.getMessage() == null ? ErrorCode.BAD_REQUEST.getMessage() : ex.getMessage(),
                        ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail("File quá lớn. Vui lòng upload file nhỏ hơn.", 413));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(
                        "Method " + ex.getMethod() + " is not supported here", ErrorCode.BAD_REQUEST.getCode()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail("Unsupported content type", ErrorCode.BAD_REQUEST.getCode()));
    }

    /** Gửi sai định dạng tới API nhận tệp (không phải multipart hợp lệ) — trước đây rơi vào 500. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipart(MultipartException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("Expected a multipart/form-data request", ErrorCode.BAD_REQUEST.getCode()));
    }

    /** Đường dẫn không có (kể cả ảnh đã bị xoá trong /uploads) — trước đây rơi vào 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.fail(
                        ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason(),
                        ex.getStatusCode().value()));
    }

    /** Vi phạm ràng buộc DB — thường là xoá dữ liệu đang được nơi khác dùng, hoặc trùng giá trị duy nhất. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return fail(ErrorCode.DATA_IN_USE);
    }

    /** Hai người sửa cùng một dữ liệu một lúc (khoá DB, deadlock, phiên bản cũ) — thử lại là được. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleConcurrency(ConcurrencyFailureException ex) {
        log.warn("Concurrent update rejected: {}", ex.getMessage());
        return fail(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(
                        ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage(), ErrorCode.UNCATEGORIZED_EXCEPTION.getCode()));
    }

    private static ResponseEntity<ApiResponse<Object>> fail(ErrorCode code) {
        return ResponseEntity.status(code.getHttpStatusCode()).body(ApiResponse.fail(code.getMessage(), code.getCode()));
    }

    private static ResponseEntity<ApiResponse<Object>> validation(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, VALIDATION_CODE));
    }

    /** {@code "create.req.quantity"} → {@code "quantity"}. */
    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
