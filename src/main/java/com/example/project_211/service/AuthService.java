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