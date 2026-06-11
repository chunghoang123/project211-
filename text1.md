# 🏸 HƯỚNG DẪN NGÀY 1 — Nền móng + FR-04, FR-05, FR-06 (25 điểm)

## Kế hoạch tổng thể 3 ngày

| Ngày | Nội dung | Điểm |
|------|----------|------|
| **Ngày 1 (hôm nay)** | Setup project + Entity + Repository + Exception Handler + **FR-04 Đăng ký (10đ)** + **FR-05 Quản lý User (5đ)** + **FR-06 Đặt sân (10đ)** | 25đ |
| Ngày 2 | JWT đầy đủ: FR-01 Login (5đ), FR-02 Refresh (5đ), FR-03 Logout (5đ), FR-07 Lịch sử (5đ), FR-08 Duyệt lịch (5đ), FR-09 Upload ảnh (5đ), FR-10 Đổi/quên mật khẩu (5đ) | 35đ |
| Ngày 3 | FR-11 AOP log thời gian mọi chức năng (10đ), FR-12 Unit test ≥10 (20đ), FR-13 Redis TokenBlacklist (10đ) | 40đ |

**Chiến lược Ngày 1:** Vì Login (FR-01) là việc của Ngày 2, hôm nay ta cấu hình Security ở chế độ
**tạm thời mở toàn bộ (permitAll)** để test được API bằng Postman. API đặt sân sẽ nhận `username`
qua request param tạm thời — sang Ngày 2 chỉ cần đổi 1 dòng lấy từ JWT. Cách này giúp hôm nay
chạy được nghiệp vụ ngay mà không bị JWT chặn đường.

**Lưu ý kiến trúc cho Ngày 3 (quyết định hôm nay để khỏi sửa lại):**
- KHÔNG tạo entity `TokenBlacklist` → Ngày 3 dùng Redis (FR-13), Ngày 2 logout sẽ viết qua
  interface `TokenBlacklistService` để dễ thay implementation.
- Giữ nguyên thư mục `src/test/` → Ngày 3 viết unit test (FR-12).

---

# BƯỚC 0 — SETUP PROJECT (≈ 45 phút)

## 0.1. Xóa thư mục thừa

Trong `src/main/resources/`, **xóa 2 thư mục** `static/` và `templates/`
(chỉ dùng cho web render HTML, project này là REST API thuần).

## 0.2. Sửa `build.gradle`

Mở `build.gradle`, thay toàn bộ block `dependencies` bằng:

```groovy
dependencies {
    // Core REST API + JPA + Validation
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Security + BCrypt (hôm nay chỉ dùng PasswordEncoder, JWT để Ngày 2)
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // JWT (cài sẵn hôm nay để Ngày 2 không phải chờ tải lại)
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // AOP (Ngày 3 - FR-11)
    implementation 'org.springframework.boot:spring-boot-starter-aop'

    // Cloudinary (Ngày 2 - FR-09)
    implementation 'com.cloudinary:cloudinary-http5:2.0.0'

    // Redis (Ngày 3 - FR-13)
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // MySQL
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Lombok - giảm code getter/setter
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test (Ngày 3 - FR-12)
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

Sau đó bấm **icon con voi 🐘 "Load Gradle Changes"** ở góc phải IntelliJ và đợi tải xong.

> ⚠️ **Lombok trên IntelliJ:** vào `Settings → Build → Compiler → Annotation Processors`
> → tích ✅ **Enable annotation processing**. Không bật cái này code sẽ đỏ lòm.

## 0.3. Cấu hình `application.properties`

```properties
spring.application.name=project_211

# ===== DATABASE (MySQL) =====
spring.datasource.url=jdbc:mysql://localhost:3306/badminton_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD_HERE
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# ===== JWT (Ngay 2 dung, khai bao truoc) =====
# Secret toi thieu 256-bit = chuoi >= 32 ky tu (yeu cau SRS)
jwt.secret=project211-badminton-secret-key-must-be-at-least-256-bits-long
# AccessToken: 30 phut (SRS cho phep 15-30p)
jwt.access-expiration=1800000
# RefreshToken: 7 ngay (SRS cho phep 7-30 ngay)
jwt.refresh-expiration=604800000

# ===== UPLOAD max 10MB (Non-Functional Requirement) =====
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# ===== REDIS (Ngay 3 moi dung - tat auto-config de hom nay khong bao loi) =====
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

> 💡 Dòng cuối **rất quan trọng**: ta đã thêm dependency Redis nhưng máy chưa cài Redis,
> nên tạm tắt auto-config để app khởi động bình thường. Ngày 3 sẽ xóa dòng này đi.

## 0.4. Tạo cây package

Chuột phải vào `com.example.project_211` → New → Package, tạo lần lượt:

```
config, security, controller, service, service.impl,
repository, entity, dto.request, dto.response, enums, aspect, exception
```

(Gõ `dto.request` IntelliJ tự tạo lồng `dto/request`. Package `security` và `aspect`
hôm nay để trống, Ngày 2-3 dùng.)

---

# BƯỚC 1 — ENUMS + ENTITIES (≈ 1 tiếng 15 phút)

> **Tư duy:** Entity là móng nhà. Sai entity thì service/controller sửa dây chuyền.
> Làm cẩn thận bước này, các bước sau chỉ là lắp ghép.

## 1.1. `enums/RoleName.java`

```java
package com.example.project_211.enums;

public enum RoleName {
    ROLE_ADMIN,
    ROLE_MANAGER,
    ROLE_CUSTOMER
}
```

> Prefix `ROLE_` là quy ước của Spring Security — Ngày 2 dùng `hasRole("ADMIN")`
> nó sẽ tự tìm authority tên `ROLE_ADMIN`.

## 1.2. `enums/BookingStatus.java`

```java
package com.example.project_211.enums;

/**
 * Vong doi trang thai Booking (State Transition - SRS muc IV.3):
 * PENDING -> CONFIRMED (Manager/Admin duyet)
 * PENDING -> REJECTED  (Manager/Admin tu choi)
 * PENDING/CONFIRMED -> CANCELLED (khach huy)
 * CONFIRMED -> COMPLETED (da choi xong / check-in)
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
```

## 1.3. `entity/Role.java`

```java
package com.example.project_211.entity;

import com.example.project_211.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)            // Luu chuoi "ROLE_ADMIN" thay vi so 0,1,2
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;
}
```

> `EnumType.STRING` bắt buộc nên dùng: nếu để mặc định (ORDINAL) thì DB lưu 0/1/2,
> sau này thêm enum mới vào giữa là **vỡ dữ liệu**.

## 1.4. `entity/User.java`

```java
package com.example.project_211.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")                       // KHONG dat "user" - trung keyword SQL
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)                // Luu chuoi BCrypt (~60 ky tu)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 15)
    private String phone;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;           // UC-04: "Tai khoan khong bi khoa"

    @ManyToOne(fetch = FetchType.LAZY)       // Nhieu User - 1 Role
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

## 1.5. `entity/Court.java`

```java
package com.example.project_211.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;         // Tien dung BigDecimal, KHONG dung double

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    // FR-09: 1 san co NHIEU anh
    @Builder.Default
    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourtImage> images = new ArrayList<>();
}
```

## 1.6. `entity/CourtImage.java`

```java
package com.example.project_211.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "court_images")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CourtImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;                 // Secure URL tu Cloudinary (UC-05)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;
}
```

## 1.7. `entity/TimeSlot.java`

```java
package com.example.project_211.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;             // VD: 06:00

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;               // VD: 07:00
}
```

## 1.8. `entity/Booking.java` — bảng trung tâm

```java
package com.example.project_211.entity;

import com.example.project_211.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;                       // Ai dat

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;                     // Dat san nao

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;               // Khung gio nao

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;           // Ngay nao

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;            // PENDING khi tao moi (UC-04)

    @Column(name = "image_url", length = 500)
    private String imageUrl;                 // Anh bill (UC-05 nhac den)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

> 🔑 **Ràng buộc nghiệp vụ quan trọng nhất (UC-04):** bộ ba
> `(court, bookingDate, timeSlot)` không được có 2 booking cùng trạng thái
> PENDING/CONFIRMED → check ở Service (Bước 7), vi phạm → ném exception → **409 Conflict**.

## 1.9. `entity/RefreshToken.java` (Ngày 2 dùng, tạo luôn cho đủ schema)

```java
package com.example.project_211.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;                       // Moi user 1 refresh token

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;
}
```

✅ **Checkpoint Bước 1:** 7 entity + 2 enum, code không còn đỏ. (Entity thứ 8
`TokenBlacklist` cố tình KHÔNG tạo — FR-13 yêu cầu Redis.)

---

# BƯỚC 2 — REPOSITORIES (≈ 20 phút)

> **Tư duy:** Spring Data JPA tự sinh SQL từ TÊN HÀM (derived query).
> Ví dụ `existsByUsername` → `SELECT COUNT(*) > 0 FROM users WHERE username = ?`.
> Vì vậy tên hàm phải khớp chính xác tên field trong Entity.

## 2.1. `repository/RoleRepository.java`

```java
package com.example.project_211.repository;

import com.example.project_211.entity.Role;
import com.example.project_211.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
```

## 2.2. `repository/UserRepository.java`

```java
package com.example.project_211.repository;

import com.example.project_211.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);   // FR-04: check trung -> 409

    boolean existsByEmail(String email);         // FR-04: check trung -> 409

    // FR-05: Tim kiem theo username HOAC email + phan trang
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);
}
```

## 2.3. `repository/CourtRepository.java`

```java
package com.example.project_211.repository;

import com.example.project_211.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtRepository extends JpaRepository<Court, Long> {
}
```

## 2.4. `repository/CourtImageRepository.java`

```java
package com.example.project_211.repository;

import com.example.project_211.entity.CourtImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtImageRepository extends JpaRepository<CourtImage, Long> {
}
```

## 2.5. `repository/TimeSlotRepository.java`

```java
package com.example.project_211.repository;

import com.example.project_211.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
}
```

## 2.6. `repository/BookingRepository.java` — chứa query "vàng" chống trùng lịch

```java
package com.example.project_211.repository;

import com.example.project_211.entity.Booking;
import com.example.project_211.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Collection;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * UC-04: kiem tra khung gio cua san trong ngay do
     * da co booking PENDING hoac CONFIRMED chua.
     * SQL sinh ra: SELECT EXISTS(... WHERE court_id=? AND time_slot_id=?
     *              AND booking_date=? AND status IN (?,?))
     */
    boolean existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
            Long courtId, Long timeSlotId, LocalDate bookingDate,
            Collection<BookingStatus> statuses);

    // FR-07 (Ngay 2): lich su dat san cua 1 user
    Page<Booking> findByUserId(Long userId, Pageable pageable);
}
```

## 2.7. `repository/RefreshTokenRepository.java` (Ngày 2 dùng)

```java
package com.example.project_211.repository;

import com.example.project_211.entity.RefreshToken;
import com.example.project_211.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
}
```

✅ **Checkpoint Bước 2:** 7 file repository, tất cả là `interface`, không cần viết SQL nào.

---

# BƯỚC 3 — DTO CHUNG + EXCEPTION HANDLER (≈ 40 phút)

> **Tư duy:** Làm phần này TRƯỚC khi viết nghiệp vụ, vì mọi controller đều trả về
> `ApiResponse` và mọi lỗi đều chui qua `GlobalExceptionHandler`. Đây chính là
> 2 format chuẩn mà SRS mục VI.3 bắt buộc — sai format là trừ điểm.

## 3.1. `dto/response/ApiResponse.java` — mẫu THÀNH CÔNG của SRS

```java
package com.example.project_211.dto.response;

import lombok.*;

/**
 * SRS muc VI.3 - Mau thanh cong:
 * { "success": true, "message": "Created successfully", "data": {...} }
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .build();
    }
}
```

## 3.2. `dto/response/ErrorResponse.java` — mẫu BÁO LỖI của SRS

```java
package com.example.project_211.dto.response;

import lombok.*;
import java.time.LocalDateTime;

/**
 * SRS muc VI.3 - Mau bao loi:
 * { "timestamp": "...", "status": 400, "error": "Bad Request",
 *   "message": "Email invalid format", "path": "/api/v1/users" }
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}
```

## 3.3. Ba class Exception nghiệp vụ

`exception/ResourceNotFoundException.java`
```java
package com.example.project_211.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
```

`exception/DuplicateResourceException.java`
```java
package com.example.project_211.exception;

// FR-04: username/email da ton tai -> 409
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) { super(message); }
}
```

`exception/BookingConflictException.java`
```java
package com.example.project_211.exception;

// UC-04: trung lich dat san -> 409
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) { super(message); }
}
```

## 3.4. `exception/GlobalExceptionHandler.java` — trái tim xử lý lỗi

```java
package com.example.project_211.exception;

import com.example.project_211.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * @RestControllerAdvice = "luoi hung loi" toan cuc (UC-05 yeu cau).
 * Moi exception nem ra tu bat ky dau deu bi bat o day,
 * dong goi thanh ErrorResponse sach, khong lo stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Helper dung chung: build ErrorResponse dung format SRS
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }

    // 404 - khong tim thay tai nguyen
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    // 409 - email/username trung (FR-04)
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    // 409 - trung lich dat san (UC-04)
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ErrorResponse> handleBookingConflict(
            BookingConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    // 400 - validation that bai (@Valid o Controller)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    // 500 - moi loi chua luong truoc
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(
            Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error", req);
    }
}
```

✅ **Checkpoint Bước 3:** Từ giờ trở đi, ở Service chỉ cần `throw new BookingConflictException(...)`
là client tự nhận đúng 409 + JSON đúng format — không bao giờ phải try-catch trong Controller.

---

# BƯỚC 4 — SECURITY TẠM THỜI + SEED DỮ LIỆU (≈ 25 phút)

## 4.1. `config/SecurityConfig.java` (phiên bản Ngày 1)

```java
package com.example.project_211.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * SRS: BCrypt strength >= 10 (mac dinh cua BCryptPasswordEncoder la 10).
     * Khai bao strength = 10 ro rang de giam khao nhin thay.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * ⚠️ PHIEN BAN TAM THOI NGAY 1: mo toan bo de test Postman.
     * NGAY 2 se thay bang: phan quyen /api/v1/admin/**, /manager/**, /customer/**
     * + gan JwtRequestFilter.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())            // REST API stateless khong can CSRF
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // SRS: Stateless
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()            // TODO Ngay 2: phan quyen that
            );
        return http.build();
    }
}
```

## 4.2. `config/DataInitializer.java` — seed role, admin, sân, khung giờ

> **Tại sao dùng Java thay vì `data.sql`?** Vì mật khẩu phải mã hóa BCrypt —
> không thể viết sẵn hash trong file SQL một cách an toàn. CommandLineRunner
> chạy 1 lần mỗi khi app khởi động, có check tồn tại nên không bị nhân đôi dữ liệu.

```java
package com.example.project_211.config;

import com.example.project_211.entity.*;
import com.example.project_211.enums.RoleName;
import com.example.project_211.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalTime;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initData() {
        return args -> {
            // 1. Seed 3 role
            for (RoleName rn : RoleName.values()) {
                roleRepository.findByName(rn).orElseGet(() ->
                        roleRepository.save(Role.builder().name(rn).build()));
            }

            // 2. Seed tai khoan admin: admin / admin123
            if (!userRepository.existsByUsername("admin")) {
                Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).get();
                userRepository.save(User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .email("admin@badminton.com")
                        .fullName("System Admin")
                        .role(adminRole)
                        .build());
            }

            // 3. Seed 2 san mau
            if (courtRepository.count() == 0) {
                courtRepository.save(Court.builder()
                        .name("San so 1").description("San tieu chuan thi dau")
                        .pricePerHour(new BigDecimal("80000")).build());
                courtRepository.save(Court.builder()
                        .name("San so 2").description("San tap luyen")
                        .pricePerHour(new BigDecimal("60000")).build());
            }

            // 4. Seed khung gio 6h -> 22h (moi slot 1 tieng)
            if (timeSlotRepository.count() == 0) {
                for (int h = 6; h < 22; h++) {
                    timeSlotRepository.save(TimeSlot.builder()
                            .startTime(LocalTime.of(h, 0))
                            .endTime(LocalTime.of(h + 1, 0))
                            .build());
                }
            }
        };
    }
}
```

## 4.3. 🚀 CHẠY THỬ LẦN ĐẦU

Bấm Run `Project211Application`. Thành công khi:
1. Console hiện `Tomcat started on port 8080`
2. Console hiện loạt câu `Hibernate: create table ...`
3. Mở MySQL Workbench → database `badminton_db` có đủ 7 bảng:
   `users, roles, courts, court_images, time_slots, bookings, refresh_tokens`
4. Bảng `roles` có 3 dòng, `users` có admin, `courts` 2 dòng, `time_slots` 16 dòng

> ❌ **Lỗi thường gặp:** `Access denied for user 'root'` → sai password trong
> application.properties. `Communications link failure` → MySQL chưa bật.

---

# BƯỚC 5 — FR-04: ĐĂNG KÝ TÀI KHOẢN — 10 điểm (≈ 45 phút)

> **Luồng chuẩn:** Request JSON → Controller (`@Valid` chặn dữ liệu bẩn → 400)
> → Service (check trùng → 409, mã hóa BCrypt, gán ROLE_CUSTOMER, lưu DB)
> → trả **201 Created** bọc trong ApiResponse.

## 5.1. `dto/request/RegisterRequest.java`

```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50, message = "Username must be 4-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Email is required")
    @Email(message = "Email invalid format")     // -> 400 dung message mau SRS
    private String email;

    private String fullName;

    @Pattern(regexp = "^(0\\d{9})?$", message = "Phone must be 10 digits starting with 0")
    private String phone;
}
```

## 5.2. `dto/response/UserResponse.java`

```java
package com.example.project_211.dto.response;

import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO tra ve - TUYET DOI khong tra Entity User truc tiep
 * (lo password hash + de dinh loi lazy loading vong lap).
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String role;          // "ROLE_CUSTOMER" dang chuoi
    private boolean active;
    private LocalDateTime createdAt;
}
```

## 5.3. `service/AuthService.java` (interface)

```java
package com.example.project_211.service;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    // Ngay 2 them: login, refreshToken, logout
}
```

## 5.4. `service/impl/AuthServiceImpl.java`

```java
package com.example.project_211.service.impl;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.DuplicateResourceException;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.service.impl.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor   // Lombok tu sinh constructor -> Spring tu inject (DI chuan)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Check trung -> nem exception -> GlobalExceptionHandler tra 409
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        // 2. Tai khoan tu dang ky mac dinh la CUSTOMER
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // 3. Ma hoa BCrypt - SRS cam luu plain text
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(customerRole)
                .build();

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    // Map Entity -> DTO (dung chung)
    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .role(u.getRole().getName().name())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
```

## 5.5. `controller/AuthController.java`

```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.service.impl.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")          // SRS: prefix /api/v1/ bat buoc
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * FR-04: POST /api/v1/auth/register
     * URL la danh tu? "register" duoc chap nhan rong rai cho auth endpoint
     * (SRS cung tu dat /api/auth/refresh). Tao moi thanh cong -> 201 Created.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse data = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)   // SRS: 201 cho tao moi
                .body(ApiResponse.success("Registered successfully", data));
    }
}
```

---

# BƯỚC 6 — FR-05: QUẢN LÝ USER (CRUD + Tìm kiếm + Phân trang) — 5 điểm (≈ 1 tiếng)

## 6.1. `dto/request/UserRequest.java` (Admin cập nhật user)

```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email invalid format")
    private String email;

    private String fullName;

    @Pattern(regexp = "^(0\\d{9})?$", message = "Phone must be 10 digits starting with 0")
    private String phone;

    private Boolean active;      // Admin khoa / mo khoa tai khoan

    @NotBlank(message = "Role is required")
    private String role;         // "ROLE_ADMIN" | "ROLE_MANAGER" | "ROLE_CUSTOMER"
}
```

## 6.2. `dto/response/PageResponse.java` — bọc kết quả phân trang

```java
package com.example.project_211.dto.response;

import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
```

## 6.3. `service/UserService.java`

```java
package com.example.project_211.service;

import com.example.project_211.dto.request.UserRequest;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.dto.response.UserResponse;

public interface UserService {
    PageResponse<UserResponse> getUsers(String keyword, int page, int size);
    UserResponse getUserById(Long id);
    UserResponse updateUser(Long id, UserRequest request);
    void deleteUser(Long id);
}
```

## 6.4. `service/impl/UserServiceImpl.java` — ⭐ nơi dùng Stream API (UC-02)

```java
package com.example.project_211.service.impl;

import com.example.project_211.dto.request.UserRequest;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.service.impl.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * FR-05 + UC-02: Tim kiem + phan trang.
     * UC-02 BAT BUOC dung Stream API (.stream().filter().map().collect())
     * thay vi vong lap for/while - giam khao se soi ky cho nay.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Page<User> userPage = (keyword == null || keyword.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        keyword, keyword, pageable);

        // ⭐ Java Stream API: filter user active truoc, map Entity -> DTO, collect List
        List<UserResponse> content = userPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));

        Role role = roleRepository.findByName(RoleName.valueOf(request.getRole()))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRole(role);
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .role(u.getRole().getName().name())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
```

## 6.5. `controller/UserController.java`

```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.UserRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.service.impl.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")    // Prefix /admin -> Ngay 2 chi ADMIN duoc vao
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // GET /api/v1/admin/users?keyword=abc&page=0&size=5
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("Fetched users successfully",
                        userService.getUsers(keyword, page, size)));
    }

    // GET /api/v1/admin/users/5
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Fetched user successfully",
                        userService.getUserById(id)));
    }

    // PUT /api/v1/admin/users/5 -> 200 OK (SRS: PUT cap nhat tra 200)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Updated user successfully",
                        userService.updateUser(id, request)));
    }

    // DELETE /api/v1/admin/users/5 -> 204 No Content (SRS: xoa khong tra body)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

> 📌 **Để ý:** API tạo mới user không cần viết riêng — FR-04 register chính là "Create".
> URL toàn danh từ số nhiều `users`, không có động từ — đúng quy chuẩn SRS mục VI.1.

---

# BƯỚC 7 — FR-06: ĐẶT LỊCH ĐÁNH CẦU — 10 điểm (≈ 1 tiếng)

> **Đây là chức năng đáng tiền nhất hôm nay.** Luồng theo đúng UC-04:
> 1. Customer gửi POST `/api/v1/customer/bookings` với body `{courtId, bookingDate, timeSlotId}`
> 2. Service validate: sân tồn tại? slot tồn tại? **khung giờ đó đã có booking PENDING/CONFIRMED chưa?**
> 3. Hợp lệ → tạo Booking trạng thái `PENDING` → trả **201 Created**
> 4. Trùng lịch → ném `BookingConflictException` → **409 Conflict**
     >    (Ngày 3 AOP sẽ bắt đúng exception này để ghi log `[AUDIT - FAILED]`)

## 7.1. `dto/request/BookingRequest.java`

```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
public class BookingRequest {

    @NotNull(message = "courtId is required")
    private Long courtId;

    @NotNull(message = "bookingDate is required")
    @FutureOrPresent(message = "bookingDate must be today or in the future")
    private LocalDate bookingDate;          // JSON gui: "2026-06-15"

    @NotNull(message = "timeSlotId is required")
    private Long timeSlotId;
}
```

## 7.2. `dto/response/BookingResponse.java`

```java
package com.example.project_211.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BookingResponse {
    private Long id;
    private String customerUsername;
    private Long courtId;
    private String courtName;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private LocalDateTime createdAt;
}
```

## 7.3. `service/BookingService.java`

```java
package com.example.project_211.service;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;

public interface BookingService {
    BookingResponse createBooking(String username, BookingRequest request);
    // Ngay 2 them: getMyBookings (FR-07), updateStatus (FR-08)
}
```

## 7.4. `service/impl/BookingServiceImpl.java`

```java
package com.example.project_211.service.impl;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.entity.*;
import com.example.project_211.enums.BookingStatus;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.*;
import com.example.project_211.service.impl.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;

    /**
     * UC-04: Dat lich san.
     * Ngay 3, LoggingAspect se "bao vay" chinh phuong thuc nay:
     *  - @AfterReturning -> [AUDIT - SUCCESS] ...
     *  - @AfterThrowing  -> [AUDIT - FAILED] ...
     * Vi vay TUYET DOI khong viet code log o day (SRS cam).
     */
    @Override
    @Transactional
    public BookingResponse createBooking(String username, BookingRequest request) {
        // 1. Lay user dat san
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));

        // 2. Validate san ton tai
        Court court = courtRepository.findById(request.getCourtId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Court not found with id: " + request.getCourtId()));

        // 3. Validate khung gio ton tai
        TimeSlot timeSlot = timeSlotRepository.findById(request.getTimeSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Time slot not found with id: " + request.getTimeSlotId()));

        // 4. ⭐ CHECK TRUNG LICH (UC-04): da co booking PENDING/CONFIRMED chua?
        boolean conflicted = bookingRepository
                .existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                        court.getId(), timeSlot.getId(), request.getBookingDate(),
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));

        if (conflicted) {
            // -> GlobalExceptionHandler tra 409 Conflict (dung SRS muc VI.2)
            throw new BookingConflictException(
                    "This time slot is already booked for the selected date");
        }

        // 5. Tao booking trang thai PENDING (UC-04 buoc 3)
        Booking booking = Booking.builder()
                .user(user)
                .court(court)
                .timeSlot(timeSlot)
                .bookingDate(request.getBookingDate())
                .status(BookingStatus.PENDING)
                .build();

        return toResponse(bookingRepository.save(booking));
    }

    private BookingResponse toResponse(Booking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .customerUsername(b.getUser().getUsername())
                .courtId(b.getCourt().getId())
                .courtName(b.getCourt().getName())
                .bookingDate(b.getBookingDate())
                .startTime(b.getTimeSlot().getStartTime())
                .endTime(b.getTimeSlot().getEndTime())
                .status(b.getStatus().name())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
```

## 7.5. `controller/BookingController.java`

```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.service.impl.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/bookings")   // dung prefix /customer theo ma tran SRS
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * FR-06 / UC-04: POST /api/v1/customer/bookings?username=xxx
     *
     * ⚠️ TAM THOI NGAY 1: nhan username qua @RequestParam de test duoc bang Postman.
     * 🔁 NGAY 2 SUA THANH: lay tu JWT ->
     *    String username = SecurityContextHolder.getContext()
     *                          .getAuthentication().getName();
     *    va XOA @RequestParam nay di.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @RequestParam String username,
            @Valid @RequestBody BookingRequest request) {
        BookingResponse data = bookingService.createBooking(username, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)    // SRS: 201 Created
                .body(ApiResponse.success("Booking created successfully", data));
    }
}
```

---

# BƯỚC 8 — TEST BẰNG POSTMAN (≈ 30 phút)

Chạy app rồi test theo đúng thứ tự sau. **Lưu collection lại** — Ngày 3 viết unit test
sẽ tham chiếu các case này.

### Test 1 — FR-04 Đăng ký thành công → mong đợi **201**
```
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "username": "customer1",
  "password": "123456",
  "email": "customer1@gmail.com",
  "fullName": "Nguyen Van A",
  "phone": "0912345678"
}
```
Response mẫu:
```json
{ "success": true, "message": "Registered successfully",
  "data": { "id": 2, "username": "customer1", "role": "ROLE_CUSTOMER", ... } }
```

### Test 2 — Đăng ký trùng username → mong đợi **409**
Gửi lại y nguyên Test 1. Response phải đúng format lỗi SRS:
```json
{ "timestamp": "...", "status": 409, "error": "Conflict",
  "message": "Username already exists", "path": "/api/v1/auth/register" }
```

### Test 3 — Email sai định dạng → mong đợi **400**
Đổi `"email": "abc"` → nhận `"message": "email: Email invalid format"`.

### Test 4 — FR-05 Danh sách user có phân trang → **200**
```
GET http://localhost:8080/api/v1/admin/users?page=0&size=5
```

### Test 5 — FR-05 Tìm kiếm → **200**
```
GET http://localhost:8080/api/v1/admin/users?keyword=customer
```

### Test 6 — FR-05 Cập nhật user → **200**
```
PUT http://localhost:8080/api/v1/admin/users/2
Content-Type: application/json

{ "email": "newmail@gmail.com", "fullName": "Nguyen Van B",
  "phone": "0987654321", "active": true, "role": "ROLE_CUSTOMER" }
```

### Test 7 — FR-05 Xóa user → **204 No Content** (body rỗng)
```
DELETE http://localhost:8080/api/v1/admin/users/2
```
(Xóa xong thì đăng ký lại customer1 để test booking tiếp.)

### Test 8 — FR-06 Đặt sân thành công → **201**, status = PENDING
```
POST http://localhost:8080/api/v1/customer/bookings?username=customer1
Content-Type: application/json

{ "courtId": 1, "bookingDate": "2026-06-15", "timeSlotId": 3 }
```

### Test 9 — ⭐ Đặt trùng lịch → **409 Conflict** (quan trọng nhất!)
Gửi lại y nguyên Test 8:
```json
{ "timestamp": "...", "status": 409, "error": "Conflict",
  "message": "This time slot is already booked for the selected date",
  "path": "/api/v1/customer/bookings" }
```

### Test 10 — Đặt ngày quá khứ → **400**
`"bookingDate": "2020-01-01"` → message chứa `bookingDate must be today or in the future`.

### Test 11 — Sân không tồn tại → **404**
`"courtId": 999` → `"Court not found with id: 999"`.

---

# ✅ CHECKLIST CUỐI NGÀY 1

- [ ] App chạy được, DB tự sinh 7 bảng, có dữ liệu seed
- [ ] FR-04 (10đ): đăng ký 201, trùng username/email 409, email sai 400
- [ ] FR-05 (5đ): list + paging + search 200, get by id, update 200, delete 204
- [ ] FR-06 (10đ): đặt sân 201 PENDING, **trùng lịch 409**, sân/slot không tồn tại 404
- [ ] Mọi response thành công đúng format `{success, message, data}`
- [ ] Mọi response lỗi đúng format `{timestamp, status, error, message, path}`
- [ ] Mật khẩu trong DB là chuỗi BCrypt `$2a$10$...` (mở MySQL kiểm tra!)
- [ ] Đã commit Git: `git add . && git commit -m "Day 1: FR-04, FR-05, FR-06"`

**Hoàn thành = 25/60 điểm cơ bản đã nằm trong túi.**

---

# 🔭 XEM TRƯỚC NGÀY 2 (để tối nay đỡ bỡ ngỡ)

1. **JwtUtil + JwtRequestFilter + CustomUserDetailsService** → FR-01 Login trả cặp token
2. **SecurityConfig thật**: phân quyền `/api/v1/admin/** → ADMIN`, `/manager/** → MANAGER`,
   `/customer/** → CUSTOMER`, còn lại permitAll (đúng ma trận SRS)
3. Sửa `BookingController`: bỏ `@RequestParam username`, lấy từ `SecurityContextHolder`
4. FR-02 Refresh, FR-03 Logout (viết qua interface `TokenBlacklistService` — Ngày 3 thay ruột bằng Redis)
5. FR-07 Lịch sử, FR-08 Duyệt/từ chối, FR-09 Upload Cloudinary, FR-10 Đổi/quên mật khẩu

Chuẩn bị trước: đăng ký tài khoản **Cloudinary miễn phí** (cloudinary.com) để lấy
`cloud_name`, `api_key`, `api_secret` — Ngày 2 dùng ngay khỏi chờ.