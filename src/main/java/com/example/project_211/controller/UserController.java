package com.example.project_211.controller;

import com.example.project_211.dto.request.UserRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")    // Prefix /admin -> Ngay 2 chi ADMIN duoc vao
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // GET /api/v1/admin/users?keyword=abc&page=0&size=5
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("Fetched users successfully",
                        userService.getUsers(keyword, page, size)));
    }

    // GET /api/v1/admin/users/5
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Fetched user successfully",
                        userService.getUserById(id)));
    }

    // PUT /api/v1/admin/users/5 -> 200 OK (SRS: PUT cap nhat tra 200)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Updated user successfully",
                        userService.updateUser(id, request)));
    }

    // DELETE /api/v1/admin/users/5 -> 204 No Content (SRS: xoa khong tra body)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}