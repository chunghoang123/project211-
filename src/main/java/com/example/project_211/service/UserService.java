package com.example.project_211.service;

import com.example.project_211.dto.request.UserRequest;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.dto.response.UserResponse;

public interface UserService {
    PageResponse<UserResponse> getUsers(String keyword, int page, int size);
    UserResponse getUserById(Long id);
    UserResponse updateUser(Long id, UserRequest request);
    void deleteUser(Long id);
}