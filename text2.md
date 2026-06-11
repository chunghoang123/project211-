# 🏸 HƯỚNG DẪN NGÀY 2 — JWT Security + 7 chức năng (35 điểm)

## Mục tiêu hôm nay

| FR | Chức năng | Điểm |
|----|-----------|------|
| FR-01 | Đăng nhập (cấp AccessToken + RefreshToken) | 5đ |
| FR-02 | Xoay vòng token (Refresh) | 5đ |
| FR-03 | Đăng xuất (Revoke → Blacklist) | 5đ |
| FR-07 | Xem lịch sử đặt sân | 5đ |
| FR-08 | Phê duyệt / Từ chối lịch | 5đ |
| FR-09 | Upload nhiều ảnh sân (Cloudinary) | 5đ |
| FR-10 | Đổi mật khẩu / Quên mật khẩu | 5đ |

**Thứ tự làm:** Security trước (Bước 1→6), vì mọi chức năng còn lại đều cần biết
"ai đang gọi API". Buổi sáng xong JWT, buổi chiều quét sạch 4 FR nghiệp vụ.

**Trật tự bài toán JWT** (nắm cái này là nắm 50% Ngày 2):

```
LOGIN:    Client gửi username/password ──> Server check BCrypt
          ──> ký AccessToken (JWT, 30 phút) + tạo RefreshToken (UUID, 7 ngày, lưu DB)
          ──> trả cặp token

REQUEST:  Client gắn "Authorization: Bearer <AccessToken>" vào mọi request
          ──> JwtRequestFilter: token hợp lệ? có trong Blacklist không (403)?
          ──> nạp user vào SecurityContext ──> Spring check role theo URL

REFRESH:  AccessToken hết hạn ──> Client gửi RefreshToken
          ──> Server tra DB, còn hạn ──> cấp AccessToken mới + xoay RefreshToken mới

LOGOUT:   Client gọi /logout kèm AccessToken
          ──> Server tính thời gian sống còn lại của token
          ──> ném vào Blacklist (hôm nay: in-memory, Ngày 3: Redis)
          ──> token đó dù còn hạn cũng bị filter chặn 403
```

---

# BƯỚC 1 — BỔ SUNG NHỎ TỪ NGÀY 1 (≈ 10 phút)

## 1.1. Thêm method vào 3 Repository có sẵn

`UserRepository.java` — thêm:
```java
Optional<User> findByEmail(String email);    // FR-10 quen mat khau
```

`RefreshTokenRepository.java` — thêm:
```java
Optional<RefreshToken> findByUserId(Long userId);
```

`BookingRepository.java` — thêm:
```java
// FR-08: Manager xem danh sach booking theo trang thai
Page<Booking> findByStatus(BookingStatus status, Pageable pageable);
```

## 1.2. Thêm cấu hình Cloudinary vào `application.properties`

Đăng ký tài khoản free tại **cloudinary.com** → Dashboard → copy 3 giá trị:

```properties
# ===== CLOUDINARY (FR-09 / UC-05) =====
cloudinary.cloud-name=YOUR_CLOUD_NAME
cloudinary.api-key=YOUR_API_KEY
cloudinary.api-secret=YOUR_API_SECRET
```

---

# BƯỚC 2 — JWT CORE (≈ 1 tiếng)

## 2.1. `security/JwtUtil.java` — nhà máy sản xuất & kiểm định token

```java
package com.example.project_211.security;

import com.example.project_211.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;                    // >= 32 ky tu = 256-bit (SRS)

    @Value("${jwt.access-expiration}")
    private long accessExpiration;            // 1800000ms = 30 phut

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * UC-01 buoc 4: tao AccessToken chua cac Claims:
     * subject = username, claim "role", issuedAt, expiration.
     */
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().getName().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())   // verify chu ky - sai key la nem JwtException
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /**
     * UC-03 buoc 2: tinh thoi gian song CON LAI cua token (ms)
     * -> dung lam TTL khi nem vao Blacklist.
     */
    public long getRemainingTime(String token) {
        return parse(token).getExpiration().getTime() - System.currentTimeMillis();
    }

    /** Token hop le = dung chu ky + chua het han. */
    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;   // sai chu ky, het han, token rac... deu vao day
        }
    }
}
```

## 2.2. `security/CustomUserDetailsService.java` — cầu nối DB ↔ Spring Security

```java
package com.example.project_211.security;

import com.example.project_211.entity.User;
import com.example.project_211.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UC-01 buoc 2: Spring Security goi loadUserByUsername()
 * de lay user tu DB roi tu so sanh BCrypt.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())          // chuoi BCrypt trong DB
                .disabled(!user.isActive())            // tai khoan bi khoa -> chan login
                .authorities(user.getRole().getName().name())  // "ROLE_CUSTOMER"
                .build();
    }
}
```

## 2.3. `service/TokenBlacklistService.java` — interface "đổi ruột được"

> **Thiết kế quan trọng cho Ngày 3:** Logout chỉ gọi qua interface này.
> Hôm nay ruột là in-memory Map, Ngày 3 thay ruột bằng Redis —
> **không phải sửa 1 dòng nào** ở chỗ khác.

```java
package com.example.project_211.service;

public interface TokenBlacklistService {

    /** Dua token vao danh sach den, tu het hieu luc sau ttlMillis. */
    void blacklist(String token, long ttlMillis);

    /** Filter goi ham nay o MOI request. */
    boolean isBlacklisted(String token);
}
```

## 2.4. `service/impl/InMemoryTokenBlacklistService.java` (tạm cho Ngày 2)

```java
package com.example.project_211.service.impl;

import com.example.project_211.service.impl.TokenBlacklistService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⚠️ Phien ban TAM Ngay 2: luu blacklist trong RAM cua app.
 * Ngay 3 se XOA file nay, thay bang RedisTokenBlacklistService (FR-13).
 */
@Service
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    // token -> thoi diem het han (epoch millis)
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String token, long ttlMillis) {
        blacklist.put(token, System.currentTimeMillis() + ttlMillis);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {   // het han thi don luon
            blacklist.remove(token);
            return false;
        }
        return true;
    }
}
```

---

# BƯỚC 3 — FILTER + 2 "NGƯỜI GÁC CỔNG" LỖI (≈ 50 phút)

## 3.1. `security/JwtRequestFilter.java`

> ⚠️ **Cố ý KHÔNG đánh dấu `@Component`** — sẽ khởi tạo thủ công trong SecurityConfig.
> Lý do: Ngày 3 viết controller test bằng `@WebMvcTest`, nếu filter là @Component
> nó sẽ bị kéo vào test và đòi đủ thứ bean. Tách ra thì test nhẹ tênh.

```java
package com.example.project_211.security;

import com.example.project_211.dto.response.ErrorResponse;
import com.example.project_211.service.impl.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Khong co header Bearer -> cho di tiep (endpoint public van chay,
        //    endpoint can quyen se bi EntryPoint tra 401 sau)
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        // 2. Token sai chu ky / het han -> khong set context -> 401 o EntryPoint
        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. ⭐ UC-03: token nam trong Blacklist -> CHAN NGAY, tra 403
        //    (ke ca token chua het han ve mat thoi gian)
        if (tokenBlacklistService.isBlacklisted(token)) {
            writeError(response, request, "Token has been revoked");
            return;
        }

        // 4. Token sach -> nap user vao SecurityContext de Spring check role
        String username = jwtUtil.extractUsername(token);
        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // 403 theo SRS
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(403)
                .error("Forbidden")
                .message(message)
                .path(request.getRequestURI())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
```

## 3.2. `security/JwtAuthEntryPoint.java` — trả 401 dạng JSON chuẩn SRS

```java
package com.example.project_211.security;

import com.example.project_211.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Kich hoat khi request vao endpoint can dang nhap ma:
 * thieu token / token sai chu ky / token het han -> 401 (SRS muc VI.2).
 * Khong co class nay, Spring tra loi mac dinh xau xi khong dung format.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(401)
                .error("Unauthorized")
                .message("Missing or invalid access token")
                .path(request.getRequestURI())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
```

## 3.3. `security/CustomAccessDeniedHandler.java` — trả 403 khi sai role

```java
package com.example.project_211.security;

import com.example.project_211.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Kich hoat khi user DA dang nhap nhung khong du quyen
 * (VD: CUSTOMER goi /api/v1/admin/**) -> 403 (dung vi du trong SRS).
 */
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(403)
                .error("Forbidden")
                .message("You do not have permission to access this resource")
                .path(request.getRequestURI())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
```

---

# BƯỚC 4 — SECURITYCONFIG "THẬT" (≈ 30 phút)

**Thay toàn bộ** `config/SecurityConfig.java` của Ngày 1 bằng:

```java
package com.example.project_211.config;

import com.example.project_211.security.*;
import com.example.project_211.service.impl.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);     // SRS: BCrypt strength >= 10
    }

    /** Bean nay de AuthService goi authenticate() khi login (UC-01). */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Filter tu khoi tao (khong phai @Component) - xem giai thich o Buoc 3.1
        JwtRequestFilter jwtFilter = new JwtRequestFilter(
                jwtUtil, userDetailsService, tokenBlacklistService, objectMapper);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // SRS: Stateless
            .exceptionHandling(e -> e
                .authenticationEntryPoint(jwtAuthEntryPoint)      // 401 JSON
                .accessDeniedHandler(accessDeniedHandler))        // 403 JSON
            .authorizeHttpRequests(auth -> auth
                // Logout phai dang nhap (FR-03: Authenticated) - dat TRUOC auth/**
                .requestMatchers("/api/v1/auth/logout").authenticated()
                // Login, register, refresh, forgot-password: Public
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Xem san + khung gio: Public (khach can xem truoc khi dat)
                .requestMatchers(HttpMethod.GET, "/api/v1/courts/**",
                                 "/api/v1/time-slots/**").permitAll()
                // Doi mat khau: chi can dang nhap, role nao cung duoc (FR-10)
                .requestMatchers("/api/v1/users/me/**").authenticated()
                // ⭐ MA TRAN PHAN QUYEN SRS (muc IV.2)
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers("/api/v1/customer/**").hasRole("CUSTOMER")
                // SRS: "Ngoai may duong dan tren thi deu truy cap duoc khong can dang nhap"
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

> 📌 **2 chỗ hay bị hỏi khi bảo vệ:**
> - `hasRole("ADMIN")` tự thêm prefix → khớp authority `ROLE_ADMIN` trong DB.
> - `/manager/**` dùng `hasAnyRole("MANAGER","ADMIN")` vì **FR-08 ghi rõ phân quyền
    >   "Admin, Manager"** — Admin phải duyệt được lịch, nếu để mỗi MANAGER là sai đề.

---

# BƯỚC 5 — FR-01, 02, 03: LOGIN / REFRESH / LOGOUT (≈ 1 tiếng 15 phút)

## 5.1. Các DTO mới

`dto/request/LoginRequest.java`
```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

`dto/request/RefreshTokenRequest.java`
```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefreshTokenRequest {
    @NotBlank(message = "refreshToken is required")
    private String refreshToken;
}
```

`dto/response/AuthResponse.java`
```java
package com.example.project_211.dto.response;

import lombok.*;

/** UC-01 buoc 5: tra cap token dang JSON. */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;     // "Bearer"
}
```

## 5.2. Exception mới: `exception/InvalidTokenException.java`

```java
package com.example.project_211.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) { super(message); }
}
```

## 5.3. Cập nhật `service/AuthService.java`

```java
package com.example.project_211.service;

import com.example.project_211.dto.request.*;
import com.example.project_211.dto.response.AuthResponse;
import com.example.project_211.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    UserResponse register(RegisterRequest request);          // Ngay 1
    AuthResponse login(LoginRequest request);                // FR-01
    AuthResponse refreshToken(RefreshTokenRequest request);  // FR-02
    void logout(HttpServletRequest request);                 // FR-03
    void changePassword(String username, ChangePasswordRequest request);  // FR-10
    String forgotPassword(ForgotPasswordRequest request);    // FR-10
}
```

## 5.4. Cập nhật `service/impl/AuthServiceImpl.java` — thêm các method sau

(Giữ nguyên `register` của Ngày 1, **thêm field mới + các method dưới**)

```java
// ===== THEM CAC FIELD MOI (inject qua constructor Lombok) =====
private final AuthenticationManager authenticationManager;
private final JwtUtil jwtUtil;
private final RefreshTokenRepository refreshTokenRepository;
private final TokenBlacklistService tokenBlacklistService;

@Value("${jwt.refresh-expiration}")
private long refreshExpiration;          // 604800000ms = 7 ngay

// ===== FR-01: LOGIN (UC-01) =====
@Override
@Transactional
public AuthResponse login(LoginRequest request) {
    // 1+2+3. Spring Security chan dau vao, goi loadUserByUsername(),
    //        dung PasswordEncoder so sanh BCrypt.
    //        Sai -> nem AuthenticationException -> handler tra 401 (UC-01 luong ngoai le)
    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    request.getUsername(), request.getPassword()));

    User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    // 4. Tao AccessToken (JWT) + RefreshToken (UUID luu DB)
    String accessToken = jwtUtil.generateAccessToken(user);
    String refreshToken = createOrRotateRefreshToken(user);

    // 5. Tra cap token
    return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .build();
}

/** Moi user chi giu 1 refresh token: co roi thi XOAY (thay chuoi + gia han). */
private String createOrRotateRefreshToken(User user) {
    RefreshToken rt = refreshTokenRepository.findByUserId(user.getId())
            .orElse(RefreshToken.builder().user(user).build());
    rt.setToken(UUID.randomUUID().toString());
    rt.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
    refreshTokenRepository.save(rt);
    return rt.getToken();
}

// ===== FR-02: REFRESH TOKEN =====
@Override
@Transactional
public AuthResponse refreshToken(RefreshTokenRequest request) {
    RefreshToken rt = refreshTokenRepository.findByToken(request.getRefreshToken())
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

    // Refresh token het han -> xoa luon, bat dang nhap lai
    if (rt.getExpiryDate().isBefore(Instant.now())) {
        refreshTokenRepository.delete(rt);
        throw new InvalidTokenException("Refresh token expired. Please login again");
    }

    User user = rt.getUser();
    String newAccessToken = jwtUtil.generateAccessToken(user);
    String newRefreshToken = createOrRotateRefreshToken(user);   // FR-02: "xoay vong"

    return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .build();
}

// ===== FR-03: LOGOUT (UC-03) =====
@Override
@Transactional
public void logout(HttpServletRequest request) {
    // 1. Trich AccessToken tu Header (UC-03 buoc 2)
    String header = request.getHeader("Authorization");
    String token = header.substring(7);   // filter da dam bao co "Bearer "

    // 2. Tinh thoi gian song con lai cua token
    long remaining = jwtUtil.getRemainingTime(token);

    // 3. Nem vao Blacklist voi TTL = thoi gian con lai (UC-03 buoc 3)
    if (remaining > 0) {
        tokenBlacklistService.blacklist(token, remaining);
    }

    // 4. Thu hoi luon RefreshToken de khong refresh duoc nua
    String username = jwtUtil.extractUsername(token);
    userRepository.findByUsername(username).ifPresent(user ->
            refreshTokenRepository.findByUserId(user.getId())
                    .ifPresent(refreshTokenRepository::delete));
}
```

> **Import cần thêm:** `org.springframework.security.authentication.AuthenticationManager`,
> `UsernamePasswordAuthenticationToken`, `org.springframework.beans.factory.annotation.Value`,
> `java.time.Instant`, `java.util.UUID`, entity/repo tương ứng.

## 5.5. Cập nhật `controller/AuthController.java` — thêm 3 endpoint

```java
// FR-01: POST /api/v1/auth/login -> 200 OK + cap token
@PostMapping("/login")
public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(
            ApiResponse.success("Login successfully", authService.login(request)));
}

// FR-02: POST /api/v1/auth/refresh (SRS tu dat ten endpoint nay)
@PostMapping("/refresh")
public ResponseEntity<ApiResponse<AuthResponse>> refresh(
        @Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(
            ApiResponse.success("Token refreshed successfully",
                    authService.refreshToken(request)));
}

// FR-03: POST /api/v1/auth/logout -> 200 OK (UC-03 buoc 4)
@PostMapping("/logout")
public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
    authService.logout(request);
    return ResponseEntity.ok(ApiResponse.success("Logout successfully", null));
}
```

## 5.6. Bổ sung `GlobalExceptionHandler` — thêm 2 handler

```java
// 401 - sai username/password (UC-01: "khong tra thong tin chi tiet de bao mat")
@ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
public ResponseEntity<ErrorResponse> handleAuthFail(
        org.springframework.security.core.AuthenticationException ex,
        HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", req);
}

// 401 - refresh token sai / het han
@ExceptionHandler(InvalidTokenException.class)
public ResponseEntity<ErrorResponse> handleInvalidToken(
        InvalidTokenException ex, HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
}
```

## 5.7. ⭐ Sửa `BookingController` — bỏ username tạm, lấy từ JWT

Sửa method `createBooking` (xóa `@RequestParam String username`):

```java
import org.springframework.security.core.Authentication;

@PostMapping
public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
        Authentication authentication,                 // Spring tu inject tu JWT
        @Valid @RequestBody BookingRequest request) {
    String username = authentication.getName();        // lay tu SecurityContext
    BookingResponse data = bookingService.createBooking(username, request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Booking created successfully", data));
}
```

### 🚀 Checkpoint giữa ngày — test luồng JWT bằng Postman:
1. `POST /api/v1/auth/login` `{"username":"admin","password":"admin123"}` → 200, nhận 2 token
2. Gọi `GET /api/v1/admin/users` **không có token** → **401** đúng format JSON
3. Gắn Header `Authorization: Bearer <accessToken>` → **200**
4. Đăng ký + login `customer1`, lấy token customer gọi `/api/v1/admin/users` → **403**
5. Customer đặt sân `POST /api/v1/customer/bookings` (có token, KHÔNG còn ?username) → **201**
6. `POST /api/v1/auth/refresh` với refreshToken → **200**, cặp token mới
7. `POST /api/v1/auth/logout` (kèm access token) → 200; **gọi lại API bằng token cũ → 403 "Token has been revoked"** ⭐

---

# BƯỚC 6 — FR-07: LỊCH SỬ ĐẶT SÂN + FR-08: DUYỆT/TỪ CHỐI (≈ 50 phút)

## 6.1. DTO: `dto/request/BookingStatusRequest.java`

```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookingStatusRequest {
    @NotBlank(message = "status is required")
    private String status;        // "CONFIRMED" hoac "REJECTED"
}
```

## 6.2. Cập nhật `service/BookingService.java`

```java
public interface BookingService {
    BookingResponse createBooking(String username, BookingRequest request);

    // FR-07: lich su dat san cua chinh minh
    PageResponse<BookingResponse> getMyBookings(String username, int page, int size);

    // FR-08: Manager/Admin xem danh sach (loc theo status) + duyet/tu choi
    PageResponse<BookingResponse> getBookings(String status, int page, int size);
    BookingResponse updateStatus(Long bookingId, String status);
}
```

## 6.3. Thêm vào `BookingServiceImpl`

```java
// ===== FR-07: Xem lich su dat san =====
@Override
@Transactional(readOnly = true)
public PageResponse<BookingResponse> getMyBookings(String username, int page, int size) {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<Booking> bookingPage = bookingRepository.findByUserId(user.getId(), pageable);

    // ⭐ Stream API (UC-02): map Entity -> DTO
    List<BookingResponse> content = bookingPage.getContent().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

    return PageResponse.<BookingResponse>builder()
            .content(content)
            .page(bookingPage.getNumber())
            .size(bookingPage.getSize())
            .totalElements(bookingPage.getTotalElements())
            .totalPages(bookingPage.getTotalPages())
            .build();
}

// ===== FR-08: Manager xem danh sach booking (loc status) =====
@Override
@Transactional(readOnly = true)
public PageResponse<BookingResponse> getBookings(String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    Page<Booking> bookingPage;
    if (status == null || status.isBlank()) {
        bookingPage = bookingRepository.findAll(pageable);
    } else {
        BookingStatus bs = parseStatus(status);
        bookingPage = bookingRepository.findByStatus(bs, pageable);
    }

    List<BookingResponse> content = bookingPage.getContent().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

    return PageResponse.<BookingResponse>builder()
            .content(content)
            .page(bookingPage.getNumber())
            .size(bookingPage.getSize())
            .totalElements(bookingPage.getTotalElements())
            .totalPages(bookingPage.getTotalPages())
            .build();
}

// ===== FR-08: Phe duyet / Tu choi =====
@Override
@Transactional
public BookingResponse updateStatus(Long bookingId, String status) {
    Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Booking not found with id: " + bookingId));

    BookingStatus newStatus = parseStatus(status);

    // Chi cho phep CONFIRMED hoac REJECTED (theo State Diagram SRS)
    if (newStatus != BookingStatus.CONFIRMED && newStatus != BookingStatus.REJECTED) {
        throw new IllegalArgumentException(
                "Status must be CONFIRMED or REJECTED");           // -> 400
    }

    // Chi booking dang PENDING moi duoc duyet/tu choi
    if (booking.getStatus() != BookingStatus.PENDING) {
        throw new BookingConflictException(
                "Only PENDING bookings can be approved or rejected");  // -> 409
    }

    booking.setStatus(newStatus);
    return toResponse(bookingRepository.save(booking));
}

private BookingStatus parseStatus(String status) {
    try {
        return BookingStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid status: " + status);   // -> 400
    }
}
```

## 6.4. FR-07 — thêm endpoint vào `BookingController`

```java
// FR-07: GET /api/v1/customer/bookings?page=0&size=10
@GetMapping
public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getMyBookings(
        Authentication authentication,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok(ApiResponse.success("Fetched booking history successfully",
            bookingService.getMyBookings(authentication.getName(), page, size)));
}
```

## 6.5. FR-08 — controller mới: `controller/ManagerBookingController.java`

```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingStatusRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.service.impl.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager/bookings")   // MANAGER + ADMIN (theo FR-08)
@RequiredArgsConstructor
public class ManagerBookingController {

    private final BookingService bookingService;

    // GET /api/v1/manager/bookings?status=PENDING&page=0&size=10
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Fetched bookings successfully",
                bookingService.getBookings(status, page, size)));
    }

    // FR-08: PUT /api/v1/manager/bookings/5/status  body: {"status":"CONFIRMED"}
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BookingResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking status updated successfully",
                bookingService.updateStatus(id, request.getStatus())));
    }
}
```

## 6.6. Bổ sung `GlobalExceptionHandler` — thêm handler 400

```java
// 400 - tham so khong hop le (status sai, v.v.)
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
}
```

---

# BƯỚC 7 — FR-09: UPLOAD NHIỀU ẢNH SÂN QUA CLOUDINARY (≈ 50 phút)

> **Luồng UC-05:** Client gửi `multipart/form-data` → Controller nhận `MultipartFile[]`
> → Service validate (PNG/JPG, ≤ 5MB) → gọi `cloudinary.uploader().upload()` qua HTTPS
> → nhận `secure_url` → lưu vào bảng `court_images` → trả 200 + danh sách URL.
> Lỗi cloud (mất mạng, sai API key) → **503** với message đúng nguyên văn SRS.

## 7.1. `config/CloudinaryConfig.java`

```java
package com.example.project_211.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));        // tra ve secure_url (HTTPS)
    }
}
```

## 7.2. Exception: `exception/CloudStorageException.java`

```java
package com.example.project_211.exception;

// UC-05 luong ngoai le: loi ket noi cloud -> 503
public class CloudStorageException extends RuntimeException {
    public CloudStorageException(String message) { super(message); }
}
```

Thêm handler vào `GlobalExceptionHandler`:

```java
// 503 - dich vu cloud loi (UC-05) - message dung NGUYEN VAN SRS
@ExceptionHandler(CloudStorageException.class)
public ResponseEntity<ErrorResponse> handleCloudError(
        CloudStorageException ex, HttpServletRequest req) {
    return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);
}

// 400 - file vuot 10MB (gioi han trong application.properties)
@ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
public ResponseEntity<ErrorResponse> handleMaxSize(
        org.springframework.web.multipart.MaxUploadSizeExceededException ex,
        HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "File size exceeds the limit", req);
}
```

## 7.3. `service/FileStorageService.java` + `service/impl/CloudinaryServiceImpl.java`

```java
package com.example.project_211.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String upload(MultipartFile file);    // tra ve secure URL
}
```

```java
package com.example.project_211.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.project_211.exception.CloudStorageException;
import com.example.project_211.service.impl.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements FileStorageService {

    private final Cloudinary cloudinary;

    private static final long MAX_SIZE = 5 * 1024 * 1024;   // UC-05: duoi 5MB

    @Override
    public String upload(MultipartFile file) {
        // Validate dinh dang + dung luong (UC-05) -> sai thi 400
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/png") && !contentType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("Only PNG/JPG images are allowed");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Image must be under 5MB");
        }

        try {
            // UC-05 buoc 3-4: SDK truyen file len cloud qua HTTPS
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "badminton_courts"));

            // UC-05 buoc 5: cloud tra ve secure URL
            return (String) result.get("secure_url");

        } catch (IOException e) {
            // UC-05 luong ngoai le: mat ket noi / sai secret key -> 503
            throw new CloudStorageException(
                "Cloud storage service is temporarily unavailable. Please try again later.");
        }
    }
}
```

## 7.4. `service/CourtService.java` + impl

```java
package com.example.project_211.service;

import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CourtService {
    List<CourtResponse> getAllCourts();                          // Public
    List<TimeSlotResponse> getAllTimeSlots();                    // Public
    List<String> uploadCourtImages(Long courtId, MultipartFile[] files);  // FR-09
}
```

DTO mới — `dto/response/CourtResponse.java`:
```java
package com.example.project_211.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CourtResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal pricePerHour;
    private boolean active;
    private List<String> images;     // danh sach URL anh (FR-09)
}
```

`dto/response/TimeSlotResponse.java`:
```java
package com.example.project_211.dto.response;

import lombok.*;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TimeSlotResponse {
    private Long id;
    private LocalTime startTime;
    private LocalTime endTime;
}
```

`service/impl/CourtServiceImpl.java`:
```java
package com.example.project_211.service.impl;

import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import com.example.project_211.entity.Court;
import com.example.project_211.entity.CourtImage;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.CourtImageRepository;
import com.example.project_211.repository.CourtRepository;
import com.example.project_211.repository.TimeSlotRepository;
import com.example.project_211.service.impl.CourtService;
import com.example.project_211.service.impl.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

    private final CourtRepository courtRepository;
    private final CourtImageRepository courtImageRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponse> getAllCourts() {
        // ⭐ Stream API (UC-02): filter san dang hoat dong -> map sang DTO -> collect
        return courtRepository.findAll().stream()
                .filter(Court::isActive)
                .map(c -> CourtResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .pricePerHour(c.getPricePerHour())
                        .active(c.isActive())
                        .images(c.getImages().stream()
                                .map(CourtImage::getImageUrl)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getAllTimeSlots() {
        return timeSlotRepository.findAll().stream()
                .map(t -> TimeSlotResponse.builder()
                        .id(t.getId())
                        .startTime(t.getStartTime())
                        .endTime(t.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * FR-09: upload NHIEU anh cho 1 san.
     * Moi file: upload Cloudinary lay URL -> tao CourtImage -> luu DB.
     */
    @Override
    @Transactional
    public List<String> uploadCourtImages(Long courtId, MultipartFile[] files) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Court not found with id: " + courtId));

        // ⭐ Stream API: upload tung file -> URL -> CourtImage entity
        List<CourtImage> images = Arrays.stream(files)
                .map(fileStorageService::upload)               // file -> secure URL
                .map(url -> CourtImage.builder()
                        .imageUrl(url)
                        .court(court)
                        .build())
                .collect(Collectors.toList());

        courtImageRepository.saveAll(images);

        return images.stream()
                .map(CourtImage::getImageUrl)
                .collect(Collectors.toList());
    }
}
```

## 7.5. Hai controller mới

`controller/CourtController.java` (public — khách xem sân + khung giờ trước khi đặt):
```java
package com.example.project_211.controller;

import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import com.example.project_211.service.impl.CourtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    // GET /api/v1/courts - dung vi du chuan trong SRS muc VI.1
    @GetMapping("/courts")
    public ResponseEntity<ApiResponse<List<CourtResponse>>> getCourts() {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched courts successfully", courtService.getAllCourts()));
    }

    // GET /api/v1/time-slots
    @GetMapping("/time-slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getTimeSlots() {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched time slots successfully", courtService.getAllTimeSlots()));
    }
}
```

`controller/ManagerCourtController.java` (FR-09 — quyền Manager):
```java
package com.example.project_211.controller;

import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.service.impl.CourtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/courts")
@RequiredArgsConstructor
public class ManagerCourtController {

    private final CourtService courtService;

    /**
     * FR-09: POST /api/v1/manager/courts/1/images
     * multipart/form-data, key "files", chon NHIEU file -> 200 + danh sach URL (UC-05)
     */
    @PostMapping("/{id}/images")
    public ResponseEntity<ApiResponse<List<String>>> uploadImages(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.ok(ApiResponse.success(
                "Images uploaded successfully",
                courtService.uploadCourtImages(id, files)));
    }
}
```

---

# BƯỚC 8 — FR-10: ĐỔI / QUÊN MẬT KHẨU (≈ 40 phút)

## 8.1. DTO

`dto/request/ChangePasswordRequest.java`:
```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChangePasswordRequest {
    @NotBlank(message = "oldPassword is required")
    private String oldPassword;

    @NotBlank(message = "newPassword is required")
    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;
}
```

`dto/request/ForgotPasswordRequest.java`:
```java
package com.example.project_211.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ForgotPasswordRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email invalid format")
    private String email;
}
```

## 8.2. Thêm vào `AuthServiceImpl`

```java
// ===== FR-10: Doi mat khau (Authenticated) =====
@Override
@Transactional
public void changePassword(String username, ChangePasswordRequest request) {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    // Kiem tra mat khau cu bang BCrypt matches()
    if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
        throw new IllegalArgumentException("Old password is incorrect");   // -> 400
    }

    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
}

// ===== FR-10: Quen mat khau (Public) =====
@Override
@Transactional
public String forgotPassword(ForgotPasswordRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException(
                    "No account found with this email"));

    // Sinh mat khau tam 8 ky tu ngau nhien
    String tempPassword = UUID.randomUUID().toString().substring(0, 8);
    user.setPassword(passwordEncoder.encode(tempPassword));
    userRepository.save(user);

    // Demo do an: tra thang mat khau tam ve response.
    // (San pham that se gui qua email - co the noi voi giang vien y nay)
    return tempPassword;
}
```

## 8.3. Endpoint

Thêm vào `AuthController` (forgot — Public):
```java
// FR-10: POST /api/v1/auth/forgot-password
@PostMapping("/forgot-password")
public ResponseEntity<ApiResponse<String>> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request) {
    String tempPassword = authService.forgotPassword(request);
    return ResponseEntity.ok(ApiResponse.success(
            "Temporary password generated. Please change it after login",
            tempPassword));
}
```

Controller mới `controller/UserProfileController.java` (change — Authenticated, mọi role):
```java
package com.example.project_211.controller;

import com.example.project_211.dto.request.ChangePasswordRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.service.impl.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")        // matcher .authenticated() trong SecurityConfig
@RequiredArgsConstructor
public class UserProfileController {

    private final AuthService authService;

    // FR-10: PUT /api/v1/users/me/password
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Object>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(
                "Password changed successfully", null));
    }
}
```

---

# ✅ CHECKLIST CUỐI NGÀY 2 (test Postman đủ các case)

**FR-01 Login (5đ):**
- [ ] Login đúng → 200 + accessToken + refreshToken
- [ ] Login sai password → 401, message chung chung "Invalid username or password"
- [ ] Token customer gọi `/api/v1/admin/**` → 403; không token → 401

**FR-02 Refresh (5đ):**
- [ ] Refresh hợp lệ → 200 + cặp token MỚI (chuỗi refresh cũ hết dùng được — xoay vòng)
- [ ] Refresh token bịa → 401 "Invalid refresh token"

**FR-03 Logout (5đ):**
- [ ] Logout → 200; gọi lại API bằng token cũ → **403 "Token has been revoked"** ⭐
- [ ] Refresh token của user đó cũng chết theo

**FR-07 (5đ):** customer xem được lịch sử của CHÍNH MÌNH, có phân trang

**FR-08 (5đ):**
- [ ] Manager/Admin duyệt PENDING → CONFIRMED (200)
- [ ] Duyệt booking đã CONFIRMED → 409; status bịa → 400
- [ ] Sau khi REJECTED, khách khác đặt lại được đúng slot đó → 201 ⭐ (vì check trùng chỉ tính PENDING/CONFIRMED)

**FR-09 (5đ):**
- [ ] Upload 2-3 ảnh cùng lúc (Postman: form-data, key `files`, type File, chọn nhiều file) → 200 + list URL
- [ ] Mở URL trên trình duyệt thấy ảnh; `GET /api/v1/courts` thấy mảng images
- [ ] Upload file .txt → 400; sửa api-secret thành sai rồi upload → **503** đúng message SRS

**FR-10 (5đ):**
- [ ] Đổi mật khẩu sai oldPassword → 400; đúng → login lại được bằng mật khẩu mới
- [ ] Quên mật khẩu → nhận mật khẩu tạm → login được

- [ ] Commit: `git commit -m "Day 2: JWT security + FR-01,02,03,07,08,09,10"`

**Xong Ngày 2 = 60/60 điểm cơ bản.** Tối nay nếu còn sức: cài **Docker Desktop**
(hoặc Memurai) để mai chạy Redis cho FR-13.