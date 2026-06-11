package com.example.project_211.controller;

import com.example.project_211.dto.request.ForgotPasswordRequest;
import com.example.project_211.dto.request.LoginRequest;
import com.example.project_211.dto.request.RefreshTokenRequest;
import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.AuthResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Xu ly dang ky, dang nhap, xoay vong token, dang xuat, quen mat khau
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Dang ky tai khoan moi, tao thanh cong tra ve 201
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse data = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", data));
    }

    // Dang nhap, tra ve access token va refresh token
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Đăng nhập thành công", authService.login(request)));
    }

    // Xoay vong token, dung refresh token de lay cap token moi
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Làm mới token thành công",
                        authService.refreshToken(request)));
    }

    // Dang xuat, thu hoi token
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    // Quen mat khau, sinh mat khau tam thoi
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        String tempPassword = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tạo mật khẩu tạm thời, vui lòng đổi mật khẩu sau khi đăng nhập",
                tempPassword));
    }
}
