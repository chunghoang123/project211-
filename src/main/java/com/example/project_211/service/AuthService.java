package com.example.project_211.service;

import com.example.project_211.dto.request.*;
import com.example.project_211.dto.response.AuthResponse;
import com.example.project_211.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(HttpServletRequest request);
    void changePassword(String username, ChangePasswordRequest request);
    String forgotPassword(ForgotPasswordRequest request);
}