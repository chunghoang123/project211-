package com.example.project_211.service.impl;

import com.example.project_211.dto.request.*;
import com.example.project_211.dto.response.AuthResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.RefreshToken;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.DuplicateResourceException;
import com.example.project_211.exception.InvalidTokenException;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RefreshTokenRepository;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.security.JwtUtil;
import com.example.project_211.service.AuthService;
import com.example.project_211.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor   // Lombok tu sinh constructor -> Spring tu inject (DI chuan)
public class AuthServiceImpl implements AuthService {

    // ===== THEM CAC FIELD MOI (inject qua constructor Lombok) =====
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;          // 604800000ms = 7 ngay

    // đăng ký tài khoản
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Customer role not found"));

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(customerRole)
                .build();

        return toUserResponse(userRepository.save(user));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().getName().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

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
}
