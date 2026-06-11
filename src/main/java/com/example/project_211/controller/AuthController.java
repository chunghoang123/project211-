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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse data = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registered successfully", data));
    }// FR-01: POST /api/v1/auth/login -> 200 OK + cap token
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
    // FR-10: POST /api/v1/auth/forgot-password
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        String tempPassword = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Temporary password generated. Please change it after login",
                tempPassword));
    }

}