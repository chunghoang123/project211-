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
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // Thoi gian song cua refresh token (mili giay)
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // Dang ky tai khoan moi
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Kiem tra trung ten dang nhap
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Tên đăng nhập đã tồn tại");
        }
        // Kiem tra trung email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email đã tồn tại");
        }

        // Tai khoan dang ky mac dinh la khach hang
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy vai trò khách hàng"));

        // Ma hoa mat khau truoc khi luu
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

    // Chuyen Entity sang DTO tra ve
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

    // Dang nhap, cap access token va refresh token
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Spring Security kiem tra ten dang nhap va mat khau, sai se nem loi 401
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng"));

        // Tao access token va refresh token
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = createOrRotateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    // Moi nguoi dung chi giu mot refresh token, co roi thi thay chuoi moi va gia han
    private String createOrRotateRefreshToken(User user) {
        RefreshToken rt = refreshTokenRepository.findByUserId(user.getId())
                .orElse(RefreshToken.builder().user(user).build());
        rt.setToken(UUID.randomUUID().toString());
        rt.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        refreshTokenRepository.save(rt);
        return rt.getToken();
    }

    // Xoay vong token, dung refresh token de lay cap token moi
    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken rt = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException(
                        "Refresh token không hợp lệ"));

        // Refresh token het han thi xoa va bat dang nhap lai
        if (rt.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt);
            throw new InvalidTokenException(
                    "Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }

        User user = rt.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = createOrRotateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .build();
    }

    // Dang xuat, thu hoi access token va refresh token
    @Override
    @Transactional
    public void logout(HttpServletRequest request) {
        // Lay access token tu header
        String header = request.getHeader("Authorization");
        String token = header.substring(7);

        // Tinh thoi gian song con lai cua token
        long remaining = jwtUtil.getRemainingTime(token);

        // Dua token vao danh sach den voi thoi gian het han bang thoi gian con lai
        if (remaining > 0) {
            tokenBlacklistService.blacklist(token, remaining);
        }

        // Xoa luon refresh token de khong xoay vong duoc nua
        String username = jwtUtil.extractUsername(token);
        userRepository.findByUsername(username).ifPresent(user ->
                refreshTokenRepository.findByUserId(user.getId())
                        .ifPresent(refreshTokenRepository::delete));
    }

    // Doi mat khau khi da dang nhap
    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng"));

        // So sanh mat khau cu bang BCrypt
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // Quen mat khau, sinh mat khau tam thoi
    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản với email này"));

        // Sinh mat khau tam thoi 8 ky tu
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        // Voi do an thi tra mat khau tam ve response, san pham that se gui qua email
        return tempPassword;
    }
}
