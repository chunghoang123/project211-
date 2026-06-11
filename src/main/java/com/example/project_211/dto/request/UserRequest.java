package com.example.project_211.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    private String fullName;

    @Pattern(regexp = "^(0\\d{9})?$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng số 0")
    private String phone;

    private Boolean active;      // Admin khoa / mo khoa tai khoan

    @NotBlank(message = "Vai trò không được để trống")
    private String role;         // "ROLE_ADMIN" | "ROLE_MANAGER" | "ROLE_CUSTOMER"
}
