package com.example.project_211.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 4, max = 50, message = "Tên đăng nhập phải từ 4 đến 50 ký tự")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    private String fullName;

    @Pattern(regexp = "^(0\\d{9})?$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng số 0")
    private String phone;
}