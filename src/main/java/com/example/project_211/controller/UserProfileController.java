package com.example.project_211.controller;

import com.example.project_211.dto.request.ChangePasswordRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.service.AuthService;
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