package com.example.project_211.service;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import jdk.jfr.Registered;

public interface AuthService {
    UserResponse register(RegisterRequest request);

}
