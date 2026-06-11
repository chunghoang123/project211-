package com.example.project_211.exception;

import com.example.project_211.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

// Bat tat ca loi nem ra tu he thong va tra ve dang JSON thong nhat
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Ham dung chung de tao ErrorResponse
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(getVietnameseError(status))
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    // Doi ten loai loi sang tieng Viet
    private String getVietnameseError(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Yêu cầu không hợp lệ";
            case UNAUTHORIZED -> "Chưa xác thực";
            case FORBIDDEN -> "Không có quyền truy cập";
            case NOT_FOUND -> "Không tìm thấy";
            case CONFLICT -> "Xung đột dữ liệu";
            case SERVICE_UNAVAILABLE -> "Dịch vụ tạm thời không khả dụng";
            case INTERNAL_SERVER_ERROR -> "Lỗi máy chủ nội bộ";
            default -> status.getReasonPhrase();
        };
    }

    // Loi 404 khong tim thay tai nguyen
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    // Loi 409 trung ten dang nhap hoac email
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    // Loi 409 trung lich dat san
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ErrorResponse> handleBookingConflict(
            BookingConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    // Loi 400 du lieu dau vao khong hop le, tra ve chi tiet tung field
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        // Gom tat ca loi cua cac field theo dang ten_field va thong bao
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() == null
                                ? "Giá trị không hợp lệ"
                                : fieldError.getDefaultMessage(),
                        // Mot field co nhieu loi thi giu loi dau tien
                        (existing, replacement) -> existing
                ));

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(getVietnameseError(HttpStatus.BAD_REQUEST))
                .message("Dữ liệu không hợp lệ")
                .path(req.getRequestURI())
                .errors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Loi 401 sai ten dang nhap hoac mat khau
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthFail(
            org.springframework.security.core.AuthenticationException ex,
            HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED,
                "Tên đăng nhập hoặc mật khẩu không chính xác", req);
    }

    // Loi 401 refresh token sai hoac het han
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    // Loi 400 tham so khong hop le
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    // Loi 503 dich vu luu tru anh gap su co
    @ExceptionHandler(CloudStorageException.class)
    public ResponseEntity<ErrorResponse> handleCloudError(
            CloudStorageException ex, HttpServletRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);
    }

    // Loi 400 file tai len vuot qua dung luong cho phep
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex,
            HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Kích thước file vượt quá giới hạn cho phép", req);
    }

    // Loi 500 cac loi chua duoc luong truoc
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(
            Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi trong hệ thống", req);
    }
}
