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

// Quan ly nguoi dung, chi vai tro ADMIN duoc truy cap
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Lay danh sach nguoi dung, co tim kiem va phan trang
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("Lấy danh sách người dùng thành công",
                        userService.getUsers(keyword, page, size)));
    }

    // Lay thong tin mot nguoi dung theo ma
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Lấy thông tin người dùng thành công",
                        userService.getUserById(id)));
    }

    // Cap nhat thong tin nguoi dung
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Cập nhật người dùng thành công",
                        userService.updateUser(id, request)));
    }

    // Xoa nguoi dung, tra ve 204 khong co noi dung
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
