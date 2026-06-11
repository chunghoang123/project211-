# 🏸 HƯỚNG DẪN NGÀY 3 — Nâng cao: AOP + Redis + Unit Test (40 điểm)

## Mục tiêu hôm nay

| FR | Chức năng | Điểm | Thời gian |
|----|-----------|------|-----------|
| FR-11 | AOP ghi log thời gian thực hiện TẤT CẢ chức năng | 10đ | ~1h |
| FR-13 | Redis TokenBlacklist (thay in-memory) | 10đ | ~1h30' |
| FR-12 | ≥10 unit test (5 service + 5 controller) | **20đ** | ~3h |

**Thứ tự khuyên làm: FR-11 → FR-13 → FR-12.** Hai cái đầu nhanh và chắc điểm,
unit test chiếm nhiều điểm nhất nên để cả buổi chiều làm cho kỹ.

---

# PHẦN 1 — FR-11: AOP GHI LOG THỜI GIAN THỰC HIỆN (10 điểm)

> **Tư duy AOP trong 3 câu:** Bạn muốn đo thời gian chạy của MỌI method nghiệp vụ,
> nhưng nếu viết `long start = ...` vào từng method thì vi phạm Separation of Concerns
> (SRS cấm). AOP cho phép viết logic đo giờ Ở MỘT NƠI DUY NHẤT (`@Aspect`), rồi khai báo
> "điểm cắt" (Pointcut) để Spring tự "bọc" nó quanh các method khớp mẫu. Method nghiệp vụ
> không hề biết mình đang bị đo giờ.

**Ba annotation dùng hôm nay:**
- `@Around` — bọc QUANH method: chạy code trước, gọi `proceed()` để method thật chạy, rồi chạy code sau → hoàn hảo để **đo thời gian** (FR-11)
- `@AfterReturning` — chạy SAU KHI method return thành công → audit đặt sân thành công (UC-04 của SRS)
- `@AfterThrowing` — chạy khi method NÉM EXCEPTION → audit đặt sân thất bại (UC-04)

## 1.1. Cấu hình ghi log ra file — thêm vào `application.properties`

```properties
# ===== LOGGING (FR-11 + UC-04: Audit Trail dang file) =====
logging.file.name=logs/application.log
```

App chạy sẽ tự tạo thư mục `logs/` chứa file log — đây chính là "nhật ký hệ thống
dạng file" mà UC-04 yêu cầu.

## 1.2. `aspect/LoggingAspect.java` — file ăn trọn 10 điểm

```java
package com.example.project_211.aspect;

import com.example.project_211.dto.response.BookingResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect          // Danh dau day la mot "khia canh" (UC-04 yeu cau @Aspect rieng)
@Component       // De Spring quan ly va kich hoat
@Slf4j           // Lombok tu tao: private static final Logger log = ...
public class LoggingAspect {

    /**
     * ============ FR-11 (10 diem) ============
     * Pointcut: "execution(* com.example.project_211.service.impl..*(..))"
     * Doc la: moi method (*), thuoc moi class trong package service.impl,
     * voi moi tham so (..). => TAT CA chuc nang nghiep vu deu bi do gio.
     *
     * @Around bao quanh method:
     *   [code truoc] -> joinPoint.proceed() = chay method that -> [code sau]
     */
    @Around("execution(* com.example.project_211.service.impl..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();          // chay method that
            long duration = System.currentTimeMillis() - start;
            log.info("[EXECUTION TIME] {}.{}() executed in {} ms",
                    className, methodName, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("[EXECUTION TIME] {}.{}() FAILED after {} ms - Reason: {}",
                    className, methodName, duration, ex.getMessage());
            throw ex;     // PHAI nem lai de GlobalExceptionHandler con xu ly!
        }
    }

    /**
     * ============ UC-04 cua SRS: Audit dat san THANH CONG ============
     * Chi cat dung method createBooking cua BookingServiceImpl.
     * "returning" tom lay gia tri return cua method (BookingResponse).
     */
    @AfterReturning(
        pointcut = "execution(* com.example.project_211.service.impl.BookingServiceImpl.createBooking(..))",
        returning = "result")
    public void logBookingSuccess(JoinPoint joinPoint, Object result) {
        BookingResponse r = (BookingResponse) result;
        log.info("[AUDIT - SUCCESS] Khach hang {} dat thanh cong {} vao ngay {}, khung gio {} - {}",
                r.getCustomerUsername(), r.getCourtName(), r.getBookingDate(),
                r.getStartTime(), r.getEndTime());
    }

    /**
     * ============ UC-04 cua SRS: Audit dat san THAT BAI ============
     * "throwing" tom lay exception vua bi nem (vi du trung lich -> 409).
     */
    @AfterThrowing(
        pointcut = "execution(* com.example.project_211.service.impl.BookingServiceImpl.createBooking(..))",
        throwing = "ex")
    public void logBookingFailed(JoinPoint joinPoint, Throwable ex) {
        // args[0] = username, args[1] = BookingRequest (theo chu ky method)
        Object[] args = joinPoint.getArgs();
        log.error("[AUDIT - FAILED] Khach hang {} co gang dat san nhung that bai - Ly do: {}",
                args[0], ex.getMessage());
    }
}
```

## 1.3. Kiểm tra

1. Chạy app, login, đặt sân, gọi vài API → mở `logs/application.log` thấy:
```
[EXECUTION TIME] AuthServiceImpl.login() executed in 245 ms
[EXECUTION TIME] BookingServiceImpl.createBooking() executed in 38 ms
[AUDIT - SUCCESS] Khach hang customer1 dat thanh cong San so 1 vao ngay 2026-06-15...
```
2. Đặt trùng lịch → thấy cả 2 dòng:
```
[EXECUTION TIME] BookingServiceImpl.createBooking() FAILED after 12 ms - Reason: This time slot...
[AUDIT - FAILED] Khach hang customer1 co gang dat san nhung that bai - Ly do: This time slot...
```

> 🎤 **Câu hỏi bảo vệ kinh điển:** *"Tại sao không viết log trong Service?"* →
> Trả lời: "Vì SRS yêu cầu Separation of Concerns — logic nghiệp vụ và logic ghi log
> phải tách rời. Em dùng @Aspect với Pointcut bọc quanh tầng service.impl, method
> nghiệp vụ không chứa một dòng log nào, muốn tắt/đổi cách log chỉ sửa 1 file."

---

# PHẦN 2 — FR-13: REDIS TOKEN BLACKLIST (10 điểm)

> **Vì sao Redis giải quyết "tắc nghẽn cổ chai"?** (đây chính là câu giảng viên sẽ hỏi)
>
> 1. Blacklist bị kiểm tra ở **MỌI request** (trong JwtRequestFilter). Nếu lưu DB,
     >    mỗi request = 1 query MySQL → DB thành nút cổ chai khi hệ thống scale.
> 2. Redis lưu **in-memory**, đọc/ghi O(1), chịu được hàng trăm nghìn ops/giây.
> 3. Redis có **TTL tự động**: set key kèm thời gian sống = thời gian còn lại của token,
     >    hết hạn Redis TỰ XÓA — không cần viết cron job dọn rác như khi lưu DB.
>
> Và đây là lúc thiết kế interface `TokenBlacklistService` từ Ngày 2 phát huy:
> **chỉ thay class impl, không sửa bất kỳ chỗ nào khác.**

## 2.1. Cài Redis trên Windows

**Cách 1 — Docker (khuyên dùng):** cài Docker Desktop, rồi chạy:
```bash
docker run --name redis -d -p 6379:6379 redis
```
Kiểm tra: `docker exec -it redis redis-cli ping` → trả về `PONG` là sống.

**Cách 2 — Memurai** (Redis bản Windows): tải tại memurai.com, cài như app thường,
tự chạy ở port 6379.

## 2.2. Sửa `application.properties`

**XÓA** dòng tắt Redis của Ngày 1:
```properties
# XOA DONG NAY:
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

**THÊM:**
```properties
# ===== REDIS (FR-13) =====
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## 2.3. Thay impl: XÓA `InMemoryTokenBlacklistService.java`, tạo file mới

`service/impl/RedisTokenBlacklistService.java`:

```java
package com.example.project_211.service.impl;

import com.example.project_211.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * FR-13: TokenBlacklist tren Redis - giai quyet tac nghen co chai.
 *
 * Cau truc luu: key = "blacklist:<token>", value = "revoked",
 * TTL = thoi gian song con lai cua token.
 * -> Het TTL, Redis TU XOA key, blacklist khong bao gio phinh to.
 */
@Service
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;   // Spring Boot tu cap bean nay

    private static final String PREFIX = "blacklist:";

    @Override
    public void blacklist(String token, long ttlMillis) {
        redisTemplate.opsForValue().set(
                PREFIX + token,
                "revoked",
                Duration.ofMillis(ttlMillis));    // TTL tu dong het han
    }

    @Override
    public boolean isBlacklisted(String token) {
        // hasKey: O(1) - cuc nhanh, khong dung den MySQL
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
```

**Hết.** Đúng nghĩa "đổi ruột" — `AuthServiceImpl.logout()` và `JwtRequestFilter`
không sửa một ký tự nào vì chúng chỉ biết đến interface.

## 2.4. Kiểm tra FR-13

1. Chạy app (Redis phải đang chạy, không thì app báo lỗi kết nối khi logout)
2. Login → Logout → gọi API bằng token cũ → **403 "Token has been revoked"** ✅
3. Soi vào Redis xem bằng chứng:
```bash
docker exec -it redis redis-cli
> KEYS blacklist:*          # thay key chua token vua logout
> TTL blacklist:<token>     # thay so giay con lai dem nguoc (~1800s)
```
4. (Demo đẹp cho giảng viên) Đợi TTL về 0 hoặc `DEL` key → token... vẫn bị chặn 401
   vì bản thân JWT cũng đã hết hạn — giải thích được tầng tầng lớp lớp bảo vệ.

> ⚠️ **Lưu ý khi nộp bài/demo:** nếu máy giảng viên không có Redis, app sẽ lỗi khi
> logout. Ghi rõ trong README: "Yêu cầu Redis chạy ở localhost:6379 —
> `docker run -d -p 6379:6379 redis`".

---

# PHẦN 3 — FR-12: UNIT TEST ≥ 10 (20 điểm — nhiều điểm nhất đồ án!)

> **Chiến lược 2 loại test:**
>
> | | Service Test (5 bài) | Controller Test (5 bài) |
> |---|---|---|
> | Công cụ | Mockito thuần (không khởi động Spring) | `@WebMvcTest` + MockMvc |
> | Mock cái gì | Repository, PasswordEncoder... | Service |
> | Kiểm tra cái gì | Logic nghiệp vụ (check trùng, BCrypt, exception) | HTTP status, JSON response, validation |
> | Tốc độ | Siêu nhanh (~ms) | Nhanh (chỉ load tầng web) |
>
> **Lưu ý version:** Spring Boot **3.4+** dùng `@MockitoBean`
> (package `org.springframework.test.context.bean.override.mockito`).
> Nếu project bạn là Boot 3.2/3.3 thì thay bằng `@MockBean` — công dụng y hệt.
>
> Nhờ Ngày 2 ta để `JwtRequestFilter` KHÔNG phải `@Component`, controller test
> sẽ không bị kéo theo cả bộ máy security — chỉ cần `addFilters = false`.

Tất cả test đặt trong `src/test/java/com/example/project_211/`.

## 3.1. SERVICE TEST 1+2 — `service/AuthServiceImplTest.java`

```java
package com.example.project_211.service;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.DuplicateResourceException;
import com.example.project_211.repository.RefreshTokenRepository;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.security.JwtUtil;
import com.example.project_211.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)        // bat Mockito, KHONG khoi dong Spring
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks                            // tu tiem cac @Mock vao constructor
    private AuthServiceImpl authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setUsername("customer1");
        request.setPassword("123456");
        request.setEmail("customer1@gmail.com");
        request.setFullName("Nguyen Van A");
    }

    @Test
    @DisplayName("TEST 1 - Dang ky thanh cong: ma hoa BCrypt, gan ROLE_CUSTOMER")
    void register_shouldSucceed_whenDataIsValid() {
        // GIVEN: gia lap moi truong
        Role customerRole = Role.builder().id(3L).name(RoleName.ROLE_CUSTOMER).build();
        when(userRepository.existsByUsername("customer1")).thenReturn(false);
        when(userRepository.existsByEmail("customer1@gmail.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER))
                .thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        // WHEN: goi method can test
        UserResponse result = authService.register(request);

        // THEN: kiem tra ket qua
        assertThat(result.getUsername()).isEqualTo("customer1");
        assertThat(result.getRole()).isEqualTo("ROLE_CUSTOMER");
        verify(passwordEncoder).encode("123456");      // chac chan co ma hoa BCrypt
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("TEST 2 - Dang ky trung username: nem DuplicateResourceException (409)")
    void register_shouldThrow_whenUsernameExists() {
        when(userRepository.existsByUsername("customer1")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username already exists");

        // Quan trong: KHONG duoc luu gi xuong DB
        verify(userRepository, never()).save(any());
    }
}
```

> ⚠️ Nếu `AuthServiceImpl` của bạn có thêm/bớt field so với hướng dẫn,
> hãy mock đúng theo danh sách `private final ...` trong class của bạn —
> `@InjectMocks` tiêm theo constructor nên phải đủ bộ.

## 3.2. SERVICE TEST 3+4 — `service/BookingServiceImplTest.java`

```java
package com.example.project_211.service;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.entity.*;
import com.example.project_211.enums.BookingStatus;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.repository.*;
import com.example.project_211.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourtRepository courtRepository;
    @Mock private TimeSlotRepository timeSlotRepository;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User customer;
    private Court court;
    private TimeSlot slot;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(3L).name(RoleName.ROLE_CUSTOMER).build();
        customer = User.builder().id(1L).username("customer1").role(role).build();
        court = Court.builder().id(1L).name("San so 1")
                .pricePerHour(new BigDecimal("80000")).build();
        slot = TimeSlot.builder().id(3L)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).build();

        request = new BookingRequest();
        request.setCourtId(1L);
        request.setTimeSlotId(3L);
        request.setBookingDate(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("TEST 3 - Dat san thanh cong: trang thai PENDING (UC-04)")
    void createBooking_shouldSucceed_whenSlotIsFree() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));
        when(courtRepository.findById(1L)).thenReturn(Optional.of(court));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(slot));
        when(bookingRepository.existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                anyLong(), anyLong(), any(), anyCollection()))
                .thenReturn(false);                            // slot trong
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });

        BookingResponse result = bookingService.createBooking("customer1", request);

        assertThat(result.getStatus()).isEqualTo("PENDING");   // UC-04 buoc 3
        assertThat(result.getCourtName()).isEqualTo("San so 1");
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("TEST 4 - Trung lich: nem BookingConflictException (409), KHONG luu DB")
    void createBooking_shouldThrow_whenSlotIsTaken() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));
        when(courtRepository.findById(1L)).thenReturn(Optional.of(court));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(slot));
        when(bookingRepository.existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                anyLong(), anyLong(), any(), anyCollection()))
                .thenReturn(true);                             // da co nguoi dat!

        assertThatThrownBy(() -> bookingService.createBooking("customer1", request))
                .isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never()).save(any());        // tuyet doi khong luu
    }
}
```

## 3.3. SERVICE TEST 5 — `service/UserServiceImplTest.java`

```java
package com.example.project_211.service;

import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("TEST 5 - Tim user khong ton tai: nem ResourceNotFoundException (404)")
    void getUserById_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
```

## 3.4. CONTROLLER TEST 6+7 — `controller/AuthControllerTest.java`

```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)               // chi load tang web cua 1 controller
@AutoConfigureMockMvc(addFilters = false)       // tat security filter trong test
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;          // gia lap HTTP request
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean                                 // Boot < 3.4 thi doi thanh @MockBean
    private AuthService authService;

    @Test
    @DisplayName("TEST 6 - POST /register hop le: tra 201 + format ApiResponse")
    void register_shouldReturn201_whenValid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("customer1");
        request.setPassword("123456");
        request.setEmail("customer1@gmail.com");

        UserResponse response = UserResponse.builder()
                .id(1L).username("customer1")
                .email("customer1@gmail.com").role("ROLE_CUSTOMER").build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                       // 201 (SRS)
                .andExpect(jsonPath("$.success").value(true))          // format SRS VI.3
                .andExpect(jsonPath("$.data.username").value("customer1"));
    }

    @Test
    @DisplayName("TEST 7 - Email sai dinh dang: tra 400 + format ErrorResponse")
    void register_shouldReturn400_whenEmailInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("customer1");
        request.setPassword("123456");
        request.setEmail("not-an-email");          // sai format

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())                    // 400 (SRS)
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }
}
```

## 3.5. CONTROLLER TEST 8+9 — `controller/BookingControllerTest.java`

```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    private BookingRequest buildRequest() {
        BookingRequest request = new BookingRequest();
        request.setCourtId(1L);
        request.setTimeSlotId(3L);
        request.setBookingDate(LocalDate.now().plusDays(1));
        return request;
    }

    @Test
    @DisplayName("TEST 8 - Dat san thanh cong: 201 + status PENDING")
    void createBooking_shouldReturn201() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(10L).customerUsername("customer1")
                .courtName("San so 1").status("PENDING").build();
        when(bookingService.createBooking(eq("customer1"), any(BookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/customer/bookings")
                        // gia lap user da dang nhap - khop authentication.getName()
                        .principal(new UsernamePasswordAuthenticationToken("customer1", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("TEST 9 - Trung lich: 409 Conflict dung format SRS")
    void createBooking_shouldReturn409_whenConflict() throws Exception {
        when(bookingService.createBooking(eq("customer1"), any(BookingRequest.class)))
                .thenThrow(new BookingConflictException(
                        "This time slot is already booked for the selected date"));

        mockMvc.perform(post("/api/v1/customer/bookings")
                        .principal(new UsernamePasswordAuthenticationToken("customer1", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isConflict())                      // 409 (SRS)
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}
```

## 3.6. CONTROLLER TEST 10 — `controller/UserControllerTest.java`

```java
package com.example.project_211.controller;

import com.example.project_211.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("TEST 10 - DELETE user: tra 204 No Content, body rong (SRS)")
    void deleteUser_shouldReturn204() throws Exception {
        doNothing().when(userService).deleteUser(5L);

        mockMvc.perform(delete("/api/v1/admin/users/5"))
                .andExpect(status().isNoContent());        // 204 (SRS muc VI.2)
    }
}
```

## 3.7. Chạy toàn bộ test

- **IntelliJ:** chuột phải thư mục `src/test/java` → **Run 'All Tests'**
- **Terminal:** `.\gradlew test` (Windows) — báo cáo HTML tại
  `build/reports/tests/test/index.html`

Mục tiêu: **10/10 PASSED, màu xanh toàn bộ.** Nếu test nào đỏ, đọc message —
thường do tên message exception hoặc field DTO lệch với code thật của bạn,
chỉnh test khớp lại là xong.

---

# ✅ CHECKLIST CUỐI NGÀY 3 + TỔNG KẾT ĐỒ ÁN

**FR-11 (10đ):**
- [ ] `logs/application.log` có `[EXECUTION TIME]` cho mọi method service
- [ ] Đặt sân thành công → `[AUDIT - SUCCESS]`; trùng lịch → `[AUDIT - FAILED]`
- [ ] `BookingServiceImpl` KHÔNG chứa dòng log nào (mở file chứng minh được)

**FR-13 (10đ):**
- [ ] Đã xóa `InMemoryTokenBlacklistService`, app chạy với Redis
- [ ] Logout → token cũ bị 403; `redis-cli KEYS blacklist:*` thấy key có TTL

**FR-12 (20đ):**
- [ ] 5 service test + 5 controller test, tất cả PASSED
- [ ] Mỗi test có `@DisplayName` tiếng Việt mô tả rõ — giảng viên đọc là hiểu

**Hoàn thiện nộp bài:**
- [ ] Viết `README.md`: cách chạy (MySQL + Redis + Cloudinary key), tài khoản admin
  mặc định (`admin/admin123`), export Postman collection đính kèm
- [ ] Commit cuối: `git commit -m "Day 3: AOP timing logs, Redis blacklist, 10 unit tests"`

## 🎤 5 câu hỏi bảo vệ dễ gặp nhất (chuẩn bị sẵn câu trả lời)

1. **"Stateless là gì, tại sao phải stateless?"** → Server không lưu session;
   mỗi request tự chứng minh danh tính bằng JWT → scale ngang được nhiều instance
   mà không cần đồng bộ session.
2. **"AccessToken bị lộ thì sao?"** → Tuổi thọ chỉ 30 phút nên thiệt hại giới hạn;
   nếu user logout thì token vào Blacklist chết ngay lập tức dù còn hạn.
3. **"Tại sao Blacklist dùng Redis mà không dùng MySQL?"** → Check ở MỌI request,
   MySQL sẽ thành nút cổ chai; Redis in-memory O(1) + TTL tự dọn token hết hạn.
4. **"Hai người bấm đặt cùng slot cùng lúc thì sao?"** → Service check tồn tại
   PENDING/CONFIRMED trước khi lưu trong transaction; người sau nhận 409 Conflict.
5. **"AOP hoạt động thế nào?"** → Spring tạo proxy bọc quanh bean service;
   Pointcut khai báo method nào bị cắt; @Around/@AfterReturning/@AfterThrowing
   chạy logic phụ (đo giờ, audit) mà không sửa code nghiệp vụ.

**🏆 Tổng: 60đ cơ bản + 40đ nâng cao = 100 điểm. Chúc bảo vệ thành công!**